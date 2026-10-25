const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const base='app/src/main/java/com/prirai/android/nira/media/';const read=p=>fs.readFileSync(base+p,'utf8');
test('DRC/DE fixes require explicit choice and durable originals; no startup action',()=>{
 const panel=read('alpha/ui/AlphaLuaPanel.kt'),ui=read('alpha/ui/AlphaAudioRepairSettings.kt');
 assert.match(panel,/AlphaAudioRepairSettings\(config,repairSource\)/);
 assert.match(ui,/Wybierz przycisk i slot/);assert.match(ui,/source.restore\(it,b\)/);
 assert.match(ui,/config.update/);assert.doesNotMatch(ui,/dispatch\(|execute\(|MPVLib|onStartup\s*=/);
 const revision=read('alpha/audio/AlphaAudioRepair.kt');assert.match(revision,/audioRepairs/);
 assert.doesNotMatch(revision,/title\s*==|title\.contains|onStartup\s*=/);
});
test('repair uses same bridge, nonce ACK and native AF checks without engine work',()=>{
 const lua=fs.readFileSync('app/src/main/assets/mpv/zavro_drc_de_repair.lua','utf8');
 assert.match(lua,/option-info\/af\/choices/);assert.match(lua,/audio-params\/channel-count/);
 assert.match(lua,/pcall\(mp.command_native/);assert.match(lua,/local before=read\(\)/);assert.match(lua,/local after=read\(\)/);
 assert.doesNotMatch(lua,/['"]af['"],\s*['"]set['"]|loadfile|playFile|initialize|onStartup|register_event/);
 const dsp=read('alpha/audio/AlphaDspSession.kt');assert.match(dsp,/!expected.ack/);
 assert.doesNotMatch(dsp,/addObserver|loadfile|playFile|initialize|setProperty|commandNode/);
 const panel=read('alpha/ui/AlphaLuaPanel.kt');assert.match(panel,/feedback.notify/);
 assert.match(panel,/dsp.bridgeResult\(request,it\)/);
});
