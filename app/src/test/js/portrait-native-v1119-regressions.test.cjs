"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = file => fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const host = source(base + "components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt");
const native = source(base + "components/toolbar/modern/PortraitNativeTabStrip.kt");
const workflow = source(".github/workflows/apk-release.yml");

test("v1119: bottom portrait uses an independent Android View, and landscape keeps Compose", () => {
  assert.match(host, /private val portraitTabStrip = PortraitNativeTabStrip\(context\)/);
  assert.match(host, /preferences\.niraGroupBarBottom/);
  assert.match(host, /orientation != android\.content\.res\.Configuration\.ORIENTATION_LANDSCAPE/);
  assert.match(host, /portraitTabStrip\.visibility = if \(native\) VISIBLE else GONE/);
  assert.match(host, /composeView\.visibility = if \(native\) GONE else VISIBLE/);
  assert.match(host, /updateOrientationMode\(newConfig\.orientation\)/);
});

test("v1119: native portrait tabs remain populated from filtered store state", () => {
  assert.match(host, /portraitTabStrip\.updateTabs\(scopedTabs, selectedId\)/);
  assert.match(native, /fun updateTabs\(tabs: List<TabSessionState>, selectedId: String\?\)/);
  assert.match(native, /parent\.addView\(tabContainer\)/);
  assert.match(native, /row\.addView\(actionText\("\+"/);
  assert.match(native, /niraTabStripHeightDp\.coerceIn\(40, 80\)/);
  assert.match(native, /contentDescription = "Karta: \$title"/);
});

test("v1119: native row supports selecting, closing, and correct-context new tabs", () => {
  assert.match(host, /portraitTabStrip\.onTabSelected =/);
  assert.match(host, /portraitTabStrip\.onTabClosed =/);
  assert.match(host, /context\.components\.tabsUseCases\.removeTab\(id\)/);
  assert.match(host, /portraitTabStrip\.onNewTab =/);
  assert.match(host, /BrowserProfile\.contextIdFor\(manager\.getActiveProfile\(\), isPrivate\)/);
  assert.match(host, /private = isPrivate/);
  assert.match(host, /selectTab = true/);
});

test("v1119: separately installable signed ARM64 artifact has current metadata", () => {
  const gradle = source("app/build.gradle");
  assert.match(gradle, /versionCode = 169/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha74-clean"/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1169"/);
  assert.match(workflow, /EXPECTED_VERSION_NAME: 0\.5\.22-alpha74-clean/);
  assert.match(workflow, /zavro-arm64-update-apk-v1169/);
  assert.match(workflow, /EXPECTED_CERT_SHA256/);
});
