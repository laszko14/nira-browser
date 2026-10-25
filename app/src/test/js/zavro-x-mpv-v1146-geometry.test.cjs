const test=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/extensions/nira-media-detector/playback-bridge.js','utf8');
const functions=source.slice(source.indexOf('  function rectOf('),source.indexOf('  function layoutUpdate('));
test('geometry exports visual viewport width and subtracts pinch-pan offset',()=>{
 const context={window:{devicePixelRatio:3,innerWidth:980,visualViewport:{width:392,offsetLeft:20,offsetTop:30}},Number};
 vm.createContext(context);vm.runInContext(functions,context);
 const r=context.rectOf({isConnected:true,getBoundingClientRect:()=>({left:40,top:80,width:900,height:506})});
 assert.equal(r.viewportWidth,392);assert.equal(r.x,20);assert.equal(r.y,50);
});
test('lost iframe geometry explicitly clears overlay rather than retaining stale rectangle',()=>{
 const functionSource=source.slice(source.indexOf('  function reportIframeGeometry('),source.indexOf('  function trackIframe('));
 const messages=[];const context={uniqueIframe:()=>null,rectOf:()=>null,send:v=>messages.push(v),protocolOneShot:()=>{}};
 vm.createContext(context);vm.runInContext(functionSource,context);context.reportIframeGeometry('lease','frame');
 assert.equal(messages.length,1);assert.equal(messages[0].w,0);
});
