import test from 'node:test';
import assert from 'node:assert/strict';
import {InspectorError,safeguardWrite,collectFiles,comparePRs,inspectCodexFleet,dependencyGraph,inspectTestLab,compareTestLabs,GithubReader,inspectLivePRs} from '../code-inspector.mjs';
const sha=n=>n.repeat(40);
const pr=(number,ref,base,paths)=>({number,state:'open',draft:true,
  head:{sha:sha(number===84?'a':number===85?'b':number===88?'c':'d'),ref,repo:{full_name:'laszko14/nira-browser'}},
  base:{sha:sha('e'),ref:base,repo:{full_name:'laszko14/nira-browser'}},
  files:paths.map(filename=>({filename,status:'modified'}))});
const clean=pr(85,'feat/zavro-v1170-clean-codex-video-tiles','feat/zavro-v1169-clean-render-frame-pacing',
  ['app/build.gradle','app/src/main/java/com/prirai/android/nira/media/VideoTilesPanel.kt','app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt']);
const mpv=pr(84,'feat/zavro-x-mpv-v1167-reference-video-panel','feat/zavro-x-mpv-v1165-testlab-diagnostics',
  ['app/build.gradle','app/src/main/java/com/prirai/android/nira/media/VideoTilesPanel.kt','app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt','docs/AI_PROGRESS.md']);
const later=pr(89,'feat/zavro-v1174-clean-video-metadata','feat/zavro-v1173-clean-video-discovery-fixes',['app/build.gradle','app/src/main/java/com/prirai/android/nira/diagnostics/testlab/TestLabRuntime.kt']);
const earlier=pr(88,'feat/zavro-v1173-clean-video-discovery-fixes','feat/zavro-v1172-clean-video-discovery-diagnostics',['app/build.gradle','app/src/main/java/com/prirai/android/nira/diagnostics/testlab/TestLabRuntime.kt']);
const err=(f,c)=>assert.throws(f,x=>x instanceof InspectorError&&x.code===c);
test('PR84 MPV vs PR85 Clean share three concrete code paths',()=>{
  const report=comparePRs(mpv,clean);
  assert.equal(report.overlap_count,3);
  assert.equal(report.classification,'POTENTIAL_OVERLAP');
  assert.equal(report.merge_conflict_proven,false);
  assert.equal(report.loss_of_changes_proven,false);
});
test('sequential PR88 -> PR89 detected as lineage, not proven conflict',()=>{
  const report=comparePRs(earlier,later);
  assert.equal(report.overlap_count,2);
  assert.equal(report.historical_chain_detected,true);
  assert.equal(report.guidance,'SEQUENTIAL_PR_LINEAGE_REVIEW');
});
test('no overlap means no overlapping file paths',()=>{
  const a=pr(88,earlier.head.ref,earlier.base.ref,['docs/A.md']);
  const b=pr(89,later.head.ref,later.base.ref,['docs/B.md']);
  assert.equal(comparePRs(a,b).classification,'NO_FILE_OVERLAP');
});
test('rename source is also overlap',()=>{
  const a=structuredClone(clean);
  a.files=[{filename:'app/New.kt',previous_filename:'app/Old.kt',status:'renamed'}];
  const b=structuredClone(mpv);b.files=[{filename:'app/Old.kt',status:'modified'}];
  assert.deepEqual(comparePRs(a,b).overlapping_paths,['app/Old.kt']);
});
test('draft PR expected, closed PR rejected',()=>{const p=structuredClone(clean);p.state='closed';err(()=>comparePRs(p,mpv),'PR_STATE_UNVERIFIED');});
test('fork origin is rejected',()=>{const p=structuredClone(clean);p.head.repo.full_name='another/repo';err(()=>comparePRs(p,mpv),'PR_REPOSITORY_MISMATCH');});
test('missing current PR SHA rejected',()=>{const p=structuredClone(clean);p.head.sha='abc';err(()=>comparePRs(p,mpv),'PR_SHA_UNVERIFIED');});
test('possible truncation at 100 PR file items rejected',()=>{const p=structuredClone(clean);p.files=Array.from({length:100},(_,i)=>({filename:'docs/'+i+'.md',status:'modified'}));err(()=>comparePRs(p,mpv),'PAGINATION_OR_INVALID_FILES');});
test('unsafe path rejected',()=>err(()=>collectFiles([{filename:'../internal',status:'added'}]),'INVALID_PATH'));
test('single inspector summarizes pair overlaps',()=>{const f=inspectCodexFleet([mpv,clean,earlier,later]);assert.equal(f.overlap_pairs.length,6);assert.equal(f.mutation_allowed,false);});
test('main mutation cannot pass',()=>err(()=>safeguardWrite({operation:'edit',branch_name:'main',expected_sha:sha('a'),current_sha:sha('a')}),'PROTECTED_BRANCH'));
test('Codex feature mutation cannot pass',()=>err(()=>safeguardWrite({operation:'edit',branch_name:mpv.head.ref,expected_sha:sha('a'),current_sha:sha('a')}),'PROTECTED_BRANCH'));
test('stale tools branch cannot pass',()=>err(()=>safeguardWrite({branch_name:'tools/zavro-development-system-stage1-20261027',expected_sha:sha('a'),current_sha:sha('b')}),'STALE_REF'));
test('tools branch still read-only, even when safe',()=>err(()=>safeguardWrite({operation:'update',branch_name:'tools/zavro-development-system-stage1-20261027',expected_sha:sha('a'),current_sha:sha('a'),changed_paths:['devtools/x']}),'READ_ONLY_INSPECTOR'));
test('tools branch may never be used to patch Android source',()=>err(()=>safeguardWrite({operation:'update',branch_name:'tools/zavro-development-system-stage1-20261027',expected_sha:sha('a'),current_sha:sha('a'),changed_paths:['app/build.gradle']}),'PRODUCTION_PATH_FORBIDDEN'));
test('explicit import dependency graph creates reverse impacted set',()=>{
 const dir='app/src/main/java/com/prirai/android/nira/diagnostics/testlab/';
 const A=dir+'A.kt',B=dir+'B.kt',C=dir+'C.kt';
 const g=dependencyGraph({
 [A]:'package com.prirai.android.nira.diagnostics.testlab\nclass A',
 [B]:'package com.prirai.android.nira.diagnostics.testlab\nimport com.prirai.android.nira.diagnostics.testlab.A\nclass B',
 [C]:'package com.prirai.android.nira.diagnostics.testlab\nimport com.prirai.android.nira.diagnostics.testlab.B\nclass C'
 },[A]);
 assert.equal(g.edges.length,2);
 assert.deepEqual(g.potentially_affected,[A,B,C]);
 assert.equal(g.runtime_dependencies_proven,false);
});
test('unresolved imports remain flagged, never fabricated',()=>{
 const g=dependencyGraph({'app/X.kt':'package com.prirai.android.nira\nimport com.prirai.android.nira.Missing\nclass X'});
 assert.equal(g.unresolved_imports.length,1);assert.equal(g.edges.length,0);
});
test('Kotlin graph read-only and bounded',()=>{err(()=>dependencyGraph({'app/../../X.kt':'class X'}),'INVALID_PATH');err(()=>dependencyGraph({}),'SOURCE_GRAPH_BOUND');});
const txt='Zavro TestLab • COMPLEX • 0.5.22-alpha73-clean (1168)\nStan: COMPLETED; rzeczywisty pomiar: 60.000 s\nNajważniejsze obserwacje (korelacja czasowa nie dowodzi przyczyny):\nOBSERVED_PERFORMANCE_ANOMALY: SLOW_FRAME ; t=50502.914 ms; wartość=208.096 MILLISECONDS; próg=BRAK DANYCH, n=1\nn=1070, jank=122 (11.4%), średnia=12 ms, max=214.7 ms\n';
test('realistic Clean TestLab TXT preserves measured limits',()=>{
 const o=inspectTestLab(txt.replaceAll('\\\n','\n'));
 assert.equal(o.test_type,'COMPLEX');assert.equal(o.status,'COMPLETED');assert.equal(o.android_window_frames,1070);
 assert.equal(o.window_jank_percent,11.4);
 assert.equal(o.gecko_fps_measured,false);assert.equal(o.mpv_frames_measured,false);assert.equal(o.causality_claimed,false);
 assert.deepEqual(o.observations,[{kind:'OBSERVED_PERFORMANCE_ANOMALY',code:'SLOW_FRAME'}]);
});
test('PARTIAL test is not COMPLETED',()=>{
 const o=inspectTestLab(txt.replaceAll('\\\n','\n').replace('Stan: COMPLETED','Stan: PARTIAL'));
 assert.equal(o.coverage_complete,false);
});
const json=()=>({schemaVersion:1,testType:'VIDEO_METADATA',appVersion:{versionCode:1174,versionName:'0.5.22-alpha79-clean'},status:'COMPLETED',durationMs:60000,
 frameMetrics:{count:1110,jankCount:15,jankPercent:1.35,maxMs:65},
 anomalies:[{kind:'SUSPECTED_CORRELATION',code:'JAVA_HEAP_GROWTH',value:22000000}],
 videoMetadata:{observedSources:12,durationKnown:10,sizeExact:8,sizeEstimated:2,durationUnknown:2,sizeUnknown:2,
 examples:[{film:1,title:'secret title',url:'https://private.local/foo?token=secret'}]},timeline:[{url:'https://secret.local',cookie:'hidden'}]});
test('TestLab JSON exports only safe aggregates, never examples or event secrets',()=>{
 const o=inspectTestLab(json());const raw=JSON.stringify(o);
 assert.equal(o.video_metadata.duration_known,10);assert.equal(o.video_metadata.size_exact,8);
 assert.ok(!raw.includes('secret'));assert.equal(o.raw_text_included,false);
});
test('JSON string with sensitive URL is rejected',()=>err(()=>inspectTestLab(JSON.stringify(json())),'POSSIBLE_PRIVATE_DATA'));
test('text with bearer token or URL rejected',()=>err(()=>inspectTestLab(txt.replaceAll('\\\n','\n')+'\nAuthorization: Bearer abc'),'POSSIBLE_PRIVATE_DATA'));
test('TestLab version must be valid and explicit',()=>{const p=json();p.appVersion.versionCode=-1;err(()=>inspectTestLab(p),'INVALID_APP_VERSION');});
test('invalid frame samples refused',()=>{const p=json();p.frameMetrics.jankCount=1111;err(()=>inspectTestLab(p),'INVALID_FRAMES');});
test('a correlation is not a confirmed error',()=>{const o=inspectTestLab(json());assert.equal(o.observations[0].kind,'SUSPECTED_CORRELATION');assert.equal(o.causality_claimed,false);});
test('performance comparison incomplete sample is inconclusive',()=>{
 const a=inspectTestLab(json()),b=inspectTestLab({...json(),status:'PARTIAL',frameMetrics:{count:1200,jankCount:300,jankPercent:25}});
 assert.equal(compareTestLabs(a,b).status,'INSUFFICIENT_COMPARABILITY');
});
test('jank regression signals a review, not proven cause',()=>{
 const a=inspectTestLab(json()),b=inspectTestLab({...json(),frameMetrics:{count:1000,jankCount:100,jankPercent:10}});
 const c=compareTestLabs(a,b);
 assert.equal(c.potential_performance_regression,true);assert.equal(c.fixes_applied,false);assert.ok(c.confidence_note.includes('ANDROID_WINDOW_ONLY'));
});
test('different TestLab modes are not comparable',()=>{
 const a=inspectTestLab(json()),b=inspectTestLab({...json(),testType:'VIDEO_DISCOVERY'});
 err(()=>compareTestLabs(a,b),'DIFFERENT_TEST_TYPES');
});
test('GitHub source only read using GET',async()=>{
 const calls=[];const api=new GithubReader(null,async(_url,opts)=>{calls.push(opts.method);return {status:200,json:async()=>({})};});
 await api.get('/pulls/84');assert.deepEqual(calls,['GET']);
 await assert.rejects(api.get('/git/refs/heads/main'),e=>e.code==='API_PATH_FORBIDDEN');
});
test('live PR inspection with mock data returns 4 PR snapshots',async()=>{
 const prs=[mpv,clean,earlier,later];let req=0;
 const api={get:async path=>{req++;const n=Number(path.match(/\/pulls\/(\d+)/)?.[1]);const p=prs.find(x=>x.number===n);if(!p)throw Error('unknown PR');return path.includes('/files')?p.files:p;}};
 const result=await inspectLivePRs(api);
 assert.equal(req,8);assert.equal(result.checked_prs.length,4);assert.equal(result.merge_conflicts_claimed,false);
});

test('VIDEO_METADATA TXT anonymous duration and size aggregates',()=>{
 const report=['Zavro TestLab • VIDEO_METADATA • 0.5.22-alpha79-clean (1174)',
  'Stan: COMPLETED; rzeczywisty pomiar: 60.000 s',
  'Źródła=12; czas znany=10; czas nieznany=2; LIVE=0',
  'Rozmiar dokładny=8; szacowany=2; nieznany=2'].join('\n');
 const out=inspectTestLab(report);
 assert.equal(out.video_metadata.observed_sources,12);
 assert.equal(out.video_metadata.duration_known,10);
 assert.equal(out.video_metadata.size_exact,8);
 assert.equal(out.video_metadata.size_unknown,2);
 assert.equal(out.raw_text_included,false);
});
test('VIDEO_DISCOVERY TXT anonymous title grouping aggregates',()=>{
 const report=['Zavro TestLab • VIDEO_DISCOVERY • 0.5.22-alpha79-clean (1174)',
  'Stan: COMPLETED; rzeczywisty pomiar: 60.000 s',
  'Zaobserwowane źródła: 19; grupy: 11; grupy wielowariantowe: 2',
  'Brak tytułu: 3; niepewne grupy: 4; konflikty grup: 1'].join('\n');
 const out=inspectTestLab(report);
 assert.equal(out.video_discovery.sources,19);
 assert.equal(out.video_discovery.groups,11);
 assert.equal(out.video_discovery.uncertain_groups,4);
 assert.equal(out.video_discovery.title_conflicts,1);
 assert.equal(out.gecko_fps_measured,false);
});
