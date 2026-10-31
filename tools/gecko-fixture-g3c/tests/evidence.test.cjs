"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { validate, KINDS, REQUIRED_ARTIFACTS, SCHEMA } = require("../check-evidence.cjs");
const { extractG2 } = require("../extract-g2.cjs");

function simulatedPacket(id = "ZC-AUD-043") {
  // In-memory mock only. A valid schema is NOT evidence of a real device test.
  let seq = 0;
  return {
    schema: SCHEMA,
    caseId: id,
    environment: {
      deviceModel: "Nothing Phone (1)",
      androidVersion: "15",
      appSha: "a".repeat(40),
      appPackage: "isolated.debug.fixture",
      geckoBuild: "TEST-SYNTHETIC-ONLY",
      physicalDeviceObserved: true,
    },
    safety: {
      syntheticOnly: true, trustedHttps: true, isolatedTestEnvironment: true,
      installationAuthorized: true, userDataUntouched: true, backupVerified: true,
    },
    artifacts: REQUIRED_ARTIFACTS[id].map(type => ({
      type, sha256: "b".repeat(64),
    })),
    events: Object.entries(KINDS[id]).map(([kind, source]) => ({
      seq: ++seq, elapsedMs: seq * 10, kind, source, sessionLabel: "S1",
    })),
  };
}

test("every case can be structurally complete but never emits a PASS or FIXED verdict", () => {
  for (const id of Object.keys(KINDS)) {
    const result = validate(simulatedPacket(id));
    assert.equal(result.status, "EVIDENCE_PACKAGE_FOR_MANUAL_REVIEW", id);
    assert.deepEqual(result.errors, []);
    assert.doesNotMatch(JSON.stringify(result), /"PASS"|"FIXED"|"NOT_REPRODUCED"/);
  }
});

test("missing native observations block 002 and 043; no fabricated device evidence", () => {
  for (const id of ["ZC-AUD-002", "ZC-AUD-043"]) {
    const packet = simulatedPacket(id);
    packet.events = packet.events.filter(e => e.source !== "NATIVE");
    const result = validate(packet);
    assert.equal(result.status, "INCOMPLETE");
    assert.ok(result.errors.some(e => e.startsWith("events: missing")));
  }
});

test("030 never counts settings without storage and privacy controls", () => {
  const packet = simulatedPacket("ZC-AUD-030");
  packet.events = packet.events.filter(e => e.kind !== "private_control");
  assert.equal(validate(packet).status, "INCOMPLETE");
});

test("031 requires verified backup and after-restart old-context control", () => {
  const packet = simulatedPacket("ZC-AUD-031");
  packet.safety.backupVerified = false;
  assert.ok(validate(packet).errors.some(e => e.includes("backupVerified")));
  packet.safety.backupVerified = true;
  packet.events = packet.events.filter(e => e.kind !== "old_P_context_verified");
  assert.equal(validate(packet).status, "INCOMPLETE");
});

test("no physical device claim means incomplete even for all requested records", () => {
  const packet = simulatedPacket();
  packet.environment.physicalDeviceObserved = false;
  assert.equal(validate(packet).status, "INCOMPLETE");
});

test("unknown fields and raw URLs cannot silently enter validated packet", () => {
  const packet = simulatedPacket();
  packet.cookie = "secret";
  packet.events[0].rawUrl = "https://secret.example/with-token";
  const result = validate(packet);
  assert.equal(result.status, "INCOMPLETE");
  assert.equal(result.errors.filter(e => e.includes("unknown field")).length, 2);
  assert.doesNotMatch(JSON.stringify(result), /secret|token|\.example/);
});

test("malformed origin, SHA and unexpected artifact type are blocked", () => {
  const p = simulatedPacket();
  p.environment.appSha = "not-sha";
  p.environment.geckoBuild = "https://secret.example";
  p.artifacts[0].sha256 = "wrong";
  p.artifacts.push({ type: "COOKIE_DUMP", sha256: "f".repeat(64) });
  assert.equal(validate(p).status, "INCOMPLETE");
});

test("time order is source-local and sequence is globally monotonic", () => {
  const p = simulatedPacket();
  p.events[1].seq = p.events[0].seq;
  assert.ok(validate(p).errors.some(e => e.includes("event.seq")));
  const q = simulatedPacket();
  q.events[1].elapsedMs = -1;
  assert.ok(validate(q).errors.some(e => e.includes("event.elapsedMs")));
});

test("A-before-B DOM order is a required property of case 043", () => {
  const p = simulatedPacket();
  const left = p.events.find(e => e.kind === "A_loadedmetadata");
  const right = p.events.find(e => e.kind === "B_loadedmetadata");
  const temp = left.kind;
  left.kind = right.kind;
  right.kind = temp;
  assert.ok(validate(p).errors.some(e => e.includes("DOM A-to-B")));
});

test("untrusted G2 DOM log is redacted and cannot claim native observations", () => {
  const raw = [
    JSON.stringify({ seq: 1, ms: 4, event: "loadedmetadata", currentSrc: "A",
      src: "A", url: "https://secret.example/path?token=123" }),
    JSON.stringify({ seq: 2, ms: 6, event: "before-B-assignment" }),
    JSON.stringify({ seq: 3, ms: 7, event: "after-B-assignment", currentSrc: "A", src: "B" }),
    JSON.stringify({ seq: 4, ms: 21, event: "loadedmetadata", currentSrc: "B" }),
    JSON.stringify({ seq: 5, ms: 22, event: "source-list-attached" }),
    JSON.stringify({ seq: 6, ms: 23, event: "blob-B-assigned" }),
  ].join("\n");
  const extracted = extractG2(raw);
  assert.equal(extracted.provenance, "UNVERIFIED_BROWSER_TEXT");
  assert.equal(extracted.inputSha256.length, 64);
  assert.deepEqual(extracted.events.map(e => e.kind),
    ["A_loadedmetadata", "before_B_assignment", "after_B_assignment",
      "B_loadedmetadata", "source_list", "blob_B"]);
  assert.doesNotMatch(JSON.stringify(extracted), /secret|https|token=123|Cookie/i);
});

test("G2 extractor is fail-closed for duplicate sequences or nonsensical input", () => {
  assert.throws(() => extractG2("{not json}"));
  const a = JSON.stringify({ seq: 1, ms: 0, event: "loadedmetadata", currentSrc: "A" });
  assert.throws(() => extractG2(a + "\n" + a));
  assert.throws(() => extractG2("x".repeat(131073)));
});
