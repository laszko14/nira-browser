"use strict";
/**
 * GV-G4A static contract check. It NEVER builds, installs or accesses devices.
 * Does not infer any observed runtime/device state from source metadata.
 */
const fs = require("node:fs");
const path = require("node:path");

const EXPECTED = Object.freeze({
  schema: "zavro.geckoview.g4a/1",
  baseSha: "cc781928f69a347e5016bf0ba587233431cb9bbc",
  packageId: "com.prirai.android.nira.debug",
  versionCode: 1195,
  versionName: "0.5.22-alpha100-clean",
  abi: "arm64-v8a",
  signerSha256: "df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00",
});

function inspect(policy, gradle, verifier, evidenceReadme) {
  const errors = [];
  const need = (condition, code) => { if (!condition) errors.push(code); };
  need(policy && typeof policy === "object" && !Array.isArray(policy), "POLICY_INVALID");
  if (errors.length) return { status: "BLOCKED", errors };
  const allowed = ["schema", "baseSha", "packageId", "versionCode", "versionName",
    "abi", "signerSha256", "buildAuthorization", "installAuthorization",
    "deviceExecution", "existingInstallationProtected"];
  need(Object.keys(policy).every(k => allowed.includes(k)), "UNKNOWN_POLICY_KEY");
  for (const [key, val] of Object.entries(EXPECTED)) {
    need(policy[key] === val, "POLICY_" + key.toUpperCase() + "_DRIFT");
  }
  for (const key of ["buildAuthorization", "installAuthorization", "deviceExecution"]) {
    need(policy[key] === "NOT_GRANTED", "UNSAFE_" + key.toUpperCase());
  }
  need(policy.existingInstallationProtected === "NOT_VERIFIED", "INSTALL_SAFETY_CLAIM");
  const applicationId = gradle.match(/\bapplicationId\s*=\s*["']([^"']+)["']/);
  const suffix = gradle.match(/\bapplicationIdSuffix\s*=\s*["']([^"']+)["']/);
  const baseCode = gradle.match(/\bversionCode\s*=\s*(\d+)/);
  const versionName = gradle.match(/\bversionName\s*=\s*["']([^"']+)["']/);
  const abiBase = gradle.match(/["']arm64-v8a["']\s*:\s*(\d+)/);
  const abiValue = Number(abiBase?.[1]);
  const derivedCode = abiValue * 1000 + Number(baseCode?.[1]);
  need(applicationId && suffix && applicationId[1] + suffix[1] === policy.packageId,
    "PACKAGE_COLLISION_CONTRACT_CHANGED");
  need(baseCode && abiBase && Number.isSafeInteger(derivedCode) &&
    derivedCode === policy.versionCode && /base\s*\*\s*1000\s*\+\s*orig/.test(gradle),
    "VERSION_CODE_DERIVATION_CHANGED");
  need(versionName && versionName[1] === policy.versionName, "VERSION_NAME_CHANGED");
  need(/include\(testAbi\s*\?:\s*['"]arm64-v8a['"]\)/.test(gradle), "ABI_SPLIT_CHANGED");
  const signerNames = ["NIRA_SIGNING_STORE_FILE", "NIRA_SIGNING_STORE_PASSWORD",
    "NIRA_SIGNING_KEY_ALIAS", "NIRA_SIGNING_KEY_PASSWORD"];
  need(signerNames.every(s => gradle.includes(s)) &&
    /signingConfig\s*=\s*signingConfigs\.getByName\(["']updateDebug["']\)/.test(gradle),
    "STABLE_SIGNING_WIRING_MISSING");
  need(verifier.includes("apksigner") && verifier.includes("aapt") &&
    verifier.includes("expected_cert") && verifier.includes("arm64-v8a"),
    "SIGNED_APK_VERIFIER_MISSING");
  need(evidenceReadme.includes("check-evidence.cjs") &&
    evidenceReadme.includes("physicalDeviceObserved"), "G3C_EVIDENCE_KIT_MISSING");
  return {
    status: errors.length ? "BLOCKED" : "PREPARED_NOT_AUTHORIZED",
    errors,
    artifactBuilt: false,
    installationAuthorized: false,
    physicalTestExecuted: false,
    warning: "Same debug package ID may replace an existing installation. No installation permitted.",
  };
}

function main() {
  try {
    const root = path.resolve(__dirname, "../..");
    const load = rel => fs.readFileSync(path.join(root, rel), "utf8");
    const result = inspect(
      JSON.parse(load("tools/gecko-fixture-g4a/BUILD_CONTRACT.json")),
      load("app/build.gradle"),
      load("scripts/verify-update-apk.sh"),
      load("tools/gecko-fixture-g3c/README.md"));
    process.stdout.write(JSON.stringify(result, null, 2) + "\n");
    process.exitCode = result.errors.length ? 1 : 0;
  } catch (_) {
    process.stderr.write("G4A static preflight cannot read required source files.\n");
    process.exitCode = 2;
  }
}
if (require.main === module) main();
module.exports = { inspect, EXPECTED };
