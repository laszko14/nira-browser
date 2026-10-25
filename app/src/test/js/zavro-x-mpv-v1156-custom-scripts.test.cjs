const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const base='app/src/main/java/com/prirai/android/nira/media/';
const read=p=>fs.readFileSync(base+p,'utf8');
test('explicit approved native clients share one engine, not button eval or directory autoload',()=>{
 const view=read('ZavroMpvActivity.kt'),inline=read('InlineMpvUi.kt');
 assert.match(view,/setOptionString\("config","no"\)/);assert.match(view,/setOptionString\("load-scripts","no"\)/);
 assert.match(view,/customRuntime\?\.paths/);assert.match(view,/File.pathSeparator/);
 const transition=inline.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.doesNotMatch(transition,/initialize\(|playFile\(|prepareAlphaBridge|CustomLuaRuntime/);
 assert.equal((inline.match(/MPVLib.addObserver\(/g)||[]).length,1);
 const runtime=read('alpha/scripts/CustomLuaRuntime.kt');assert.match(runtime,/store.snapshot\(\)/);
 assert.doesNotMatch(runtime,/MPVLib|loadfile|initialize\(|playFile\(/);
 const host=fs.readFileSync('app/src/main/assets/mpv/zavro_custom_client.lua','utf8');
 assert.match(host,/loadfile\(source_path\)/);assert.match(host,/set_property_native/);
 assert.doesNotMatch(host,/mpv_alpha_eval|rex_treble_5|rex_bass_5|rex_ambient_plus|onStartup/);
});
test('SAF import keeps ephemeral grants and private config has no Lua startup passthrough',()=>{
 const ui=read('alpha/ui/CustomLuaScriptsSettings.kt');
 assert.match(ui,/OpenMultipleDocuments/);assert.match(ui,/EXTRA_LOCAL_ONLY/);assert.match(ui,/revokeUriPermission/);
 assert.doesNotMatch(ui,/takePersistableUriPermission|MPVLib|initialize\(|playFile\(/);
 const conf=read('alpha/scripts/CustomMpvConf.kt');assert.doesNotMatch(conf,/"tls-verify"\s*->|"scripts"\s*->|"af"\s*->/);
 assert.match(ui,/store.enable\(row.id,true,true,row.hash\)/);
});
