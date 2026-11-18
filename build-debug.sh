#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-"$HOME/Library/Android/sdk"}"
BUILD_TOOLS="${BUILD_TOOLS:-"$SDK/build-tools/36.1.0"}"
JAVA_HOME="${JAVA_HOME:-"/Applications/Android Studio.app/Contents/jbr/Contents/Home"}"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
OUT="$ROOT/build/manual"
GEN="$OUT/generated"
CLASSES="$OUT/classes"
DEX="$OUT/dex"
APK_DIR="$ROOT/app/build/outputs/apk/debug"
UNALIGNED="$OUT/app-debug-unaligned.apk"
SIGNED="$APK_DIR/app-debug.apk"
KEYSTORE="$OUT/debug.keystore"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$BUILD_TOOLS:$PATH"

rm -rf "$OUT"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$APK_DIR"

sed 's/<manifest /<manifest package="com.codex.hotspotvpnproxy" /' \
    "$ROOT/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

"$BUILD_TOOLS/aapt2" compile \
    --dir "$ROOT/app/src/main/res" \
    -o "$OUT/resources.zip"

"$BUILD_TOOLS/aapt2" link \
    -o "$OUT/linked.apk" \
    --manifest "$OUT/AndroidManifest.xml" \
    --java "$GEN" \
    --custom-package com.codex.hotspotvpnproxy \
    --min-sdk-version 23 \
    --target-sdk-version 36 \
    --version-code 1 \
    --version-name 1.0 \
    --debug-mode \
    --auto-add-overlay \
    -I "$ANDROID_JAR" \
    -R "$OUT/resources.zip"

find "$ROOT/app/src/main/java" "$GEN" -name '*.java' | sort \
    | while IFS= read -r source_file; do
        printf '"%s"\n' "$source_file"
    done > "$OUT/sources.txt"

javac \
    -source 8 \
    -target 8 \
    -classpath "$ANDROID_JAR" \
    -d "$CLASSES" \
    @"$OUT/sources.txt"

jar cf "$OUT/classes.jar" -C "$CLASSES" .

"$BUILD_TOOLS/d8" \
    --lib "$ANDROID_JAR" \
    --min-api 23 \
    --output "$DEX" \
    "$OUT/classes.jar"

cp "$OUT/linked.apk" "$UNALIGNED"
(cd "$DEX" && zip -q -r "$UNALIGNED" classes.dex)

"$BUILD_TOOLS/zipalign" -f -p 4 "$UNALIGNED" "$OUT/app-debug-aligned.apk"

keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" \
    >/dev/null

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$SIGNED" \
    "$OUT/app-debug-aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$SIGNED"
echo "$SIGNED"
