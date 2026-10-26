'use strict';
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const layer=require('./v1165-testlab-patches.json');
const root='app/src/main/';
const kotlin=root+'java/com/prirai/android/nira/';
const expected=[root+'AndroidManifest.xml',root+'res/xml/preferences_headers.xml',
 ...['BrowserFragment','browser/SwipeGestureLayout','browser/bookmark/ui/BookmarksBottomSheetFragment',
 'browser/tabgroups/UnifiedTabGroupManager','browser/tabs/TabsBottomSheetFragment',
 'browser/tabs/compose/TabOrderManager','browser/tabs/compose/UnifiedDragSystem',
 'components/Components','components/menu/Material3BrowserMenu','components/toolbar/modern/ModernToolbarSystem',
 'components/toolbar/unified/UnifiedToolbar','media/InlineMpvUi','media/MediaDetectorDiagnostics','media/MediaFloatingUi',
 'media/MpvDebugTrace','media/MpvSupertest','media/ZavroMpvActivity','media/alpha/AlphaLuaConsent',
 'media/alpha/audio/AlphaDspSession','request/AppRequestInterceptor','settings/fragment/HeadersSettingsFragment'].map(x=>kotlin+x+'.kt')];
assert.equal(layer.baseCommit,'d890c68bd15d0f79e25ccd2935fb64ee955286b8');
assert.deepEqual(Object.keys(layer.files).sort(),expected.sort());
function reverseTestLab(path,bytes){
 const file=layer.files[path];if(!file)return bytes;
 let source=bytes.toString('utf8');
 for(const patch of file.patches.slice().reverse()){
  assert.equal(source.split(patch.after).length-1,1,`${path}: unique v1165 diagnostic hunk`);
  source=source.replace(patch.after,()=>patch.before);
 }
 assert.equal(crypto.createHash('sha256').update(source).digest('hex'),file.baseSha256,`${path}: exact v1164 base`);
 return source;
}
function addedLines(patch){
 const counts=new Map();for(const line of patch.before.split('\n').map(x=>x.trim()))counts.set(line,(counts.get(line)||0)+1);
 return patch.after.split('\n').map(x=>x.trim()).filter(line=>{
  const count=counts.get(line)||0;if(count){counts.set(line,count-1);return false;}return true;
 });
}
function verifyPassiveNativeScope(){
 for(const [path,file] of Object.entries(layer.files).filter(([path])=>path.includes('/media/'))){
  for(const line of file.patches.flatMap(addedLines)){
   assert.doesNotMatch(line,/\bMPVLib\s*\.\s*(?:command|get|set|observe|init|destroy|create|attach|detach)\w*\s*\(/,`${path}: diagnostics add no JNI polling/commands`);
   assert.doesNotMatch(line,/\bnew\s+SurfaceView|\bSurfaceView\s*\(|\.loadfile\s*\(|\.requestAudioFocus\s*\(/,`${path}: no new playback resource`);
  }
 }
}
module.exports={reverseTestLab,verifyPassiveNativeScope,layer};
