"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = file => fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";

test("v1117: duplicate tab uses named tab and group IDs", () => {
  const code = source(base + "components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt");
  assert.match(code, /addTabToGroup\(tabId = newTabId, groupId = request\.groupId\)/);
  assert.doesNotMatch(code, /addTabToGroup\(request\.groupId, newTabId\)/);
});
test("v1117: private group new tab remains private", () => {
  const code = source(base + "browser/tabs/compose/TabViewModel.kt");
  const start = code.indexOf("fun createNewTabInGroup(");
  assert.ok(start > -1);
  assert.match(code.slice(start, start + 260), /orderManager\.addNewTabToGroup\(groupId\)/);
  const manager = source(base + "browser/tabs/compose/TabOrderManager.kt");
  const creation = manager.slice(manager.indexOf("suspend fun addNewTabToGroup("));
  assert.match(creation, /private = contextId == "private"/);
});
test("v1117: group close really removes tabs", () => {
  const code = source(base + "browser/tabs/compose/TabViewModel.kt");
  const start = code.indexOf("fun closeAllTabsInGroup(");
  assert.ok(start > -1);
  assert.match(code.slice(start, start + 700), /useCases\.removeTab\(id\)/);
});
test("v1117: new group order stores canonical Room ID", () => {
  const code = source(base + "browser/tabs/compose/TabOrderManager.kt");
  const start = code.indexOf("suspend fun createGroup(");
  const part = code.slice(start, start + 1000);
  assert.match(part, /val created = groupManager\.createGroup\(/);
  assert.match(part, /val groupId = created\.id/);
});
test("v1117: group ordering is reindexed transactionally", () => {
  const code = source(base + "browser/tabgroups/UnifiedTabGroupManager.kt");
  assert.match(code, /database\.withTransaction/);
  assert.match(code, /dao\.insertTabGroupMember\(TabGroupMember\(tabId = id, groupId = groupId, position = index\)\)/);
});
test("v1117: startup waits for group database before cleanup", () => {
  const code = source(base + "browser/tabs/compose/TabOrderManager.kt");
  assert.match(code, /if \(!groupManager\.awaitInitialized\(\)\) return order/);
});
test("v1117: current Android data extraction rules have their own resource", () => {
  const xml = source("app/src/main/res/xml/data_extraction_rules.xml");
  const manifest = source("app/src/main/AndroidManifest.xml");
  assert.match(xml, /<data-extraction-rules>/);
  assert.match(xml, /<cloud-backup>/);
  assert.match(xml, /<device-transfer>/);
  assert.match(manifest, /android:dataExtractionRules="@xml\/data_extraction_rules"/);
});
test("v1117: imported ZIP entries have strict limits", () => {
  const code = source(base + "browser/profile/ProfileExportManager.kt");
  assert.match(code, /maxEntryBytes = 2 \* 1024 \* 1024/);
  assert.match(code, /maxTotalBytes = 4 \* 1024 \* 1024/);
  assert.match(code, /entry\.name in allowed/);
});
test("v1128: stable debug package and update version agree", () => {
  const gradle = source("app/build.gradle");
  assert.match(gradle, /versionCode = 189/);
  assert.match(gradle, /applicationIdSuffix = '\.debug'/);
  const workflow = source(".github/workflows/apk-release.yml");
  assert.match(workflow, /EXPECTED_VERSION_CODE: "1189"/);
});
