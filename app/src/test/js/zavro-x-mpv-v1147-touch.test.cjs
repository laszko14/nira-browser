const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
test('inline touch router retains children until vertical drag and replays DOWN before MOVE',()=>{
 const s=fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/InlineMpvTouchLayout.kt','utf8');
 assert.match(s,/scaledTouchSlop/);
 assert.match(s,/override fun onInterceptTouchEvent/);
 assert.match(s,/forwardScreen\(down\)/);
 assert.match(s,/MotionEvent.ACTION_CANCEL/);
 assert.match(s,/offsetLocation\(-origin\[0\]/);
 assert.match(s,/engine.dispatchTouchEvent/);
});
test('fullscreen and teardown disable forwarding; compact click handlers remain',()=>{
 const s=fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/InlineMpvUi.kt','utf8');
 assert.match(s,/InlineMpvTouchLayout\(context,engine\) \{ !fullscreen && !destroyed \}/);
 assert.match(s,/overlay.cancelGesture\(\)/);
 assert.match(s,/player\?\.setOnClickListener \{ revealControls\(\) \}/);
 assert.match(s,/setOnClickListener \{ revealControls\(\);action\(\) \}/);
});

test('current diagnostics header and first intercepted MOVE are preserved',()=>{
 const touch=fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/InlineMpvTouchLayout.kt','utf8');
 const report=fs.readFileSync('app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt','utf8');
 assert.match(touch,/interceptedEvent=event/);
 assert.match(touch,/if \(event===interceptedEvent\) return true/);
 assert.match(report,/Zavro x MPV v1164 — SUPERTEST 40 s/);
 assert.doesNotMatch(report,/Zavro x MPV v1145/);
});
