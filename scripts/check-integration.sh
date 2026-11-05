#!/usr/bin/env bash
# Copyright 2026 SmartUnit contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO="$ROOT/examples/maven-demo"
SOURCE="$DEMO/src/main/java/dev/smartunit/demo/PriceCalculator.java"
TMP="$(mktemp -d)"
cp "$SOURCE" "$TMP/PriceCalculator.java"
cleanup() { cp "$TMP/PriceCalculator.java" "$SOURCE"; rm -rf "$TMP"; }
trap cleanup EXIT
command -v mvn >/dev/null || { echo 'Maven required for end-to-end smoke test' >&2; exit 2; }

mvn -B -ntp -f "$ROOT/pom.xml" -DskipTests install
rm -rf "$DEMO/.smartunit"
mvn -B -ntp -f "$DEMO/pom.xml" clean test \
  -Dsmartunit.mode=record -Dsmartunit.includes=dev.smartunit.demo \
  -Dsmartunit.verbose=true | tee "$TMP/record.log"
test -s "$DEMO/.smartunit/cache.tsv"
grep -q '\[SmartUnit\] RECORDED' "$TMP/record.log"

mvn -B -ntp -f "$DEMO/pom.xml" test \
  -Dsmartunit.mode=select -Dsmartunit.includes=dev.smartunit.demo \
  -Dsmartunit.verbose=true | tee "$TMP/select.log"
grep -q '\[SmartUnit\] SKIP' "$TMP/select.log"

# Audit logs a predicted skip, but still executes the actual test.
mvn -B -ntp -f "$DEMO/pom.xml" test \
  -Dsmartunit.mode=audit -Dsmartunit.includes=dev.smartunit.demo | tee "$TMP/audit.log"
grep -q '\[SmartUnit\] WOULD_SKIP' "$TMP/audit.log"

# A bytecode-changing edit to an actually executed class must cause RUN.
python3 - "$SOURCE" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
original = 'return unitPrice * quantity;'
assert original in s
p.write_text(s.replace(original, 'if (quantity == Integer.MIN_VALUE) throw new AssertionError();\n        ' + original))
PY
mvn -B -ntp -f "$DEMO/pom.xml" test \
  -Dsmartunit.mode=select -Dsmartunit.includes=dev.smartunit.demo \
  -Dsmartunit.verbose=true | tee "$TMP/changed.log"
grep -q 'DEPENDENCY_CHANGED' "$TMP/changed.log"

# Changing selection policy invalidates earlier fingerprints.
mvn -B -ntp -f "$DEMO/pom.xml" test \
  -Dsmartunit.mode=select -Dsmartunit.policy=conservative \
  -Dsmartunit.includes=dev.smartunit.demo -Dsmartunit.verbose=true | tee "$TMP/conservative.log"
grep -q 'SCOPE_CHANGED' "$TMP/conservative.log"

echo 'PASS: record, skip, audit, changed code, conservative policy, ordinary untagged tests'
