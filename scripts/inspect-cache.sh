#!/usr/bin/env bash
# Copyright 2026 SmartUnit contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# No Maven needed; compile core classes in a temporary directory.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 17 -d "$TMP" "$ROOT"/src/main/java/dev/smartunit/core/*.java
java -cp "$TMP" dev.smartunit.core.CacheInspector "${1:-.smartunit/cache.tsv}" "${@:2}"
