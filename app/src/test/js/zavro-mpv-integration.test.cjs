const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const source = fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt', 'utf8');
const browser = fs.readFileSync('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt', 'utf8');
const build = fs.readFileSync('app/build.gradle', 'utf8');
test('real embedded mpv with speed and seek controls', () => {
  assert.match(source, /BaseMPVView/);
  assert.match(source, /setOptionString\("speed", "1\.5"\)/);
  for (const value of ['-10','10','30']) assert.match(source, new RegExp('seek\\('+value+'\\)'));
});
test('installs independently and opens native player on detected videos', () => {
  assert.match(build, /applicationIdSuffix = '\.mpv'/);
  assert.match(build, /Zavro x MPV/);
  assert.match(browser, /ZavroMpvActivity::class\.java/);
});
