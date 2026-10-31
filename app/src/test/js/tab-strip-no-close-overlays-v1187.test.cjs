"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = file => fs.readFileSync(path.resolve(__dirname, "../../../../", file), "utf8");
const base = "app/src/main/java/com/prirai/android/nira/";
const native = source(base + "components/toolbar/modern/PortraitNativeTabStrip.kt");
const compose = source(base + "browser/tabs/compose/TabBarCompose.kt");
const swipe = source(base + "browser/tabs/compose/SwipeableTabPill.kt");
const swipeOwner = source(base + "browser/tabs/compose/SwipePillGestureOwner.kt");
const hybrid = source(base + "browser/tabs/compose/HybridGroupStrip.kt");

test("native tab icons contain no close overlays; contextual close remains", () => {
  const start = native.indexOf("private fun tabChip(");
  const end = native.indexOf("    private fun ", start + 1);
  assert.ok(start >= 0 && end > start);
  const pill = native.slice(start, end);
  assert.doesNotMatch(pill, /tabContainer\.addView\(close|val close = TextView|badgeSize/);
  assert.match(pill, /setOnClickListener \{ onTabSelected\?\.invoke\(tab\.id\) \}/);
  assert.match(native, /menu\.add\("Zamknij kartę"\)/);
});

test("Compose tab circles have no touchable X; swipe close remains", () => {
  const start = compose.indexOf("private fun TabPill(");
  const end = compose.indexOf("* Invisible divider", start);
  assert.ok(start >= 0 && end > start);
  const pill = compose.slice(start, end);
  assert.doesNotMatch(pill, /badgeSize|Icons\.Default\.Close|onTabClose/);
  assert.match(compose, /onTabClose\(item\.tab\.id\)/);
});

test("swipeable group-tab circles have no close badge; gestures remain", () => {
  assert.doesNotMatch(swipe, /badgeSize|Icons\.Default\.Close|clickable\(onClick = onTabClose\)/);
  assert.match(swipeOwner, /offsetY < -threshold -> SwipePillOutcome.CLOSE/);
  assert.match(swipeOwner, /offsetY > threshold -> SwipePillOutcome.MENU/);
  assert.match(swipe, /SwipePillOutcome.CLOSE -> currentOnTabClose\(\)/);
  assert.match(swipe, /SwipePillOutcome.MENU -> currentOnShowMenu\(\)/);
});

test("hybrid group strip does not draw a dedicated X beside tabs", () => {
  const start = hybrid.indexOf("fun HybridGroupTabs(");
  assert.ok(start >= 0);
  const strip = hybrid.slice(start);
  assert.doesNotMatch(strip, /Icons\.Default\.Close|onClick = \{ onTabClosed\(tab\.id\) \}/);
  assert.match(strip, /onTabSelected\(tab\.id\)/);
  assert.match(strip, /onNewTabInGroup/);
});
