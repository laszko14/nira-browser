"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const read = p => fs.readFileSync(p, "utf8");
const xml = read("app/src/main/res/xml/preferences_customization.xml");
const settings = read("app/src/main/java/com/prirai/android/nira/settings/fragment/CustomizationSettingsFragment.kt");
const browser = read("app/src/main/java/com/prirai/android/nira/BrowserFragment.kt");
const toolbar = read("app/src/main/java/com/prirai/android/nira/components/toolbar/unified/UnifiedToolbar.kt");
const tabs = read("app/src/main/java/com/prirai/android/nira/browser/tabs/TabsBottomSheetFragment.kt");
const prefs = read("app/src/main/java/com/prirai/android/nira/preferences/UserPreferences.kt");
const helper = read("app/src/main/java/com/prirai/android/nira/browser/tabs/TabGridViewPreference.kt");

test("tab bar respects a persisted choice rather than forcing enabled at startup", () => {
  assert.doesNotMatch(browser, /showTabGroupBar\s*=\s*true/);
  assert.match(toolbar, /private var showTabGroupBar: Boolean = prefs\.showTabGroupBar/);
  assert.match(toolbar, /if \(!showTabGroupBar\)/);
  assert.match(settings, /UserPreferences\(requireContext\(\)\)\.showTabGroupBar = it/);
  assert.match(xml, /app:key="@string\/key_show_tab_group_bar"/);
});
test("settings and actual Compose dialog use a canonical list/grid value", () => {
  assert.match(settings, /TabGridViewPreference\.read\(requireContext\(\)\)/);
  assert.match(settings, /TabGridViewPreference\.store\(requireContext\(\), it\)/);
  assert.match(tabs, /isGridViewState\.value = TabGridViewPreference\.read\(requireContext\(\)\)/);
  assert.match(tabs, /TabGridViewPreference\.store\(requireContext\(\), (true|false)\)/);
  assert.match(helper, /canonical\.contains\(KEY\) -> canonical\.getBoolean\(KEY, false\)/);
  assert.match(helper, /legacyPrefs\.contains\(LEGACY_KEY\) -> legacy\.showTabsInGrid/);
  assert.match(tabs, /if \(isSidePanel\) \{[\s\S]*CompactSideTabs/);
});
test("unsupported controls disappear without erasing old data", () => {
  assert.doesNotMatch(xml, /app:key="@string\/key_swap_drawers"/);
  assert.doesNotMatch(xml, /app:key="interface_font_scale"/);
  assert.doesNotMatch(settings, /showFontScaleDialog\(/);
  assert.match(prefs, /var interfaceFontScale by floatPreference\(INTERFACE_FONT_SCALE, 1\.0f\)/);
  assert.match(prefs, /var showTabsInGrid by booleanPreference\(SHOW_TABS_IN_GRID, false\)/);
});
