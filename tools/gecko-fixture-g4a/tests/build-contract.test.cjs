"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { inspect, EXPECTED } = require("../check-build-contract.cjs");
const root = path.resolve(__dirname, "../../..");
const read = rel => fs.readFileSync(path.join(root, rel), "utf8");
const current = () => [
  JSON.parse(read("tools/gecko-fixture-g4a/BUILD_CONTRACT.json")),
  read("app/build.gradle"), read("scripts/verify-update-apk.sh"),
  read("tools/gecko-fixture-g3c/README.md")
];
test("exact G3C source configuration stays PREPARED_NOT_AUTHORIZED, never READY_TO_INSTALL", () => {
  const result = inspect(...current());
  assert.equal(result.status, "PREPARED_NOT_AUTHORIZED");
  assert.deepEqual(result.errors, []);
  assert.equal(result.artifactBuilt, false);
  assert.equal(result.installationAuthorized, false);
  assert.equal(result.physicalTestExecuted, false);
  assert.deepEqual(EXPECTED.packageId, "com.prirai.android.nira.debug");
});
test("explicit build authorization in policy is rejected (no auto-build)", () => {
  const x = current(); x[0].buildAuthorization = "GRANTED";
  assert.equal(inspect(...x).status, "BLOCKED");
});
test("installation or device-use claims are rejected", () => {
  for (const key of ["installAuthorization", "deviceExecution", "existingInstallationProtected"]) {
    const x = current(); x[0][key] = "GRANTED";
    assert.equal(inspect(...x).status, "BLOCKED", key);
  }
});
test("no debug suffix / changed package cannot be mistaken for update-safe artifact", () => {
  const x = current(); x[1] = x[1].replace("applicationIdSuffix = '.debug'", "applicationIdSuffix = '.fixture'");
  assert.equal(inspect(...x).status, "BLOCKED");
});
test("versionCode, ABI and versionName drift block instead of guessing", () => {
  for (const [a,b] of [["versionCode = 195","versionCode = 196"],
    ["\"arm64-v8a\": 1","\"arm64-v8a\": 2"],
    ["0.5.22-alpha100-clean","0.5.22-other"]]) {
    const x = current(); x[1] = x[1].replace(a,b);
    assert.equal(inspect(...x).status, "BLOCKED", a);
  }
});
test("stable signing hookup and APK verification are mandatory in source", () => {
  const x = current(); x[1] = x[1].replace("NIRA_SIGNING_KEY_ALIAS", "REMOVED_ALIAS");
  assert.equal(inspect(...x).status, "BLOCKED");
  const y = current(); y[2] = y[2].replace(/apksigner/g,"no_tool");
  assert.equal(inspect(...y).status, "BLOCKED");
});
test("unknown fields, missing G3C, altered certificate are blocked", () => {
  const x = current(); x[0].unapproved = "X";
  assert.equal(inspect(...x).status, "BLOCKED");
  const y = current(); y[3] = "nothing";
  assert.equal(inspect(...y).status, "BLOCKED");
  const z = current(); z[0].signerSha256 = "0".repeat(64);
  assert.equal(inspect(...z).status, "BLOCKED");
});
