# Zavro x MPV v1138

Fix the simulated Gecko Port disconnect test: the VM harness lacked setTimeout although production Android content scripts have it. No functional interception behavior changed from v1136. v1137 had already corrected historical version-specific tests.

The package is still com.prirai.android.nira.mpv; ARM64 versionCode 1138; versionName 0.5.22-alpha44-mpv12; manifest content script extension 1.5.4. Signing verification enforces the existing SHA256 certificate. No stable Zavro changes and no merge.

For phone verification and remaining limitations, see docs/ZAVRO_X_MPV_V1136_STATUS.md.
