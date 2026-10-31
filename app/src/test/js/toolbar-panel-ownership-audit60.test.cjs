'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const read = (p) => fs.readFileSync(path.resolve(__dirname, '../../main/java/com/prirai/android/nira/', p), 'utf8');
const fragment = read('BrowserFragment.kt');
const toolbar = read('browser/toolbar/ToolbarGestureHandler.kt');
const panels = read('browser/panels/SidePanelInfrastructure.kt');
const dispatcher = read('browser/SwipeGestureLayout.kt');

test('ZC-AUD-048 browser supplies live panel ownership, not a hitbox snapshot', () => {
  assert.match(fragment, /ToolbarGestureHandler\([\s\S]*?canClaimToolbarSwipe\s*=\s*\{[\s\S]*?sidePanelInfrastructure\?\.anyPanelEngaged\(\) != true/);
  assert.match(fragment, /setupEdgePanelGestures\(\)/);
  assert.match(fragment, /SidePanelInfrastructure\.attach\(/);
  assert.match(fragment, /gestures\.addGestureListener\(this\)/.test(panels) ? /ToolbarGestureHandler\(/ : /unlikely-register/);
  assert.match(dispatcher, /listeners\.firstOrNull \{ listener ->[\s\S]*?listener\.onSwipeStarted\(start, next\)/);
});

test('The panel engagement includes mounted, dragging, settling and pending unmount', () => {
  assert.match(panels, /fun leftIsEngaged\(\): Boolean[\s\S]*?host\.leftContainer\.childCount > 0 \|\| controller\.snapshot\.side == SidePanelSide\.LEFT/);
  assert.match(panels, /fun rightIsEngaged\(\): Boolean[\s\S]*?host\.rightContainer\.childCount > 0 \|\| controller\.snapshot\.side == SidePanelSide\.RIGHT/);
  assert.match(panels, /fun anyPanelEngaged\(\): Boolean = leftIsEngaged\(\) \|\| rightIsEngaged\(\)/);
});

test('The occluded toolbar refuses acquisition before the geometric test', () => {
  assert.match(toolbar, /private val canClaimToolbarSwipe: \(\) -> Boolean = \{ true \}/);
  assert.match(toolbar, /override fun onSwipeStarted\(start: PointF, next: PointF\): Boolean \{[\s\S]*?if \(!toolbarIsUncovered\(\)\) return false[\s\S]*?start\.isInToolbar\(\)/);
  assert.match(toolbar, /private fun toolbarIsUncovered\(\): Boolean \{[\s\S]*?if \(canClaimToolbarSwipe\(\)\) return true[\s\S]*?gestureEpoch = animationOwner\.beginGesture\(\)/);
});

test('Opening a drawer during an owned gesture or settle prevents stale navigation', () => {
  for (const method of ['onSwipeUpdate', 'onSwipeFinished', 'onSwipeCancelled']) {
    const start = toolbar.indexOf('override fun ' + method);
    assert.ok(start >= 0, method);
    assert.match(toolbar.slice(start,start+260), /if \(!toolbarIsUncovered\(\) \|\| !gestureActive/);
  }
  assert.match(toolbar, /addUpdateListener \{ animator ->\s*if \(!canClaimToolbarSwipe\(\)\) \{[\s\S]*?animationOwner\.beginGesture\(\)/);
  assert.match(toolbar, /getAnimator\(browserFinalXCoordinate[\s\S]*?\) \{\s*if \(!canClaimToolbarSwipe\(\)\)/);
  assert.match(toolbar, /store\.state\.canFinishSwipe\(source, tab\.id\)/);
});
