'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const shared = [
  'app/src/main/java/com/prirai/android/nira/BrowserApp.kt',
  'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt',
  'app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt',
];

test('v1167: all shared UI and handoff changes reverse exactly to the verified v1165 base', () => {
  const layerPath = 'app/src/test/js/v1167-ui-patches.json';
  assert.ok(fs.existsSync(layerPath), 'v1167 needs an exact reversible layer, never replaced historical hashes');
  const {layer, reverseV1167} = require('./v1167-ui-integrity.cjs');
  assert.equal(layer.baseCommit, '6ede3b499be3a922e38c3a7b72fbc6e639f7de5c');
  assert.deepEqual(Object.keys(layer.files).sort(), shared.sort());
  for (const path of shared) {
    const original = reverseV1167(path, fs.readFileSync(path));
    assert.equal(crypto.createHash('sha256').update(original).digest('hex'), layer.files[path].baseSha256, path);
    assert.ok(layer.files[path].patches.length > 0, `${path}: exact reviewed changes required`);
  }
});

test('v1167: refresh policy and tiles never take ownership of Gecko or MPV rendering surfaces', () => {
  for (const path of [
    'app/src/main/java/com/prirai/android/nira/display/BrowserRefreshRate.kt',
    'app/src/main/java/com/prirai/android/nira/media/VideoTilesPanel.kt',
  ]) {
    const source = fs.readFileSync(path, 'utf8');
    assert.doesNotMatch(source, /MPVLib\s*\.|SurfaceView\s*\(|\.setFrameRate\s*\(|preferredDisplayModeId\s*=/, path);
  }
});
