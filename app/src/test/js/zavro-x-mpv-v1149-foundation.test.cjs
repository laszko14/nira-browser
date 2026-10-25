const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const read=p=>fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/'+p,'utf8');
test('session foundation never owns the engine; opt-in Lua uses the existing owner',()=>{
 const adapter=read('alpha/AlphaSession.kt');
 assert.doesNotMatch(adapter,/MPVLib|initialize\(|playFile\(|loadfile|destroy\(/);
 const bridge=read('alpha/AlphaLuaBridge.kt');
 assert.doesNotMatch(bridge,/MPVLib|playFile\(|"loadfile"/);
 assert.match(bridge,/fun install\(configDir:File\)/);
 const ui=read('InlineMpvUi.kt');
 assert.equal((ui.match(/video.initialize\(/g)||[]).length,1);
 assert.equal((ui.match(/video.playFile\(/g)||[]).length,1);
 assert.equal((ui.match(/MPVLib.addObserver\(/g)||[]).length,1);
 assert.match(ui,/MpvEngineGate.owns\(token\)/);
 assert.match(ui,/alphaAdapter.invalidate\(\)/);
 assert.match(ui,/alphaLua.close\(\)/);
 const transitions=ui.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.doesNotMatch(transitions,/alphaAdapter|alphaSession|initialize\(|playFile\(/);
});
