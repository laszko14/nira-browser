#!/usr/bin/env python3
"""Offline verification of an existing GitHub Actions APK ZIP; never builds an APK."""
import argparse
import hashlib
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path, PurePosixPath

HEX = re.compile(r"[0-9a-f]{64}\Z")

class ArtifactError(ValueError):
    pass

def verify_archive(archive, expected_digest, temp_dir=None):
    digest = expected_digest.removeprefix("sha256:")
    if not HEX.fullmatch(digest):
        raise ArtifactError("EXPECTED_DIGEST_REQUIRED")
    p = Path(archive)
    if not p.is_file():
        raise ArtifactError("ZIP_MISSING")
    sha = hashlib.sha256()
    with p.open("rb") as f:
        for part in iter(lambda: f.read(1024 * 1024), b""):
            sha.update(part)
    if sha.hexdigest() != digest:
        raise ArtifactError("ZIP_DIGEST_MISMATCH")
    try:
        with zipfile.ZipFile(p) as archive_zip:
            members = [item for item in archive_zip.infolist() if not item.is_dir()]
            if len(members) != 1:
                raise ArtifactError("EXPECTED_EXACTLY_ONE_APK")
            item = members[0]
            name = PurePosixPath(item.filename)
            mode = item.external_attr >> 16
            if name.name != item.filename or not name.name.endswith(".apk") or "\\" in item.filename:
                raise ArtifactError("UNSAFE_APK_ZIP_PATH")
            if item.file_size <= 0 or (mode & 0o170000) == 0o120000:
                raise ArtifactError("UNSAFE_APK_MEMBER")
            if item.file_size > 800 * 1024 * 1024:
                raise ArtifactError("APK_EXCESSIVE_SIZE")
            if archive_zip.testzip() is not None:
                raise ArtifactError("ZIP_CRC_FAILURE")
            if temp_dir is None:
                raise ArtifactError("TEMP_DIRECTORY_REQUIRED")
            path = Path(temp_dir) / name.name
            with archive_zip.open(item) as source, path.open("wb") as dest:
                for part in iter(lambda: source.read(1024 * 1024), b""):
                    dest.write(part)
            return path
    except (zipfile.BadZipFile, EOFError, OSError) as error:
        raise ArtifactError("CORRUPT_ZIP") from error

def verify_binary(zip_path, digest, verifier, package, version_code, version_name, cert):
    if not HEX.fullmatch(cert):
        raise ArtifactError("INVALID_CERTIFICATE")
    if not isinstance(version_code, int) or version_code <= 0:
        raise ArtifactError("INVALID_VERSION_CODE")
    script = Path(verifier)
    if not script.is_file():
        raise ArtifactError("TRUSTED_VERIFIER_NOT_FOUND")
    with tempfile.TemporaryDirectory(prefix="zavro-apk-verify-") as temporary:
        apk_path = verify_archive(zip_path, digest, temporary)
        result = subprocess.run(
            ["bash", str(script), str(apk_path), package, str(version_code), version_name, cert],
            capture_output=True, text=True, timeout=100, check=False
        )
        if result.returncode != 0:
            raise ArtifactError("APKSIGNER_AAPT_VERIFICATION_FAILED")
        evidence = (f"Verified package={package} versionCode={version_code} "
                    f"versionName={version_name} ABI=arm64-v8a certificateSHA256={cert}")
        if evidence not in result.stdout:
            raise ArtifactError("APK_VALIDATION_EVIDENCE_MISSING")
    return True

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--archive", required=True)
    p.add_argument("--zip-sha256", required=True, help="digest read from trusted GitHub artifact metadata")
    p.add_argument("--verifier", required=True, help="trusted pinned scripts/verify-update-apk.sh")
    p.add_argument("--package", required=True)
    p.add_argument("--version-code", required=True, type=int)
    p.add_argument("--version-name", required=True)
    p.add_argument("--certificate-sha256", required=True)
    a = p.parse_args()
    try:
        verify_binary(a.archive, a.zip_sha256, a.verifier, a.package, a.version_code, a.version_name, a.certificate_sha256)
    except ArtifactError as error:
        print("APK_ARTIFACT_INVALID: " + str(error), file=sys.stderr)
        return 1
    print("APK_ARTIFACT_INDEPENDENTLY_VERIFIED: ZIP digest, CRC, one APK, arm64-v8a, package, version and certificate")
    return 0

if __name__ == "__main__":
    sys.exit(main())
