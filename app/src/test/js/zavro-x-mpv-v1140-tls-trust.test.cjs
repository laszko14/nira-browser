const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const read = p => fs.readFileSync(p,'utf8');
const activity = read('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt');
const trust = read('app/src/main/java/com/prirai/android/nira/media/MpvTlsTrust.kt');
const debug = read('app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt');

test('v1140: libmpv gets Android system CA trust before initialize and loadfile', () => {
  assert.match(activity, /surface\.tlsCaFile = MpvTlsTrust\.prepare\(this\)/);
  assert.ok(activity.indexOf('MpvTlsTrust.prepare(this)') < activity.indexOf('surface.initialize('));
  assert.match(activity,/MPVLib\.setOptionString\("tls-ca-file", it\)/);
  assert.match(activity,/MPVLib\.setOptionString\("tls-verify", "yes"\)/);
  assert.doesNotMatch(activity,/MPVLib\.setOptionString\("tls-verify", "no"\)/);
});
test('v1140: certificate bundle includes system roots only and is stored privately', () => {
  assert.match(trust,/KeyStore\.getInstance\("AndroidCAStore"\)/);
  assert.match(trust,/alias\.startsWith\("system:"\)/);
  assert.match(trust,/context\.filesDir/);
  assert.match(trust,/StandardCopyOption\.REPLACE_EXISTING/);
  assert.match(trust,/BEGIN CERTIFICATE/);
});
test('v1140: diagnostic categories separate handshake/cert and remain allowlisted', () => {
  assert.match(debug,/mpv_tls_ca_prepared/);
  assert.match(debug,/mpv_tls_ca_unavailable/);
  assert.match(debug,/mpv_tls_certificate_error/);
  assert.match(debug,/mpv_tls_handshake_error/);
  assert.match(debug,/Zavro x MPV v11\d{2}/);
});
