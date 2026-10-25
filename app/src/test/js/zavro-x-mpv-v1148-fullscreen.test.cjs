const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
test('fullscreen replaces controls only and preserves engine, surface and playback properties',()=>{
 const s=fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/InlineMpvUi.kt','utf8');
 const transitions=s.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.match(s,/buildFullscreenControls\(/);
 assert.match(s,/absolute\+exact/);
 assert.match(s,/postDelayed\(action,300L\)/);
 assert.match(s,/override fun onDoubleTap/);
 assert.match(s,/cancelHold\(\)/);
 assert.doesNotMatch(transitions,/initialize\(|playFile\(|command\("loadfile"|setPropertyBoolean|setPropertyDouble|startActivity|addView\(video/);
});
test('pause/speed are observed, temporary boost restores baseline, seek waits for release',()=>{
 const s=fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/InlineMpvUi.kt','utf8');
 assert.equal((s.match(/video.initialize\(/g)||[]).length,1);
 assert.equal((s.match(/video.playFile\(/g)||[]).length,1);
 assert.match(s,/observeProperty\("pause"/);
 assert.match(s,/observeProperty\("speed"/);
 assert.match(s,/setSpeed\(holdFrom\)/);
 assert.match(s,/holdFrom=speed/);
 assert.match(s,/dragging=true/);
 const seek=s.split('override fun onStopTrackingTouch')[1].split('fullscreenSeek=bar')[0];
 assert.match(seek,/absolute\+exact/);
 const transitions=s.split('private fun toggleFullscreen()')[1].split('fun detach()')[0];
 assert.doesNotMatch(transitions,/(?:speed|paused|lastPosition)\s*=/);
 assert.match(s,/!fullscreen && !destroyed/);
});
