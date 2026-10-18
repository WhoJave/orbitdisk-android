#!/bin/sh
# No Android SDK, Gradle, network or third-party test framework required.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=javac
    JAVA=java
fi
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/orbitdisk-scanner-test.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$JAVAC" -encoding UTF-8 -d "$BUILD" \
    "$ROOT"/tests/java/android/system/*.java \
    "$ROOT/app/src/main/java/dev/orbitdisk/app/StorageScanner.java" \
    "$ROOT/app/src/main/java/dev/orbitdisk/app/ScanSnapshot.java" \
    "$ROOT/tests/java/dev/orbitdisk/app/ScanSnapshotTest.java" \
    "$ROOT/tests/java/dev/orbitdisk/app/StorageScannerTest.java"
"$JAVA" -ea -cp "$BUILD" dev.orbitdisk.app.StorageScannerTest

"$JAVA" -ea -cp "$BUILD" dev.orbitdisk.app.ScanSnapshotTest
