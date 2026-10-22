#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="$ROOT_DIR/scripts/verify-update-apk.sh"
EXPECTED_CERT="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

mkdir -p "$tmp_dir/android/build-tools/99.0.0" "$tmp_dir/apk/lib/arm64-v8a"
printf 'native' > "$tmp_dir/apk/lib/arm64-v8a/libnira.so"
(cd "$tmp_dir/apk" && zip -q -r "$tmp_dir/nira.apk" .)

cat > "$tmp_dir/android/build-tools/99.0.0/apksigner" <<EOF
#!/usr/bin/env bash
echo 'Verifies'
echo 'Signer #1 certificate SHA-256 digest: $EXPECTED_CERT'
EOF
chmod +x "$tmp_dir/android/build-tools/99.0.0/apksigner"

cat > "$tmp_dir/android/build-tools/99.0.0/aapt" <<'EOF'
#!/usr/bin/env bash
echo "package: name='com.prirai.android.nira.debug' versionCode='1098' versionName='0.5.22-alpha4'"
EOF
chmod +x "$tmp_dir/android/build-tools/99.0.0/aapt"

ANDROID_HOME="$tmp_dir/android" bash "$VERIFY_SCRIPT" \
  "$tmp_dir/nira.apk" \
  'com.prirai.android.nira.debug' \
  '1098' \
  '0.5.22-alpha4' \
  "$EXPECTED_CERT"

if ANDROID_HOME="$tmp_dir/android" bash "$VERIFY_SCRIPT" \
  "$tmp_dir/nira.apk" \
  'com.prirai.android.nira.debug' \
  '1098' \
  '0.5.22-alpha4' \
  "${EXPECTED_CERT%?}0" >/dev/null 2>&1; then
  echo 'Expected certificate mismatch to fail.' >&2
  exit 1
fi

mkdir -p "$tmp_dir/x86/lib/x86_64"
printf 'native' > "$tmp_dir/x86/lib/x86_64/libbad.so"
(cd "$tmp_dir/x86" && zip -q -u "$tmp_dir/nira.apk" lib/x86_64/libbad.so)

if ANDROID_HOME="$tmp_dir/android" bash "$VERIFY_SCRIPT" \
  "$tmp_dir/nira.apk" \
  'com.prirai.android.nira.debug' \
  '1098' \
  '0.5.22-alpha4' \
  "$EXPECTED_CERT" >/dev/null 2>&1; then
  echo 'Expected non-arm64 ABI to fail.' >&2
  exit 1
fi

echo 'verify-update-apk tests passed'
