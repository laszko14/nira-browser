const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = 'app/src/main/java/com/prirai/android/nira/';
const browser = fs.readFileSync(base+'BrowserFragment.kt','utf8');
const toolbar = fs.readFileSync(base+'components/toolbar/unified/UnifiedToolbar.kt','utf8');
const tabs = fs.readFileSync(base+'browser/tabs/TabsBottomSheetFragment.kt','utf8');
const prefs = fs.readFileSync(base+'preferences/UserPreferences.kt','utf8');
const settings = fs.readFileSync(base+'settings/fragment/CustomizationSettingsFragment.kt','utf8');
const xml = fs.readFileSync('app/src/main/res/xml/preferences_customization.xml','utf8');

test('tab group bar switch is no longer overwritten during browser startup', () => {
  assert.doesNotMatch(browser,/showTabGroupBar\s*=\s*true/);
  assert.match(toolbar,/private var showTabGroupBar: Boolean = prefs\.showTabGroupBar/);
  assert.match(toolbar,/if \(!showTabGroupBar\)\s*\{\s*return/);
  assert.match(settings,/UserPreferences\(requireContext\(\)\)\.showTabGroupBar = it/);
});
test('dialog grid and settings switch share one preference', () => {
  assert.match(xml, /app:key="@string\/key_show_tabs_in_grid"/);
  assert.match(prefs,/var showTabsInGrid by booleanPreference\(SHOW_TABS_IN_GRID,/);
  assert.match(tabs,/isGridViewState\.value = viewPrefs\.showTabsInGrid/);
  assert.match(tabs,/viewPrefs\.showTabsInGrid = false/);
  assert.match(tabs,/viewPrefs\.showTabsInGrid = true/);
  assert.doesNotMatch(tabs,/getBoolean\("is_grid_view"|putBoolean\("is_grid_view"/);
});
test('unsupported swap and font scale controls are not offered as functional', () => {
  assert.doesNotMatch(xml,/app:key="@string\/key_swap_drawers"/);
  assert.doesNotMatch(xml,/app:key="interface_font_scale"/);
  assert.doesNotMatch(settings,/preference = "interface_font_scale"/);
  assert.match(prefs,/var interfaceFontScale by floatPreference\(INTERFACE_FONT_SCALE,/);
});
