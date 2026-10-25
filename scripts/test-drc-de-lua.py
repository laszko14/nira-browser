#!/usr/bin/env python3
"""Shipped repair code in the real system Lua runtime; synthetic MP API, not Android."""
import ctypes, ctypes.util, pathlib, re, textwrap
root=pathlib.Path(__file__).resolve().parents[1]
source=(root/'app/src/main/assets/mpv/zavro_drc_de_repair.lua').read_text()
bridge=textwrap.dedent(re.search(r'val source:String = """\n(.*?)\n    """.trimIndent',(root/'app/src/main/java/com/prirai/android/nira/media/alpha/AlphaLuaBridge.kt').read_text(),re.S)[1])
lib=ctypes.CDLL(ctypes.util.find_library('lua5.4'))
lib.luaL_newstate.restype=ctypes.c_void_p
lib.luaL_openlibs.argtypes=[ctypes.c_void_p]
lib.luaL_loadstring.argtypes=[ctypes.c_void_p,ctypes.c_char_p]
lib.lua_pcallk.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_longlong,ctypes.c_void_p]
lib.lua_tolstring.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_void_p];lib.lua_tolstring.restype=ctypes.c_char_p
lib.lua_close.argtypes=[ctypes.c_void_p]
setup=r'''
local chain, messages, props, commands, callbacks = {}, {}, {}, {}, {}
local available={'lavfi','acompressor','dialoguenhance'}
local channels=2
local fail, readfail, mutate, throw, publishfail, restorefail, corrupt, lose=false,false,false,false,false,false,false,false
local function clone(x) if type(x)~='table' then return x end local y={} for k,v in pairs(x) do y[k]=clone(v) end return y end
mp={register_script_message=function(k,f) callbacks[k]=f end,msg={error=function() end},get_property_native=function(k)
 if k=='af' then if readfail then return nil end return clone(chain) end
 if k=='option-info/af/choices' then return available end
 if k=='audio-params/channel-count' then return channels end
 return clone(props[k])
end,
set_property_native=function(k,v) if publishfail then return nil,'private' end props[k]=clone(v); return true end,
osd_message=function(text,seconds) messages[#messages+1]=text;assert(seconds==2.5) end,
command_native=function(c)
 commands[#commands+1]=clone(c);assert(c[1]=='af');assert(c[2]=='add' or c[2]=='remove')
 if throw then error('synthetic private error') end
 if lose then chain={};lose=false;return nil,'error running command' end
 if fail or (restorefail and #commands>2) then fail=false;return nil,'error running command' end
 local label=c[3]:match('^@([^:]+)')
 local index
 for i,f in ipairs(chain) do if f.label==label then index=i end end
 if c[2]=='remove' then if index then table.remove(chain,index) end
 else
  local graph=assert(c[3]:match(':lavfi=%[(.*)%]$'))
  local item={name='lavfi',label=label,enabled=true,params={graph=graph}}
  if index then chain[index]=item else chain[#chain+1]=item end
  if corrupt then chain[#chain].params.graph='synthetic foreign takeover' end
 end
 if mutate then chain[#chain+1]={name='volume',label='other_writer',enabled=true,params={volume='0.8'}};mutate=false end
 -- Real mpv command_native of a void command returns nil WITHOUT error.
 return nil
end}
local function run(kind,action,id) return zavro_audio_repair(kind,action,id or 'synthetic-call') end
local function last() local r=props['user-data/zavro/drc-de-v1'];return r.results[#r.results] end
local function foreign() chain[#chain+1]={name='acompressor',label='user_compressor',enabled=true,params={ratio='7'}} end
'''
cases=[
('bridge ACK plus DSP receipt success round trip',"@BRIDGE@\ncallbacks.mpv_alpha_probe('session');callbacks.mpv_alpha_eval(\"local x=1;return zavro_audio_repair('drc','cycle','call-1')\",'session-1','session');assert(props['user-data/mpvex/zavro_alpha_bridge'].results[1].status=='ok');assert(last().request=='call-1' and last().status=='VERIFIED' and #messages==1)"),
('bridge EXEC_OK is NOT proof of accepted DSP',"@BRIDGE@\ncallbacks.mpv_alpha_probe('session');fail=true;callbacks.mpv_alpha_eval(\"return zavro_audio_repair('de','cycle','call-1')\",'session-1','session');assert(props['user-data/mpvex/zavro_alpha_bridge'].results[1].status=='ok');assert(last().status=='MPV_REJECTED' and #messages==0)"),
('handled Lua command exception is separate DSP error with bridge ACK',"@BRIDGE@\ncallbacks.mpv_alpha_probe('session');throw=true;callbacks.mpv_alpha_eval(\"return zavro_audio_repair('drc','cycle','call-1')\",'session-1','session');assert(props['user-data/mpvex/zavro_alpha_bridge'].results[1].status=='ok');assert(last().status=='LUA_EXCEPTION' and #messages==0)"),
('no execution on template load',"assert(#commands==0 and #messages==0 and next(props)==nil)"),
('DRC full repeated cycles no duplicates',"for i=1,20 do assert(run('drc','cycle'));assert(#chain==(i%4==0 and 0 or 1));assert(last().level==i%4) end"),
('DRC long press OFF',"assert(run('drc','cycle'));assert(run('drc','off'));assert(#chain==0 and last().level==0)"),
('DE five DISTINCT canonical levels and OFF',"local graphs={} for i=1,5 do assert(run('de','cycle'));graphs[chain[1].params.graph]=true;assert(last().level==i) end local n=0 for _ in pairs(graphs) do n=n+1 end assert(n==5);assert(run('de','cycle'));assert(#chain==0)"),
('DE long OFF',"assert(run('de','cycle'));assert(run('de','off'));assert(#chain==0)"),
('DRC + DE preserve foreign filters and order',"foreign();for i=1,12 do assert(run('drc','cycle'));assert(run('de','cycle'));assert(chain[1].label=='user_compressor') end assert(run('drc','off'));assert(run('de','off'));assert(#chain==1)"),
('foreign same label is conflict even identical graph',"run('drc','cycle');_G.__zavro_audio_repair_v1=nil;assert(not run('drc','cycle'));assert(last().status=='CONFLICT' and #chain==1)"),
('duplicate owned label is conflict',"run('de','cycle');chain[2]=clone(chain[1]);assert(not run('de','cycle'));assert(last().status=='CONFLICT' and #chain==2)"),
('foreign changed owned graph not overwritten',"run('drc','cycle');chain[1].params.graph='private foreign';assert(not run('drc','off'));assert(chain[1].params.graph=='private foreign')"),
('foreign extra parameters invalidate ownership',"run('drc','cycle');chain[1].params.o='foreign';assert(not run('drc','off'));assert(last().status=='CONFLICT' and #chain==1)"),
('unavailable compressor',"available={'lavfi','dialoguenhance'};assert(not run('drc','cycle'));assert(last().status=='UNSUPPORTED_FILTER' and #commands==0)"),
('unavailable DE',"available={'lavfi','acompressor'};assert(not run('de','cycle'));assert(last().status=='UNSUPPORTED_FILTER')"),
('unavailable capability query',"available=nil;assert(not run('de','cycle'));assert(last().status=='CAPABILITY_UNCONFIRMED')"),
('DE requires confirmed stereo no silent downmix',"channels=6;assert(not run('de','cycle'));assert(last().status=='AUDIO_FORMAT' and #commands==0)"),
('DE mono guarded',"channels=1;assert(not run('de','cycle'));assert(last().status=='AUDIO_FORMAT')"),
('bad AF readback no mutation',"readfail=true;assert(not run('drc','cycle'));assert(last().status=='AF_READ_FAILED' and #commands==0)"),
('rejected add retains old state',"run('drc','cycle');local graph=chain[1].params.graph;fail=true;assert(not run('drc','cycle'));assert(last().status=='MPV_REJECTED' and last().rollback=='VERIFIED');assert(chain[1].params.graph==graph)"),
('failed native reinitialisation loses own node and targeted rollback restores it',"run('drc','cycle');local graph=chain[1].params.graph;lose=true;assert(not run('drc','cycle'));assert(chain[1].params.graph==graph and last().rollback=='VERIFIED')"),
('rejected remove retains state',"run('de','cycle');fail=true;assert(not run('de','off'));assert(last().level==1 and #chain==1)"),
('foreign concurrent writer preserved with targeted rollback',"foreign();run('drc','cycle');local graph=chain[2].params.graph;mutate=true;assert(not run('drc','cycle'));assert(last().status=='CONFLICT');assert(chain[1].label=='user_compressor' and chain[2].params.graph==graph and chain[3].label=='other_writer')"),
('unconfirmed rollback never success',"run('drc','cycle');mutate=true;restorefail=true;assert(not run('drc','cycle'));assert(last().rollback=='UNCONFIRMED')"),
('unexpected native Lua exception distinguished',"throw=true;assert(not run('de','cycle'));assert(last().status=='LUA_EXCEPTION');assert(#messages==0)"),
('bad configuration rejected',"assert(not run('other','cycle'));assert(last().status=='BAD_CONFIGURATION' and #commands==0)"),
('no success OSD on failure',"fail=true;assert(not run('drc','cycle'));assert(#messages==0)"),
('verified OSD matches true level',"run('drc','cycle');assert(messages[1]=='DRC: 1/3');run('drc','off');assert(messages[2]=='DRC: WYŁĄCZONY')"),
('two rapid correlated results retained',"run('drc','cycle','call-1');run('de','cycle','call-2');local r=props['user-data/zavro/drc-de-v1'].results;assert(r[1].request=='call-1' and r[2].request=='call-2' and r[1].level==1)"),
('bounded mailbox excludes arbitrary errors and graphs',"for i=1,100 do run('drc','cycle','call-'..i) end local r=props['user-data/zavro/drc-de-v1'].results;assert(#r==64);for _,x in ipairs(r) do for k,v in pairs(x) do assert(k~='error' and k~='graph' and k~='code') end end"),
('publisher failure no repeat operation',"publishfail=true;run('drc','cycle');assert(#commands==1);assert(props['user-data/zavro/drc-de-v1']==nil)"),
('new engine has no inherited global state',"assert(_G.__zavro_audio_repair_v1==nil);run('drc','off');assert(#commands==0 and last().level==0)"),
('post-command native mismatch never EXEC DSP OK',"corrupt=true;assert(not run('drc','cycle'));assert(last().status=='AF_MISMATCH' and last().rollback=='UNCONFIRMED')"),
]
for label,check in cases:
 state=lib.luaL_newstate();lib.luaL_openlibs(state)
 try:
  code=(source+'\n'+setup+'\n'+check.replace('@BRIDGE@',bridge)).encode()
  status=lib.luaL_loadstring(state,code)
  if not status:status=lib.lua_pcallk(state,0,0,0,0,None)
  if status:raise AssertionError(label+': '+(lib.lua_tolstring(state,-1,None) or b'error').decode())
  print('PASS:',label)
 finally:lib.lua_close(state)
print(f'{len(cases)} real-Lua repair tests PASS; synthetic MP API, not Android.')
