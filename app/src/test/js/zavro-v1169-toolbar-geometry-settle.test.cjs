"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../../../..");
const source = relative => fs.readFileSync(path.join(root, relative), "utf8");

test("v1169: toolbar animation always settles the Gecko viewport after its final frame", () => {
    const toolbar = source("app/src/main/java/com/prirai/android/nira/components/toolbar/modern/ModernToolbarSystem.kt");
    const fragment = source("app/src/main/java/com/prirai/android/nira/BrowserFragment.kt");

    // The final update MUST occur after turning off the animation flag,
    // even if the last ValueAnimator callback reached the exact target.
    assert.match(toolbar, /offsetAnimating = false\s+animatedTargetOffset = null[\s\S]*?setToolbarOffset\(target\)/);
    assert.doesNotMatch(toolbar, /if\s*\(currentOffset\s*!=\s*target\)\s*setToolbarOffset\(target\)/);

    // During animation only a temporary translation is safe for GeckoView;
    // the non-animating final callback restores it and reconciles margins.
    assert.match(fragment, /isScrollAnimationRunning\(\) == true\)\s*\{\s*binding\.swipeRefresh\.translationY/);
    assert.match(fragment, /binding\.swipeRefresh\.translationY = 0f[\s\S]*?lp\.topMargin != visibleHeight/);
});

test("v1169: duplicate in-flight toolbar animations are still skipped", () => {
    const toolbar = source("app/src/main/java/com/prirai/android/nira/components/toolbar/modern/ModernToolbarSystem.kt");
    assert.match(toolbar, /hideAnimator\.isRunning && animatedTargetOffset == target/);
    assert.match(toolbar, /if \(frameOffset != currentOffset\) setToolbarOffset\(frameOffset\)/);
});
