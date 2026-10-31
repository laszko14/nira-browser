#!/usr/bin/env bash
# GV-G4D: read-only on-device identity check. Run locally ONLY after operator opts in.
set -euo pipefail
stop() { printf 'BLOCKED: %s\n' "$1" >&2; exit 3; }
if [ "$#" -ne 2 ]; then
  echo 'Usage: bash preflight-device.sh ADB_SERIAL ANDROID_SDK_PATH' >&2
  exit 64
fi
serial="$1"
sdk="$2"
[ -n "$serial" ] || stop 'An explicit device serial is required'
command -v adb >/dev/null || stop 'ADB unavailable'
[ -d "$sdk/build-tools" ] || stop 'Android SDK build-tools unavailable'
signer="$(find "$sdk/build-tools" -maxdepth 2 -type f -name apksigner | sort -V | tail -n 1)"
aapt="$(find "$sdk/build-tools" -maxdepth 2 -type f -name aapt | sort -V | tail -n 1)"
[ -n "$signer" ] && [ -n "$aapt" ] || stop 'apksigner/aapt unavailable'
[ "$(adb -s "$serial" get-state | tr -d '\r')" = device ] || stop 'Device not authorized/online'
[ "$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" = 35 ] || stop 'Expected Android 15 (SDK 35)'
paths="$(adb -s "$serial" shell pm path com.prirai.android.nira.debug | tr -d '\r')"
base="$(printf '%s\n' "$paths" | grep -E '^package:/data/app/.*/base\.apk$' | sed 's/^package://' || true)"
[ -n "$base" ] && [ "$(printf '%s\n' "$base" | wc -l | tr -d ' ')" = 1 ] || stop 'Unique installed base.apk not accessible'
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
adb -s "$serial" pull "$base" "$temp/installed.apk" >/dev/null || stop 'Cannot read installed APK; no fallback'
[ -s "$temp/installed.apk" ] || stop 'Installed APK copy empty'
meta="$("$aapt" dump badging "$temp/installed.apk" | head -n 1)"
for field in "name='com.prirai.android.nira.debug'" "versionCode='1195'" "versionName='0.5.22-alpha100-clean'"; do
  [[ "$meta" == *"$field"* ]] || stop 'Installed package/version differs from 1195 baseline'
done
verified="$("$signer" verify --verbose --print-certs "$temp/installed.apk")" || stop 'Installed APK signature verification failed'
printf '%s\n' "$verified" | grep -Eq '^[[:space:]]*Number of signers:[[:space:]]*1[[:space:]]*$' || stop 'Expected one installed APK signer'
cert="$(printf '%s\n' "$verified" | sed -nE 's/^.*Signer[[:space:]]*#[0-9]+:?[[:space:]]+certificate SHA-256 digest:[[:space:]]*//p' | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[ "$cert" = df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 ] || stop 'Installed signing certificate differs from G4C certificate'
printf 'INSTALLED_PACKAGE_VERSION_SIGNER=MATCH\n'
printf 'CANDIDATE_1196=VERIFIED_IN_G4C_CI_ONLY\n'
printf 'RESTORABLE_FULL_BACKUP=NOT_VERIFIED\n'
printf 'INSTALLATION=NOT_AUTHORIZED\n'
