const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const read = p => fs.readFileSync(p,'utf8');
test('v1142 one 40s one-tap safe supertest covering Gecko, handoff, MPV, buffering, TLS and progress', () => {
  const browser=read('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt');
  const activity=read('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt');
  const engine=read('app/src/main/java/com/prirai/android/nira/media/MpvSupertest.kt');
  const trace=read('app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt');
  assert.match(browser, /id = "mpv_supertest"/);
  assert.match(browser, /onClick = \{ startMpvSupertest\(\) \}/);
  assert.doesNotMatch(browser, /id = "mpv_diag_(?:web|handoff|stream)"/);
  assert.match(activity, /control\("SUPERTEST 40 s"\)/);
  assert.match(activity, /MpvSupertest\.sample\(traceSession, position\)/);
  assert.match(engine, /TEST_MS = 40_000L/);
  assert.match(engine, /main\.postDelayed\(\{ finish\(capture\) \}, TEST_MS\)/);
  assert.match(engine, /capture\.owner !== session/);
  assert.match(engine, /position\.isFinite\(\)/);
  assert.match(engine, /clipboard\.setPrimaryClip\(ClipData\.newPlainText/);
  assert.doesNotMatch(engine, /https?:\/\/|MPVLib\.command\("seek"|MPVLib\.command\("loadfile"/);
  assert.match(trace, /fun superReport\(/);
  for (const value of ['source_blob','probe_ack_oneshot','auto_offer_received',
      'mpv_file_loaded','mpv_cache_wait','mpv_tls_error','mpv_retry',
      'mpv_manual_error','mpv_playback_started']) assert.ok(trace.includes(value), value);
  assert.match(trace, /takeLast\(65\)/);
  assert.match(trace, /Zavro x MPV v11\d{2} — SUPERTEST 40 s/);
});
