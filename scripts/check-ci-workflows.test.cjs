'use strict';
const { test }=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const {inventory}=require('./ci-workflow-inventory.cjs');
const shared=read('.github/workflows/zavro-tests-reusable.yml');
const main=read('.github/workflows/apk-release.yml');
const process=read('.github/workflows/zavro-ai-process-docs.yml');
test('reusable shared test jobs have no signing, assemble or APK upload',()=>{
  assert.match(shared,/^  workflow_call:/m);
  for (const marker of ['javascript:','unit:','android-test-compile:','lint:','testDebugUnitTest','compileDebugAndroidTestKotlin','lintDebug','node --test'])assert.ok(shared.includes(marker),marker);
  assert.match(shared,/needs: javascript/);
  assert.match(shared,/test "\$\(git rev-parse HEAD\)" = "\$GITHUB_SHA"/);
  for(const forbidden of [/\bassembleDebug\b/,/\bassembleRelease\b/,/secrets\./,/app\/build\/outputs\/apk\//])assert.doesNotMatch(shared,forbidden);
});
test('PR, push and tags cannot reach installation or release job in Android CI',()=>{
  assert.match(main,/^  pull_request:/m);
  assert.match(main,/^  push:/m);
  assert.match(main,/^  workflow_dispatch:/m);
  assert.match(main,/build_kind:/);
  const b=main.split(/^  build:\s*$/m)[1].split(/^  regression-tests:\s*$/m)[0];
  assert.match(b,/needs: regression-tests/);
  assert.match(b,/github\.event_name == 'workflow_dispatch'/);
  assert.match(b,/inputs\.build_kind == 'debug-apk'/);
  assert.match(b,/inputs\.task_kind == 'implementation'/);
  assert.match(b,/inputs\.task_id != ''/);
  assert.match(b,/inputs\.source_sha == github.sha/);
  assert.match(b,/github.ref != 'refs\/heads\/main'/);
  assert.match(b,/Validate dispatched implementation task/);
  assert.match(b,/git rev-parse HEAD/);
  const dispatchedValidation=b.split(/- name: Validate authorized implementation task, ref and exact source\s*\n/)[1];
  assert.ok(dispatchedValidation,'dispatch implementation validation is required');
  assert.match(dispatchedValidation,/^\s*if: github\.event_name == 'workflow_dispatch'\s*$/m,
    'workflow_dispatch-only inputs must not block the independently authorized review flow');
  assert.match(b,/github\.event_name == 'pull_request_review'/);
  assert.match(b,/github\.event\.review\.state == 'commented'/);
  assert.match(b,/github\.event\.review\.user\.login == 'laszko14'/);
  assert.match(b,/scripts\/authorize-private-apk-review\.cjs/);
  assert.match(b,/REVIEW_HEAD_SHA/);
  assert.match(b,/head_sha=\$REVIEW_HEAD_SHA/);
  assert.match(b,/verify-update-apk\.sh/);
  const release=main.split(/^  release:\s*$/m)[1];
  assert.match(release,/github\.event_name == 'workflow_dispatch'/);
  assert.match(release,/inputs\.build_kind == 'publish-release'/);
  assert.match(release,/inputs\.approval_phrase == 'Publikuj'/);
  assert.match(release,/inputs\.tag != ''/);
  assert.doesNotMatch(release,/if:\s*startsWith\(github\.ref/);
  assert.match(release,/Resolve expected release version from the checked-out tag/);
  assert.match(main,/uses: \.\/\.github\/workflows\/zavro-tests-reusable\.yml/);
  assert.doesNotMatch(main,/^  test:\s*$/m);
  assert.doesNotMatch(main,/^  lint:\s*$/m);
});
test('stage 6: release depends on green tests and exact tagged SHA',()=>{
  const gate=yaml=>{
    const release=yaml.split(/^  release:\s*$/m)[1];
    assert.ok(release,'release job is required');
    assert.match(release,/^    needs: regression-tests\s*$/m);
    assert.match(release,/Verify tagged source is the tested dispatch SHA/);
    assert.match(release,/if \[ "\$\(git rev-parse HEAD\)" != "\$GITHUB_SHA" \]; then/);
  };
  gate(main);
  const withoutTests=main.replace('  release:\n    needs: regression-tests\n','  release:\n');
  const withoutShaCheck=main.replace('if [ "$(git rev-parse HEAD)" != "$GITHUB_SHA" ]; then',
    'if [ "any-tag" != "any-tag" ]; then');
  assert.notEqual(withoutTests,main);
  assert.notEqual(withoutShaCheck,main);
  assert.throws(()=>gate(withoutTests),assert.AssertionError);
  assert.throws(()=>gate(withoutShaCheck),assert.AssertionError);
});
test('process branch calls exactly the shared tests and keeps its safe doc gate',()=>{
  assert.match(process,/^  shared-tests:/m);
  assert.match(process,/feat\/zavro-clean-search-redesign-v2-20261101/);
  assert.match(process,/uses: \.\/\.github\/workflows\/zavro-tests-reusable\.yml/);
  assert.match(process,/node --test scripts\/check-ci-workflows\.test\.cjs/);
  assert.match(process,/node --test scripts\/check-ai-handoff\.test\.cjs/);
  assert.doesNotMatch(process,/gradlew|secrets\.|\bassembleDebug\b/);
});
test('all legacy workflow files remain and are inventoried, never silently accepted as migrated',()=>{
  const all=inventory();
  assert.ok(all.length>=66,'legacy workflow files must be preserved');
  assert.ok(all.some(x=>x.file==='zavro-audit60-gecko-update-g4c.yml'));
  assert.ok(all.some(x=>x.file==='zavro-audit60-stage19b-regression.yml'));
  assert.ok(all.some(x=>x.file==='zavro-tests-reusable.yml'&&x.events.includes('workflow_call')));
  const risky=all.filter(x=>x.file!=='apk-release.yml'&&x.assembly&&x.events.includes('push'));
  assert.ok(risky.length>0,'legacy push-build hazards should remain listed for migration');
  assert.ok(!all.find(x=>x.file==='apk-release.yml').assembly || main.includes("inputs.build_kind == 'debug-apk'"));
});

test('v2 negative controls fail on removed task, SHA, ref or verifier protections',()=>{
  const build=main.split(/^  build:\s*$/m)[1].split(/^  regression-tests:\s*$/m)[0];
  function contract(x){
    for(const s of ["github.event_name == 'workflow_dispatch'","inputs.task_kind == 'implementation'",
       "inputs.task_id != ''","inputs.source_sha == github.sha","github.ref != 'refs/heads/main'",
       '"$SOURCE_SHA" == "$GITHUB_SHA"','git rev-parse HEAD','verify-update-apk.sh']){
      assert.ok(x.includes(s),'Missing security contract: '+s);
    }
  }
  contract(build);
  const inputs=[
    ["inputs.task_kind == 'implementation'","true"],
    ["inputs.task_id != ''","true"],
    ["inputs.source_sha == github.sha","true"],
    ["github.ref != 'refs/heads/main'","true"],
    ['"$SOURCE_SHA" == "$GITHUB_SHA"','true'],
    ['git rev-parse HEAD','fake-head'],
    ['verify-update-apk.sh','skip-verification']
  ];
  for(const [good,bad] of inputs){
    const altered=build.replaceAll(good,bad);
    assert.notEqual(altered,build);
    assert.throws(()=>contract(altered),assert.AssertionError);
  }
});
