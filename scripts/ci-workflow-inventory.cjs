'use strict';
const fs = require('node:fs'), path = require('node:path');
const dir = path.join(__dirname, '../.github/workflows');
function inventory() {
  return fs.readdirSync(dir).filter(n => /\.ya?ml$/.test(n)).sort().map(file => {
    const text = fs.readFileSync(path.join(dir, file),'utf8');
    const triggers = text.split(/^jobs:/m)[0];
    const events = [...triggers.matchAll(/^ {2}(push|pull_request|workflow_dispatch|workflow_call|workflow_run|schedule|repository_dispatch):/gm)].map(m=>m[1]);
    const assembly = /(?:\.\/gradlew|\bgradlew)[^\n]*(?:assembleDebug|assembleRelease)/.test(text);
    const signing = /secrets\.(?:ANDROID_KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_PASSWORD)/.test(text);
    return {file,events,assembly,signing,
      js: /node --test app\/src\/test\/js/.test(text),
      jvm: /testDebugUnitTest/.test(text),
      lint: /lintDebug/.test(text),
      androidTestCompile: /compileDebugAndroidTestKotlin/.test(text),
      reusableCaller:/uses:\s*\.\/\.github\/workflows\/zavro-tests-reusable\.yml/.test(text)};
  });
}
if (require.main === module) {
  const all=inventory();
  console.log(JSON.stringify({total:all.length,autoBuildCandidates:all.filter(w=>w.assembly&&w.events.some(x=>x==='push'||x==='pull_request')).map(w=>w.file),
    reusableCallers:all.filter(w=>w.reusableCaller).map(w=>w.file),workflows:all},null,2));
}
module.exports={inventory};
