"use strict";
// Stage 3/5: source-ownership evidence, not proof of a specific site's Play.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname,'../../../..');
const read = p => fs.readFileSync(path.join(root,p),'utf8');

test('removed embedded MPV is not accidentally reintroduced', () => {
  const main = read('app/src/main/AndroidManifest.xml');
  const gradle = read('app/build.gradle');
  assert.doesNotMatch(main,/ZavroMpvActivity|mpvRex|libmpv/i);
  assert.doesNotMatch(gradle,/mpvRex|libmpv/i);
  const fragment = read('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt');
  assert.match(fragment,/launchVideoInMpv/);
  assert.match(fragment,/Intent\.ACTION_VIEW/);
  assert.match(fragment,/MpvPlaybackHeaders/);
});

test('preview helper is the only shipped preview takeover mechanism in scope', () => {
  const manifest = JSON.parse(read('app/src/main/assets/extensions/nira-media-detector/manifest.json'));
  const scripts = manifest.content_scripts.flatMap(s => s.js);
  assert.ok(scripts.includes('preview-assist.js'));
  const helper = read('app/src/main/assets/extensions/nira-media-detector/preview-assist.js');
  assert.match(helper,/data-preview-video/);
  assert.match(helper,/video\.dataset\.niraPreviewCreated\s*=\s*"1"/);
  assert.match(helper,/event\.preventDefault\(\)/);
  assert.match(helper,/event\.stopImmediatePropagation\(\)/);
  assert.match(helper,/if \(!source && !explicitPreviewControl\) return;/);
});

test('background-play patch is still allowlist gated and never proves actual page execution', () => {
  const script = read('app/src/main/assets/extensions/nira-bg-play/content.js');
  const controller = read('app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt');
  assert.match(script,/if \(!resp \|\| !resp\.allowed\) return;/);
  assert.match(script,/HTMLVideoElement|video\.pause\s*=/);
  assert.match(script,/isTransientPreview/);
  assert.match(controller,/allowlist\.origins/);
  assert.match(controller,/listInstalledWebExtensions/);
  const evidence = read('app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt');
  assert.match(evidence,/execution_on_page", "NOT_CONFIRMED"/);
  assert.match(evidence,/bgplay_page_patch", "NOT_OBSERVED"/);
  assert.match(evidence,/raw_extension_ids_exported", false/);
});

test('Gecko addon inventory is explicit-start only, session-nonce bound and no foreign IDs exported', () => {
  const bridge = read('app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt');
  const ledger = read('app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt');
  assert.match(bridge,/if \(action == "start"\) captureAddonInventory/);
  assert.match(bridge,/ProfileTabScope\.belongsToNormalProfile/);
  assert.match(bridge,/engine\.listInstalledWebExtensions/);
  assert.match(ledger,/ledger\.sessionId != nonce/);
  assert.match(ledger,/addon-inventory\.json/);
  assert.match(ledger,/play_owner", "UNKNOWN"/);
  assert.doesNotMatch(ledger,/installed\.joinToString/);
});
