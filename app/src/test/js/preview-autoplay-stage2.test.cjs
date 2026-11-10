"use strict";
// Stage 2/10: source-contract tests, not a real GeckoView/device playback test.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const sourceRoot = path.resolve(__dirname, "../..");
const read = relative => fs.readFileSync(path.join(sourceRoot, relative), "utf8");
const prefs = read("main/java/com/prirai/android/nira/components/UserJsPreferences.kt");
const components = read("main/java/com/prirai/android/nira/components/Components.kt");

// Parse the exact map that is serialized into Gecko's configFilePath JSON.
function mediaMap(source) {
  const body = source.match(/private val mediaPrefs = mapOf<String, Any>\(\s*([\s\S]*?)\n    \)/);
  assert.ok(body, "shipped mediaPrefs map must exist");
  return body[1];
}
function numericSetting(map, name) {
  const escaped = name.replace(/\./g, "\\.");
  const found = map.match(new RegExp('"' + escaped + '"\\s+to\\s+(\\d+)'));
  assert.ok(found, "missing numeric Gecko preference: " + name);
  return Number(found[1]);
}
function assertSafeAutoplayPolicy(source) {
  const map = mediaMap(source);
  // Mozilla: 1 blocks audible autoplay, 5 blocks even muted, 0 allows sound.
  assert.equal(numericSetting(map, "media.autoplay.default"), 1,
    "allow muted previews without opening audible autoplay");
  // Preserve the existing strict click-to-play requirement for audible media.
  assert.equal(numericSetting(map, "media.autoplay.blocking_policy"), 2);
  assert.doesNotMatch(map, /"media\.autoplay\.allow-muted"\s+to\s+false/,
    "do not override Gecko's default allowance for muted video");
}

test("stage2: shipped Gecko preferences allow muted but block audible autoplay", () => {
  assertSafeAutoplayPolicy(prefs);
});

test("stage2: regression oracle rejects block-all and allow-all modes", () => {
  for (const wrong of [0, 5]) {
    const mutated = prefs.replace(
      /("media\.autoplay\.default"\s+to\s+)1\b/, "$1" + wrong);
    assert.notEqual(mutated, prefs, "negative fixture must modify real setting");
    assert.throws(() => assertSafeAutoplayPolicy(mutated));
  }
  const relaxed = prefs.replace(
    /("media\.autoplay\.blocking_policy"\s+to\s+)2\b/, "$1" + 0);
  assert.notEqual(relaxed, prefs);
  assert.throws(() => assertSafeAutoplayPolicy(relaxed));
});

test("stage2: Gecko runtime still receives merged config before build", () => {
  assert.match(prefs, /securityPrefs \+ mediaPrefs \+ performancePrefs/);
  assert.match(prefs, /builder\.configFilePath\(configFile\.absolutePath\)/);
  assert.match(components, /UserJsPreferences\.applyTo\(applicationContext, builder\)/);
  const first = components.indexOf("UserJsPreferences.applyTo(applicationContext, builder)");
  const build = components.indexOf(".build()", first);
  assert.ok(first >= 0 && build > first, "apply config before building runtime");
  assert.match(components, /GeckoRuntime\.create\(applicationContext, runtimeSettings\)/);
});

test("stage2: no Gecko or site permission bypass introduced", () => {
  const map = mediaMap(prefs);
  assert.doesNotMatch(map, /"media\.autoplay\.default"\s+to\s+0/);
  assert.doesNotMatch(map, /"media\.geckoview\.autoplay\.request"/);
});
