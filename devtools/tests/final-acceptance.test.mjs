import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {auditStatic,classifyEvidence,evidenceManifest,acceptanceSummary,ReadOnlyGitHub,AcceptanceError} from '../final-acceptance.mjs';
import {newCheckpoint,validateCheckpoint,advance,OrchestrationError} from '../orchestrate.mjs';
import {chooseTests} from '../smart-ci.mjs';
import {validateRequest as buildRequest} from '../build-request-validator.mjs';

const config=JSON.parse(readFileSync(new URL('../build-control.json',import.meta.url),'utf8'));
const checkpoint=JSON.parse(readFileSync(new URL('../../docs/dev-system/CHECKPOINT.json',import.meta.url),'utf8'));
const wf={
 probe:readFileSync(new URL('../../.github/workflows/zavro-chat-probe.yml',import.meta.url),'utf8'),
 smart:readFileSync(new URL('../../.github/workflows/zavro-smart-ci-js.yml',import.meta.url),'utf8'),
 orchestrator:readFileSync(new URL('../../.github/workflows/zavro-stage7-orchestrator.yml',import.meta.url),'utf8'),
 bridge:readFileSync(new URL('../../.github/workflows/zavro-chat-build.yml',import.meta.url),'utf8')
};
const source=readFileSync(new URL('../orchestrate.mjs',import.meta.url),'utf8');
const inputs=()=>({config:structuredClone(config),checkpoint:structuredClone(checkpoint),workflows:{...wf},orchestrator:source});
const deny=(fn,code)=>assert.throws(fn,e=>e instanceof AcceptanceError&&e.code===code);
const denyAsync=(fn,code)=>assert.rejects(fn,e=>e instanceof AcceptanceError&&e.code===code);
const run=e=>({id:e.id,name:e.name,path:e.path,repository:{full_name:'laszko14/nira-browser'},head_branch:'tools/zavro-development-system-stage1-20261027',head_sha:'a'.repeat(40),status:'completed',conclusion:e.conclusion,event:'push'});
const artifact=e=>[{id:e.artifact,expired:false,size_in_bytes:1255,workflow_run:{head_sha:'a'.repeat(40)}}];
const safeRuns=()=>evidenceManifest.map(e=>classifyEvidence(run(e),e,e.artifact?artifact(e):[]));
const summary=v=>acceptanceSummary({staticAudit:{static_controls:'PASS'},results:safeRuns(),main:'0823f24eb48e2eefbe38c7616bc1d55e73917b37',clean:'3330a0b3a057008886fde392f788a1941e0dfb84',mpv:'d63f414acecae84c3507c3f4bb998e690281b91e',jobCheck:{stage3_dispatch_skipped:true,stage7_negative_requires_tests:true},...v});

test('Final acceptance recognizes completed stage 8 or pending stage 8',()=>{assert.ok([7,8].includes(checkpoint.last_completed_stage));if(checkpoint.last_completed_stage===7)assert.equal(checkpoint.next_stage,8);else assert.equal(checkpoint.stage_state,'COMPLETED_DIAGNOSTIC_ACCEPTED_PRODUCTION_BUILD_UNVERIFIED');});
test('static safety audit accepts pinned variants and read-only workflows',()=>{const s=auditStatic(inputs());assert.equal(s.static_controls,'PASS');assert.equal(s.repository_owner_dispatch_gate_present,true);assert.equal(s.signed_build_dispatch_proven,false);});
test('dangerous execution flag triggers stop',()=>{const d=inputs();d.config.execution_enabled=true;deny(()=>auditStatic(d),'CONTROL_FLAGS_UNSAFE');});
test('create_refs cannot be enabled',()=>{const d=inputs();d.config.create_refs_enabled=true;deny(()=>auditStatic(d),'CONTROL_FLAGS_UNSAFE');});
test('release flag cannot be enabled',()=>{const d=inputs();d.config.allow_releases=true;deny(()=>auditStatic(d),'CONTROL_FLAGS_UNSAFE');});
test('Clean identity pin and MPV identity are independent',()=>{const d=inputs();d.config.targets.mpv.package=d.config.targets.clean.package;deny(()=>auditStatic(d),'TARGET_METADATA_CHANGED');});
test('stale clean commit fails audit',()=>{const d=inputs();d.config.targets.clean.pinned_sha='f'.repeat(40);deny(()=>auditStatic(d),'TARGET_METADATA_CHANGED');});
test('rogue third variant fails audit',()=>{const d=inputs();d.config.targets.experimental={};deny(()=>auditStatic(d),'TARGET_SET_CHANGED');});
test('misleading old checkpoint rejected',()=>{const d=inputs();d.checkpoint.last_completed_stage=5;deny(()=>auditStatic(d),'CHECKPOINT_NOT_CURRENT');});
test('checkout to main is not an allowed trigger',()=>{const d=inputs();d.workflows.orchestrator=d.workflows.orchestrator.replace('tools/zavro-development-system-stage1-20261027','main');deny(()=>auditStatic(d),'TRIGGER_SCOPE_orchestrator');});
test('unexpected probe path blocked',()=>{const d=inputs();d.workflows.smart=d.workflows.smart.replace('devtools/smart-ci-probes/*.json','app/**/*.kt');deny(()=>auditStatic(d),'TRIGGER_SCOPE_smart');});
test('write access in diagnostics blocked',()=>{const d=inputs();d.workflows.orchestrator=d.workflows.orchestrator.replace('  contents: read','  contents: write');deny(()=>auditStatic(d),'READ_ONLY_PERMISSIONS_orchestrator');});
test('GitHub POST in diagnostic runner blocked',()=>{const d=inputs();d.workflows.probe+='\\n  gh api -X POST repos/example/refs\\n';deny(()=>auditStatic(d),'DIAGNOSTIC_WORKFLOW_UNSAFE_probe');});
test('Gradle build added to fast CI is blocked',()=>{const d=inputs();d.workflows.smart+='\\n  ./gradlew assembleDebug\\n';deny(()=>auditStatic(d),'DIAGNOSTIC_WORKFLOW_UNSAFE_smart');});
test('missing owner dispatch gate blocks final acceptance',()=>{const d=inputs();d.workflows.bridge=d.workflows.bridge.replace(" && vars.ZAVRO_ALLOW_SIGNED_BUILD_DISPATCH == 'ENABLED_BY_REPOSITORY_OWNER'",'');deny(()=>auditStatic(d),'SIGNED_BUILD_ADMIN_GATE_ABSENT');});
test('no shadow Android compile in orchestrator source',()=>{const d=inputs();d.orchestrator+='\\n gh api -X POST';deny(()=>auditStatic(d),'ORCHESTRATOR_NOT_READ_ONLY');});
test('Stage 3 denial run is intentionally red, not success',()=>{let e=evidenceManifest[0];assert.equal(classifyEvidence(run(e),e).conclusion,'failure');});
test('7 expected historical evidence manifests, including stage 7 deny',()=>{assert.equal(evidenceManifest.length,7);assert.equal(evidenceManifest.at(-1).conclusion,'failure');});
test('historical source run status cannot be pending',()=>{const e=evidenceManifest[1];deny(()=>classifyEvidence({...run(e),status:'in_progress'},e),'RUN_NOT_VERIFIED');});
test('historical negative run cannot be reported success',()=>{const e=evidenceManifest[0];deny(()=>classifyEvidence({...run(e),conclusion:'success'},e),'RUN_NOT_VERIFIED');});
test('historical evidence from wrong repo rejected',()=>{const e=evidenceManifest[1];const r=run(e);r.repository.full_name='other/repo';deny(()=>classifyEvidence(r,e),'RUN_PROVENANCE_INVALID');});
test('historical evidence from changed branch rejected',()=>{const e=evidenceManifest[2];deny(()=>classifyEvidence({...run(e),head_branch:'main'},e),'RUN_PROVENANCE_INVALID');});
test('artifact must belong to exact run SHA',()=>{const e=evidenceManifest.find(x=>x.artifact);const z=artifact(e);z[0].workflow_run.head_sha='f'.repeat(40);deny(()=>classifyEvidence(run(e),e,z),'CHECKPOINT_ARTIFACT_MISSING');});
test('artifact expiration is a hard failure',()=>{const e=evidenceManifest.find(x=>x.artifact);const z=artifact(e);z[0].expired=true;deny(()=>classifyEvidence(run(e),e,z),'CHECKPOINT_ARTIFACT_MISSING');});
test('final summary records untested Android build and APK recheck',()=>{const x=summary();assert.equal(x.audit,'STAGE8_DIAGNOSTIC_ACCEPTED_WITH_OPEN_GATES');assert.equal(x.signed_android_dispatch_tested,false);assert.equal(x.signed_apk_binary_independently_checked,false);assert.equal(x.android_build_started,false);});
test('any moved protected git ref denies final success',()=>deny(()=>summary({main:'f'.repeat(40)}),'PROTECTED_REF_MOVED'));
test('missing any scenario evidence denies final success',()=>deny(()=>summary({results:safeRuns().slice(1)}),'EVIDENCE_INCOMPLETE'));
test('stage 3 dispatch must be observed skipped on unauthorized request',()=>deny(()=>summary({jobCheck:{stage3_dispatch_skipped:false,stage7_negative_requires_tests:true}}),'NEGATIVE_TEST_NOT_PROVEN'));
test('stage 7 failure must be observed as such',()=>deny(()=>summary({jobCheck:{stage3_dispatch_skipped:true,stage7_negative_requires_tests:false}}),'NEGATIVE_TEST_NOT_PROVEN'));
test('fetch uses only GET and refuse any POST endpoint',async()=>{const calls=[];const api=new ReadOnlyGitHub(null,async(_url,opts)=>{calls.push(opts.method);return {status:200,json:async()=>({})}});await api.get('/actions/runs/40472241780');assert.deepEqual(calls,['GET']);await denyAsync(()=>api.get('/actions/workflows/apk-release.yml/dispatches'),'URL_NOT_ALLOWED');});
test('offline read-only parser prevents real build requests without approval',()=>{const r={schema_version:1,kind:'build',repo:'laszko14/nira-browser',approval:'APPROVE_ONE_SIGNED_ARM64_BUILD',variant:'clean',branch:config.targets.clean.branch,sha:config.targets.clean.pinned_sha,version_code:1174,version_name:config.targets.clean.version_name};const file='devtools/build-requests/clean-'+r.sha+'.json';assert.equal(buildRequest(r,config,file).variant,'clean');assert.throws(()=>buildRequest({...r,approval:'not-approved'},config,file));});
test('Kotlin change selects Android test families and may not be JS-only',()=>{const a=chooseTests([{filename:'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt',status:'modified'}],'clean',config);assert.ok(a.tasks.includes('android-build'));assert.ok(a.tasks.includes('android-lint'));assert.equal(a.android_tasks_blocked,true);});
test('checkpoint accepts only matching SHA, not checksum alone',()=>{const r={schema_version:1,kind:'orchestrator-diagnostic',mode:'DIAGNOSTIC_ONLY',repo:'laszko14/nira-browser',variant:'clean',branch:config.targets.clean.branch,sha:config.targets.clean.pinned_sha,build_requested:false,changed_files:[{filename:'app/src/test/js/video-metadata-v1174.test.cjs',status:'added'}],test_run_id:40469224355,fast_probe_path:'devtools/smart-ci-probes/clean-v1174-js-20261027-01.json'};const c=newCheckpoint(r,config);assert.equal(validateCheckpoint(c,r,config).revision,0);const changed={...r,sha:'f'.repeat(40)};assert.throws(()=>validateCheckpoint(c,changed,config));});
