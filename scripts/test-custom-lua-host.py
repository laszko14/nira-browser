#!/usr/bin/env python3
"""Real system Lua, synthetic mp API. Does not claim Android/GPU/DSP execution."""
import ctypes, ctypes.util, json, pathlib, tempfile
lib = ctypes.util.find_library('lua5.4') or ctypes.util.find_library('lua5.3')
if not lib: raise SystemExit('System Lua unavailable; NOT RUN')
lua = ctypes.CDLL(lib)
lua.luaL_newstate.restype=ctypes.c_void_p
lua.luaL_openlibs.argtypes=[ctypes.c_void_p]
lua.luaL_loadstring.argtypes=[ctypes.c_void_p,ctypes.c_char_p]
lua.lua_pcallk.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_longlong,ctypes.c_void_p]
lua.lua_tolstring.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_void_p];lua.lua_tolstring.restype=ctypes.c_char_p
lua.lua_close.argtypes=[ctypes.c_void_p]
template=(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/mpv/zavro_custom_client.lua').read_text()
setup='''
local callbacks, events, observers, timers, writes = {}, {}, {}, {}, {}
local report
local function clone(t) local r={} for k,v in pairs(t) do r[k]=v end return r end
mp={set_property_native=function(k,v) report=clone(v);writes[#writes+1]=report;return true end,
 register_script_message=function(k,v) callbacks[k]=v end,
 unregister_script_message=function(k) callbacks[k]=nil end,
 register_event=function(k,v) events[k]=v end,
 unregister_event=function(fn) for k,v in pairs(events) do if v==fn then events[k]=nil end end end,
 observe_property=function(k,fmt,v) observers[k]=v end,
 unobserve_property=function(fn) for k,v in pairs(observers) do if v==fn then observers[k]=nil end end end,
 add_timeout=function(s,fn) timers[#timers+1]=fn;return {kill=function() end} end,
 add_periodic_timer=function(s,fn) timers[#timers+1]=fn;return {kill=function() end} end,
 get_script_name=function() return 'synthetic' end}
package.preload['mp']=function() return mp end
'''
cases=[
 ('own native client identity and handler',"assert(mp.get_script_name()=='synthetic');mp.register_script_message('cycle',function() synthetic=42 end)","assert(report.loaded and report.cycle and report.handlers==1);callbacks.cycle();assert(synthetic==42)"),
 ('syntax failure without execution','local = invalid',"assert(report.syntax and not report.loaded and not report.runtime)"),
 ('load runtime failure is redacted',"error('synthetic private failure')","assert(not report.loaded and report.runtime and report.errors==1);assert(report.error==nil)"),
 ('callback runtime failure is redacted',"mp.register_script_message('x',function() error('synthetic secret') end)","callbacks.x();callbacks.zavro_custom_status('synthetic-session');assert(report.loaded and report.runtime and report.errors==1)"),
 ('multiple instructions execute only on callback',"mp.register_script_message('x',function() local a=2;synthetic=a+3 end)","assert(synthetic==nil);callbacks.x();assert(synthetic==5)"),
 ('rapid callbacks keep cumulative errors',"mp.register_script_message('x',function() error('test') end)","callbacks.x();callbacks.zavro_custom_status('synthetic-session');local first=report;callbacks.x();callbacks.zavro_custom_status('synthetic-session');assert(first.errors==1 and report.errors==2)"),
 ('callback return values preserve nil and arity',"mp.register_script_message('x',function() return 1,nil,3 end)","local v=table.pack(callbacks.x());assert(v.n==3 and v[1]==1 and v[2]==nil and v[3]==3)"),
 ('events properties and timer callbacks remain functional',"mp.register_event('file-loaded',function() synthetic=1 end);mp.observe_property('speed','number',function(_,v) synthetic=v end);mp.add_timeout(0,function() synthetic=3 end)","events['file-loaded']();assert(synthetic==1);observers.speed('speed',2);assert(synthetic==2);timers[1]();assert(synthetic==3)"),
 ('unregister preserves function identity',"local fn=function() end;mp.register_event('file-loaded',fn);mp.unregister_event(fn);mp.observe_property('speed','number',fn);mp.unobserve_property(fn)","assert(events['file-loaded']==nil and observers.speed==nil)"),
 ('handler replacement and unregister reflect actual registrations',"mp.register_script_message('cycle',function() end);mp.register_script_message('cycle',function() end);mp.register_script_message('off',function() end);mp.unregister_script_message('off')","assert(report.handlers==1 and report.cycle and not report.off)"),
 ('periodic errors are counted',"mp.add_periodic_timer(1,function() error('private') end)","timers[1]();callbacks.zavro_custom_status('synthetic-session');assert(report.errors==1 and report.runtime)"),
 ('status probes are correlated and callbacks never self-publish',"mp.register_script_message('x',function() error('test') end)","local n=#writes;callbacks.x();assert(#writes==n);callbacks.zavro_custom_status('old');assert(#writes==n);callbacks.zavro_custom_status('synthetic-session');assert(#writes==n+1 and report.errors==1)"),
 ('native client collision never executes source',"synthetic=99","assert(report.conflict and not report.loaded and synthetic==nil)"),
 ('status write failure is unconfirmed',"synthetic=1","assert(report==nil and synthetic==1)"),
 ('initial source executes once, not every status read',"synthetic=(synthetic or 0)+1;mp.register_script_message('x',function() return synthetic end)","callbacks.x();callbacks.x();assert(synthetic==1)"),
]
with tempfile.TemporaryDirectory() as directory:
 for name,source,check in cases:
  path=pathlib.Path(directory)/'source.lua';path.write_text(source)
  host=template.replace('@SOURCE@',json.dumps(str(path))).replace('@PROPERTY@',"'user-data/synthetic'").replace('@EPOCH@',"'synthetic-session'").replace('@CLIENT@',"'synthetic'")
  state=lua.luaL_newstate();lua.luaL_openlibs(state)
  try:
   prefix=setup
   if name=='native client collision never executes source': prefix+="mp.get_script_name=function() return 'synthetic2' end\n"
   if name=='status write failure is unconfirmed': prefix+="mp.set_property_native=function() return nil,'private failure' end\n"
   code=(prefix+'\ndo\n'+host+'\nend\n'+check).encode()
   status=lua.luaL_loadstring(state,code)
   if status==0: status=lua.lua_pcallk(state,0,0,0,0,None)
   if status: raise AssertionError(name+': '+(lua.lua_tolstring(state,-1,None) or b'Lua error').decode())
   print('PASS:',name)
  finally:lua.lua_close(state)
print(f'{len(cases)} custom client Lua tests passed (synthetic mp API, not Android).')
