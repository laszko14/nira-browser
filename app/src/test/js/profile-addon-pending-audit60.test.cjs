'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('ProfileAddonPolicy waits for Gecko addon callbacks before policy lock releases', () => {
  const source = fs.readFileSync(path.join(__dirname, '../../main/java/com/prirai/android/nira/browser/profile/ProfileAddonPolicy.kt'),'utf8');
  assert.equal((source.match(/getAddons\(waitForPendingActions = true\)/g) || []).length, 2);
  assert.ok(source.indexOf('if (submittedChange)') > source.indexOf('enableAddon(addon)'));
  assert.ok(source.indexOf('getAddons(waitForPendingActions = true)',source.indexOf('if (submittedChange)')) > 0);
});
