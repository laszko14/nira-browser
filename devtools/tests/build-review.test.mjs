import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {ReviewError,validateReview,validateTrigger,review} from '../build-review.mjs';
const conf=JSON.parse(readFileSync(new URL('../build-control.json',import.meta.url),'utf8'));
const r=v=>({schema_version:1,kind:'build-readiness-review',repo:'laszko14/nira-browser',build_requested:false,variant:v,sha:conf.targets[v].pinned_sha,branch:conf.targets[v].branch});
const p='devtools/build-review-probes/example.json';
const bad=(fn,k)=>assert.throws(fn,e=>e instanceof ReviewError&&e.code===k);
test('both pinned variants are valid read-only reviews',()=>{for(const v of ['clean','mpv'])assert.equal(validateReview(r(v),conf,p).variant,v);});
test('review never accepts build_requested=true',()=>bad(()=>validateReview({...r('clean'),build_requested:true},conf,p),'NOT_READ_ONLY'));
test('review rejects unknown extra token field',()=>bad(()=>validateReview({...r('clean'),token:'x'},conf,p),'FIELDS'));
test('review refuses stale SHA',()=>bad(()=>validateReview({...r('clean'),sha:'f'.repeat(40)},conf,p),'PIN'));
test('review refuses cross-package branch',()=>bad(()=>validateReview({...r('clean'),branch:conf.targets.mpv.branch},conf,p),'PIN'));
test('review refuses disabled config flag enabled',()=>bad(()=>validateReview(r('clean'),{...conf,execution_enabled:true},p),'CONFIG'));
test('review confined to one namespace',()=>bad(()=>validateReview(r('clean'),conf,'devtools/build-requests/c.json'),'PATH'));
test('trigger allows only a new request file on tools branch',()=>{
 const e={GITHUB_REPOSITORY:'laszko14/nira-browser',GITHUB_REF:'refs/heads/tools/zavro-development-system-stage1-20261027',GITHUB_ACTOR:'laszko14',BEFORE_SHA:'a'.repeat(40),GITHUB_SHA:'b'.repeat(40)};
 assert.equal(validateTrigger(e,'A\t'+p),p);
 bad(()=>validateTrigger(e,'A\t'+p+'\nM\tapp/build.gradle'),'SINGLE_ADDITION');
 bad(()=>validateTrigger({...e,GITHUB_REF:'refs/heads/main'},'A\t'+p),'EVENT');
 bad(()=>validateTrigger(e,'M\t'+p),'UNSAFE_CHANGE');
});
const mock=()=>({
 head:async()=>conf.targets.clean.pinned_sha,
 workflowMetadata:async()=>({state:'active',path:'.github/workflows/apk-release.yml',id:392221495}),
 workflowText:async()=>['name: Android CI','  workflow_dispatch:','  build:','    env:','      EXPECTED_PACKAGE: '+conf.targets.clean.package,'      EXPECTED_VERSION_CODE: "'+conf.targets.clean.version_code+'"','      EXPECTED_VERSION_NAME: '+conf.targets.clean.version_name,'      EXPECTED_CERT_SHA256: '+conf.targets.clean.certificate_sha256,'    steps:','      - run: ./gradlew assembleDebug','      - run: bash scripts/verify-update-apk.sh','  release:',"    if: startsWith(github.ref, 'refs/tags/v')"].join('\n'),
 runs:async()=>[]
});
test('positive plan cannot dispatch or forge APK status',async()=>{const x=await review(r('clean'),conf,mock());assert.equal(x.stage,'READ_ONLY_BUILD_REVIEW');assert.equal(x.build_started,false);assert.equal(x.dispatch_enabled,false);assert.equal(x.ready_for_build,'NEEDS_EXPLICIT_APPROVAL');assert.equal(x.apk_created,false);});
test('branch moved prevents plan',async()=>{const a=mock();a.head=async()=>'e'.repeat(40);await assert.rejects(review(r('clean'),conf,a),e=>e.code==='HEAD_MOVED');});
test('earlier run forces review rather than duplicate build',async()=>{const a=mock();a.runs=async()=>[{head_sha:conf.targets.clean.pinned_sha,head_branch:conf.targets.clean.branch,workflow_id:392221495,status:'completed',conclusion:'success',id:10}];const x=await review(r('clean'),conf,a);assert.equal(x.decision,'REUSE_SUCCESS');assert.equal(x.ready_for_build,'REVIEW_EXISTING_RUN');});
