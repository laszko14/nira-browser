const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = (path) => fs.readFileSync(path, 'utf8');
const gradle = read('app/build.gradle');
const workflow = read('.github/workflows/apk-release.yml');
const manifest = read('app/src/main/AndroidManifest.xml');
const strings = read('app/src/main/res/values/strings.xml');
const browser = read('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt');
const player = read('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt');
const registry = read('app/src/main/java/com/prirai/android/nira/media/PlaybackSourceRegistry.kt');

test('v1130 installs BESIDE Zavro and has distinct launcher label', () => {
  assert.match(gradle, /applicationId = "com\.prirai\.android\.nira\.mpv"/);
  assert.doesNotMatch(gradle, /applicationIdSuffix\s*=/);
  assert.match(strings, /name="app_name" translatable="false">Zavro x MPV<\/string>/);
  assert.match(manifest, /android:label="@string\/app_name"/);
  assert.match(workflow, /EXPECTED_PACKAGE: com\.prirai\.android\.nira\.mpv/);
  assert.doesNotMatch(workflow, /EXPECTED_PACKAGE: com\.prirai\.android\.nira\.debug/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1130"/);
  assert.match(workflow, /name: zavro-x-mpv-arm64-v1130/);
});

test('v1130 pauses browser media and resumes only its unchanged Gecko document', () => {
  assert.match(browser, /mediaSessionState\?\.controller\?\.pause\(\)/);
  assert.match(browser, /internalMpvLauncher\.launch\(intent\)/);
  assert.match(browser, /registerForActivityResult/);
  assert.match(browser, /tab\.engineState\.engineSession !== held\.session/);
  assert.match(browser, /tab\.content\.url != held\.pageUrl/);
  assert.match(browser, /mediaSessionState\?\.controller\?\.play\(\)/);
  assert.match(registry, /fresh\.binding\.session !== owner\.session/);
  assert.match(registry, /fresh\.binding\.contextId != owner\.contextId/);
  assert.match(manifest, /android:name="\.media\.ZavroMpvActivity"[\s\S]*?android:exported="false"/);
});

test('v1130 provides stream recovery, media focus, PiP, tracks and speed gestures', () => {
  assert.match(player, /AudioFocusRequest\.Builder/);
  assert.match(player, /requestMediaAudioFocus\(\)/);
  assert.match(player, /abandonMediaAudioFocus\(\)/);
  assert.match(player, /setOptionString\("cache-secs", "180"\)/);
  assert.match(player, /setOptionString\("network-timeout", "15"\)/);
  assert.match(player, /MPV_EVENT_FILE_LOADED/);
  assert.match(player, /MPVLib\.command\("cycle", "aid"\)/);
  assert.match(player, /MPVLib\.command\("cycle", "sid"\)/);
  assert.match(player, /enterPictureInPictureMode/);
  assert.match(player, /showAudioFilters/);
  assert.match(player, /denoiseLevel/);
  assert.match(player, /dialogueLevel/);
  assert.match(player, /afftdn=nr=/);
  assert.match(player, /equalizer=f=2300/);
  assert.match(player, /MPVLib\.command\("af", "set"/);
  assert.match(manifest, /android:supportsPictureInPicture="true"/);
  assert.match(player, /postDelayed\(action, 300L\)/);
  assert.match(player, /MPVLib\.command\("loadfile", url\)/);
});

test('native player URL remains in RAM, not an exported intent', () => {
  assert.match(registry, /UUID\.randomUUID/);
  assert.match(registry, /private var pending: Pending\?/);
  assert.match(registry, /pending = null/);
  assert.match(browser, /putExtra\(ZavroMpvActivity\.EXTRA_TICKET_ID, ticket\)/);
  assert.doesNotMatch(browser, /putExtra\(ZavroMpvActivity\.EXTRA_TICKET_ID, uri/);
});
