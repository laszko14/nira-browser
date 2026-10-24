"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = file => fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const native = source(base + "components/toolbar/modern/PortraitNativeTabStrip.kt");
const host = source(base + "components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt");

test("v1121: portrait remains Android Views while landscape remains Compose", () => {
  assert.match(host, /portraitTabStrip\.visibility = if \(native\) VISIBLE else GONE/);
  assert.match(host, /composeView\.visibility = if \(native\) GONE else VISIBLE/);
});

test("v1121: compact row contains count, favicon and add; not full-width title/close pills", () => {
  assert.match(native, /actionText\("⌃"/);
  assert.match(native, /actionText\("\(\$\{segmentTabs\.size\}\)"/);
  assert.match(native, /parent\.addView\(tabContainer\)/);
  assert.match(native, /row\.addView\(actionText\("\+"/);
  assert.match(native, /FrameLayout\(context\)/);
  assert.match(native, /ImageView\(context\)/);
  assert.match(native, /LinearLayout\.LayoutParams\(dp\(39\), dp\(34\)\)/);
  assert.doesNotMatch(native, /val titleView = TextView\(context\)/);
  assert.doesNotMatch(native, /val closeView = TextView\(context\)/);
});

test("v1121: favicon from tab content, cached or async BrowserIcons loader; no main-thread network", () => {
  assert.match(native, /tab\.content\.icon \?: FaviconLoader\.getFromMemorySync\(context, url\)/);
  assert.match(native, /iconScope\.launch/);
  assert.match(native, /FaviconLoader\.loadFavicon\(context, url\)/);
  assert.match(native, /image\.tag == requestKey/);
  assert.match(native, /onDetachedFromWindow/);
  assert.match(native, /iconScope\.cancel\(\)/);
});

test("v1121: selection is visibly outlined, normal click switches tabs, long press closes from menu", () => {
  assert.match(native, /if \(selected\) setStroke\(dp\(1\), Color\.rgb\(227, 96, 157\)\)/);
  assert.match(native, /setOnClickListener \{ onTabSelected\?\.invoke\(tab\.id\) \}/);
  assert.match(native, /setOnLongClickListener/);
  assert.match(native, /menu\.add\("Zamknij kartę"\)/);
  assert.match(native, /onTabClosed\?\.invoke\(tab\.id\)/);
});

test("v1121: keeps horizontal scroll while selected favicon is visible", () => {
  assert.match(native, /HorizontalScrollView\(context\)/);
  assert.match(native, /val oldScroll = scroller\.scrollX/);
  assert.match(native, /scroller\.scrollTo\(newScroll\.coerceIn\(0, maxScroll\), 0\)/);
});

test("v1121: signed APK version is 1121 and independent package + same certificate", () => {
  const gradle = source("app/build.gradle");
  const workflow = source(".github/workflows/apk-release.yml");
  assert.match(gradle, /versionCode = 127/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha33-mpv1"/);
  assert.match(gradle, /applicationIdSuffix = '\.mpv'/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1127"/);
  assert.match(workflow, /EXPECTED_CERT_SHA256/);
  assert.match(workflow, /zavro-x-mpv-arm64-apk-v1127/);
});
