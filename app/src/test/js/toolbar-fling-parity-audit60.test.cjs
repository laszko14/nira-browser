'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const src = p => fs.readFileSync(path.resolve(__dirname, '../../main/java/com/prirai/android/nira/', p), 'utf8');
const www = src('browser/toolbar/ToolbarGestureHandler.kt');
const home = src('browser/toolbar/HomeToolbarGestureHandler.kt');
const policy = src('browser/toolbar/ToolbarSwipeFinishPolicy.kt');
const dispatch = src('browser/SwipeGestureLayout.kt');

test('ZC-AUD-049 both production handlers delegate identical physical direction and velocity', () => {
  for (const [name, handler] of [['WWW', www], ['Home', home]]) {
    const start = handler.indexOf('private fun isGestureComplete(velocityX: Float)');
    const end = handler.indexOf('private fun getAnimator(', start);
    assert.ok(start >= 0 && end > start, name);
    const body = handler.slice(start, end);
    assert.match(body, /ToolbarSwipeFinishPolicy\.completes\(/);
    assert.match(body, /swipingLeft = gestureDirection == GestureDirection\.RIGHT_TO_LEFT/);
    assert.match(body, /velocityX = velocityX/);
    assert.match(body, /minimumFlingVelocity = minimumFlingVelocity/);
    assert.match(body, /visibleFraction = /);
    assert.doesNotMatch(body, /velocityMatchesDirection|reverseFling/);
    assert.match(handler, /if \(dx < 0\) \{\s*GestureDirection\.RIGHT_TO_LEFT/);
    assert.match(handler, /store\.state\.canFinishSwipe\(source, tab\.id\)/);
  }
  assert.match(www, /visibleWidth\.toDouble\(\)/);
  assert.match(home, /visibleWidth \/ windowWidth\)\.toDouble\(\)/);
});
test('GestureDetector retains raw X sign and rejects only strong reverse flings', () => {
  assert.match(dispatch, /override fun onFling\([\s\S]*?activeListener\?\.onSwipeFinished\(velocityX, velocityY\)/);
  assert.match(policy, /val strongFling = abs\(velocityX\) >= minimumFlingVelocity/);
  assert.match(policy, /if \(swipingLeft\) velocityX > 0f else velocityX < 0f/);
  assert.match(policy, /visibleFraction >= FINISH_FRACTION \|\| strongFling/);
  assert.match(policy, /FINISH_FRACTION = 0\.25/);
  assert.match(www, /val isLtr = activity\.resources\.configuration\.layoutDirection == View\.LAYOUT_DIRECTION_LTR/);
});
