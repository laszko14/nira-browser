const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const root='app/src/main/java/com/prirai/android/nira/media/';
const read=p=>fs.readFileSync(root+p,'utf8');
test('typed bridge mailbox uses native map and matching JNI NODE observer',()=>{
 const bridge=read('alpha/AlphaLuaBridge.kt'),inline=read('InlineMpvUi.kt');
 assert.match(bridge,/pcall\(mp.set_property_native/);
 assert.doesNotMatch(bridge,/utils.format_json|pcall\(mp.set_property,/);
 assert.match(inline,/observeProperty\(AlphaLuaBridge.PROPERTY,MPVLib.MpvFormat.MPV_FORMAT_NODE\)/);
 const node=inline.split('override fun eventProperty(property:String,value:MPVNode)')[1].split('override fun eventProperty')[0];
 assert.match(node,/value.toJson\(\)/);assert.match(node,/alphaLua.onProperty/);
});
test('only fixed bridge is configured before init, unrelated scripts never autoload',()=>{
 const view=read('ZavroMpvActivity.kt');
 assert.match(view,/setOptionString\("load-scripts","no"\)/);
 assert.match(view,/setOptionString\("config","no"\)/);
 assert.match(view,/setOptionString\("scripts",/);
 const ui=read('InlineMpvUi.kt');
 const transitions=ui.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.doesNotMatch(transitions,/initialize\(|playFile\(|prepareAlphaBridge|mpv_alpha_probe/);
 assert.equal((ui.match(/MPVLib.addObserver\(/g)||[]).length,1);
 const device=fs.readFileSync('app/src/androidTest/java/com/prirai/android/nira/media/AlphaLuaDeviceTest.kt','utf8');
 assert.match(device,/getPropertyNode/);
 assert.match(device,/MPV_FORMAT_NODE/);
 assert.match(device,/unrelated/);
});
