import test from 'node:test';
import assert from 'node:assert/strict';
import {validateRequest,validateDiff,validateEvent,RequestError} from '../build-request-validator.mjs';

const config={repo:'laszko14/nira-browser',mode:'preflight-only',execution_enabled:false,create_refs_enabled:false,allow_releases:false,targets:{
  clean:{branch:'feat/zavro-v1174-clean-video-metadata',pinned_sha:'3'.repeat(40),abi:'arm64-v8a',package:'com.prirai.android.nira.debug',version_code:1174,version_name:'0.5.22-alpha79-clean',certificate_sha256:'f'.repeat(64)},
  mpv:{branch:'feat/zavro-x-mpv-v1167-reference-video-panel',pinned_sha:'d'.repeat(40),abi:'arm64-v8a',package:'com.prirai.android.nira.mpv',version_code:1167,version_name:'0.5.22-alpha73-mpv40',certificate_sha256:'f'.repeat(64)}
}};
const req=v=>({schema_version:1,kind:'build',repo:'laszko14/nira-browser',approval:'APPROVE_ONE_SIGNED_ARM64_BUILD',variant:v,branch:config.targets[v].branch,sha:config.targets[v].pinned_sha,version_code:config.targets[v].version_code,version_name:config.targets[v].version_name});
const path=v=>'devtools/build-requests/'+v+'-'+req(v).sha+'.json';
const reject=(run,code)=>assert.throws(run,e=>e instanceof RequestError&&e.message===code);

test('Clean and MPV pinned requests accepted',()=>{for(const v of ['clean','mpv'])assert.equal(validateRequest(req(v),config,path(v)).variant,v);});
test('Explicit approval required',()=>reject(()=>validateRequest({...req('clean'),approval:false},config,path('clean')),'NO_APPROVAL'));
test('Cross-variant tampering forbidden',()=>reject(()=>validateRequest({...req('clean'),variant:'mpv'},config,path('clean')),'BRANCH_MISMATCH'));
test('Stale SHA rejected',()=>reject(()=>validateRequest({...req('clean'),sha:'f'.repeat(40)},config,path('clean')),'SHA_MISMATCH'));
test('Wrong version rejected',()=>reject(()=>validateRequest({...req('mpv'),version_code:1174},config,path('mpv')),'VERSION_MISMATCH'));
test('Extra request fields rejected',()=>reject(()=>validateRequest({...req('clean'),token:'unsafe'},config,path('clean')),'UNEXPECTED_FIELDS'));
test('Deterministic filename required',()=>reject(()=>validateRequest(req('clean'),config,'devtools/build-requests/other.json'),'NON_IDEMPOTENT_FILENAME'));
test('Stage-2 config may not be enabled',()=>reject(()=>validateRequest(req('clean'),{...config,execution_enabled:true},path('clean')),'UNSAFE_CONTROLLER_CONFIG'));
test('One changed file only',()=>reject(()=>validateDiff('A\t'+path('clean')+'\nM\tmain',path('clean')),'EXPECTED_SINGLE_CHANGE'));
test('Modifications never count as new requests',()=>reject(()=>validateDiff('M\t'+path('clean'),path('clean')),'UNSAFE_CHANGE'));
test('GitHub actor/ref/repo validation',()=>{
  const e={GITHUB_REPOSITORY:'laszko14/nira-browser',GITHUB_REF:'refs/heads/tools/zavro-development-system-stage1-20261027',GITHUB_ACTOR:'laszko14',BEFORE_SHA:'3'.repeat(40),GITHUB_SHA:'d'.repeat(40)};
  validateEvent(e);
  reject(()=>validateEvent({...e,GITHUB_ACTOR:'other'}),'UNAUTHORIZED_ACTOR');
  reject(()=>validateEvent({...e,GITHUB_REF:'refs/heads/main'}),'UNAUTHORIZED_REPO_REF');
});
