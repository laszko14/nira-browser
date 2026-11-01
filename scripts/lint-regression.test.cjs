'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {loadBaseline,normalizeFile,parseLint,compareLint,summary}=require('./lint-regression.cjs');
const file='app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt';
const issue=(rule='UseKtx',severity='Warning',f=file)=>
  '<issue id="'+rule+'" severity="'+severity+'"><location file="/home/runner/work/nira-browser/nira-browser/'+f+'" line="123"/></issue>';
const report=(...issues)=>'<issues format="6">'+issues.join('')+'</issues>';
test('frozen real report has 2152 known incidents and source path groups',()=>{
  const data=loadBaseline();
  assert.equal(Object.keys(data).length,124);
  assert.equal(data[file]['Warning:UseKtx'],1);
  assert.equal(data['app/src/main/res/values/strings.xml']['Error:MissingTranslation'],162);
  assert.equal(Object.values(data).reduce((n,rs)=>n+Object.values(rs).reduce((x,v)=>x+v,0),0),2152);
});
test('known issue passes even if file line changes',()=>{
  const x=parseLint(report(issue()));
  assert.deepEqual(compareLint(x),[]);
  assert.equal(x.totals.Warning,1);
  assert.match(summary(x,[]),/PASS/);
});
test('new rule or repeated old finding fails against baseline',()=>{
  const x=parseLint(report(issue('NewProblem')));
  assert.equal(compareLint(x)[0].baseline,0);
  const y=parseLint(report(issue(),issue()));
  assert.equal(compareLint(y)[0].delta,1);
  assert.match(summary(y,compareLint(y)),/FAIL/);
});
test('escalation from warning to error is never silently allowed',()=>{
  assert.equal(compareLint(parseLint(report(issue('UseKtx','Error'))))[0].baseline,0);
});
test('checkout path normalization cannot be bypassed by another root',()=>{
  assert.equal(normalizeFile('/home/runner/work/nira-browser/nira-browser/'+file),file);
  assert.throws(()=>normalizeFile('/tmp/other.kt'),/Unexpected lint file/);
  assert.throws(()=>normalizeFile('app/../other.kt'),/Unexpected lint file/);
});
test('stage 6: lint absolute paths cannot escape or impersonate checkout',()=>{
  const workspace='/home/runner/work/nira-browser/nira-browser';
  assert.equal(normalizeFile(workspace+'/'+file,workspace),file);
  assert.equal(normalizeFile(workspace+'/'+file,''),file);
  assert.throws(()=>normalizeFile(workspace+'/other/app/src/main/Fake.kt',workspace),/Unexpected lint file/);
  assert.throws(()=>normalizeFile(workspace+'/other/app/src/main/Fake.kt',''),/Unexpected lint file/);
  assert.throws(()=>normalizeFile('/tmp/nira-browser/'+file,workspace),/Unexpected lint file/);
  assert.throws(()=>normalizeFile('app/src/../other.kt',workspace),/Unexpected lint file/);
});
test('missing XML, invalid issue or unsupported severity fails closed',()=>{
  assert.throws(()=>parseLint(''),/Invalid lint report XML/);
  assert.throws(()=>parseLint(report('<issue id="X" severity="Error"></issue>')),/Incomplete lint issue/);
  assert.throws(()=>parseLint(report(issue('X','None'))),/Incomplete lint issue/);
  assert.throws(()=>parseLint(report('<issue id="X" severity="Error">')),/Unparsed lint issue/);
});
