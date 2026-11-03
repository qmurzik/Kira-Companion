#!/usr/bin/env bash
# Runs the app's unit-test targets on the first available iPhone simulator.
set -euo pipefail
DEVICE_ID=$(xcrun simctl list devices available -j | python3 -c '
import json,sys
d=json.load(sys.stdin)["devices"]
c=[(k,x) for k,v in d.items() if "iOS" in k for x in v if x["name"].startswith("iPhone")]
c.sort(key=lambda t:t[0])
print(c[-1][1]["udid"] if c else "")')
[ -n "$DEVICE_ID" ] || { echo "No iPhone simulator found"; xcrun simctl list devices; exit 1; }
echo "Simulator: $DEVICE_ID"
mkdir -p build
# Tuist generates a "<Workspace>-Workspace" scheme whose test action contains every test target.
SCHEME=$(xcodebuild -list -json -workspace ForPDA.xcworkspace 2>/dev/null | python3 -c '
import json,sys
s=json.load(sys.stdin)["workspace"]["schemes"]
w=[x for x in s if x.endswith("-Workspace")]
print(w[0] if w else "")')
[ -n "$SCHEME" ] || { echo "No workspace scheme found"; exit 1; }
echo "Scheme: $SCHEME"
set +e
xcodebuild test \
  -workspace ForPDA.xcworkspace \
  -scheme "$SCHEME" \
  -destination "id=$DEVICE_ID" \
  -skipPackagePluginValidation -skipMacroValidation \
  CODE_SIGNING_ALLOWED=NO FORPDA_CODE_SIGNING_ALLOWED=NO \
  > build/test.log 2>&1
STATUS=$?
set -e
grep -E "Test Suite .* (passed|failed)|Executed [0-9]+ test|error:|\*\* TEST" build/test.log | tail -80
[ $STATUS -eq 0 ] || tail -150 build/test.log
exit $STATUS
