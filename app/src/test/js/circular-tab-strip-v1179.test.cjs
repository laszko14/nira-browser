const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.join(__dirname, "../../main");
const read = name => fs.readFileSync(path.join(root, name), "utf8");

test("v1179 adjustable diameter and signed icon gap are persisted", () => {
  const prefs = read("java/com/prirai/android/nira/preferences/UserPreferences.kt");
  const settings = read("java/com/prirai/android/nira/settings/fragment/CustomizationSettingsFragment.kt");
  const xml = read("res/xml/preferences_customization.xml");
  assert.match(prefs, /niraTabCircleSizeDp.*34/);
  assert.match(prefs, /niraTabCircleGapDp.*4/);
  assert.match(xml, /app:min="10"/);
  assert.match(xml, /app:min="-6"/);
  assert.match(settings, /coerceIn\(10, 52\)/);
  assert.match(settings, /coerceIn\(-6, 40\)/);
});
test("v1187 circles have no close overlays; collapsed headers and contextual/swipe close remain", () => {
  const native = read("java/com/prirai/android/nira/components/toolbar/modern/PortraitNativeTabStrip.kt");
  const bar = read("java/com/prirai/android/nira/browser/tabs/compose/TabBarCompose.kt");
  const grouped = read("java/com/prirai/android/nira/browser/tabs/compose/SwipeableTabPill.kt");
  assert.match(native, /shape = GradientDrawable\.OVAL/);
  assert.match(native, /onTabClosed\?\.invoke\(tab\.id\)/);
  assert.match(native, /if \(collapsed\) "" else/);
  assert.match(bar, /shape = CircleShape/);
  assert.match(bar, /if \(expanded\) .* else "\(/);
  assert.match(grouped, /shape = CircleShape/);
  // Closing tabs remains available via contextual menu and vertical swipe,
  // without any touchable close overlay on the favicon itself.
  assert.match(native, /menu\.add\("Zamknij kartę"\)/);
  assert.doesNotMatch(native, /tabContainer\.addView\(close/);
  assert.doesNotMatch(bar.slice(bar.indexOf("private fun TabPill("), bar.indexOf("* Invisible divider")), /badgeSize|Icons\.Default\.Close/);
  assert.match(grouped, /offsetY < -swipeThreshold/);
  assert.match(grouped, /onTabClose\(\)/);
  assert.doesNotMatch(grouped, /clickable\(onClick = onTabClose\)|badgeSize/);
});
test("v1179 signed spacing controls group and ungrouped layout", () => {
  const native = read("java/com/prirai/android/nira/components/toolbar/modern/PortraitNativeTabStrip.kt");
  const bar = read("java/com/prirai/android/nira/browser/tabs/compose/TabBarCompose.kt");
  assert.match(native, /marginEnd = dp\(circleGap\)/);
  assert.match(bar, /Arrangement\.spacedBy\(iconGapDp\.dp\)/);
  assert.match(bar, /iconSizeDp \+ if \(/);
});
