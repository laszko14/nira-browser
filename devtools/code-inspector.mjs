#!/usr/bin/env node
// Stage 6: bounded, read-only Zavro Code Inspector; no patching, builds or git ref writes.
import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

const REPO='laszko14/nira-browser';
const SHA=/^[a-f0-9]{40}$/;
const SAFE_PATH=/^[A-Za-z0-9_./+\-]+$/;
const SAFE_BRANCH=/^(?:main|feat\/[a-z0-9/-]+|fix\/[a-z0-9/-]+|tools\/zavro-development-system-[a-z0-9/-]+)$/;
const PROTECTED_EXACT=new Set(['main']);
const KNOWN_TYPES=new Set(['COMPLEX','PANELS_GESTURES','TABS_GROUPS','GECKO_PAGES','VIDEO_DISCOVERY','VIDEO_METADATA','RESOURCES','STABILITY','VIDEO_MPV']);
const KNOWN_PHASES=new Set(['COMPLETED','PARTIAL','CANCELLED','FAILED','RUNNING']);
const OBS_KINDS=new Set(['CONFIRMED_INVARIANT_FAILURE','OBSERVED_PERFORMANCE_ANOMALY','SUSPECTED_CORRELATION','INSUFFICIENT_DATA','NOT_TESTED']);
const FAMILIES=['testlab','video','ui','tabs','gecko','mpv','build','tests','other'];
export class InspectorError extends Error{constructor(code){super(code);this.code=code;}}
function ensure(ok,code){if(!ok)throw new InspectorError(code);}
function path(p){ensure(typeof p==='string'&&p.length>0&&p.length<=400&&SAFE_PATH.test(p)&&!p.startsWith('/')&&!p.includes('..')&&!p.includes('//'),'INVALID_PATH');return p;}
function branch(b){ensure(typeof b==='string'&&b.length<=180&&SAFE_BRANCH.test(b)&&!b.includes('..'),'INVALID_BRANCH');return b;}
const safeNum=(x)=>typeof x==='number'&&Number.isFinite(x)&&x>=0;
const countInt=x=>Number.isSafeInteger(x)&&x>=0;
export function safeguardWrite({operation,branch_name,expected_sha,current_sha,changed_paths=[]}){
 // Inspector is fundamentally read-only; this hook always refuses mutation.
 branch(branch_name);
 ensure(SHA.test(expected_sha)&&SHA.test(current_sha),'INVALID_SHA');
 if(expected_sha!==current_sha)throw new InspectorError('STALE_REF');
 if(PROTECTED_EXACT.has(branch_name)||branch_name.startsWith('feat/')||branch_name.startsWith('fix/'))throw new InspectorError('PROTECTED_BRANCH');
 ensure(Array.isArray(changed_paths),'INVALID_DIFF');
 for(const p of changed_paths){path(p);ensure(p.startsWith('devtools/')||p.startsWith('docs/dev-system/'),'PRODUCTION_PATH_FORBIDDEN');}
 throw new InspectorError('READ_ONLY_INSPECTOR');
}
export function collectFiles(items){
 ensure(Array.isArray(items)&&items.length<=99,'PAGINATION_OR_INVALID_FILES');
 const out=new Set();
 for(const f of items){
  ensure(f&&['added','modified','removed','renamed','changed','copied'].includes(f.status),'INVALID_FILE_STATUS');
  out.add(path(f.filename));
  if(f.status==='renamed'){ensure(f.previous_filename,'MISSING_RENAME_SOURCE');out.add(path(f.previous_filename));}
 }
 return [...out].sort();
}
function family(file){
 if(file.includes('/diagnostics/testlab/')||/testlab/i.test(file))return 'testlab';
 if(file.includes('/media/')||file.includes('/nira-media-detector/'))return 'video';
 if(/(VideoTiles|MediaFloatingUi|BrowserRefreshRate|display\/)/.test(file))return 'ui';
 if(/\/(tab|groups?)\//i.test(file))return 'tabs';
 if(/gecko|mozilla/i.test(file))return 'gecko';
 if(/mpv|\.lua$/i.test(file))return 'mpv';
 if(/\.github\/workflows\/|gradle|^scripts\//.test(file))return 'build';
 if(/\/test\//.test(file)||file.endsWith('.test.cjs'))return 'tests';
 return 'other';
}
export function comparePRs(a,b){
 for(const p of [a,b]){
  ensure(p&&countInt(p.number)&&p.number>0&&p.state==='open'&&p.draft===true,'PR_STATE_UNVERIFIED');
  ensure(p.head&&SHA.test(p.head.sha)&&branch(p.head.ref)&&p.base&&SHA.test(p.base.sha)&&branch(p.base.ref),'PR_SHA_UNVERIFIED');
  ensure(p.head.repo?.full_name===REPO&&p.base.repo?.full_name===REPO,'PR_REPOSITORY_MISMATCH');
 }
 ensure(a.number!==b.number,'DUPLICATE_PR');
 const left=collectFiles(a.files),right=collectFiles(b.files);
 const intersect=left.filter(f=>right.includes(f));
 const chained=a.base.ref===b.head.ref||b.base.ref===a.head.ref;
 const types=[...new Set(intersect.map(family))].sort();
 return {pr_a:a.number,pr_b:b.number,sha_a:a.head.sha,sha_b:b.head.sha,
  overlapping_paths:intersect,overlap_count:intersect.length,affected_families:types,
  historical_chain_detected:chained,classification:intersect.length?'POTENTIAL_OVERLAP':'NO_FILE_OVERLAP',
  merge_conflict_proven:false,loss_of_changes_proven:false,
  guidance:chained?'SEQUENTIAL_PR_LINEAGE_REVIEW':'REVIEW_BEFORE_CROSS_BRANCH_PORT',
  protected_branches:[a.head.ref,b.head.ref]};
}
export function inspectCodexFleet(prs){
 ensure(Array.isArray(prs)&&prs.length>=2&&prs.length<=15,'PR_SET_INVALID');
 const pairs=[];
 for(let i=0;i<prs.length;i++)for(let j=i+1;j<prs.length;j++)pairs.push(comparePRs(prs[i],prs[j]));
 return {repo:REPO,checked_prs:prs.map(p=>({number:p.number,branch:p.head.ref,sha:p.head.sha,draft:p.draft})),
  overlap_pairs:pairs,mutation_allowed:false,merge_conflicts_claimed:false};
}
function moduleName(file,source){
 const pack=String(source).match(/^\s*package\s+([A-Za-z0-9_.]+)/m)?.[1];
 const kind=String(source).match(/^\s*(?:internal\s+|private\s+|public\s+|data\s+|sealed\s+|abstract\s+|open\s+)*\b(?:class|object|interface|enum\s+class)\s+([A-Za-z][A-Za-z0-9_]*)/m)?.[1];
 return pack&&kind?pack+'.'+kind:null;
}
export function dependencyGraph(sources,changed=[]){
 ensure(sources&&typeof sources==='object'&&!Array.isArray(sources),'SOURCE_MAP_REQUIRED');
 const names=Object.keys(sources);ensure(names.length>=1&&names.length<=45,'SOURCE_GRAPH_BOUND');
 const byClass=new Map(),nodes=[],missing=[];
 for(const file of names){
  path(file);ensure(file.endsWith('.kt')||file.endsWith('.java'),'UNSUPPORTED_SOURCE_FORMAT');
  const content=sources[file];
  ensure(typeof content==='string'&&content.length<=160000,'SOURCE_TOO_LARGE');
  const cls=moduleName(file,content);
  if(cls)byClass.set(cls,file);
  nodes.push({file,module:cls});
 }
 const edges=[];
 for(const file of names){
  const imports=[...sources[file].matchAll(/^\s*import\s+(com\.prirai\.android\.nira\.[A-Za-z0-9_.]+)\s*$/gm)].map(m=>m[1]);
  for(const dep of imports){const resolved=byClass.get(dep);if(resolved&&resolved!==file)edges.push({from:file,to:resolved,evidence:'EXPLICIT_SOURCE_IMPORT'});else if(!resolved)missing.push({from:file,unresolved_import:dep});}
 }
 const impacted=new Set(changed.map(p=>{ensure(names.includes(path(p)),'UNKNOWN_CHANGED_FILE');return p;}));
 let updated=true;while(updated){updated=false;for(const e of edges)if(impacted.has(e.to)&&!impacted.has(e.from)){impacted.add(e.from);updated=true;}}
 return {nodes,edges,unresolved_imports:missing,changed:changed.slice(),
  potentially_affected:[...impacted].sort(),analysis:'EXPLICIT_IMPORTS_ONLY',
  runtime_dependencies_proven:false,implicit_same_package_links_not_analyzed:true,
  edits_applied:false};
}
function rejectSensitive(s){
 ensure(typeof s==='string'&&s.length<=280000,'REPORT_INVALID_OR_OVERSIZE');
 ensure(!/https?:\/\/|(?:^|\s)(?:Cookie:|Authorization:|Bearer\s|token=|access_token=)|data:[^ \n]+;base64,/i.test(s),'POSSIBLE_PRIVATE_DATA');
}
function extractType(s){return s.match(/^Zavro TestLab\s*•\s*([A-Z_]+)\s*•\s*(\S+)\s*\((\d+)\)/m);}
export function inspectTestLab(input){
 if(typeof input==='string'&&input.trimStart().startsWith('{')){
  rejectSensitive(input);
  let object;try{object=JSON.parse(input);}catch{throw new InspectorError('INVALID_JSON');}
  return inspectTestLab(object);
 }
 if(typeof input==='string'){
  rejectSensitive(input);const header=extractType(input);
  ensure(header&&KNOWN_TYPES.has(header[1]),'UNKNOWN_TESTLAB_FORMAT');
  const state=input.match(/^Stan:\s*([A-Z_]+);\s*rzeczywisty pomiar:\s*([\d.,]+)\s*s/m);
  ensure(state&&KNOWN_PHASES.has(state[1]),'INVALID_TESTLAB_STATUS');
  const seconds=Number(state[2].replace(',','.'));ensure(safeNum(seconds)&&seconds<=120,'INVALID_DURATION');
  const frame=input.match(/^n=(\d+), jank=(\d+) \(([\d.,]+)%\), średnia=([\d.,]+) ms, max=([\d.,]+) ms/m);
  const observations=[...input.matchAll(/^(CONFIRMED_INVARIANT_FAILURE|OBSERVED_PERFORMANCE_ANOMALY|SUSPECTED_CORRELATION|INSUFFICIENT_DATA|NOT_TESTED):\s*([A-Z_]+)/gm)]
    .slice(0,30).map(x=>({kind:x[1],code:x[2]}));
  const rate=frame?Number(frame[3].replace(',','.')):null;
  const n=frame?Number(frame[1]):0, j=frame?Number(frame[2]):0;
  ensure(!frame||(n>=j&&rate>=0&&rate<=100),'INVALID_FRAMES');
  const dm=input.match(/^Zaobserwowane źródła:\s*(\d+);\s*grupy:\s*(\d+);\s*grupy wielowariantowe:\s*\d+/m);
  const dn=input.match(/^Brak tytułu:\s*\d+;\s*niepewne grupy:\s*(\d+);\s*konflikty grup:\s*(\d+)/m);
  const vm=input.match(/^Źródła=(\d+);\s*czas znany=(\d+);\s*czas nieznany=(\d+);\s*LIVE=\d+/m);
  const vn=input.match(/^Rozmiar dokładny=(\d+);\s*szacowany=(\d+);\s*nieznany=(\d+)/m);
  const vd=dm&&dn?{sources:Number(dm[1]),groups:Number(dm[2]),uncertain_groups:Number(dn[1]),title_conflicts:Number(dn[2])}:null;
  const meta=vm&&vn?{observed_sources:Number(vm[1]),duration_known:Number(vm[2]),size_exact:Number(vn[1]),size_estimated:Number(vn[2]),duration_unknown:Number(vm[3]),size_unknown:Number(vn[3])}:null;
  return {format:'TXT',test_type:header[1],version_name:header[2],version_code:Number(header[3]),status:state[1],
   duration_seconds:seconds,android_window_frames:n,window_jank_count:j,window_jank_percent:rate,
   max_frame_ms:frame?Number(frame[5].replace(',','.')):null,
   observations,coverage_complete:state[1]==='COMPLETED',
   export_truncated:/EKSPORT OGRANICZONY/.test(input),gecko_fps_measured:false,mpv_frames_measured:false,
   video_discovery:header[1]==='VIDEO_DISCOVERY'?vd:null,video_metadata:header[1]==='VIDEO_METADATA'?meta:null,causality_claimed:false,raw_text_included:false};
 }
 ensure(input&&typeof input==='object'&&!Array.isArray(input),'INVALID_REPORT');
 const o=input;
 ensure(o.schemaVersion===1&&KNOWN_TYPES.has(o.testType)&&KNOWN_PHASES.has(o.status),'INVALID_REPORT_SCHEMA');
 ensure(countInt(o.appVersion?.versionCode)&&typeof o.appVersion?.versionName==='string'&&o.appVersion.versionName.length<=64,'INVALID_APP_VERSION');
 ensure(safeNum(o.durationMs)&&o.durationMs<=120000,'INVALID_DURATION');
 const f=o.frameMetrics;
 const n=f?.count??0,j=f?.jankCount??0;
 ensure(countInt(n)&&countInt(j)&&j<=n,'INVALID_FRAMES');
 const pct=f?.jankPercent??null;
 ensure(pct===null||(safeNum(pct)&&pct<=100),'INVALID_JANK');
 const observations=(o.anomalies??[]);
 ensure(Array.isArray(observations)&&observations.length<=128,'INVALID_OBSERVATIONS');
 const findings=observations.map(v=>{ensure(OBS_KINDS.has(v.kind)&&typeof v.code==='string'&&/^[A-Z_]+$/.test(v.code),'INVALID_OBSERVATION');return {kind:v.kind,code:v.code};});
 const vd=o.videoDiscovery??null,vm=o.videoMetadata??null;
 if(vd)ensure(countInt(vd.sources)&&countInt(vd.groups)&&countInt(vd.uncertainGroups)&&countInt(vd.groupTitleConflicts),'INVALID_DISCOVERY');
 if(vm)ensure(countInt(vm.observedSources)&&countInt(vm.durationKnown)&&countInt(vm.sizeExact)&&countInt(vm.sizeEstimated)&&countInt(vm.durationUnknown)&&countInt(vm.sizeUnknown),'INVALID_METADATA');
 // Never serialize raw examples, event timeline, URL/title/cookie into inspector output.
 return {format:'JSON',test_type:o.testType,version_name:o.appVersion.versionName,version_code:o.appVersion.versionCode,status:o.status,
  duration_seconds:o.durationMs/1000,android_window_frames:n,window_jank_count:j,window_jank_percent:pct,
  max_frame_ms:f?.maxMs??null,observations:findings,coverage_complete:o.status==='COMPLETED',
  export_truncated:o.exportTruncated===true,gecko_fps_measured:false,mpv_frames_measured:false,
  video_discovery:vd?{sources:vd.sources,groups:vd.groups,uncertain_groups:vd.uncertainGroups,title_conflicts:vd.groupTitleConflicts}:null,
  video_metadata:vm?{observed_sources:vm.observedSources,duration_known:vm.durationKnown,size_exact:vm.sizeExact,size_estimated:vm.sizeEstimated,duration_unknown:vm.durationUnknown,size_unknown:vm.sizeUnknown}:null,
  causality_claimed:false,raw_text_included:false};
}
export function compareTestLabs(left,right){
 ensure(left&&right&&left.test_type===right.test_type,'DIFFERENT_TEST_TYPES');
 const invariant=right.observations.filter(o=>o.kind==='CONFIRMED_INVARIANT_FAILURE').length;
 const comparable=left.status==='COMPLETED'&&right.status==='COMPLETED'&&!left.export_truncated&&!right.export_truncated&&left.android_window_frames>=250&&right.android_window_frames>=250;
 const delta=left.window_jank_percent!=null&&right.window_jank_percent!=null
   ?Math.round((right.window_jank_percent-left.window_jank_percent)*100)/100:null;
 const regression_candidate=comparable&&delta!=null&&delta>=3;
 return {test_type:left.test_type,comparable,window_jank_delta_points:delta,
   potential_performance_regression:regression_candidate,
   confirmed_invariant_failures_in_new_report:invariant,
   status:!comparable?'INSUFFICIENT_COMPARABILITY':regression_candidate?'REVIEW_MEASURED_ANOMALY':'NO_JANK_THRESHOLD_CROSSED',
   confidence_note:'CORRELATION_NOT_CAUSATION; ANDROID_WINDOW_ONLY_NOT_GECKO_COMPOSITOR',
   fixes_applied:false};
}
export class GithubReader{
 constructor(token=null,transport=fetch){this.token=token;this.transport=transport;}
 async get(pathname){
  ensure(/^\/pulls\/(?:8[4589])(?:\/files\?per_page=100)?$/.test(pathname),'API_PATH_FORBIDDEN');
  let response;
  const headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'};
  if(this.token)headers.Authorization='Bearer '+this.token;
  try{response=await this.transport('https://api.github.com/repos/'+REPO+pathname,{method:'GET',headers,redirect:'error',signal:AbortSignal.timeout(15000)});}catch{throw new InspectorError('API_UNAVAILABLE');}
  ensure(response.status===200,'API_HTTP_'+response.status);
  try{return await response.json();}catch{throw new InspectorError('API_INVALID_JSON');}
 }
}
export async function inspectLivePRs(api=new GithubReader(process.env.GH_TOKEN||null)){
 const prs=[];
 for(const number of [84,85,88,89]){
  const [data,files]=await Promise.all([api.get('/pulls/'+number),api.get('/pulls/'+number+'/files?per_page=100')]);
  ensure(data.number===number&&Array.isArray(files)&&files.length<100,'PR_PAGINATION_OR_MISMATCH');
  const item={number,state:data.state,draft:data.draft,
   head:{sha:data.head?.sha,ref:data.head?.ref,repo:{full_name:data.head?.repo?.full_name}},
   base:{sha:data.base?.sha,ref:data.base?.ref,repo:{full_name:data.base?.repo?.full_name}},files};
  prs.push(item);
 }
 return inspectCodexFleet(prs);
}
async function readLocalReport(file){
 ensure(typeof file==='string'&&file.length>0&&file.length<=4096,'INVALID_FILE');
 const info=await fs.lstat(file).catch(()=>{throw new InspectorError('FILE_UNAVAILABLE');});
 ensure(info.isFile()&&!info.isSymbolicLink()&&info.size<=280000&&info.size>0,'FILE_NOT_SAFE');
 return inspectTestLab(await fs.readFile(file,'utf8'));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const args=process.argv.slice(2);
 (async()=>{
  let report;
  if(args.length===1&&args[0]==='audit-prs')report=await inspectLivePRs();
  else if(args.length===2&&args[0]==='testlab')report=await readLocalReport(args[1]);
  else if(args.length===3&&args[0]==='compare-testlab'){
    const a=await readLocalReport(args[1]);const b=await readLocalReport(args[2]);
    report=compareTestLabs(a,b);
  }else throw new InspectorError('USAGE: audit-prs | testlab FILE | compare-testlab OLD NEW');
  console.log(JSON.stringify(report,null,2));
 })().catch(e=>{console.error('ZAVRO_INSPECTOR '+(e instanceof InspectorError?e.code:'FAILED'));process.exitCode=1;});
}
