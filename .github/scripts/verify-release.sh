#!/usr/bin/env bash
set -euo pipefail

: "${VERSION_NAME:?Missing VERSION_NAME}"
: "${VERSION_CODE:?Missing VERSION_CODE}"
: "${EXPECTED_APP_ID:?Missing EXPECTED_APP_ID}"
: "${EXPECTED_SIGNER_SHA256:?Missing EXPECTED_SIGNER_SHA256}"
tools="${ANDROID_HOME:?Missing ANDROID_HOME}/build-tools/36.0.0"
signer_tool="$tools/apksigner"
if [ ! -f "$signer_tool" ] && [ -f "$tools/apksigner.bat" ]; then
  signer_tool="$tools/apksigner.bat"
fi
dist=tmp/distribution
checks=tmp/distribution-checks
mkdir -p "$dist" "$checks"

for abi in arm64-v8a armeabi-v7a; do
  apk="$dist/MemosM-$VERSION_NAME-$abi.apk"
  if [ "${RESTORED:-false}" != true ]; then
    cp "app/build/outputs/apk/release/app-$abi-release.apk" "$apk"
  fi
  test -s "$apk"
  "$signer_tool" verify --verbose --print-certs "$apk" > "$checks/signature-$abi.txt"
  signer="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$checks/signature-$abi.txt" | tr -d '\r')"
  if [ "$signer" != "$EXPECTED_SIGNER_SHA256" ]; then
    echo "::error::Release signer differs from the published MemosM key: $abi"
    exit 1
  fi
  badging="$("$tools/aapt2" dump badging "$apk")"
  printf '%s\n' "$badging" > "$checks/badging-$abi.txt"
  if [[ "$badging" != *"package: name='$EXPECTED_APP_ID' versionCode='$VERSION_CODE' versionName='$VERSION_NAME'"* ]]; then
    echo "::error::Unexpected application ID or version: $abi"
    exit 1
  fi
  abis="$(unzip -Z1 "$apk" | awk -F/ '$1 == "lib" && NF == 3 { print $2 }' | sort -u)"
  if [ "$abis" != "$abi" ]; then
    echo "::error::Unexpected native ABI: $abis"
    exit 1
  fi
  "$tools/aapt2" dump resources "$apk" > "$checks/resources-$abi.txt"
  grep -F 'raw/aboutlibraries' "$checks/resources-$abi.txt" > /dev/null
  bytes="$(wc -c < "$apk")"
  if [ "$abi" = arm64-v8a ] && [ "$bytes" -gt 12500000 ]; then
    echo "::warning::ARM64 APK exceeds the 12.5 MB investigation threshold: $bytes bytes"
  fi
  printf '%s: %s bytes; release signer verified\n' "$abi" "$bytes"
done

mapping="$dist/MemosM-$VERSION_NAME-mapping.txt.gz"
checksums="SHA256SUMS-$VERSION_NAME.txt"
if [ "${RESTORED:-false}" != true ]; then
  source=app/build/outputs/mapping/release
  test -s "$source/mapping.txt"
  for type in \
    org.example.memosm.data.sync.ReactionOpPayload \
    org.example.memosm.ui.component.composer.EditorRequest \
    org.example.memosm.ui.component.composer.EditorSnapshot \
    org.example.memosm.ui.component.composer.EditorAttachment \
    org.example.memosm.ui.component.composer.StoredEditorSession \
    org.example.memosm.viewmodel.delegates.EditorMemoCommit; do
    grep -F "$type -> $type:" "$source/mapping.txt" > /dev/null
    grep -F "$type" "$source/configuration.txt" > /dev/null
  done
  gzip -n -c "$source/mapping.txt" > "$mapping"
  (
    cd "$dist"
    sha256sum "MemosM-$VERSION_NAME-arm64-v8a.apk" \
      "MemosM-$VERSION_NAME-armeabi-v7a.apk" "MemosM-$VERSION_NAME-mapping.txt.gz" > "$checksums"
  )
fi
test -s "$mapping"
gzip -t "$mapping"
(
  cd "$dist"
  sha256sum --check "$checksums"
)
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    printf '### Verified APKs\n\nVersion: `%s` (%s). Application ID: `%s`.\n\n' "$VERSION_NAME" "$VERSION_CODE" "$EXPECTED_APP_ID"
    printf 'Both ARM APKs use the existing release signer. R8 mapping and SHA-256 checksums are included.\n'
  } >> "$GITHUB_STEP_SUMMARY"
fi
