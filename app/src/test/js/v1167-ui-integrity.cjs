'use strict';
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const layer = require('./v1167-ui-patches.json');
assert.equal(layer.baseCommit, '6ede3b499be3a922e38c3a7b72fbc6e639f7de5c');

/** Peel only the exact v1167 changes, then leave all older integrity layers authoritative. */
function reverseV1167(path, bytes) {
  const file = layer.files[path];
  if (!file) return bytes;
  let source = bytes.toString('utf8');
  for (const patch of file.patches.slice().reverse()) {
    assert.equal(source.split(patch.after).length - 1, 1, `${path}: unique v1167 hunk`);
    source = source.replace(patch.after, () => patch.before);
  }
  assert.equal(crypto.createHash('sha256').update(source).digest('hex'), file.baseSha256,
    `${path}: exact verified v1165 base`);
  return source;
}
module.exports = {layer, reverseV1167};
