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
  assert.match(native, /onGroupToggle\?\.invoke\(group\.id\)/);
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

test("v1128: side drawer delegates all drop types to a persisted transaction", () => {
  assert.match(side, /viewModel\.commitSidePanelDrop\(fromId, target, after, expectedProfile\)/);
  const model = read(base + "browser/tabs/compose/TabViewModel.kt");
  assert.match(model, /targetKey\.startsWith\("group_tab_"\)/);
  assert.match(model, /targetKey\.startsWith\("group_"\)/);
  assert.match(model, /targetKey\.startsWith\("single_tab_"\)/);
  assert.match(model, /groupManager\.addTabToGroup\(/);
});

test("v1122: metadata and stable signing", () => {
  const gradle = read("app/build.gradle");
  const workflow = read(".github/workflows/apk-release.yml");
  assert.match(gradle, /versionCode = 168/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha73-clean"/);
  assert.match(gradle, /applicationIdSuffix = '\.debug'/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1168"/);
  assert.match(workflow, /EXPECTED_CERT_SHA256:/);
  assert.match(workflow, /zavro-arm64-update-apk-v1168/);
});

test("v1128: side-drawer drag commits one awaited transaction, not an async redraw", () => {
  const model = read(base + "browser/tabs/compose/TabViewModel.kt");
  assert.match(side, /dragY = pointerRoot\.y - listTopY/);
  assert.match(side, /currentMove\(rowOrigin \+ change\.position\)/);
  assert.match(side, /viewModel\.commitSidePanelDrop\(fromId, target, after, expectedProfile\)/);
  assert.match(model, /suspend fun commitSidePanelDrop\(/);
  assert.match(model, /groupManager\.moveTabRelative\(draggedId, group\.id, targetId, insertAfter\)/);
  assert.match(model, /saveCurrentOrder\(profileId\)/);
});

test("v1128: floating media button and expanded list match the selected reference", () => {
  const ui = read(base + "media/MediaFloatingUi.kt");
  assert.match(ui, /FrameLayout\.LayoutParams\(dp\(29\), dp\(29\), Gravity\.CENTER\)/);
  assert.match(ui, /downloadGlyph\.visibility = if \(amount > 0\) View\.GONE/);
  assert.match(ui, /behavior\.skipCollapsed = true/);
  assert.match(ui, /behavior\.state = BottomSheetBehavior\.STATE_EXPANDED/);
  assert.match(ui, /height = body\.measuredHeight\.coerceIn\(dp\(130\), maxHeight\)/);
});

test("v1128: address-bar previous/next follows persisted group and tab order", () => {
  const toolbar = read(base + "components/toolbar/unified/UnifiedToolbar.kt");
  assert.match(toolbar, /action\(android\.R\.drawable\.ic_media_next, "Następna karta"\) \{ cycleTab\(1\) \}/);
  assert.match(toolbar, /action\(android\.R\.drawable\.ic_media_previous, "Poprzednia karta"\) \{ cycleTab\(-1\) \}/);
  assert.match(toolbar, /order\.getAllTabIds\(\)/);
  assert.match(toolbar, /orderManager\.rebuildOrderForProfile\(profileId, tabs\)/);
  assert.match(toolbar, /if \(!manager\.awaitInitialized\(\)\)/);
  assert.match(toolbar, /tabCycleMutex\.withLock/);
  assert.match(toolbar, /ids\[next\]/);
  assert.doesNotMatch(toolbar, /selectTab\(tabs\[\(index \+ direction/);
});
test("v1128: side panel supplements temporarily missing group headers from DB state", () => {
  assert.match(side, /unifiedGroupManager\.groupsState\.collectAsState\(\)/);
  assert.match(side, /val groups = liveGroups/);
  assert.doesNotMatch(side, /liveGroups \+ cachedGroups/);
  assert.match(side, /group\.tabIds\.any \{ it in visibleTabIds \}/);
  assert.match(side, /group\.contextId == "private"/);
  assert.match(side, /groupMenuId = group\.id/);
});

test("v1128: ungrouped tabs have a named recovery section and real group creation", () => {
  assert.match(side, /text = "\(\$\{ungroupedTabs\.size\}\) Bez grupy"/);
  assert.match(side, /item\(key = "ungrouped_header"\)/);
  assert.match(side, /showNameUngroupedDialog = true/);
  assert.match(side, /viewModel\.createNamedGroupFromUngrouped\(ids, chosenName\)/);
  assert.match(side, /groupMenuId = group\.id/);
});
test("v1128: creating groups persists profile context and verifies membership", () => {
  const vm = read(base + "browser/tabs/compose/TabViewModel.kt");
  assert.match(vm, /val resolvedContext = contextId \?: contextIdForProfile\(profileId\)/);
  assert.match(vm, /suspend fun createNamedGroupFromUngrouped/);
  assert.match(vm, /groupManager\.getAllGroups\(\)\.any/);
  assert.match(vm, /contextId = contextId/);
  assert.match(vm, /_expandedGroups\.value = _expandedGroups\.value \+ created\.id/);
});
