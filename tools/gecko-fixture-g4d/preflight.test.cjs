const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const expectedCert = 'df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00';
const script = path.join(__dirname, 'preflight-device.sh');

function runPreflight(overrides = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'g4d-unit-'));
  const bin = path.join(dir, 'bin');
  const sdk = path.join(dir, 'sdk');
  const bt = path.join(sdk, 'build-tools', '35.0.0');
  fs.mkdirSync(bin, { recursive: true });
  fs.mkdirSync(bt, { recursive: true });
  const writeExecutable = (name, content, parent = bin) => {
    const p = path.join(parent, name);
    fs.writeFileSync(p, content, { mode: 0o755 });
  };
  writeExecutable('adb', `#!/bin/sh
case "$*" in
  *' get-state') echo device ;;
  *' shell getprop ro.build.version.sdk') echo 35 ;;
  *' shell pm path com.prirai.android.nira.debug')
    [ "$FAKE_NO_BASE" = 1 ] || echo 'package:/data/app/~~fake/test/base.apk' ;;
  *' pull '*)
    for arg; do last="$arg"; done
    printf 'dummy unsigned test bytes' > "$last" ;;
  *) echo 'Unexpected adb command' >&2; exit 8 ;;
esac
`);
  writeExecutable('aapt', `#!/bin/sh
printf "package: name='com.prirai.android.nira.debug' versionCode='%s' versionName='0.5.22-alpha100-clean'\\n" "$FAKE_CODE"
`, bt);
  writeExecutable('apksigner', `#!/bin/sh
echo 'Number of signers: 1'
printf 'Signer #1 certificate SHA-256 digest: %s\\n' "$FAKE_CERT"
`, bt);
  const env = {
    ...process.env,
    PATH: bin + path.delimiter + process.env.PATH,
    FAKE_CERT: expectedCert,
    FAKE_CODE: '1195',
    ...overrides,
  };
  try {
    return spawnSync('bash', [script, 'synthetic-test-device', sdk], { env, encoding: 'utf8' });
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test('matching installed APK metadata is not permission to install', () => {
  const r = runPreflight();
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /INSTALLED_PACKAGE_VERSION_SIGNER=MATCH/);
  assert.match(r.stdout, /RESTORABLE_FULL_BACKUP=NOT_VERIFIED/);
  assert.match(r.stdout, /INSTALLATION=NOT_AUTHORIZED/);
});

test('different installed certificate is a hard block', () => {
  const r = runPreflight({ FAKE_CERT: 'a'.repeat(64) });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /certificate differs/);
});

test('same installed package but different version is a hard block', () => {
  const r = runPreflight({ FAKE_CODE: '1194' });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /version differs/);
});

test('missing or inaccessible base APK is a hard block', () => {
  const r = runPreflight({ FAKE_NO_BASE: '1' });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /base.apk not accessible/);
});

test('tool never carries install, erase or restore authorization', () => {
  const content = fs.readFileSync(script, 'utf8');
  assert.doesNotMatch(content, /adb[^\n]*\b(?:install|uninstall|pm clear|shell rm|backup|restore)\b/);
  assert.match(content, /INSTALLATION=NOT_AUTHORIZED/);
});
