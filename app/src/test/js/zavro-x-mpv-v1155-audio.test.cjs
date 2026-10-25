const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const root='app/src/main/java/com/prirai/android/nira/media/';
const read=p=>fs.readFileSync(root+p,'utf8');
test('audio uses the original EQ and readback without engine or whole-chain ownership',()=>{
 const audio=read('alpha/audio/AlphaAudioController.kt');
 assert.match(audio,/RexEqualizerAudio/);assert.match(audio,/rollback/i);
 assert.doesNotMatch(audio,/initialize\(|playFile\(|loadfile|MPVLib|onStartup/);
 const inline=read('InlineMpvUi.kt');
 assert.equal((inline.match(/MPVLib.addObserver\(/g)||[]).length,1);
 assert.match(inline,/audioController.refresh/);
 const transition=inline.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.doesNotMatch(transition,/audioController|initialize\(|playFile\(/);
 const native=read('alpha/audio/AlphaAudioNative.kt');
 assert.match(native,/getPropertyNode\("af"\)/);assert.match(native,/commandNode/);
 assert.doesNotMatch(native,/["']set["']/);
});
