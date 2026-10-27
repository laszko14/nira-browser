import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {chooseTests,validateProject,historySummary,liveAudit,GitHubReader,SmartCIError} from '../smart-ci.mjs';
const c=JSON.parse(readFileSync(new URL('../build-control.json',import.meta.url),'utf8'));
const decide=(paths,v='clean')=>chooseTests(paths,v,c);
const expect=(f,s)=>assert.throws(f,e=>e instanceof SmartCIError&&e.code===s);
test('docs do not trigger Android',()=>{const r=decide(['docs/README.md']);assert.deepEqual(r.tasks,[]);assert.equal(r.requires_explicit_android_approval,false);});
test('JS unit tests select JS alone',()=>{const r=decide(['app/src/test/js/smoke.test.cjs']);assert.deepEqual(r.tasks,['js']);});
test('runtime JS requires full packaging',()=>{const r=decide(['app/src/main/assets/extensions/x.js']);assert.ok(r.tasks.includes('android-build'));assert.ok(r.tasks.includes('apk-metadata'));});
test('production Kotlin requires unit/lint/build',()=>{const r=decide(['app/src/main/java/com/x/Foo.kt']);assert.ok(r.tasks.includes('kotlin-unit'));assert.ok(r.tasks.includes('android-lint'));});
test('Kotlin-only test change skips Android build',()=>{const r=decide(['app/src/test/java/com/x/FooTest.kt']);assert.deepEqual(r.tasks,['kotlin-unit']);});
test('resources require Android compile',()=>{const r=decide(['app/src/main/res/layout/main.xml']);assert.ok(r.tasks.includes('android-build'));});
test('MPV Lua changes select Lua and packaging',()=>{const r=decide(['app/src/main/assets/osc.lua'],'mpv');assert.ok(r.tasks.includes('lua'));assert.ok(r.tasks.includes('android-build'));});
test('unknown file fails closed to full matrix',()=>{const r=decide(['unknown.py']);assert.equal(r.selection_kind,'FULL_FAIL_CLOSED');assert.equal(r.tasks.length,7);});
test('Gradle changes select full matrix',()=>{const r=decide([{filename:'gradle.properties',status:'modified'}]);assert.equal(r.tasks.length,7);});
test('renamed files inspect original and target',()=>{const r=decide([{filename:'docs/new.md',previous_filename:'app/src/main/java/X.kt',status:'renamed'}]);assert.ok(r.tasks.includes('android-build'));});
test('renamed without previous rejected',()=>expect(()=>decide([{filename:'docs/new.md',status:'renamed'}]),'RENAME_SOURCE_REQUIRED'));
test('no source diff is an error, never docs only',()=>expect(()=>decide([]),'CHANGE_LIST_REQUIRED'));
test('diff with >200 files aborts',()=>expect(()=>decide(Array.from({length:201},(_,i)=>'docs/file'+i+'.md')),'CHANGE_LIST_REQUIRED'));
test('unsafe path traversal blocked',()=>expect(()=>decide(['docs/../main.kt']),'UNSAFE_PATH'));
test('repeated path blocked',()=>expect(()=>decide(['a.js','a.js']),'DUPLICATE_PATH'));
test('invalid diff status blocked',()=>expect(()=>decide([{filename:'file.js',status:'unmerged'}]),'UNEXPECTED_DIFF_STATUS'));
test('main or unpinned variant is not valid',()=>expect(()=>decide(['a.md'],'main'),'INVALID_VARIANT'));
test('Android tasks are NEVER approved automatically',()=>{const r=decide(['app/src/main/java/X.kt']);assert.equal(r.android_tasks_blocked,true);assert.equal(r.approved_android_runs,0);});
test('two variants differ on SHA and branch',()=>{assert.notEqual(decide(['docs/a.md']).commit_sha,decide(['docs/a.md'],'mpv').commit_sha);});
const gradle=(v)=>{let t=c.targets[v],name=v==='clean'?'com.prirai.android.nira':'com.prirai.android.nira.mpv';return "applicationId = \""+name+"\"\nversionCode = "+(t.version_code-1000)+"\nversionName = \""+t.version_name+"\"\n"+(v==='clean'?"applicationIdSuffix = '.debug'\n":'')+"include(testAbi ?: 'arm64-v8a')\n";};
const workflow=v=>{let t=c.targets[v];return "EXPECTED_VERSION_CODE: \""+t.version_code+"\"\nEXPECTED_VERSION_NAME: "+t.version_name+"\nEXPECTED_PACKAGE: "+t.package+"\nEXPECTED_CERT_SHA256: "+t.certificate_sha256+"\nverify-update-apk.sh\nassembleDebug\ngradle/actions/setup-gradle@v5\n";};
const props='org.gradle.caching=true\norg.gradle.parallel=true\norg.gradle.configuration-cache=true\n';
const setup=v=>({variant:v,config:c,appGradle:gradle(v),workflow:workflow(v),gradleProperties:props,head:c.targets[v].pinned_sha});
test('Clean and MPV validate signed package and version',()=>{for(const v of ['clean','mpv'])assert.equal(validateProject(setup(v)).static_audit,'PASS');});
test('MPV debug suffix explicitly rejected',()=>{const s=setup('mpv');s.appGradle+="applicationIdSuffix = '.debug'\n";expect(()=>validateProject(s),'MPV_PACKAGE');});
test('stale branch rejected',()=>{const s=setup('clean');s.head='f'.repeat(40);expect(()=>validateProject(s),'HEAD_MOVED');});
test('wrong version rejected',()=>{const s=setup('clean');s.workflow=s.workflow.replace('1174','1173');expect(()=>validateProject(s),'CI_VERSION');});
test('Gradle cache flags enforced',()=>{const s=setup('clean');s.gradleProperties='org.gradle.caching=false\n';expect(()=>validateProject(s),'GRADLE_CACHE_CONFIG_CHANGED');});
test('signature drift rejected',()=>{const s=setup('clean');s.workflow=s.workflow.replace('df90b01217','ff90b01217');expect(()=>validateProject(s),'CI_IDENTITY');});
test('untrusted API paths rejected before network',async()=>{const a=new GitHubReader(null,()=>{throw Error('network unexpected')});await assert.rejects(a.get('/actions/workflows/trigger'),e=>e.code==='API_PATH_DENIED');});
test('real API adapter only allows GET',async()=>{let methods=[];const a=new GitHubReader(null,async(_,opts)=>{methods.push(opts.method);return {status:200,json:async()=>({object:{sha:c.targets.clean.pinned_sha}})}});await a.get('/git/ref/heads/'+c.targets.clean.branch);assert.deepEqual(methods,['GET']);});
test('HEAD movement in live audit blocks source fetch',async()=>{await assert.rejects(liveAudit(c,'clean',{get:async()=>({object:{sha:'f'.repeat(40)}})}),e=>e.code==='HEAD_MOVED');});
const runs=[
 {run_id:40462413839,sha:c.targets.clean.pinned_sha,result:'success',elapsed_seconds:312,jobs:{build:202,test:185,lint:307}},
 {run_id:40462391836,sha:'299db5fe94ff959bcca6c3a0726d8327ecba6cad',result:'success',elapsed_seconds:316,jobs:{build:179,test:211,lint:311}},
 {run_id:40462211883,sha:'3e194d2a20b6f649e55600580f1e62ba7d78439b',result:'success',elapsed_seconds:332,jobs:{build:161,test:181,lint:329}}
];
test('actual historical baseline medians calculated correctly',()=>{let x=historySummary(runs);assert.deepEqual(x.median_seconds,{overall:316,build:179,test:185,lint:311});assert.deepEqual(x.slowest_job_in_each_run,['lint','lint','lint']);assert.equal(x.predicted_speedup_from_new_changes,null);});
test('no speculative claim of speedup',()=>assert.equal(historySummary(runs).optimization_measured,false));
test('invalid or failed historical samples rejected',()=>{let x=structuredClone(runs);x[1].result='failure';expect(()=>historySummary(x),'INVALID_HISTORY_RUN');});
