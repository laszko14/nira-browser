"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const read = relative => fs.readFileSync(path.resolve(__dirname, "../../../../", relative), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const palette = read(base + "theme/TabGroupTones.kt");
const drawer = read(base + "browser/tabs/TabsBottomSheetFragment.kt");
const native = read(base + "components/toolbar/modern/PortraitNativeTabStrip.kt");
const hybrid = read(base + "browser/tabs/compose/HybridGroupStrip.kt");
const allTabs = read(base + "browser/tabs/compose/TabBarCompose.kt");
const swipeable = read(base + "browser/tabs/compose/SwipeableTabPill.kt");

test("v1177 colors come from the persisted group model, with blue ungrouped fallback", () => {
  assert.match(palette, /UNGROUPED_BLUE: Int = 0xFF2196F3/);
  assert.match(drawer, /groupColor = group\.color/);
  assert.match(drawer, /groupColor = null/);
  assert.match(drawer, /val tones = TabGroupTones\.forGroup\(if \(ungrouped\) null else groupColor\)/);
  assert.match(drawer, /val tones = TabGroupTones\.forGroup\(groupColor\)/);
  assert.match(native, /groupBackground\(group\.color\)/);
  assert.match(native, /groupBackground\(null\)/);
  assert.match(native, /tabChip\(tab, selectedId, dark, segment, group\.color\)/);
  assert.match(native, /tabChip\(tab, selectedId, dark, neutral, null\)/);
});

test("v1177 both under-address bars use the same tone mapping for all member tabs", () => {
  assert.match(hybrid, /TabGroupTones\.forGroup\(groupColor\)/);
  assert.match(hybrid, /Color\(if \(selected\) tones\.activeStart else tones\.tabStart\)/);
  assert.match(allTabs, /TabGroupTones\.forGroup\(null\)/);
  assert.match(allTabs, /TabGroupTones\.forGroup\(group\.color\)/);
  assert.match(swipeable, /TabGroupTones\.forGroup\(groupColor\)/);
  assert.match(swipeable, /Color\(if \(isSelected\) tone\.activeStart else tone\.tabStart\)/);
});

test("v1177 changes to Room group colors invalidate the bar's color cache", () => {
  assert.match(allTabs, /remember\(order, tabs, groups\)/);
  assert.match(allTabs, /liveGroups\.firstOrNull \{ it\.id == orderItem\.groupId \}\?\.color/);
  assert.match(drawer, /unifiedGroupManager\.groupsState\.collectAsState\(\)/);
});
