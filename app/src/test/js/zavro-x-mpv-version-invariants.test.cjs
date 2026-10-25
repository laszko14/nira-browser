const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const read = p => fs.readFileSync(p,'utf8');
const gradle = read('app/build.gradle');
const workflow = read('.github/workflows/apk-release.yml');
test('experimental signed APK metadata stays in sync across Gradle and CI', () => {
  const baseCode = Number(gradle.match(/versionCode = (\d+)/)?.[1]);
  const versionName = gradle.match(/versionName = "([^"]+)"/)?.[1];
  assert.ok(Number.isInteger(baseCode) && baseCode >= 139);
  assert.match(versionName,/^0\.5\.22-alpha\d+-mpv\d+$/);
  const expectedCode = baseCode + 1000;
  assert.ok(workflow.includes('EXPECTED_VERSION_CODE: "'+expectedCode+'"'));
  assert.ok(workflow.includes('EXPECTED_VERSION_NAME: '+versionName));
  assert.ok(workflow.includes('name: zavro-x-mpv-arm64-v'+expectedCode));
  assert.match(gradle,/applicationId = "com\.prirai\.android\.nira\.mpv"/);
  assert.match(workflow,/EXPECTED_CERT_SHA256: df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00/);
});
