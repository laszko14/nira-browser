'use strict';
const fs = require('node:fs');
const STATES = ['NOT_STARTED','ACTIVE','FOLLOWUP_PENDING','FOLLOWUP_COMPLETE'];
const SCOPES = ['UI_GESTURES','KOTLIN_API','JS_PROTOCOL','DATA_PRIVACY','GECKO','CI_TOOLING'];
const COMPLEXITY = ['LOW','MEDIUM','HIGH'];
const CAUSES = ['production','test_harness','workflow','environment','intentional_red','undetermined'];
const NONNEGATIVE_INTS = ['regressions','correction_rounds','user_interventions','avoidable_ci_runs',
  'acceptance_unverified','messages_count','messages_chars'];
const NONNEGATIVE_MINUTES = ['work_minutes','ci_window_minutes','wait_minutes'];
const FIELDS = [...NONNEGATIVE_INTS,...NONNEGATIVE_MINUTES,'reopened'];
const METRIC_KEYS = [...FIELDS,'correction_causes'];
const SLOTS = Array.from({length:16},(_,i)=>'T'+String(i+1).padStart(2,'0'));
function guard(ok,message){ if(!ok) throw Error('Pilot data: '+message); }
function iso(value) {return typeof value==='string' && !Number.isNaN(Date.parse(value)) &&
  new Date(value).toISOString()===value && value.endsWith('Z');}
// Replay data are prospective and separate from the unchanged T01–T16 observation cohort.
function validateInstructionExperiments(block){
  guard(block && typeof block==='object','missing instruction experiment registry');
  guard(Object.keys(block).sort().join()===
    ['method_id','status','max_total_runs','arms','trials'].sort().join(),
    'invalid instruction experiment schema');
  guard(block.method_id==='zavro_v3_instruction_ab_20261101','unknown instruction method');
  guard(['NOT_MEASURED','IN_PROGRESS','INCONCLUSIVE','COMPLETE'].includes(block.status),
    'invalid instruction status');
  guard(block.max_total_runs===32 && block.arms?.A==='V2-repaired' && block.arms?.B==='V3',
    'invalid arms or budget');
  guard(Array.isArray(block.trials) && block.trials.length<=32,'experiment run limit exceeded');
  const seen=new Set();
  for(const trial of block.trials){
    guard(trial && Object.keys(trial).sort().join()===
      ['trial_id','scenario_id','arm','source_sha','instructions_sha256','client',
       'model','effort','speed','status','result','first_result','metrics',
       'evidence_links'].sort().join(),'invalid trial fields');
    guard(typeof trial.trial_id==='string'&&/^[A-Z0-9-]{4,64}$/.test(trial.trial_id)
      &&!seen.has(trial.trial_id),'duplicate or invalid trial id');
    seen.add(trial.trial_id);
    guard(/^S0[1-8]$/.test(trial.scenario_id) && ['A','B'].includes(trial.arm),
      'invalid scenario or arm');
    guard(/^[0-9a-f]{40}$/.test(trial.source_sha) &&
      /^[0-9a-f]{64}$/.test(trial.instructions_sha256),
      'trial source/instructions identity required');
    guard(['client','model','effort','speed'].every(k=>typeof trial[k]==='string'&&trial[k].length>0),
      'trial environment identity missing');
    guard(['INTERRUPTED','COMPLETE'].includes(trial.status),'invalid trial status');
    guard(['PASS','FAIL','BLOCKED','INCONCLUSIVE'].includes(trial.result),'invalid trial result');
    guard(trial.first_result===trial.result,'first real result is immutable in this trial');
    guard(trial.metrics && Object.keys(trial.metrics).sort().join()===
      ['input_tokens','cached_input_tokens','output_tokens','actions_runner_minutes',
       'product_limit_units','avoidable_tool_calls'].sort().join(),
      'invalid trial metrics');
    for(const key of Object.keys(trial.metrics)){
      const value=trial.metrics[key];
      guard(value===null || (typeof value==='number'&&Number.isFinite(value)&&value>=0),
        'trial metric must be nonnegative or null');
    }
    guard(Array.isArray(trial.evidence_links)&&trial.evidence_links.length>0 &&
      trial.evidence_links.every(v=>typeof v==='string'&&/^https:\/\/github\.com\/laszko14\/nira-browser\/(?:commit\/[0-9a-f]{40}|pull\/[0-9]+|actions\/runs\/[0-9]+)$/.test(v)),
      'trial must have verified source links');
  }
  if(block.status==='NOT_MEASURED') guard(block.trials.length===0,'unmeasured registry must be empty');
  if(block.status==='COMPLETE'){
    guard(block.trials.length>=8,'completed comparison requires real paired trials');
    const a=block.trials.filter(t=>t.arm==='A'&&t.status==='COMPLETE').length;
    const b=block.trials.filter(t=>t.arm==='B'&&t.status==='COMPLETE').length;
    guard(a>=4&&b>=4&&a===b,'completed comparison requires balanced pairs');
  }
}
function validate(data) {
  const v2=data?.schema==='zavro.ai.pilot/2';
  guard(data && (data.schema==='zavro.ai.pilot/1'||v2) && data.target_tasks===16,
    'unknown schema or target');
  const topKeys=v2?['schema','target_tasks','method','records','instruction_experiments']:
    ['schema','target_tasks','method','records'];
  guard(Object.keys(data).sort().join()===topKeys.sort().join(),'unexpected top-level fields');
  if(v2) validateInstructionExperiments(data.instruction_experiments);
  guard(data.method==='consecutive_eligible_tasks_not_retrospective_fill','cohort must be prospective');
  guard(Array.isArray(data.records) && data.records.length===16,'exactly 16 slots required');
  guard(new Set(data.records.map(r=>r.slot)).size===16 &&
    data.records.every((r,i)=>r.slot===SLOTS[i]),'slots unique and ordered');
  let finished=0,started=0;
  for(const row of data.records) {
    guard(STATES.includes(row.state),row.slot+' invalid state');
    guard(Object.keys(row).sort().join() ===
      ['slot','state','task_ref','scope','complexity','started_at','completed_at',
      'followup_due_at','followup_checked_at','final_sha','evidence_links','metrics'].sort().join(),
      row.slot+' unsupported or missing fields');
    guard(row.metrics && Object.keys(row.metrics).sort().join()===METRIC_KEYS.slice().sort().join(),
      row.slot+' metrics fields mismatch');
    guard(row.metrics.correction_causes && Object.keys(row.metrics.correction_causes).sort().join()===
      CAUSES.slice().sort().join(),row.slot+' correction cause fields mismatch');
    for(const key of NONNEGATIVE_INTS) {
      const v=row.metrics[key];
      guard(v===null||(Number.isSafeInteger(v)&&v>=0),row.slot+' '+key+' invalid');
    }
    for(const key of NONNEGATIVE_MINUTES) {
      const v=row.metrics[key];
      guard(v===null||(typeof v==='number'&&Number.isFinite(v)&&v>=0),row.slot+' '+key+' invalid');
    }
    guard(row.metrics.reopened===null||typeof row.metrics.reopened==='boolean',
      row.slot+' reopened must be boolean or null');
    for(const key of CAUSES) {
      const v=row.metrics.correction_causes[key];
      guard(v===null||(Number.isSafeInteger(v)&&v>=0),row.slot+' '+key+' invalid');
    }
    guard(Array.isArray(row.evidence_links)&&row.evidence_links.every(link=>
      typeof link==='string' && /^https:\/\/github\.com\/laszko14\/nira-browser\/(?:actions\/runs\/[0-9]+|commit\/[0-9a-f]{40}|issues\/[0-9]+|pull\/[0-9]+)$/.test(link)),
      row.slot+' evidence must be a public repository URL');
    if(row.state==='NOT_STARTED') {
      guard(['task_ref','scope','complexity','started_at','completed_at','followup_due_at',
        'followup_checked_at','final_sha'].every(k=>row[k]===null) &&
        row.evidence_links.length===0 &&
        FIELDS.every(k=>row.metrics[k]===null) &&
        CAUSES.every(k=>row.metrics.correction_causes[k]===null),
        row.slot+' NOT_STARTED cannot contain measured data or invented zero');
      continue;
    }
    started++;
    guard(typeof row.task_ref==='string' && /^[A-Z0-9][A-Z0-9_-]{1,79}$/.test(row.task_ref),
      row.slot+' task_ref required');
    guard(SCOPES.includes(row.scope)&&COMPLEXITY.includes(row.complexity),
      row.slot+' risk and complexity required before start');
    guard(iso(row.started_at),row.slot+' started_at UTC required');
    if(row.state==='ACTIVE') {
      guard(row.completed_at===null && row.followup_due_at===null &&
        row.followup_checked_at===null && row.final_sha===null,
        row.slot+' active task has unearned completion');
      continue;
    }
    guard(iso(row.completed_at) && Date.parse(row.completed_at)>=Date.parse(row.started_at),
      row.slot+' completion timestamp invalid');
    guard(iso(row.followup_due_at) &&
      Date.parse(row.followup_due_at)-Date.parse(row.completed_at)>=14*86400000,
      row.slot+' observation window must be at least 14 days');
    guard(/^[0-9a-f]{40}$/.test(row.final_sha||''),row.slot+' final SHA missing');
    if(row.state==='FOLLOWUP_PENDING') {
      guard(row.followup_checked_at===null,row.slot+' pending followup has final observation');
      continue;
    }
    guard(iso(row.followup_checked_at) &&
      Date.parse(row.followup_checked_at)>=Date.parse(row.followup_due_at),
      row.slot+' follow-up cannot finish before observation window');
    guard(row.evidence_links.length>0,row.slot+' verified follow-up requires source links');
    guard(FIELDS.every(k=>row.metrics[k]!==null) &&
      CAUSES.every(k=>row.metrics.correction_causes[k]!==null),
      row.slot+' missing measures must remain incomplete, not zero');
    const sum=CAUSES.reduce((acc,k)=>acc+row.metrics.correction_causes[k],0);
    guard(sum===row.metrics.correction_rounds,row.slot+' categories do not sum to rounds');
    finished++;
  }
  return {started,finished};
}
function summarize(data) {
  const {started,finished}=validate(data);
  const cohort=data.records.filter(x=>x.state==='FOLLOWUP_COMPLETE');
  const totals=cohort.length?Object.fromEntries(NONNEGATIVE_INTS.map(k=>
    [k,cohort.reduce((a,r)=>a+r.metrics[k],0)])):null;
  return {status:finished===0?'NO_RESULTS_YET':finished<12?'PARTIAL_PILOT':'READY_FOR_MANUAL_REVIEW',
    sample_target:16,started,completed_14_day_followups:finished,
    totals_for_completed_followups_only:totals,
    improved_quality:'NOT_ESTABLISHED',
    baseline_comparability:'NOT_ESTABLISHED',
    device_verified:'NOT_EVALUATED',
    note:'Registry assertions require separate verification in GitHub/device evidence',
    instruction_experiments_status:data.schema==='zavro.ai.pilot/2'?
      data.instruction_experiments.status:'NOT_CONFIGURED'};
}
if(require.main===module){
  const file=process.argv[2]||'docs/ai/PILOT_16.json';
  try{
    const data=JSON.parse(fs.readFileSync(file,'utf8'));
    process.stdout.write(JSON.stringify(summarize(data),null,2)+'\n');
  }catch(err){process.stderr.write(err.message+'\n');process.exitCode=1;}
}
module.exports={validate,summarize,CAUSES,FIELDS};
