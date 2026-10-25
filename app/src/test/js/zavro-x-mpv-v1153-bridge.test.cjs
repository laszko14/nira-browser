const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const root='app/src/main/java/com/prirai/android/nira/media/';
const read=p=>fs.readFileSync(root+p,'utf8');
test('bridge is prepared once before each existing owner initializes, never in transitions',()=>{
 const inline=read('InlineMpvUi.kt'),activity=read('ZavroMpvActivity.kt');
 assert.ok(inline.indexOf('video.prepareAlphaBridge(')<inline.indexOf('video.initialize('));
 assert.match(inline,/video.prepareAlphaBridge\(/);
 assert.ok(activity.indexOf('surface.prepareAlphaBridge(')<activity.indexOf('surface.initialize('));
 const transitions=inline.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.doesNotMatch(transitions,/prepareAlphaBridge|initialize\(|playFile\(|"loadfile"|mpv_alpha_probe/);
 assert.equal((inline.match(/MPVLib.addObserver\(/g)||[]).length,1);
 assert.match(inline,/alphaLua.onProperty/);
});
test('full Lua requires separate consent and reports only redacted correlated outcomes',()=>{
 const bridge=read('alpha/AlphaLuaBridge.kt');
 assert.match(bridge,/mpv_alpha_eval/);assert.match(bridge,/mpv_alpha_probe/);
 assert.match(bridge,/loadstring or load/);assert.match(bridge,/pcall\(fn\)/);
 assert.doesNotMatch(bridge,/mp\.osd_message|tostring\(err/);
 assert.match(bridge,/mp.msg.error\("ZAVRO_ALPHA_PUBLISH_FAILED"\)/);
 const panel=read('alpha/ui/AlphaLuaPanel.kt');
 assert.match(panel,/AlphaLuaExecutionSettings/);
 assert.match(panel,/config.state.value/);
 assert.doesNotMatch(panel,/MPVLib|onStartup/);
 const importer=read('alpha/AlphaSettingsImporter.kt');
 assert.doesNotMatch(importer,/AlphaLuaController|AlphaLuaConsent|mpv_alpha_eval/);
});
