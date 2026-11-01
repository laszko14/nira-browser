'use strict';
const fs = require('node:fs'), path = require('node:path');
const dir = path.join(__dirname, '../.github/workflows');
function inventory() {
  return fs.readdirSync(dir).filter(n => /\.ya?ml$/.test(n)).sort().map(file => {
    const text = fs.readFileSync(path.join(dir, file),'utf8');
    const triggers = text.split(/^jobs:/m)[0];
    const events = [...triggers.matchAll(/^ {2}(push|pull_request|pull_request_review|workflow_dispatch|workflow_call|workflow_run|schedule|repository_dispatch):/gm)].map(m=>m[1]);
    const assembly = /(?:\.\/gradlew|\bgradlew)[^\n]*(?:assembleDebug|assembleRelease)/.test(text);
    const signing = /secrets\.(?:ANDROID_KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_PASSWORD|KEY_ALIAS)/.test(text);
    return {file,events,assembly,signing,
      js: /node --test app\/src\/test\/js/.test(text),
      jvm: /testDebugUnitTest/.test(text),
      lint: /lintDebug/.test(text),
      androidTestCompile: /compileDebugAndroidTestKotlin/.test(text),
      reusableCaller:/uses:\s*\.\/\.github\/workflows\/zavro-tests-reusable\.yml/.test(text)};
  });
}
function enforceSingleSigningEntrypoint(all) {
  const canonical=all.filter(w=>w.file==='apk-release.yml');
  if (canonical.length!==1 || !canonical[0].signing || !canonical[0].assembly ||
      !canonical[0].events.includes('pull_request_review')) {
    throw new Error('Canonical signed workflow or reviewed authorization trigger is missing');
  }
  const legacy=all.filter(w=>w.file!=='apk-release.yml' && (w.signing || w.assembly));
  if (legacy.length) {
    throw new Error('Extra active APK/signing workflow(s): '+legacy.map(w=>w.file).join(', '));
  }
  return {signedWorkflow:canonical[0].file,activeLegacySigning:0};
}
if (require.main === module) {
  const all=inventory();
  const boundary=enforceSingleSigningEntrypoint(all);
  console.log(JSON.stringify({total:all.length,boundary,autoBuildCandidates:all.filter(w=>w.assembly&&w.events.some(x=>x==='push'||x==='pull_request')).map(w=>w.file),
    reusableCallers:all.filter(w=>w.reusableCaller).map(w=>w.file),workflows:all},null,2));
}
module.exports={inventory,enforceSingleSigningEntrypoint};
