'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
// Hashes are anchored in the supplied v1158 commit. This runs in shallow CI too.
const protectedFiles = require('./v1158-protected-hashes.json');
test('v1159: protected v1158 MPV, Lua, audio, fullscreen and Room schemas are byte-identical', () => {
  for (const [path, expected] of Object.entries(protectedFiles)) {
    const actual = crypto.createHash('sha256').update(fs.readFileSync(path)).digest('hex');
    assert.equal(actual, expected, path);
  }
});
test('v1159: verified ARM64 update identity is exact in Gradle, CI and verifier', () => {
  const gradle = fs.readFileSync('app/build.gradle','utf8');
  const ci = fs.readFileSync('.github/workflows/apk-release.yml','utf8');
  assert.match(gradle,/versionCode = 159\b/);
  assert.match(gradle,/versionName = "0\.5\.22-alpha65-mpv33"/);
  assert.match(gradle,/applicationId = "com\.prirai\.android\.nira\.mpv"/);
  assert.match(ci,/EXPECTED_VERSION_CODE: "1159"/);
  assert.match(ci,/name: zavro-x-mpv-arm64-v1159/);
  assert.match(ci,/EXPECTED_CERT_SHA256: df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00/);
  assert.match(ci,/bash scripts\/verify-update-apk\.sh/);
  assert.match(fs.readFileSync('scripts/verify-update-apk.sh','utf8'),/apksigner/);
});
test('v1159: data regressions and native View tests are executed by the CI unit-test task', () => {
  const ci = fs.readFileSync('.github/workflows/apk-release.yml','utf8');
  assert.match(ci,/testDebugUnitTest compileDebugAndroidTestKotlin/);
  assert.match(ci,/node --test app\/src\/test\/js\/\*\.test\.cjs/);
  // UI gestures require a device; these are supplementary wiring/integrity checks.
  const drag = fs.readFileSync('app/src/main/java/com/prirai/android/nira/browser/tabs/compose/UnifiedDragSystem.kt','utf8');
  assert.doesNotMatch(drag.slice(drag.indexOf('private suspend fun performDrop')),/delay\((50|100)\)/);
});
