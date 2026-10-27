'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
// Hashes are anchored in the supplied v1158 commit. This runs in shallow CI too.
const protectedFiles = require('./v1158-protected-hashes.json');
// Exact reviewed UI-only changes. Reverse them and still verify the original hash;
// no protected file is omitted and no baseline hash is replaced.
const uiPatches = require('./v1160-ui-patches.json');
// Reviewed v1163 discovery-only hunks are reversible; original 92 hashes stay authoritative.
const discoveryPatches = require('./v1163-discovery-patches.json');
const advancedPatches = require('./v1164-discovery-patches.json');
const {reverseTestLab,verifyPassiveNativeScope,layer:labLayer}=require('./v1165-testlab-integrity.cjs');
assert.deepEqual(Object.keys(advancedPatches).sort(), [
  ...['background.js','content.js','manifest.json','playback-bridge.js'].map(name=>'app/src/main/assets/extensions/nira-media-detector/'+name),
  'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt',
  ...['BlobHlsSelector','MediaDetectorController','MediaElementPlaybackBridge','MediaMessageProtocol','MediaRepository'].map(name=>'app/src/main/java/com/prirai/android/nira/media/'+name+'.kt')
].sort());
function reverseAdvanced(path, bytes) {
  bytes=reverseTestLab(path,bytes);
  if (!advancedPatches[path]) return bytes;
  let source = bytes.toString('utf8');
  for (const patch of advancedPatches[path].slice().reverse()) {
    assert.equal(source.split(patch.after).length - 1, 1, `${path}: unique v1164 hunk`);
    source = source.replace(patch.after, () => patch.before);
  }
  return source;
}
test('v1167: protected v1158 MPV, Lua, audio, fullscreen and Room schemas are retain original hashes after reversing exact UI-only patches', () => {
  assert.deepEqual(Object.keys(uiPatches).sort(), [
    'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt',
    'app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt'
  ]);
  assert.deepEqual(Object.keys(discoveryPatches).sort(), [
    'app/src/main/assets/extensions/nira-media-detector/background.js',
    'app/src/main/assets/extensions/nira-media-detector/content.js',
    'app/src/main/assets/extensions/nira-media-detector/manifest-parser.js',
    ...['BlobHlsSelector','MediaClassifier','MediaDetectorController','MediaDetectorDiagnostics',
      'MediaMessageProtocol','MediaRepository'].map(name=>'app/src/main/java/com/prirai/android/nira/media/'+name+'.kt')
  ].sort());
  for (const [path, expected] of Object.entries(protectedFiles)) {
    let source = reverseAdvanced(path, fs.readFileSync(path));
    if (discoveryPatches[path]) {
      source = source.toString('utf8');
      for (const patch of discoveryPatches[path].slice().reverse()) {
        assert.equal(source.split(patch.after).length - 1, 1, `${path}: unique discovery hunk`);
        source = source.replace(patch.after, () => patch.before);
      }
    }
    if (uiPatches[path]) source = source.toString('utf8');
    for (const patch of (uiPatches[path] || []).slice().reverse()) {
      assert.equal(source.split(patch.after).length - 1, 1, `${path}: unique approved UI hunk`);
      source = source.replace(patch.after, () => patch.before);
    }
    const actual = crypto.createHash('sha256').update(source).digest('hex');
    assert.equal(actual, expected, path);
  }
});
test('v1167: verified ARM64 update identity is exact in Gradle, CI and verifier', () => {
  const gradle = fs.readFileSync('app/build.gradle','utf8');
  const ci = fs.readFileSync('.github/workflows/apk-release.yml','utf8');
  assert.match(gradle,/versionCode = 167\b/);
  assert.match(gradle,/versionName = "0\.5\.22-alpha73-mpv40"/);
  assert.match(gradle,/applicationId = "com\.prirai\.android\.nira\.mpv"/);
  assert.match(ci,/EXPECTED_VERSION_CODE: "1167"/);
  assert.match(ci,/name: zavro-x-mpv-arm64-v1167/);
  assert.match(ci,/EXPECTED_CERT_SHA256: df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00/);
  assert.match(ci,/bash scripts\/verify-update-apk\.sh/);
  assert.match(fs.readFileSync('scripts/verify-update-apk.sh','utf8'),/apksigner/);
});
test('v1167: data regressions and native View tests are executed by the CI unit-test task', () => {
  const ci = fs.readFileSync('.github/workflows/apk-release.yml','utf8');
  assert.match(ci,/testDebugUnitTest compileDebugAndroidTestKotlin/);
  assert.match(ci,/node --test app\/src\/test\/js\/\*\.test\.cjs/);
  // UI gestures require a device; these are supplementary wiring/integrity checks.
  const drag = fs.readFileSync('app/src/main/java/com/prirai/android/nira/browser/tabs/compose/UnifiedDragSystem.kt','utf8');
  assert.doesNotMatch(drag.slice(drag.indexOf('private suspend fun performDrop')),/delay\((50|100)\)/);
});

test('v1167: v1160 floating button, manual groups and shared order remain byte-identical', () => {
  for (const [path, expected] of Object.entries(require('./v1160-tab-ui-hashes.json'))) {
    assert.equal(crypto.createHash('sha256').update(reverseAdvanced(path, fs.readFileSync(path))).digest('hex'), expected, path);
  }
});

test('v1167: app diagnostics do not interpolate raw OAuth, favicon or cookie origins', () => {
  for (const path of [
    'app/src/main/java/com/prirai/android/nira/request/AppRequestInterceptor.kt',
    'app/src/main/java/com/prirai/android/nira/browser/tabs/compose/FaviconImage.kt',
    'app/src/main/java/com/prirai/android/nira/privacy/cookies/CookiePolicyWorker.kt'
  ]) {
    for (const line of fs.readFileSync(path, 'utf8').split('\n').filter(line => /Log\.[diew]\(/.test(line))) {
      assert.doesNotMatch(line, /\$uri|\$url|\$\{(?:policy\.origin|tab\.content\.url)\}/, path);
    }
  }
});

test('v1167: every shared diagnostic hunk reverses to v1164 and introduces no JNI polling or commands',()=>{
 verifyPassiveNativeScope();
 for(const path of Object.keys(labLayer.files))reverseTestLab(path,fs.readFileSync(path));
});
