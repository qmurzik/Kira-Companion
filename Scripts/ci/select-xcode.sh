#!/usr/bin/env bash
# Selects the newest stable Xcode installed on the runner.
set -euo pipefail
XCODE=$(ls -d /Applications/Xcode_*.app 2>/dev/null | grep -v -i beta | sort -V | tail -1 || true)
if [ -z "$XCODE" ]; then XCODE=/Applications/Xcode.app; fi
echo "Using $XCODE"
sudo xcode-select -s "$XCODE/Contents/Developer"
xcodebuild -version
swift --version
