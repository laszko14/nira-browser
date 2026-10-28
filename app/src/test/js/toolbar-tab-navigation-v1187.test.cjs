"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = file => fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const config = source(base + "components/toolbar/ToolbarActionConfiguration.kt");
const toolbar = source(base + "components/toolbar/unified/UnifiedToolbar.kt");
const dialog = source(base + "settings/fragment/CustomizationSettingsFragment.kt");

test("seven stable actions include previous and next with user-facing labels", () => {
  assert.match(config, /val editableIds = listOf\("new", "close", "settings", "back", "forward", "previous", "next"\)/);
  assert.match(config, /"previous" to "Poprzednia karta/);
  assert.match(config, /"next" to "Następna karta/);
  assert.match(config, /parsed \+ editableIds\.filterNot \{ it in parsed \}/);
  assert.match(config, /raw\.split\(","\)\.filter \{ it in editableIds \}\.toSet\(\)/);
});

test("portrait and landscape share the same seven-ID list but store separate preferences", () => {
  assert.match(config, /niraToolbarLandscapeActionOrder/);
  assert.match(config, /niraToolbarPortraitActionOrder/);
  assert.match(config, /niraToolbarLandscapeHiddenActions/);
  assert.match(config, /niraToolbarPortraitHiddenActions/);
  assert.match(config, /niraToolbarLandscapeActionSizeDp/);
  assert.match(config, /niraToolbarPortraitActionSizeDp/);
  assert.match(dialog, /order\.forEach \{ id ->/);
  assert.match(dialog, /config\.storeOrder\(prefs, landscape, order\)/);
  assert.match(dialog, /config\.storeHidden\(prefs, landscape, hidden\)/);
  assert.match(dialog, /config\.storeSize\(prefs, landscape/);
});

test("tab-cycling views are the original actions, now reordered/hidden by configuration", () => {
  assert.match(toolbar, /action\(android\.R\.drawable\.ic_media_next, "Następna karta"\) \{ cycleTab\(1\) \}/);
  assert.match(toolbar, /action\(android\.R\.drawable\.ic_media_previous, "Poprzednia karta"\) \{ cycleTab\(-1\) \}/);
  assert.match(toolbar, /val desiredIds = \(0 until prefixCount\)\.map \{ "refresh_\$it" \} \+\s*orderedIds/);
  assert.doesNotMatch(toolbar, /orderedIds \+ listOf\("next", "previous"\)/);
  assert.match(toolbar, /ids\[child\] in options\.editableIds/);
  assert.match(toolbar, /orderedIds\.asReversed\(\)/);
  assert.match(toolbar, /options\.storeOrder\(prefs, landscape\(\),/);
});
