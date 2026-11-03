#!/usr/bin/env bash
# Re-signs an unsigned IPA with a certificate (.p12) and a provisioning profile.
# The profile must cover the app and its extension bundle IDs (a wildcard profile works best).
# Env: IOS_P12_BASE64, IOS_P12_PASSWORD, IOS_PROFILE_BASE64
set -euo pipefail
IN="$1"; OUT="$2"
WORK=$(mktemp -d)
KEYCHAIN="$WORK/signing.keychain-db"
KC_PASS=$(uuidgen)
echo "$IOS_P12_BASE64" | base64 --decode > "$WORK/cert.p12"
echo "$IOS_PROFILE_BASE64" | base64 --decode > "$WORK/profile.mobileprovision"
security create-keychain -p "$KC_PASS" "$KEYCHAIN"
security set-keychain-settings -lut 3600 "$KEYCHAIN"
security unlock-keychain -p "$KC_PASS" "$KEYCHAIN"
security import "$WORK/cert.p12" -k "$KEYCHAIN" -P "${IOS_P12_PASSWORD:-}" -T /usr/bin/codesign
security set-key-partition-list -S apple-tool:,apple: -s -k "$KC_PASS" "$KEYCHAIN" >/dev/null
security list-keychains -d user -s "$KEYCHAIN" $(security list-keychains -d user | tr -d '"')
IDENTITY=$(security find-identity -v -p codesigning "$KEYCHAIN" | head -1 | awk '{print $2}')
[ -n "$IDENTITY" ] || { echo "No signing identity in p12"; exit 1; }

security cms -D -i "$WORK/profile.mobileprovision" > "$WORK/profile.plist"
/usr/libexec/PlistBuddy -x -c "Print :Entitlements" "$WORK/profile.plist" > "$WORK/entitlements.plist"

unzip -q "$IN" -d "$WORK/x"
APP=$(ls -d "$WORK"/x/Payload/*.app)
cp "$WORK/profile.mobileprovision" "$APP/embedded.mobileprovision"
for f in "$APP"/Frameworks/*; do
  codesign --force --sign "$IDENTITY" --keychain "$KEYCHAIN" "$f"
done
for p in "$APP"/PlugIns/*.appex; do
  [ -d "$p" ] || continue
  cp "$WORK/profile.mobileprovision" "$p/embedded.mobileprovision"
  codesign --force --sign "$IDENTITY" --keychain "$KEYCHAIN" --entitlements "$WORK/entitlements.plist" "$p"
done
codesign --force --sign "$IDENTITY" --keychain "$KEYCHAIN" --entitlements "$WORK/entitlements.plist" "$APP"
codesign --verify --deep --strict "$APP"
( cd "$WORK/x" && zip -qry "$WORK/signed.ipa" Payload )
mv "$WORK/signed.ipa" "$OUT"
security delete-keychain "$KEYCHAIN"
echo "Signed IPA: $OUT"
