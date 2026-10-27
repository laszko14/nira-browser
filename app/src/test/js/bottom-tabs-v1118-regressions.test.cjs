"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = file =>
  fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const compose = source(base + "components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt");
const toolbar = source(base + "components/toolbar/unified/UnifiedToolbar.kt");
const browser = source(base + "BrowserFragment.kt");

test("v1119: portrait tab host and Compose child both have a minimum real height", () => {
  assert.match(compose, /minimumHeight = rowMinimumHeightPx/);
  assert.match(compose, /composeView\.minimumHeight = rowMinimumHeightPx/);
  assert.match(compose, /Modifier\.fillMaxWidth\(\)\.heightIn\(/);
  assert.match(compose, /niraTabStripHeightDp\.coerceIn\(40, 80\)/);
});

test("v1119: orientation change forces a new Compose measurement and Gecko viewport reconciliation", () => {
  assert.match(compose, /override fun onConfigurationChanged\(/);
  assert.match(compose, /composeView\.requestLayout\(\)/);
  assert.match(browser, /override fun onConfigurationChanged\(/);
  assert.match(browser, /bottomStrip\.requestLayout\(\)/);
  assert.match(browser, /unifiedToolbar\?\.reconcileEngineViewHeight\(\)/);
  assert.match(toolbar, /bottom\.bringToFront\(\)/);
});

test("v1119: tab updates change reactive state without tearing down the composition", () => {
  const start = compose.indexOf("fun updateTabs(");
  const end = compose.indexOf("fun updateProfileIcon(", start);
  assert.ok(start > 0 && end > start);
  const update = compose.slice(start, end);
  assert.match(update, /lastPushedTabState = PushedTabState\(/);
  assert.doesNotMatch(update, /^\s*(?:composeView\.)?setContent\(/m);
  assert.match(compose, /val lifecycleOwner = context as LifecycleOwner/);
  assert.match(compose, /DisposeOnViewTreeLifecycleDestroyed/);
});

test("v1119: fallback tabs remain strictly scoped to current profile and privacy mode", () => {
  assert.match(compose, /it\.profileId == currentProfile\.id && it\.privateMode == isPrivateMode/);
  assert.match(compose, /if \(tab\.content\.private != isPrivateMode\) return false/);
  assert.match(compose, /tab\.contextId == "private"/);
  assert.match(compose, /currentProfile\.id == "default" && tab\.contextId == null/);
});

test("v1119: APK metadata matches the next update and Zavro debug update identity", () => {
  const gradle = source("app/build.gradle");
  const workflow = source(".github/workflows/apk-release.yml");
  assert.match(gradle, /versionCode = 179/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha84-clean"/);
  assert.match(gradle, /applicationIdSuffix = '\.debug'/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1179"/);
  assert.match(workflow, /name: zavro-arm64-update-apk-v1179/);
});
