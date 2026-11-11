const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../../..');

test('MPV diagnostic UI is opt-in and has explicit start/mark/stop/share controls', () => {
  const fragment = fs.readFileSync(path.join(root, 'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt'), 'utf8');
  for (const id of ['mpv_play_diagnostic_start', 'mpv_play_diagnostic_mark',
                   'mpv_play_diagnostic_stop', 'mpv_play_diagnostic_export']) {
    assert.match(fragment, new RegExp(id));
  }
  assert.match(fragment, /mpvDiagnostics\.export/);
  assert.match(fragment, /Intent\.FLAG_GRANT_READ_URI_PERMISSION/);
  assert.match(fragment, /current\.content\.url\.toUri\(\)/);
  assert.match(fragment, /pageUri\?\.scheme\?\.lowercase\(\) in setOf\("http", "https"\)/);
  assert.match(fragment, /!domain\.isNullOrBlank\(\)/);
  assert.doesNotMatch(fragment, /android\.net\.Uri\.parse\(current\.content\.url\)/);
});

test('diagnostic recorder is bounded, opt-in, and excludes private sessions in stage 1', () => {
  const source = fs.readFileSync(path.join(root, 'app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt'), 'utf8');
  assert.match(source, /MAX_MARKS = 8/);
  assert.match(source, /MAX_SNAPSHOT_CHARS = 12_000/);
  assert.match(source, /privateTab\) return@synchronized Start\.PRIVATE_TAB/);
  assert.match(source, /dom_capture", "BOUNDED_STRUCTURE_ONLY"/);
  assert.match(source, /MAX_PAGE_EVENTS = 160/);
  assert.match(source, /appendPageEvent/);
  assert.match(source, /page-events\.json/);
  assert.match(source, /FileProvider\.getUriForFile/);
  assert.doesNotMatch(source, /sendBroadcast|HttpURLConnection|OkHttpClient/);
});
