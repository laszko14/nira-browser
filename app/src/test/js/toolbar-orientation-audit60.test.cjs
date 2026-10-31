const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const toolbar = fs.readFileSync('app/src/main/java/com/prirai/android/nira/components/toolbar/unified/UnifiedToolbar.kt', 'utf8');
const policy = fs.readFileSync('app/src/main/java/com/prirai/android/nira/components/toolbar/ToolbarActionLayoutRefreshPolicy.kt', 'utf8');

test('same-width orientation changes have explicit Android configuration invalidation', () => {
  assert.match(toolbar, /override fun onConfigurationChanged\(newConfig: android\.content\.res\.Configuration\)[\s\S]*?refreshToolbarActionsForConfiguration\?\.invoke\(\)/);
  assert.match(toolbar, /refreshToolbarActionsForConfiguration = \{[\s\S]*?actions\.post \{[\s\S]*?applyActionPreferences\(\)/);
  assert.match(toolbar, /refreshToolbarActionsForConfiguration = null/);
});

test('layout policy compares orientation as well as width and uses the same preferences', () => {
  assert.match(toolbar, /ToolbarActionLayoutRefreshPolicy\.needsRefresh\([\s\S]*?orientation = resources\.configuration\.orientation,[\s\S]*?lastOrientation = lastOrientation/);
  assert.match(toolbar, /lastOrientation = orientation/);
  assert.match(policy, /orientation != lastOrientation/);
  assert.match(toolbar, /options\.getOrder\(prefs, horizontal\)/);
  assert.match(toolbar, /options\.getHidden\(prefs, horizontal\)/);
  assert.match(toolbar, /options\.getSize\(prefs, horizontal\)/);
  assert.match(toolbar, /options\.storeOrder\(prefs, landscape\(\)/);
});
