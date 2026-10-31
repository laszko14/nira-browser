const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const fragment = fs.readFileSync('app/src/main/java/com/prirai/android/nira/settings/fragment/CustomizationSettingsFragment.kt', 'utf8');
const placement = fs.readFileSync('app/src/main/java/com/prirai/android/nira/components/toolbar/ToolbarActionDropPosition.kt', 'utf8');

test('toolbar drop uses the target row midpoint for before/after placement', () => {
  assert.match(fragment, /ACTION_DROP[\s\S]*placement\.move\(/);
  assert.match(fragment, /placement\.isAfter\(event\.y, row\.height\)/);
  assert.match(placement, /result\.add\(targetIndex \+ if \(after\) 1 else 0, sourceId\)/);
  assert.doesNotMatch(fragment, /order\.add\(order\.indexOf\(id\), moved\)/);
});

test('orientation and visible-button preferences are unchanged and order saves on mutations only', () => {
  assert.match(fragment, /if \(updated != order\) \{[\s\S]*config\.storeOrder\(prefs, landscape, order\)/);
  assert.match(fragment, /config\.getOrder\(prefs, landscape\)/);
  assert.match(fragment, /config\.storeHidden\(prefs, landscape, hidden\)/);
  assert.match(fragment, /config\.storeSize\(prefs, landscape,/);
});
