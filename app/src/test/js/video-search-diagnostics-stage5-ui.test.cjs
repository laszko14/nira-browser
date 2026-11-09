'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '../../main');
const manifest = fs.readFileSync(path.join(root, 'AndroidManifest.xml'), 'utf8');
const sites = fs.readFileSync(path.join(root,
  'java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt'), 'utf8');
const controls = fs.readFileSync(path.join(root,
  'java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticControls.kt'), 'utf8');
const provider = fs.readFileSync(path.join(root,
  'java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticReportProvider.kt'), 'utf8');

test('stage 5 share provider is not exported and grants only temporary URI reads',()=>{
  assert.match(manifest,
    /android:name="\.videosearch\.VideoSearchDiagnosticReportProvider"[\s\S]*?android:exported="false"[\s\S]*?android:grantUriPermissions="true"/);
  assert.match(provider,/if \(mode != "r"\)/);
  assert.match(provider,/ParcelFileDescriptor\.createPipe\(\)/);
  assert.match(provider,/privateLines\(owner\)/);
  assert.doesNotMatch(provider,/FileOutputStream|createTempFile|cacheDir|openFileOutput|sendBroadcast/);
});

test('stage 5 controls are present in both My Sites UI modes only',()=>{
  assert.match(sites,/normalHeader\.addView\(normalDiagnosticControls\.panel/);
  assert.match(sites,/header\.addView\(privateDiagnosticControls\.panel\)/);
  assert.match(controls,/tag = if \(privateMode\) "video-diag-private-start"/);
  assert.match(controls,/tag = if \(privateMode\) "video-diag-private-stop"/);
  assert.match(controls,/tag = if \(privateMode\) "video-diag-private-share"/);
  assert.match(controls,/\.setPositiveButton\("Rozpocznij"\)/);
  assert.match(controls,/\.setPositiveButton\("Wybierz odbiorcę"\)/);
  assert.match(controls,/Intent\(Intent.ACTION_SEND\)/);
  assert.match(controls,/Intent.FLAG_GRANT_READ_URI_PERMISSION/);
  assert.doesNotMatch(controls,/intent\(Intent.ACTION_VIEW\)|ACTION_SENDTO|startActivityForResult/);
});

test('stage 5 stop drains after JS stop ACK with separate bounded diagnostics rate',()=>{
  const bridge = fs.readFileSync(path.join(root,
    'java/com/prirai/android/nira/videosearch/VideoSearchBridgeController.kt'), 'utf8');
  const policy = fs.readFileSync(path.join(root,
    'java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticDrainPolicy.kt'), 'utf8');
  assert.match(bridge, /withinDiagnosticRate\(session\)/);
  assert.match(bridge, /withinRate\(session\)/);
  assert.match(bridge, /requestFinishDiagnostics/);
  assert.match(bridge, /diagnosticFinalizing/);
  assert.match(policy, /phase == "stopping" && status == "stopped"/);
  assert.match(policy, /"draining" -> if \(empty\) "drained" else "draining"/);
});

test('stage 5 does not add background event uploads',()=>{
  assert.doesNotMatch(provider,/\bfetch\(|HttpURLConnection|OkHttpClient|Retrofit|WorkManager/);
  assert.doesNotMatch(controls,/\bfetch\(|HttpURLConnection|OkHttpClient|Retrofit|WorkManager/);
});
