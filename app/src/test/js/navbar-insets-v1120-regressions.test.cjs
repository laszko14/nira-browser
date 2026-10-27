"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = file => fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const activity = source(base + "BrowserActivity.kt");
const positioning = source(base + "browser/WebContentPositionManager.kt");
const toolbar = source(base + "components/toolbar/unified/UnifiedToolbar.kt");

test("v1120: activity reserves bottom navigation inset in portrait and side inset in landscape", () => {
  assert.match(activity, /WindowInsetsCompat\.Type\.navigationBars\(\)/);
  assert.match(activity, /root\.paddingBottom != nav\.bottom/);
  assert.match(activity, /root\.setPadding\(nav\.left, 0, nav\.right, nav\.bottom\)/);
  assert.doesNotMatch(activity, /root\.setPadding\(nav\.left, 0, nav\.right, 0\)/);
});

test("v1120: fullscreen reclaims hidden navigation space and the webview has no duplicate inset", () => {
  assert.match(activity, /insets\.getInsets\(/);
  assert.match(positioning, /engineViewInstance\.setPadding\(0, 0, 0, 0\)/);
  assert.doesNotMatch(positioning, /engineViewInstance\.setPadding\(0, 0, 0, navigationBarHeight\)/);
});

test("v1120: footer still attaches at bottom inside inset-safe activity content", () => {
  assert.match(toolbar, /parent\.addView\(bottom, CoordinatorLayout\.LayoutParams\(/);
  assert.match(toolbar, /gravity = android\.view\.Gravity\.BOTTOM/);
  assert.match(toolbar, /bottom\.bringToFront\(\)/);
});

test("v1120: signed ARM64 update code and artifact are bumped", () => {
  const gradle = source("app/build.gradle");
  const workflow = source(".github/workflows/apk-release.yml");
  assert.match(gradle, /versionCode = 173/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha78-clean"/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1173"/);
  assert.match(workflow, /EXPECTED_VERSION_NAME: 0\.5\.22-alpha78-clean/);
  assert.match(workflow, /zavro-arm64-update-apk-v1173/);
});
