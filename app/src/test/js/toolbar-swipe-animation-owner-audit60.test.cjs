'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../main/java/com/prirai/android/nira/browser/toolbar');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const owner = read('ToolbarSwipeAnimationOwner.kt');

for (const file of ['ToolbarGestureHandler.kt', 'HomeToolbarGestureHandler.kt']) {
  const src = read(file);
  test(file + ' invalidates older animation on accepted gesture, not on rejected offer', () => {
    assert.match(src, /return if \([\s\S]*?gestureEpoch = animationOwner\.beginGesture\(\)/);
    assert.match(src, /gestureActive = true[\s\S]*?gestureDirection = direction/);
    if (file === 'ToolbarGestureHandler.kt') {
      // Stage 13C adds the panel's higher-priority owner without weakening
      // stage 13B's epoch/gesture guards.
      assert.match(src, /if \(!toolbarIsUncovered\(\) \|\| !gestureActive \|\| !animationOwner\.isCurrent\(gestureEpoch\)\) return/);
    } else {
      assert.match(src, /if \(!gestureActive \|\| !animationOwner\.isCurrent\(gestureEpoch\)\) return/);
    }
    assert.match(src, /val epoch = gestureEpoch[\s\S]*?val direction = gestureDirection/);
    assert.match(src, /if \(!animationOwner\.isCurrent\(epoch\)\) return@addUpdateListener/);
    assert.match(src, /animationOwner\.play\([\s\S]*?getAnimator\(browserFinalXCoordinate/);
    assert.match(src, /animationOwner\.fadePreview\(epoch, shortDuration\.toLong\(\)\)/);
    assert.doesNotMatch(src, /doOnEnd|\.setListener\(object : AnimatorListenerAdapter/);
  });
}

test('Shared animation owner invalidates before cancel() and protects completed callbacks', () => {
  assert.match(owner, /generation \+= 1L[\s\S]*?activeAnimator = null[\s\S]*?stale\?\.cancel\(\)/);
  assert.match(owner, /override fun onAnimationCancel\(animation: Animator\)[\s\S]*?cancelled = true/);
  assert.match(owner, /if \(!cancelled && isCurrent\(token\)\) onComplete\(\)/);
  assert.match(owner, /if \(isCurrent\(token\)\) preview\.isVisible = false/);
  assert.match(owner, /onViewDetachedFromWindow\(v: View\)[\s\S]*?invalidate\(\)/);
  assert.match(owner, /preview\.animate\(\)\.cancel\(\)/);
});
