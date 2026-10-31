"use strict";

const fs = require("node:fs");
const path = require("node:path");

/**
 * Computes expected release APK metadata from the Gradle source checked out
 * AT THE REQUESTED TAG, never from hard-coded values in a workflow branch.
 * This script does not build, sign, publish or inspect any APK.
 */
function resolveReleaseContract(gradle) {
  const single = (expression, label) => {
    const matches = [...gradle.matchAll(expression)];
    if (matches.length !== 1) throw new Error("Expected exactly one " + label);
    return matches[0][1];
  };
  const baseCode = Number(single(/^\s*versionCode\s*=\s*(\d+)\s*$/gm, "versionCode"));
  const versionName = single(/^\s*versionName\s*=\s*"([^"\r\n]+)"\s*$/gm, "versionName");
  const packageName = single(/^\s*applicationId\s*=\s*"([^"\r\n]+)"\s*$/gm, "applicationId");
  const abiMultiplier = Number(single(/^\s*def abiBaseMap\s*=\s*\[\s*"arm64-v8a"\s*:\s*(\d+)\s*\]\s*$/gm, "arm64 version map"));
  if (packageName !== "com.prirai.android.nira") throw new Error("Unexpected release applicationId");
  if (!Number.isSafeInteger(baseCode) || baseCode < 1) throw new Error("Invalid Gradle base versionCode");
  if (!Number.isSafeInteger(abiMultiplier) || abiMultiplier !== 1) {
    throw new Error("Unexpected arm64 ABI versionCode multiplier");
  }
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(versionName)) {
    throw new Error("Unsafe Gradle versionName for GITHUB_ENV");
  }
  if (!/\boutput\.versionCode\.set\(base \* 1000 \+ orig\)/.test(gradle) ||
      !/\binclude\(testAbi \?: 'arm64-v8a'\)/.test(gradle) ||
      !/\buniversalApk\s*=\s*false\b/.test(gradle) ||
      !/applicationIdSuffix\s*=\s*'\.debug'/.test(gradle)) {
    throw new Error("Unexpected ABI split or debug/release Gradle contract");
  }
  const versionCode = abiMultiplier * 1000 + baseCode;
  if (!Number.isSafeInteger(versionCode)) throw new Error("Invalid arm64 release versionCode");
  return { packageName, versionCode, versionName };
}

function writeGitHubEnv(contract, envFile) {
  if (!envFile) throw new Error("GITHUB_ENV is missing");
  fs.appendFileSync(envFile,
    "EXPECTED_VERSION_CODE=" + contract.versionCode + "\n" +
    "EXPECTED_VERSION_NAME=" + contract.versionName + "\n",
    { encoding: "utf8" });
}

if (require.main === module) {
  const gradle = fs.readFileSync(path.join(__dirname, "../app/build.gradle"), "utf8");
  const contract = resolveReleaseContract(gradle);
  writeGitHubEnv(contract, process.env.GITHUB_ENV);
  console.log("Validated tag checkout metadata: release package=" + contract.packageName +
    " arm64 code=" + contract.versionCode + " name=" + contract.versionName);
}

module.exports = { resolveReleaseContract, writeGitHubEnv };
