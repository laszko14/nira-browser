const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const read=p=>fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/'+p,'utf8');
test('local SAF importer has preview, no persistent grants and no playback access',()=>{
 const ui=read('alpha/ui/AlphaSettingsImport.kt');
 assert.match(ui,/rememberLauncherForActivityResult/);
 assert.match(ui,/EXTRA_LOCAL_ONLY/);
 assert.match(ui,/openInputStream/);
 assert.doesNotMatch(ui,/takePersistableUriPermission|MPVLib|loadfile|onStartup/);
 assert.match(ui,/AlphaImportScope/);
 assert.match(ui,/applyImport/);
 const importer=read('alpha/AlphaSettingsImporter.kt');
 assert.doesNotMatch(importer,/MPVLib|Log\.|println|URL\(/);
 assert.match(importer,/setEntityResolver/);
});
test('HUD settings reach the original renderer; persistence has a separate import undo point',()=>{
 const hud=read('alpha/ui/AlphaConfiguredHud.kt');
 for(const key of ['scale_percent','font_weight_percent','outline_percent','layout_v2','position_x','position_y',
 'row_gap_dp','background_opacity','clock_color','show_icons']) assert.ok(hud.includes('alpha_hud_'+key),key);
 assert.match(hud,/HudBarContent/);
 assert.match(hud,/without\(HudElement.DOWNLOAD\)/);
 const store=read('alpha/AlphaLuaRepository.kt');
 assert.match(store,/pre_import_snapshot/);
 assert.match(store,/writeImport/);
 const config=read('alpha/AlphaLuaConfig.kt');
 assert.match(config,/if\(next==mutableState.value\)/);
 assert.match(config,/restoreBeforeImport/);
 assert.doesNotMatch(config,/MPVLib|Log\.|println/);
});
