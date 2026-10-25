#!/usr/bin/env python3
"""Real Lua tests for the config=no compatibility layer; synthetic mp API, not Android."""
import ctypes,ctypes.util,pathlib,re,textwrap,json,tempfile
root=pathlib.Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/com/prirai/android/nira/media/alpha/AlphaLuaEnvironment.kt').read_text()
source=textwrap.dedent(re.search(r'val template:String="""\n(.*?)\n    """.trimIndent',src,re.S)[1])
lib=ctypes.CDLL(ctypes.util.find_library('lua5.4') or ctypes.util.find_library('lua5.3'))
lib.luaL_newstate.restype=ctypes.c_void_p;lib.luaL_openlibs.argtypes=[ctypes.c_void_p]
lib.luaL_loadstring.argtypes=[ctypes.c_void_p,ctypes.c_char_p]
lib.lua_pcallk.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_longlong,ctypes.c_void_p]
lib.lua_tolstring.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_void_p];lib.lua_tolstring.restype=ctypes.c_char_p
lib.lua_close.argtypes=[ctypes.c_void_p]
setup='''local callbacks,props={},{};local nativeCalls=0;local duration
local fail=false;local mp={};_G.mp=mp
function mp.register_script_message(k,fn) callbacks[k]=fn end
function mp.command_native(a) nativeCalls=nativeCalls+1;return 'relative-path',nil,3 end
function mp.commandv(cmd,text,millis) duration=millis;if fail=='throw' then error('synthetic private') end;if fail then return nil,'synthetic private' end;return true end
function mp.set_property_native(k,v) if fail then return nil,'synthetic private' end props[k]=v;return true end
function mp.osd_message(text,seconds) mp.commandv('show-text',text,tostring(math.floor(seconds*1000))) end
package.preload.mp=function() return mp end
'''
cases=[
 ('tilde root restored despite no-config',"assert(mp.command_native({'expand-path','~~/script-opts/std.rnnn'})==root..'/script-opts/std.rnnn');assert(nativeCalls==0)"),
 ('named expand-path form preserves resource root',"assert(mp.command_native({name='expand-path',text='~~/shader.glsl'})==root..'/shader.glsl')"),
 ('other commands preserve native return tuple',"local v=table.pack(mp.command_native({'other'}));assert(v.n==3 and v[1]=='relative-path' and v[3]==3)"),
 ('script duration preserved and success counted',"mp.osd_message('synthetic secret',2.75);assert(duration=='2750');assert(mp._zavro_feedback.sent==1 and mp._zavro_feedback.accepted==1)"),
 ('failed OSD is not success',"fail=true;mp.osd_message('private',2);assert(mp._zavro_feedback.errors==1 and mp._zavro_feedback.accepted==0)"),
 ('native throw propagates without recording text',"fail='throw';assert(not pcall(mp.osd_message,'private',2));assert(mp._zavro_feedback.errors==1)"),
 ('status excludes text paths and exception values',"mp.osd_message('synthetic secret',2);callbacks.zavro_feedback_probe();for k,v in pairs(props['user-data/synthetic']) do assert(type(v)=='number' or type(v)=='boolean') end"),
 ('actual native shader setter failure classified',"fail=true;mp.set_property_native('glsl-shaders',{});assert(mp._zavro_feedback.shader_failed==1)"),
 ('rejected view option classified',"fail=true;mp.set_property_native('video-scale-x',2);assert(mp._zavro_feedback.view_failed==1)"),
 ('own shader file open and deletion counted',"local path=root..'/rex_ambient_plus_v4_123_1.glsl';local file=assert(io.open(path,'wb'));file:write('synthetic');file:close();assert(mp._zavro_feedback.opened==1);assert(os.remove(path));assert(mp._zavro_feedback.removed==1)"),
 ('view restore requires actual native values',"mp.get_property_native=function(k) return props[k] end;props['video-scale-x']=1;mp.set_property_native('video-scale-x',2);assert(not mp._zavro_collect_feedback().view_restored);mp.set_property_native('video-scale-x',1);assert(mp._zavro_collect_feedback().view_restored)"),
 ('foreign file handle identity and deletion preserved',"local p=root..'/foreign.glsl';local f=assert(io.open(p,'wb'));assert(io.type(f)=='file');f:write('keep');f:close();assert(mp._zavro_feedback.opened==0);os.remove(p);assert(mp._zavro_feedback.removed==0)"),
]
with tempfile.TemporaryDirectory() as d:
 for label,check in cases:
  state=lib.luaL_newstate();lib.luaL_openlibs(state)
  try:
   env=source.replace('@CONFIG@',json.dumps(d)).replace('@FEEDBACK@',"'user-data/synthetic'")
   code=(setup+'\nlocal root='+json.dumps(d)+'\n'+env+'\n'+check).encode()
   status=lib.luaL_loadstring(state,code)
   if not status:status=lib.lua_pcallk(state,0,0,0,0,None)
   if status:raise AssertionError(label+': '+(lib.lua_tolstring(state,-1,None) or b'error').decode())
   print('PASS:',label)
  finally:lib.lua_close(state)
print(f'{len(cases)} Lua environment tests PASS; synthetic mp API, not Android.')
