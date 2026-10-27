import test from 'node:test';
import assert from 'node:assert/strict';
import {EditError,allowedFile,validateEdit,makeEdit,applyEditResult} from '../code-editor.mjs';
const sha='3330a0b3a057008886fde392f788a1941e0dfb84';
const mk=(v='clean')=>({schema_version:1,kind:'sandbox-text-edit',repo:'laszko14/nira-browser',variant:v,source_branch:v==='clean'?'feat/zavro-v1174-clean-video-metadata':'feat/zavro-x-mpv-v1167-reference-video-panel',source_sha:v==='clean'?sha:'d63f414acecae84c3507c3f4bb998e690281b91e',workspace_branch:'tools/zavro-edit-'+v+'-20261027',workspace_head:v==='clean'?sha:'d63f414acecae84c3507c3f4bb998e690281b91e',path:'app/src/test/java/com/prirai/android/nira/devtools/ZavroEditorSmokeTest.kt',operation:'create',expected_blob_sha:null,content:'package com.prirai.android.nira.devtools\nclass ZavroEditorSmokeTest {}\n'});
const bad=(fn,s)=>assert.throws(fn,e=>e instanceof EditError&&e.code===s);
test('clean/MPV isolated workspaces authorized',()=>{for(const v of ['clean','mpv'])assert.equal(validateEdit(mk(v)).variant,v);});
test('production Kotlin blocked',()=>bad(()=>allowedFile('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt'),'NOT_ALLOWLISTED'));
test('workflow/Gradle/signing blocked',()=>{for(const p of ['.github/workflows/apk-release.yml','app/build.gradle','gradle.properties','scripts/verify-update-apk.sh'])bad(()=>allowedFile(p),'NOT_ALLOWLISTED');});
test('file outside allowlist blocked',()=>bad(()=>allowedFile('../tmp/exfiltration'),'UNSAFE_PATH'));
test('alternate user repo blocked',()=>bad(()=>validateEdit({...mk(),repo:'other/private'}),'REQUEST'));
test('source pin from other variant blocked',()=>bad(()=>validateEdit({...mk(),source_sha:'d63f414acecae84c3507c3f4bb998e690281b91e'}),'SOURCE_PIN'));
test('cannot target main or Codex branch',()=>bad(()=>validateEdit({...mk(),workspace_branch:'feat/zavro-v1174-clean-video-metadata'}),'WORKSPACE_ISOLATION'));
test('cannot use arbitrary sandbox name',()=>bad(()=>validateEdit({...mk(),workspace_branch:'tools/zavro-edit-clean-stolen'}),'WORKSPACE_ISOLATION'));
test('existing file forbids create duplicate',()=>bad(()=>makeEdit(mk(),{sha:'a'.repeat(40),content:'hello',type:'file'},sha),'FILE_CAS_MISMATCH'));
test('branch moved denies commit',()=>bad(()=>makeEdit(mk(),null,'f'.repeat(40)),'WORKSPACE_MOVED'));
test('malformed object extra auth key refused',()=>bad(()=>validateEdit({...mk(),token:'password'}),'FIELDS'));
test('content with signing secret refused',()=>bad(()=>validateEdit({...mk(),content:'KEYSTORE_PASSWORD=hidden'}),'SENSITIVE_CONTENT'));
test('content >16KB refused',()=>bad(()=>validateEdit({...mk(),content:'a'.repeat(16001)}),'CONTENT'));
test('successful create is only one allowlisted file',()=>{const r=makeEdit(mk(),null,sha);assert.equal(r.path,mk().path);assert.equal(r.source_branch_untouched,true);assert.equal(r.requires_independent_tests,true);assert.equal(r.production_affected,false);});
test('replace needs matching blob SHA',()=>{const r={...mk(),operation:'replace',expected_blob_sha:'a'.repeat(40),content:'new'};bad(()=>makeEdit(r,{sha:'b'.repeat(40),type:'file',content:'old'},sha),'FILE_CAS_MISMATCH');const p=makeEdit(r,{sha:'a'.repeat(40),type:'file',content:'old'},sha);assert.equal(p.operation,'replace');});
test('same content is not a change',()=>{const r={...mk(),operation:'replace',expected_blob_sha:'a'.repeat(40),content:'same'};bad(()=>makeEdit(r,{sha:'a'.repeat(40),type:'file',content:'same'},sha),'NO_CHANGE');});
test('edit result never claims a verified build',()=>{const r=makeEdit(mk(),null,sha);const x=applyEditResult(r,sha,'f'.repeat(40));assert.equal(x.compiled_verified,false);assert.equal(x.android_build_started,false);});
test('race on branch update fails closed',()=>{const p=makeEdit(mk(),null,sha);bad(()=>applyEditResult(p,'e'.repeat(40),'f'.repeat(40)),'RACE_PRECONDITION');});

const productionFile='app/src/main/java/com/prirai/android/nira/media/MediaRecentDeduplicator.kt';
const originalMedia='package com.prirai.android.nira.media\n\ninternal class Example {\n val old = true\n}\n';
const productionReq=()=>({...mk(),operation:'replace',path:productionFile,expected_blob_sha:'a'.repeat(40),content:originalMedia.replace('val old = true','val old = false')});
const previous=()=>({sha:'a'.repeat(40),type:'file',content:originalMedia});
test('controlled production Kotlin replacement requires exact CAS, reports full checks',()=>{
 const r=makeEdit(productionReq(),previous(),sha);
 assert.equal(r.production_affected,true);assert.deepEqual(r.required_checks,['kotlin-unit','android-lint','android-build']);
});
test('production Kotlin cannot be created from scratch',()=>{
 const r={...productionReq(),operation:'create',expected_blob_sha:null};
 bad(()=>makeEdit(r,null,sha),'PRODUCTION_REPLACEMENT_RESTRICTED');
});
test('production Kotlin change refuses MPV cross-variant',()=>{
 const r={...productionReq(),variant:'mpv',source_branch:mk('mpv').source_branch,source_sha:mk('mpv').source_sha,
  workspace_branch:mk('mpv').workspace_branch,workspace_head:mk('mpv').workspace_head};
 bad(()=>makeEdit(r,previous(),r.workspace_head),'PRODUCTION_REPLACEMENT_RESTRICTED');
});
test('large production rewrite refused',()=>{
 const r=productionReq();r.content='package com.prirai.android.nira.media\n'+Array.from({length:30},(_,i)=>'val x'+i+' = true').join('\n');
 bad(()=>makeEdit(r,previous(),sha),'PRODUCTION_CHANGE_TOO_LARGE');
});
test('package rename refused',()=>{
 const r=productionReq();r.content=r.content.replace('package com.prirai.android.nira.media','package com.prirai.android.nira.secret');
 bad(()=>makeEdit(r,previous(),sha),'PRODUCTION_PACKAGE_CHANGED');
});
test('dependency import injection refused',()=>{
 const r=productionReq();r.content=r.content.replace('val old = false','import java.io.File');
 bad(()=>makeEdit(r,previous(),sha),'PRODUCTION_IMPORT_OR_PACKAGE_CHANGED');
});
test('production change must not target any unapproved file',()=>{
 bad(()=>allowedFile('app/src/main/java/com/prirai/android/nira/browser/tabs/compose/TabBarCompose.kt'),'NOT_ALLOWLISTED');
});
