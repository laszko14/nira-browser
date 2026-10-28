"use strict";

const assert = require("node:assert/strict");

/**
 * Assert the signed update contract, not a particular historical version.
 * Gradle's abiBaseMap adds 1000 to arm64's base versionCode.
 * A version bump must update workflow verification and artifact identity together.
 */
function assertUpdateApkContract(gradle, workflow) {
  const versionCode = gradle.match(/^\s*versionCode\s*=\s*(\d+)\s*$/m);
  const versionName = gradle.match(/^\s*versionName\s*=\s*"([^"]+)"\s*$/m);
  assert.ok(versionCode, "Gradle must declare an integer versionCode");
  assert.ok(versionName, "Gradle must declare a versionName");
  const baseCode = Number(versionCode[1]);
  assert.ok(Number.isSafeInteger(baseCode) && baseCode >= 189, "Unexpected versionCode regression");
  assert.match(versionName[1], /^0\.5\.22-alpha\d+-clean$/);
  assert.match(gradle, /applicationId\s*=\s*"com\.prirai\.android\.nira"/);
  assert.match(gradle, /applicationIdSuffix\s*=\s*'\.debug'/);
  assert.match(gradle, /"arm64-v8a":\s*1/);
  assert.match(gradle, /output\.versionCode\.set\(base \* 1000 \+ orig\)/);
  const installCode = baseCode + 1000;
  // Only the debug update job is relevant; release can have a separate version.
  const buildJob = workflow.split(/\n\s{2}lint:\s*\n/)[0];
  const expectField = (key, expected) => {
    const matches = buildJob.split("\n").filter(line =>
      line.trim().startsWith(key + ":"));
    assert.equal(matches.length, 1, "Expected exactly one " + key + " in build job");
    const actual = matches[0].trim().slice(key.length + 1).trim().replace(/^["']|["']$/g, "");
    assert.equal(actual, String(expected), "Mismatch for " + key);
  };
  expectField("EXPECTED_PACKAGE", "com.prirai.android.nira.debug");
  expectField("EXPECTED_VERSION_CODE", installCode);
  expectField("EXPECTED_VERSION_NAME", versionName[1]);
  expectField("EXPECTED_CERT_SHA256", "df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00");
  expectField("name", "zavro-arm64-update-apk-v" + installCode);
  assert.match(buildJob, /scripts\/verify-update-apk\.sh/);
  assert.match(buildJob, /ANDROID_KEYSTORE_BASE64/);
  return { installCode, versionName: versionName[1] };
}

module.exports = { assertUpdateApkContract };
