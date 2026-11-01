'use strict';
const { test }=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const {inventory,enforceSingleSigningEntrypoint}=require('./ci-workflow-inventory.cjs');
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
test('push, PR, tag and private dispatch cannot authorize private signed APK',()=>{
  assert.match(main,/^  pull_request:/m);
  assert.match(main,/^  pull_request_review:/m);
  const build=main.split(/^  build:\s*$/m)[1].split(/^  regression-tests:\s*$/m)[0];
  assert.ok(build,'build step expected');
  assert.match(build,/always\(\)/);
  assert.match(build,/github\.event_name == 'pull_request_review'/);
  assert.match(build,/github\.event\.review\.state == 'commented'/);
  assert.match(build,/github\.event\.review\.user\.login == 'laszko14'/);
  assert.match(build,/scripts\/authorize-private-apk-review\.cjs/);
  assert.match(build,/node scripts\/ci-run-receipt\.cjs/);
  assert.match(build,/Recheck live PR HEAD immediately before signing secrets/);
  assert.match(build,/verify-update-apk\.sh/);
  assert.doesNotMatch(build,/github\.event_name == 'workflow_dispatch'/);
  assert.doesNotMatch(build,/inputs\.build_kind/);
  assert.doesNotMatch(main,/^          - debug-apk\s*$/m);
  assert.match(main,/regression-tests:\n    if: github\.event_name != 'pull_request_review'/);
  const release=main.split(/^  release:\s*$/m)[1];
  assert.match(release,/github\.event_name == 'workflow_dispatch'/);
  assert.match(release,/inputs\.build_kind == 'publish-release'/);
  assert.match(release,/inputs\.approval_phrase == 'Publikuj'/);
  assert.match(release,/inputs\.tag != ''/);
  assert.match(release,/Resolve expected release version from the checked-out tag/);
  assert.match(main,/uses: \.\/\.github\/workflows\/zavro-tests-reusable\.yml/);
  assert.doesNotMatch(main,/^  test:\s*$/m);
});
test('removing exact-SHA source gate, live head recheck or signing verifier is rejected',()=>{
  const build=main.split(/^  build:\s*$/m)[1].split(/^  regression-tests:\s*$/m)[0];
  const security=part=>{
    for(const marker of ['github.event_name == \'pull_request_review\'',
      'authorize-private-apk-review.cjs','ci-run-receipt.cjs',
      'Recheck live PR HEAD immediately before signing secrets','verify-update-apk.sh']){
      assert.ok(part.includes(marker),'Missing security marker: '+marker);
    }
  };
  security(build);
  for(const marker of ['ci-run-receipt.cjs','authorize-private-apk-review.cjs',
      'Recheck live PR HEAD immediately before signing secrets','verify-update-apk.sh']){
    assert.throws(()=>security(build.replace(marker,'removed-check')),assert.AssertionError);
  }
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
  for(const pattern of ['feat/zavro-clean-**','fix/zavro-clean-**','chore/zavro-clean-**']) assert.ok(process.includes(pattern),'missing source CI branch '+pattern);
  assert.match(process,/uses: \.\/\.github\/workflows\/zavro-tests-reusable\.yml/);
  assert.match(process,/node --test scripts\/check-ci-workflows\.test\.cjs/);
  assert.match(process,/node --test scripts\/check-ai-handoff\.test\.cjs/);
  assert.doesNotMatch(process,/gradlew|secrets\.|\bassembleDebug\b/);
});
test('only exact owner-review workflow can access signing or assemble Android APK',()=>{
  const all=inventory();
  assert.ok(all.length>=60,'historical source-only checks are preserved');
  assert.deepEqual(enforceSingleSigningEntrypoint(all),
    {signedWorkflow:'apk-release.yml',activeLegacySigning:0});
  const old=['video-search-build-once.yml','video-search-p3-regression.yml','zavro-audit60-gecko-build-g4b.yml','zavro-audit60-gecko-update-g4c.yml','zavro-t03-v1199-signed-update.yml','zavro-v1195-signed-arm64-build.yml','zavro-video-results-fuzzy-v1202-update.yml','zavro-video-results-v1200-update.yml','zavro-video-results-v1201-update.yml'];
  for (const file of old)assert.ok(!all.some(x=>x.file===file),'legacy signing entrypoint is still active: '+file);
  assert.ok(all.some(x=>x.file==='zavro-audit60-stage19b-regression.yml'));
  assert.ok(all.some(x=>x.file==='zavro-tests-reusable.yml'&&x.events.includes('workflow_call')));
});
test('reintroduced legacy signing, APK build or missing review event fail closed',()=>{
  const all=inventory();
  for (const payload of [
    {file:'legacy-push-apk.yml',events:['push'],assembly:true,signing:true},
    {file:'legacy-manual-apk.yml',events:['workflow_dispatch'],assembly:false,signing:true},
    {file:'legacy-assemble-apk.yml',events:['push'],assembly:true,signing:false}
  ]) {
    assert.throws(()=>enforceSingleSigningEntrypoint([...all,payload]),/Extra active APK\/signing/);
  }
  assert.throws(()=>enforceSingleSigningEntrypoint(all.filter(x=>x.file!=='apk-release.yml')),
    /Canonical signed workflow/);
  assert.throws(()=>enforceSingleSigningEntrypoint(all.map(x=>
    x.file==='apk-release.yml'?{...x,events:['workflow_dispatch']}:x)),
    /Canonical signed workflow/);
});
