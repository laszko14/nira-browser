"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../../../..");
const read = file => fs.readFileSync(path.join(root,file), "utf8");

test("stage4: exported evidence names and explicit unconfirmed attribution", () => {
  const ledger = read("app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt");
  const report = read("app/src/main/java/com/prirai/android/nira/browser/MpvPlayEvidenceReport.kt");
  assert.match(ledger, /add\("analysis\.json", analysis\.toString\(2\)\)/);
  assert.match(ledger, /add\("analysis\.txt", MpvPlayEvidenceReport\.explanation\(analysis\)\)/);
  assert.match(report, /"diagnosis", "UNDETERMINED"/);
  assert.match(report, /"attribution_confidence", "NOT_ESTABLISHED"/);
  assert.match(report, /"play_owner", "UNKNOWN"/);
  assert.match(report, /"INACCESSIBLE_DOM_NOT_EXCLUDED"/);
  assert.match(report, /"CAUGHT_PLAY_REJECTIONS_NOT_OBSERVED"/);
  assert.match(report, /"REAL_SITE_REPRO_NOT_VERIFIED"/);
  assert.doesNotMatch(report, /\.getAttribute\(|\.textContent|\.innerHTML|\.src\b/);
});

test("stage4: native ingest disallows uppercase arbitrary secrets and noncanonical metric types", () => {
  const ledger = read("app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt");
  assert.match(ledger, /detail !in setOf\(/);
  assert.match(ledger, /"USER_SELECTED"/);
  assert.match(ledger, /"EVENT_LIMIT"/);
  assert.match(ledger, /val enums = mapOf\(/);
  assert.match(ledger, /name in enums && value is String && value in enums\.getValue\(name\)/);
  assert.match(ledger, /name in bools && value is Boolean/);
  assert.doesNotMatch(ledger, /value\.matches\(Regex\("\[A-Z_\]/);
});

test("stage4: stale content ports sharing a sender URL must not pass ownership", () => {
  const bridge = read("app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt");
  assert.match(bridge, /MpvPlayPortIdentity\.matches\(ports\[session\], port\)/);
  assert.match(bridge, /connected\?\.any \{ it === incoming \} == true/);
  assert.doesNotMatch(bridge, /it\.senderUrl\(\) == port\.senderUrl\(\)/);
  assert.match(bridge, /ProfileTabScope\.belongsToNormalProfile\(tab, profile\)/);
});

test("stage4: selected Play control is correlated by synthetic node, without site text", () => {
  const probe = read("app/src/main/assets/extensions/zavro-preview-diagnostics/content.js");
  assert.match(probe, /send\("PICK",targetName\(node\),"USER_SELECTED",\s*\{node:elementId\(node\),shadow:/);
  assert.match(probe, /if \(!active \|\| serial >= MAX_EVENTS \|\| seenEvents\.has\(event\)\) return;/);
  assert.match(probe, /seenEvents\.add\(event\)/);
  assert.doesNotMatch(probe, /\.innerHTML|\.textContent|\.currentSrc\b/);
});
