const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const root='app/src/main/java/com/prirai/android/nira/media/';
const read=p=>fs.readFileSync(root+p,'utf8');
test('original Lua renderer retains passive holes, dual regions and no interpreter',()=>{
 const grid=read('alpha/ui/LuaButtonGrid.kt');
 assert.match(grid,/Spacer\(boundModifier\)/);
 assert.match(grid,/LUA_EXTRA_START until/);
 assert.match(grid,/LuaMinimalSoftTile/);
 assert.match(grid,/detectDragGesturesAfterLongPress/);
 const host=read('alpha/ui/AlphaLuaPanel.kt');
 assert.match(host,/LuaButtonsInPlayerDialog/);
 assert.doesNotMatch(host,/MPVLib|eval|loadfile|onStartup/);
 const ui=read('InlineMpvUi.kt');
 assert.equal((ui.match(/video.initialize\(/g)||[]).length,1);
 assert.equal((ui.match(/video.playFile\(/g)||[]).length,1);
});
test('empty playback cells have no input handler and multi-pointer reservation cancels old gestures',()=>{
 const grid=read('alpha/ui/LuaButtonGrid.kt');
 const visible=grid.split('val visible =')[1].split('\n')[0];
 assert.doesNotMatch(visible,/\|\| emptySlots == 2/);
 assert.match(grid,/if \(editMode\) Modifier.pointerInput\(index\)/);
 const ui=read('InlineMpvUi.kt');
 assert.match(ui,/cancel.action=MotionEvent.ACTION_CANCEL/);
 assert.match(ui,/ACTION_DOWN\) luaGesture=false/);
});
test('all twenty original font resources are present as real fonts',()=>{
 const dir='app/src/main/res/font';
 const files=fs.readdirSync(dir).filter(n=>n.startsWith('lua_')&&n.endsWith('.ttf'));
 assert.equal(files.length,20);
 for(const name of files) {
   const b=fs.readFileSync(dir+'/'+name);
   assert.ok(b.length>1000,name);
   assert.ok(b.subarray(0,4).equals(Buffer.from([0,1,0,0]))||b.subarray(0,4).toString()==='OTTO',name);
 }
});
