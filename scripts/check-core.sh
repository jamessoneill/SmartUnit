#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/selftest
javac -d target/selftest src/main/java/dev/smartunit/core/*.java src/test/java/dev/smartunit/core/CoreSelfTest.java
java -cp target/selftest dev.smartunit.core.CoreSelfTest
