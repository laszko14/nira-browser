'use strict';
const {test}=require('node:test'), assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {assertUpdateApkContract}=require('./helpers/update-apk-contract.cjs');
const root=path.join(__dirname,'../../..');
const gradle=fs.readFileSync(path.join(root,'build.gradle'),'utf8');
const workflow=fs.readFileSync(path.join(root,'../.github/workflows/apk-release.yml'),'utf8');

test('debug update contract survives separate manual release and reusable test jobs',()=>{
  const valid=assertUpdateApkContract(gradle,workflow);
  assert.ok(Number.isInteger(valid.installCode));
  assert.match(valid.versionName,/^0\.5\.22-alpha\d+-clean$/);
});

test('negative control: wrong version or cert in debug job still fails',()=>{
  const wrongVersion=workflow.replace(/EXPECTED_VERSION_CODE: "1197"/,'EXPECTED_VERSION_CODE: "0"');
  assert.notEqual(wrongVersion, workflow);
  assert.throws(()=>assertUpdateApkContract(gradle,wrongVersion),assert.AssertionError);
  const wrongCert=workflow.replace(/EXPECTED_CERT_SHA256: df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00/,
    'EXPECTED_CERT_SHA256: 0000000000000000000000000000000000000000000000000000000000000000');
  assert.notEqual(wrongCert,workflow);
  assert.throws(()=>assertUpdateApkContract(gradle,wrongCert),assert.AssertionError);
});

test('reject missing or duplicated debug build job rather than searching release env',()=>{
  assert.throws(()=>assertUpdateApkContract(gradle,workflow.replace(/^  build:\s*$/m,'  other-build:')),assert.AssertionError);
  assert.throws(()=>assertUpdateApkContract(gradle,workflow+'\n  build:\n    runs-on: ubuntu-latest\n'),assert.AssertionError);
});
