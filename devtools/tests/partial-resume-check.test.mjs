import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {newCheckpoint,advance} from '../orchestrate.mjs';
import {checkProbe,verifyPartial} from '../partial-resume-check.mjs';
const config=JSON.parse(readFileSync(new URL('../build-control.json',import.meta.url),'utf8'));
const req=JSON.parse(readFileSync(new URL('../orchestrator-probes/stage7-negative-kotlin-20261027-01.json',import.meta.url),'utf8'));
const probe={schema_version:1,kind:'verify-fail-closed-partial',repo:'laszko14/nira-browser',source_run_id:40472397516,
 source_artifact_id:12719177242,source_request:'devtools/orchestrator-probes/stage7-negative-kotlin-20261027-01.json',build_requested:false};
function partial(){
 let s=newCheckpoint(req,config);
 s=advance(s,req,config,'ANALYZE',{tasks:['kotlin-unit','android-lint','android-build','apk-metadata']});
 s=advance(s,req,config,'INSPECT',{repo:'laszko14/nira-browser',
  checked_prs:[84,85,88,89].map(number=>({number,sha:'a'.repeat(40)})),
  mutation_allowed:false,merge_conflicts_claimed:false});
 s=advance(s,req,config,'PREPARE',{implementation_applied:false,isolation_only:true,branch_plan:'tools/zavro-isolated-clean-'+req.sha.slice(0,12)});
 return s;
}
test('exact fixed historical artifact probe accepted',()=>assert.deepEqual(checkProbe(probe),probe));
test('changed artifact ID refused',()=>assert.throws(()=>checkProbe({...probe,source_artifact_id:12}),e=>e.code==='PROBE_NOT_APPROVED'));
test('signed APK request cannot enter partial recovery',()=>assert.throws(()=>checkProbe({...probe,build_requested:true}),e=>e.code==='PROBE_NOT_APPROVED'));
test('partial checkpoint refuses Kotlin evidence shortcut and performs zero calls',async()=>{
 const r=await verifyPartial({state:partial(),request:req,config});
 assert.equal(r.status,'PARTIAL_CHECKPOINT_RESUMED_SAFELY');
 assert.equal(r.prior_steps_replayed,0);assert.equal(r.remote_operations_started,0);assert.equal(r.build_started,false);
 assert.ok(r.required_tests_missing.includes('android-build'));
});
test('full checkpoint is not misclassified as partial',async()=>{
 const s=newCheckpoint(req,config);
 await assert.rejects(()=>verifyPartial({state:s,request:req,config}),e=>e.code==='NOT_EXPECTED_PARTIAL_STATE');
});
test('tampered checkpoint digest is refused before retry',async()=>{
 const s=partial();s.checksum='f'.repeat(64);
 await assert.rejects(()=>verifyPartial({state:s,request:req,config}),e=>e.code==='CHECKPOINT_DIGEST_MISMATCH');
});
test('changed source SHA rejected before recovery',async()=>{
 const s=partial();await assert.rejects(()=>verifyPartial({state:s,request:{...req,sha:'f'.repeat(40)},config}));
});
