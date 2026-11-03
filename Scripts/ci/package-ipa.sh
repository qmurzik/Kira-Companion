#!/usr/bin/env bash
# Packs <archive>/Products/Applications/*.app into an IPA (Payload/<App>.app) and verifies it.
set -euo pipefail
ARCHIVE="$1"; OUT="$2"
APP=$(ls -d "$ARCHIVE"/Products/Applications/*.app | head -1)
[ -d "$APP" ] || { echo "No .app in archive"; exit 1; }
WORK=$(mktemp -d)
mkdir -p "$WORK/Payload"
cp -R "$APP" "$WORK/Payload/"
APP_NAME=$(basename "$APP")
( cd "$WORK" && zip -qry "ipa.zip" Payload )
mkdir -p "$(dirname "$OUT")"
mv "$WORK/ipa.zip" "$OUT"

echo "== Verifying IPA structure"
LIST=$(unzip -l "$OUT")
echo "$LIST" | grep -q "Payload/$APP_NAME/Info.plist" || { echo "Missing Info.plist"; exit 1; }
EXEC=$(/usr/libexec/PlistBuddy -c "Print :CFBundleExecutable" "$APP/Info.plist")
echo "$LIST" | grep -q "Payload/$APP_NAME/$EXEC\$" || { echo "Missing executable $EXEC"; exit 1; }
BUNDLE_ID=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$APP/Info.plist")
VERSION=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$APP/Info.plist")
echo "App: $APP_NAME  executable: $EXEC  bundle id: $BUNDLE_ID  version: $VERSION"
echo "Frameworks:"; ls "$APP/Frameworks" 2>/dev/null | sed 's/^/  /' || true
echo "PlugIns:"; ls "$APP/PlugIns" 2>/dev/null | sed 's/^/  /' || true
lipo -info "$APP/$EXEC"
ls -lh "$OUT"
rm -rf "$WORK"
