"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const read = name => fs.readFileSync(path.resolve(__dirname, "../../../../", name), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const native = read(base + "components/toolbar/modern/PortraitNativeTabStrip.kt");
const host = read(base + "components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt");
const side = read(base + "browser/tabs/TabsBottomSheetFragment.kt");

test("v1122: separate colored group containers with scoped icons and counts", () => {
  assert.match(native, /fun updateGroups\(groups: List<TabGroupData>\)/);
  assert.match(native, /groupSegment\(group, members, selectedId, dark\)/);
  assert.match(native, /members = group\.tabIds\.mapNotNull \{ byId\[it\] \}/);
  assert.match(native, /background = groupBackground\(group\.color\)/);
  assert.match(native, /"\(\$\{members\.size\}\) " \+ group\.name/);
  assert.match(native, /val groupedIds = visibleGroups\.flatMap/);
  assert.match(native, /collapsedGroups/);
  assert.match(native, /onNewTabInGroup\?\.invoke\(group\.id\)/);
  assert.match(host, /groupsState\?\.collect \{ groups/);
  assert.match(host, /portraitTabStrip\.updateGroups\(groups\)/);
});

test("v1122: native compact tabs still show favicons and handle tap and long press", () => {
  assert.match(native, /private fun tabChip\(/);
  assert.match(native, /bindFavicon\(image, tab\)/);
  assert.match(native, /setOnClickListener \{ onTabSelected\?\.invoke\(tab\.id\) \}/);
  assert.match(native, /setOnLongClickListener/);
  assert.match(native, /scroller\.scrollTo\(newScroll\.coerceIn\(0, maxScroll\), 0\)/);
});

test("v1122: side panel has a dedicated drag handle and preview that follows the finger", () => {
  assert.match(side, /Icons\.Default\.DragHandle/);
  assert.match(side, /contentDescription = "Przytrzymaj i przeciągnij kartę"/);
  assert.match(side, /detectDragGesturesAfterLongPress\(/);
  assert.match(side, /haptic\.performHapticFeedback\(HapticFeedbackType\.LongPress\)/);
  assert.match(side, /if \(draggedTab != null\) \{/);
  assert.match(side, /dragY - 23f \* density/);
  assert.match(side, /userScrollEnabled = dragTabId == null/);
  assert.match(side, /hoveredKey == "group_" \+ targetId/);
});

test("v1124: side drawer delegates all drop types to a persisted transaction", () => {
  assert.match(side, /viewModel\.commitSidePanelDrop\(fromId, target, after\)/);
  const model = read(base + "browser/tabs/compose/TabViewModel.kt");
  assert.match(model, /targetKey\.startsWith\("group_tab_"\)/);
  assert.match(model, /targetKey\.startsWith\("group_"\)/);
  assert.match(model, /targetKey\.startsWith\("single_tab_"\)/);
  assert.match(model, /groupManager\.addTabToGroup\(/);
});

test("v1122: metadata and stable signing", () => {
  const gradle = read("app/build.gradle");
  const workflow = read(".github/workflows/apk-release.yml");
  assert.match(gradle, /versionCode = 124/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha30"/);
  assert.match(gradle, /applicationIdSuffix = '\.debug'/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1124"/);
  assert.match(workflow, /EXPECTED_CERT_SHA256:/);
  assert.match(workflow, /zavro-arm64-update-apk-v1124/);
});

test("v1124: side-drawer drag commits one awaited transaction, not an async redraw", () => {
  const model = read(base + "browser/tabs/compose/TabViewModel.kt");
  assert.match(side, /dragY = pointerRootY - listTopY/);
  assert.match(side, /currentMove\(originY \+ change\.position\.y\)/);
  assert.match(side, /viewModel\.commitSidePanelDrop\(fromId, target, after\)/);
  assert.match(model, /suspend fun commitSidePanelDrop\(/);
  assert.match(model, /groupManager\.updateGroup\(group\.id, tabIds = reordered\)/);
  assert.match(model, /saveCurrentOrder\(profileId\)/);
});

test("v1124: floating media button and expanded list match the selected reference", () => {
  const ui = read(base + "media/MediaFloatingUi.kt");
  assert.match(ui, /FrameLayout\.LayoutParams\(dp\(29\), dp\(29\), Gravity\.CENTER\)/);
  assert.match(ui, /downloadGlyph\.visibility = if \(amount > 0\) View\.GONE/);
  assert.match(ui, /behavior\.skipCollapsed = true/);
  assert.match(ui, /behavior\.state = BottomSheetBehavior\.STATE_EXPANDED/);
  assert.match(ui, /height = body\.measuredHeight\.coerceIn\(dp\(130\), maxHeight\)/);
});
