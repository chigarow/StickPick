#!/bin/sh
set -e
./gradlew assembleDebug
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
