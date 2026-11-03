#!/usr/bin/env bash
# Archives the app without code signing. The result must be signed before installation
# (Sideloadly, AltStore, a developer certificate via resign-ipa.sh, etc.).
set -euo pipefail
mkdir -p build
set -o pipefail
xcodebuild archive \
  -workspace ForPDA.xcworkspace \
  -scheme ForPDA \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/ForPDA.xcarchive \
  -skipPackagePluginValidation \
  -skipMacroValidation \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  FORPDA_CODE_SIGNING_ALLOWED=NO \
  COMPILER_INDEX_STORE_ENABLE=NO \
  | tee build/archive.log | grep -E "error:|warning: .*deprecated|\*\* ARCHIVE" || true
if ! grep -q "ARCHIVE SUCCEEDED" build/archive.log; then
  echo "::group::Last 300 lines of archive log"
  tail -300 build/archive.log
  echo "::endgroup::"
  grep -E "error:" build/archive.log | sort -u | head -100
  exit 1
fi
