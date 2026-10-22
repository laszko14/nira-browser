#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 5 ]]; then
  echo "Usage: $0 APK EXPECTED_PACKAGE EXPECTED_VERSION_CODE EXPECTED_VERSION_NAME EXPECTED_CERT_SHA256" >&2
  exit 64
fi

apk_path="$1"
expected_package="$2"
expected_version_code="$3"
expected_version_name="$4"
expected_cert="$(printf '%s' "$5" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"

if [[ ! -f "$apk_path" ]]; then
  echo "APK not found: $apk_path" >&2
  exit 1
fi

if [[ ! "$expected_cert" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Expected certificate SHA-256 must contain exactly 64 hexadecimal characters." >&2
  exit 1
fi

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$android_home" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must be configured." >&2
  exit 1
fi

apksigner="$(find "$android_home/build-tools" -maxdepth 2 -type f -name apksigner | sort -V | tail -n 1)"
aapt="$(find "$android_home/build-tools" -maxdepth 2 -type f -name aapt | sort -V | tail -n 1)"
if [[ -z "$apksigner" || -z "$aapt" ]]; then
  echo "Android build-tools (apksigner and aapt) were not found." >&2
  exit 1
fi

signer_output="$("$apksigner" verify --verbose --print-certs "$apk_path")"
mapfile -t signer_certs < <(printf '%s\n' "$signer_output" | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p')
if [[ "${#signer_certs[@]}" -ne 1 ]]; then
  echo "APK must have exactly one signing certificate; found ${#signer_certs[@]}." >&2
  exit 1
fi
actual_cert="$(printf '%s' "${signer_certs[0]}" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
if [[ "$actual_cert" != "$expected_cert" ]]; then
  echo "Signing certificate SHA-256 does not match the project certificate." >&2
  exit 1
fi

badging="$("$aapt" dump badging "$apk_path")"
package_line="$(printf '%s\n' "$badging" | sed -n '1p')"
for expected_field in \
  "name='$expected_package'" \
  "versionCode='$expected_version_code'" \
  "versionName='$expected_version_name'"; do
  if [[ "$package_line" != *"$expected_field"* ]]; then
    echo "APK metadata mismatch: expected $expected_field." >&2
    exit 1
  fi
done

mapfile -t apk_abis < <(unzip -Z1 "$apk_path" | awk -F/ '/^lib\/[^/]+\// {print $2}' | sort -u)
if [[ "${#apk_abis[@]}" -ne 1 || "${apk_abis[0]}" != "arm64-v8a" ]]; then
  printf 'APK must contain only arm64-v8a native libraries; found: %s\n' "${apk_abis[*]:-none}" >&2
  exit 1
fi

printf 'Verified package=%s versionCode=%s versionName=%s ABI=arm64-v8a certificateSHA256=%s\n' \
  "$expected_package" "$expected_version_code" "$expected_version_name" "$actual_cert"
