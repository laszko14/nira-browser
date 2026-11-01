"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const root = path.resolve(__dirname, "../../../..");
const gradle = fs.readFileSync(path.join(root, "app/build.gradle"), "utf8");
const workflow = fs.readFileSync(path.join(root, ".github/workflows/apk-release.yml"), "utf8");
const verifier = fs.readFileSync(path.join(root, "scripts/verify-update-apk.sh"), "utf8");
const { resolveReleaseContract, writeGitHubEnv } =
  require(path.join(root, "scripts/resolve-release-contract.cjs"));
const { assertUpdateApkContract } = require("./helpers/update-apk-contract.cjs");

test("stage19A current debug contract stays update-compatible", () => {
  const update = assertUpdateApkContract(gradle, workflow);
  assert.equal(update.installCode, 1203);
  assert.equal(update.versionName, "0.5.22-alpha108-clean");
});

test("stage19A release version derives from actual Gradle checkout (not old release constants)", () => {
  const value = resolveReleaseContract(gradle);
  assert.deepEqual(value, {
    packageName: "com.prirai.android.nira",
    versionCode: 1203,
    versionName: "0.5.22-alpha108-clean",
  });
  // Simulate checkout at a DIFFERENT tag with a different Gradle version.
  const olderTag = gradle
    .replace("versionCode = 203", "versionCode = 182")
    .replace('versionName = "0.5.22-alpha108-clean"', 'versionName = "0.5.22-alpha87-clean"');
  assert.deepEqual(resolveReleaseContract(olderTag), {
    packageName: "com.prirai.android.nira",
    versionCode: 1182,
    versionName: "0.5.22-alpha87-clean",
  });
});

test("stage19A rejects unsupported ABI, package, malformed metadata and untrusted names", () => {
  assert.throws(() => resolveReleaseContract(
    gradle.replace('applicationId = "com.prirai.android.nira"', 'applicationId = "example.invalid"')),
    /applicationId/);
  assert.throws(() => resolveReleaseContract(
    gradle.replace('"arm64-v8a": 1', '"arm64-v8a": 2')),
    /multiplier/);
  assert.throws(() => resolveReleaseContract(
    gradle.replace("universalApk = false", "universalApk = true")),
    /ABI split/);
  assert.throws(() => resolveReleaseContract(
    gradle.replace('versionName = "0.5.22-alpha108-clean"', 'versionName = "bad name"')),
    /Unsafe/);
  assert.throws(() => resolveReleaseContract(gradle.replace(
    "versionCode = 203", "versionCode = 203\n        versionCode = 203")),
    /exactly one versionCode/);
});

test("stage19A writes only version to GitHub env, not certificates, secrets or package", () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "zavro-release-contract-"));
  try {
    const envFile = path.join(tempDir, "env");
    writeGitHubEnv(resolveReleaseContract(gradle), envFile);
    assert.equal(fs.readFileSync(envFile, "utf8"),
      "EXPECTED_VERSION_CODE=1203\nEXPECTED_VERSION_NAME=0.5.22-alpha108-clean\n");
    assert.throws(() => writeGitHubEnv(resolveReleaseContract(gradle), ""), /GITHUB_ENV/);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test("stage19A release job validates tag before checkout, then resolves version from tag content", () => {
  const release = workflow.split(/\n  release:\s*\n/)[1];
  assert.ok(release, "release job must exist");
  assert.match(release, /REQUESTED_TAG: \$\{\{ github\.event\.inputs\.tag \}\}/);
  assert.match(release, /set -euo pipefail\s+tag="\$\{REQUESTED_TAG:-\$\{GITHUB_REF_NAME\}\}"/);
  assert.match(release, /\[\[ ! "\$tag" =~ \^v\[0-9\]\+/);
  assert.match(release, /ref: refs\/tags\/\$\{\{ steps\.tag\.outputs\.tag \}\}/);
  assert.doesNotMatch(release, /ref: \$\{\{ steps\.tag\.outputs\.tag \}\}/);
  assert.match(release, /run: node scripts\/resolve-release-contract\.cjs/);
  assert.doesNotMatch(release, /^\s*EXPECTED_VERSION_(CODE|NAME):/gm);
  const checkout = release.indexOf("ref: refs/tags/");
  const resolve = release.indexOf("run: node scripts/resolve-release-contract.cjs");
  const build = release.indexOf("name: Build and verify one signed arm64 release APK");
  assert.ok(0 <= checkout && checkout < resolve && resolve < build);
});

test("stage19A stable certificate, strict verification and release package remain unchanged", () => {
  const build = workflow.split(/\n  build:\s*\n/)[1].split(/\n  lint:\s*\n/)[0];
  const release = workflow.split(/\n  release:\s*\n/)[1];
  const certificate = "df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00";
  for (const job of [build, release]) {
    assert.ok(job.includes("EXPECTED_CERT_SHA256: " + certificate));
    assert.match(job, /ANDROID_KEYSTORE_BASE64/);
    assert.match(job, /KEYSTORE_PASSWORD/);
    assert.match(job, /KEY_ALIAS/);
    assert.match(job, /KEY_PASSWORD/);
    assert.match(job, /scripts\/verify-update-apk\.sh/);
    assert.match(job, /arm64-v8a/);
  }
  assert.match(release, /EXPECTED_PACKAGE: com\.prirai\.android\.nira\s*$/m);
  assert.match(build, /EXPECTED_PACKAGE: com\.prirai\.android\.nira\.debug\s*$/m);
  assert.match(verifier, /Number of signers:/);
  assert.match(verifier, /certificate SHA-256 digest:/);
  assert.match(verifier, /Signing certificate SHA-256 does not match/);
  assert.match(verifier, /versionCode='\$expected_version_code'/);
  assert.match(verifier, /versionName='\$expected_version_name'/);
  assert.match(verifier, /must contain only arm64-v8a/);
});
