const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const player = fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt', 'utf8');
const browser = fs.readFileSync('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt', 'utf8');
const registry = fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/PlaybackSourceRegistry.kt', 'utf8');
const build = fs.readFileSync('app/build.gradle', 'utf8');
const workflow = fs.readFileSync('.github/workflows/apk-release.yml', 'utf8');

test('keeps the canonical Zavro identity for in-place updates', () => {
  assert.match(build, /applicationId = "com\.prirai\.android\.nira\.mpv"/);
  assert.doesNotMatch(build, /applicationIdSuffix\s*=\s*['"]\.mpv/);
  assert.doesNotMatch(build, /applicationIdSuffix/);
  assert.match(workflow, /EXPECTED_PACKAGE: com\.prirai\.android\.nira\.mpv\b/);
  assert.match(build, /versionCode = 130/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1130"/);
});

test('embeds native libmpv and keeps the basic 1.5x and seek controls', () => {
  assert.match(player, /BaseMPVView/);
  assert.match(player, /setOptionString\("speed", "1\.5"\)/);
  for (const value of ['-10', '10', '30']) {
    assert.ok(player.includes('seek(' + value + ')'));
  }
});

test('passes only an opaque in-process ticket, not signed media URLs in Intents', () => {
  assert.match(browser, /PlaybackSourceRegistry\.issue\(/);
  assert.match(browser, /putExtra\(ZavroMpvActivity\.EXTRA_TICKET_ID, ticket\)/);
  assert.doesNotMatch(browser, /data = uri\s*\n\s*putExtra\(ZavroMpvActivity/);
  assert.match(player, /PlaybackSourceRegistry\.take\(/);
  assert.match(registry, /UUID\.randomUUID\(\)/);
  assert.match(registry, /SystemClock\.elapsedRealtime\(\)/);
  assert.match(registry, /pending = null/);
  assert.match(registry, /fresh\.binding\.session !== owner\.session/);
  assert.match(registry, /fresh\.binding\.contextId != owner\.contextId/);
  assert.match(player, /MPV_EVENT_FILE_LOADED/);
  assert.match(player, /postDelayed\(action, 300L\)/);
  assert.match(player, /MpvEngineGate\.claim/);
  assert.match(player, /FLAG_SECURE/);
});

test('does not automatically hijack arbitrary Gecko fullscreen requests', () => {
  const fullscreen = browser.split('override fun onFullScreenModeChanged')[1]
    .split('private fun expandBrowserViewForFullscreen')[0];
  assert.doesNotMatch(fullscreen, /launchVideoInMpv/);
  assert.doesNotMatch(fullscreen, /super\.onBackPressed/);
  assert.match(browser, /launchInternalVideo/);
  assert.match(browser, /launchVideoExternally/);
});
