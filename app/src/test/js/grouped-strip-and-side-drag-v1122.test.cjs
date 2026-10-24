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

test("v1122: drop saves changes via viewmodel reorder and group moves", () => {
  assert.match(side, /viewModel\.moveTabToGroup\(fromId, groupId\)/);
  assert.match(side, /viewModel\.reorderTabInGroup\(fromId, targetId, targetGroup\.id, after\)/);
  assert.match(side, /viewModel\.moveTabToGroup\(fromId, targetGroup\.id, targetId, after\)/);
  assert.match(side, /viewModel\.moveTabToPosition\(fromId, index\)/);
});

test("v1122: metadata and stable signing", () => {
  const gradle = read("app/build.gradle");
  const workflow = read(".github/workflows/apk-release.yml");
  assert.match(gradle, /versionCode = 123/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha29"/);
  assert.match(gradle, /applicationIdSuffix = '\.debug'/);
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1123"/);
  assert.match(workflow, /EXPECTED_CERT_SHA256:/);
  assert.match(workflow, /zavro-arm64-update-apk-v1123/);
});
