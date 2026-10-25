const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const read = p => fs.readFileSync(p, 'utf8');
const act = read('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt');
const controller = read('app/src/main/java/com/prirai/android/nira/media/MpvRetryController.kt');
const debug = read('app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt');

test('v1141: TLS remains verified with bounded HLS/DASH connection retries', () => {
  assert.match(act,/segmentedNetworkReconnect = MpvDebugTrace\.classifySource\(url\)/);
  assert.match(act,/MPVLib\.setOptionString\("stream-lavf-o", retryOptions\)/);
  assert.match(act,/MPVLib\.setOptionString\("demuxer-lavf-o", retryOptions\)/);
  assert.match(act,/reconnect_on_network_error=1/);
  assert.match(act,/reconnect_streamed=1,reconnect_delay_max=2/);
  assert.match(act,/MPVLib\.setOptionString\("tls-verify", "yes"\)/);
  assert.doesNotMatch(act,/reconnect_at_eof=1|reconnect_on_http_error=4xx|tls-verify", "no"/);
});

test('v1141: retry uses single replace and classifies prior-file END_FILE', () => {
  const start=act.indexOf('    private fun retry() {');
  const end=act.indexOf('    private fun scheduleTimeout()', start);
  const retry=act.slice(start, end);
  assert.match(retry,/retryController\.prepare\(fileLoaded, position, duration\)/);
  assert.match(retry,/MPVLib\.command\("loadfile", url, "replace"\)/);
  assert.doesNotMatch(retry,/MPVLib\.command\("stop"\)/);
  assert.match(act,/retryController\.consumeReplacementEnd\(\)/);
  assert.match(controller,/expectedReplacementEnd = previousLoaded/);
  assert.match(debug,/mpv_retry_replacement_end/);
});

test('v1141: VOD resume guarded and no raw URL in trace', () => {
  assert.match(controller,/durationSeconds > 30\.0/);
  assert.match(controller,/positionSeconds < durationSeconds - 5\.0/);
  assert.match(act,/MPVLib\.command\("seek", resumeAt\.toString\(\), "absolute\+keyframes"\)/);
  assert.match(debug,/mpv_tls_after_load/);
  assert.match(debug,/Zavro x MPV v1141/);
});
