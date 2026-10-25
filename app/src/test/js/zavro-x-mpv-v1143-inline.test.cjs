const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const read=p=>fs.readFileSync(p,'utf8');
test('v1143 verified inline libmpv, same player fullscreen, safe Gecko fallback',()=>{
 const js=read('app/src/main/assets/extensions/nira-media-detector/playback-bridge.js');
 const bridge=read('app/src/main/java/com/prirai/android/nira/media/MediaElementPlaybackBridge.kt');
 const frag=read('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt');
 const ui=read('app/src/main/java/com/prirai/android/nira/media/InlineMpvUi.kt');
 assert.match(js,/window === window.top/);
 assert.match(js,/inline_layout/);
 assert.match(bridge,/fun activateInline\(/);
 assert.match(bridge,/l.nonce == data.optString\("nonce"\)/);
 assert.match(frag,/launchInlineVideo\(uri,safeReferrer,offer\)/);
 assert.match(frag,/inline_frame_unsupported/);
 assert.match(ui,/video.playFile\(url\)/);
 assert.match(ui,/private fun toggleFullscreen\(/);
 assert.match(ui,/MpvTlsTrust.prepare\(context\)/);
 assert.doesNotMatch(ui,/EXTRA_TICKET_ID|tls-verify","no"/);
});
