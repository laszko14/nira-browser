'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = path.resolve(__dirname, '../../main/java/com/prirai/android/nira');
const read = p => fs.readFileSync(path.join(base,p),'utf8');

test('default PWA creates GeckoSession in the canonical context', () => {
  const frag = read('webapp/WebAppFragment.kt');
  assert.match(frag,/BrowserProfile\.contextIdFor\(profileId, isPrivate = false\)/);
  assert.doesNotMatch(frag,/val contextId = "profile_\$profileId"/);
});
test('PWA has no reference to main BrowserStore-selected hit result', () => {
  const frag=read('webapp/WebAppFragment.kt');
  assert.match(frag,/override fun onLongPress\(hitResult: HitResult\)/);
  assert.match(frag,/PwaHitResultMenu\.safeTarget\(hitResult\)/);
  assert.doesNotMatch(frag,/ContextMenuIntegration\(/);
});
test('profile deletion restricts storage cleanup to explicit named context and blocks resurrection', () => {
  const manager=read('browser/profile/ProfileManager.kt');
  const middleware=read('browser/profile/ProfileMiddleware.kt');
  assert.match(manager,/clearDataForSessionContext\(contextId\)/);
  assert.match(manager,/sessionStorage\.save\(appContext\.components\.store\.state\)/);
  assert.match(manager,/ProfilePwaLiveSessions\.beginDeletion\(profileId\)/);
  assert.match(middleware,/isDeletedProfileContext\(it\.state\.contextId\)/);
});
test('opener storage partition remains suspected until native Gecko inheritance measured', () => {
  const middleware=read('browser/profile/ProfileMiddleware.kt');
  assert.match(middleware,/val parent = store\.state\.tabs\.firstOrNull/);
  assert.match(middleware,/parent\.contextId/);
});
