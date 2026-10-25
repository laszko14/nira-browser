const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const base='app/src/main/java/com/prirai/android/nira/media/';const read=p=>fs.readFileSync(base+p,'utf8');
test('no floating host action bar, settings own one scroll-safe footer and IME adaptation',()=>{
 const panel=read('alpha/ui/AlphaLuaPanel.kt'),dialog=read('alpha/ui/LuaButtonsInPlayerDialog.kt');
 assert.doesNotMatch(panel,/Alignment.BottomEnd|Text\("Zapisz i zamknij"\)/);
 assert.match(panel,/AlphaFeedbackPolicy.immediate/);assert.match(panel,/AlphaFeedbackPolicy.completion/);
 assert.equal((dialog.match(/Text\("Zapisz i zamknij"\)/g)||[]).length,1);
 assert.match(dialog,/Modifier.weight\(1f\).verticalScroll/);assert.match(dialog,/WindowInsets.ime/);
 assert.match(dialog,/isDragHidden/);assert.match(dialog,/imePadding/);
});
test('native OSD and resource root restore do not change ownership or autoload',()=>{
 const view=read('ZavroMpvActivity.kt'),bridge=read('alpha/AlphaLuaBridge.kt');
 assert.match(view,/NativeOsd.options/);assert.match(view,/setOptionString\("config","no"\)/);
 assert.match(bridge,/AlphaLuaEnvironment.source/);
 const hud=read('alpha/ui/AlphaConfiguredHud.kt');assert.match(hud,/onGloballyPositioned/);
 const native=read('alpha/NativeOsd.kt');assert.match(native,/lua_roboto/);assert.match(native,/osd-fonts-dir/);
 const session=read('alpha/scripts/CustomScriptsSession.kt');
 assert.match(session,/AlphaFeedbackPolicy.clientStatus/);assert.match(session,/vo-passes/);assert.match(session,/current-vo/);
 assert.doesNotMatch(session,/initialize\(|playFile\(|loadfile/);
});
