"use strict";
/**
 * G3C structural evidence gate, OFFLINE ONLY.
 * Never says PASS/FIXED/NOT_REPRODUCED and never changes the audit.
 * Reports field names only, never supplied values, URLs or secrets.
 */
const fs = require("node:fs");
const SHA40 = /^[a-f0-9]{40}$/;
const SHA256 = /^[a-f0-9]{64}$/;
const SCHEMA = "zavro.geckoview.g3c/1";
const KINDS = Object.freeze({
  "ZC-AUD-002": {
    extension_epoch_before: "NATIVE",
    high_water_before: "NATIVE",
    background_restarted_same_engine: "NATIVE",
    extension_epoch_after: "NATIVE",
    document_decision_after: "NATIVE",
    control_without_restart: "CONTROL",
  },
  "ZC-AUD-030": {
    storage_P: "DOM",
    storage_Q: "DOM",
    popup_opener: "DOM",
    popup_noopener: "DOM",
    session_context_comparison: "NATIVE",
    cross_profile_control: "CONTROL",
    private_control: "CONTROL",
    cold_restart_control: "CONTROL",
  },
  "ZC-AUD-031": {
    synthetic_P_and_Q_seeded: "CONTROL",
    P_sessions_closed: "NATIVE",
    session_save_acknowledged: "NATIVE",
    P_delete_initiated: "CONTROL",
    cold_start_one: "CONTROL",
    old_P_context_verified: "CONTROL",
    cold_start_two: "CONTROL",
    Q_control_verified: "CONTROL",
  },
  "ZC-AUD-043": {
    A_loadedmetadata: "DOM",
    before_B_assignment: "DOM",
    after_B_assignment: "DOM",
    B_loadedmetadata: "DOM",
    invalid_B: "DOM",
    source_list: "DOM",
    blob_B: "DOM",
    native_A: "NATIVE",
    native_B: "NATIVE",
    ui_candidate_correlated: "CONTROL",
  },
});
const REQUIRED_ARTIFACTS = Object.freeze({
  "ZC-AUD-002": ["EXTENSION_LIFECYCLE", "NATIVE_DOCUMENT_TRACE"],
  "ZC-AUD-030": ["DOM_G2", "NATIVE_G3B", "STORAGE_CONTROLS"],
  "ZC-AUD-031": ["STORAGE_CONTROLS", "NATIVE_G3B", "COLD_START_RECORD"],
  "ZC-AUD-043": ["DOM_G2", "NATIVE_G3B", "UI_CONTROL"],
});
const TYPES = new Set([
  "DOM_G2", "NATIVE_G3B", "NATIVE_DOCUMENT_TRACE",
  "EXTENSION_LIFECYCLE", "STORAGE_CONTROLS", "COLD_START_RECORD", "UI_CONTROL",
]);
const idRegex = /^(?:S[1-9]\d*|NO_SESSION)$/;

function plain(value) {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
function keys(value, allowed, path, errors) {
  if (!plain(value)) {
    errors.push(path + ": expected object");
    return false;
  }
  for (const key of Object.keys(value)) {
    if (!allowed.includes(key)) errors.push(path + ": unknown field");
  }
  return true;
}
function validate(packet) {
  const errors = [];
  if (!keys(packet, ["schema", "caseId", "environment", "safety", "artifacts", "events"], "packet", errors))
    return { status: "INCOMPLETE", errors };
  if (packet.schema !== SCHEMA) errors.push("schema: unsupported");
  const kindMap = KINDS[packet.caseId];
  if (!Object.prototype.hasOwnProperty.call(KINDS, packet.caseId))
    errors.push("caseId: unsupported");
  const envKeys = ["deviceModel", "androidVersion", "appSha", "appPackage",
    "geckoBuild", "physicalDeviceObserved"];
  if (keys(packet.environment, envKeys, "environment", errors)) {
    const e = packet.environment;
    if (e.deviceModel !== "Nothing Phone (1)") errors.push("environment.deviceModel: not target");
    if (e.androidVersion !== "15") errors.push("environment.androidVersion: not target");
    if (typeof e.appSha !== "string" || !SHA40.test(e.appSha))
      errors.push("environment.appSha: invalid");
    if (e.appPackage !== "com.prirai.android.nira.debug" &&
        e.appPackage !== "isolated.debug.fixture")
      errors.push("environment.appPackage: invalid");
    if (typeof e.geckoBuild !== "string" || !/^[A-Za-z0-9._-]{3,80}$/.test(e.geckoBuild))
      errors.push("environment.geckoBuild: missing");
    if (typeof e.physicalDeviceObserved !== "boolean")
      errors.push("environment.physicalDeviceObserved: invalid");
  }
  const safetyKeys = ["syntheticOnly", "trustedHttps", "isolatedTestEnvironment",
    "installationAuthorized", "userDataUntouched", "backupVerified",
    "profileDeletionExplicitlyAuthorized"];
  if (keys(packet.safety, safetyKeys, "safety", errors)) {
    for (const k of safetyKeys) {
      if (k === "profileDeletionExplicitlyAuthorized" && packet.safety[k] === undefined) continue;
      if (typeof packet.safety[k] !== "boolean") errors.push("safety." + k + ": invalid");
      else if (k !== "backupVerified" && packet.safety[k] !== true)
        errors.push("safety." + k + ": safety gate not met");
    }
    if (packet.caseId === "ZC-AUD-031" && packet.safety.backupVerified !== true)
      errors.push("safety.backupVerified: mandatory for deletion case");
    if (packet.caseId === "ZC-AUD-031" && packet.safety.profileDeletionExplicitlyAuthorized !== true)
      errors.push("safety.profileDeletionExplicitlyAuthorized: mandatory for deletion case");
  }
  if (!Array.isArray(packet.artifacts) || packet.artifacts.length > 24) {
    errors.push("artifacts: invalid");
  } else {
    const types = new Set();
    packet.artifacts.forEach((artifact) => {
      if (!keys(artifact, ["type", "sha256"], "artifact", errors)) return;
      if (!TYPES.has(artifact.type)) errors.push("artifact.type: invalid");
      else types.add(artifact.type);
      if (typeof artifact.sha256 !== "string" || !SHA256.test(artifact.sha256))
        errors.push("artifact.sha256: invalid");
    });
    for (const t of REQUIRED_ARTIFACTS[packet.caseId] || []) {
      if (!types.has(t)) errors.push("artifacts: missing " + t);
    }
  }
  const found = new Set();
  if (!Array.isArray(packet.events) || packet.events.length > 512) {
    errors.push("events: invalid");
  } else {
    let previousSeq = 0;
    const previousClock = { DOM: -1, NATIVE: -1, CONTROL: -1 };
    packet.events.forEach((ev) => {
      if (!keys(ev, ["seq", "elapsedMs", "kind", "source", "sessionLabel"], "event", errors)) return;
      if (!Number.isSafeInteger(ev.seq) || ev.seq <= previousSeq)
        errors.push("event.seq: not strictly increasing");
      else previousSeq = ev.seq;
      if (!Number.isSafeInteger(ev.elapsedMs) || ev.elapsedMs < 0)
        errors.push("event.elapsedMs: invalid");
      if (typeof ev.source !== "string" || !["DOM", "NATIVE", "CONTROL"].includes(ev.source)) {
        errors.push("event.source: invalid");
      } else {
        if (Number.isSafeInteger(ev.elapsedMs) &&
            ev.elapsedMs < previousClock[ev.source])
          errors.push("event.elapsedMs: nonmonotonic within source");
        else if (Number.isSafeInteger(ev.elapsedMs))
          previousClock[ev.source] = ev.elapsedMs;
      }
      if (!kindMap || !Object.prototype.hasOwnProperty.call(kindMap, ev.kind)) {
        errors.push("event.kind: unknown for case");
      } else if (kindMap[ev.kind] !== ev.source) {
        errors.push("event.source: mismatched kind");
      } else {
        found.add(ev.kind);
      }
      if (ev.sessionLabel !== undefined && (typeof ev.sessionLabel !== "string" ||
          !idRegex.test(ev.sessionLabel))) errors.push("event.sessionLabel: invalid");
    });
    for (const k of Object.keys(kindMap || {})) {
      if (!found.has(k)) errors.push("events: missing " + k);
    }
    // Sequence constraints are meaningful ONLY within a given source (DOM/Native).
    if (packet.caseId === "ZC-AUD-043") {
      const dom = packet.events.filter(e => e && e.source === "DOM");
      const order = ["A_loadedmetadata", "before_B_assignment",
        "after_B_assignment", "B_loadedmetadata"];
      let last = -1;
      for (const kind of order) {
        const ix = dom.findIndex((e, i) => i > last && e.kind === kind);
        if (ix < 0) { errors.push("events: DOM A-to-B sequence incomplete"); break; }
        last = ix;
      }
    }
    if (packet.caseId === "ZC-AUD-002") {
      const native = packet.events.filter(e => e && e.source === "NATIVE");
      const order = ["extension_epoch_before", "high_water_before",
        "background_restarted_same_engine", "extension_epoch_after", "document_decision_after"];
      let last = -1;
      for (const kind of order) {
        const ix = native.findIndex((e, i) => i > last && e.kind === kind);
        if (ix < 0) { errors.push("events: restart sequence incomplete"); break; }
        last = ix;
      }
    }
  }
  if (packet.environment?.physicalDeviceObserved !== true)
    errors.push("environment: no physical-device observation");
  return {
    status: errors.length ? "INCOMPLETE" : "EVIDENCE_PACKAGE_FOR_MANUAL_REVIEW",
    errors,
    note: "Structural validation only; NEVER PASS/FIXED/NOT_REPRODUCED. Manual source verification required.",
  };
}
function main(argv) {
  if (argv.length !== 1) {
    process.stderr.write("Usage: node tools/gecko-fixture-g3c/check-evidence.cjs FILE.json\n");
    return 2;
  }
  try {
    const stat = fs.statSync(argv[0]);
    if (stat.size > 262144 || !stat.isFile()) throw new Error("invalid file");
    const packet = JSON.parse(fs.readFileSync(argv[0], "utf8"));
    const result = validate(packet);
    process.stdout.write(JSON.stringify(result, null, 2) + "\n");
    return result.errors.length ? 1 : 0;
  } catch (_) {
    // Never include file contents, real paths, parsed values or URLs in errors.
    process.stderr.write("Evidence packet not readable or invalid JSON.\n");
    return 2;
  }
}
if (require.main === module) process.exitCode = main(process.argv.slice(2));
module.exports = { validate, KINDS, REQUIRED_ARTIFACTS, SCHEMA };
