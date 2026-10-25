#!/usr/bin/env python3
"""Run the shipped Lua source using the system Lua library, synthetic MPV API only.
This verifies real compilation/execution, NOT Android libmpv integration.
For the latter run AlphaLuaDeviceTest on an ARM64 Android device without playback.
No dependencies installed, no network, no real user configuration.
"""
import ctypes
import ctypes.util
import pathlib
import re
import textwrap

path = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/java/com/prirai/android/nira/media/alpha/AlphaLuaBridge.kt'
source = textwrap.dedent(re.search(r'val source:String = """\n(.*?)\n    """.trimIndent', path.read_text(), re.S)[1])
name = ctypes.util.find_library('lua5.4') or ctypes.util.find_library('lua5.3')
if not name:
    raise SystemExit('System Lua 5.3/5.4 library unavailable; real-Lua test NOT RUN')
lua = ctypes.CDLL(name)
lua.luaL_newstate.restype = ctypes.c_void_p
lua.luaL_openlibs.argtypes = [ctypes.c_void_p]
lua.luaL_loadstring.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
lua.lua_pcallk.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_longlong, ctypes.c_void_p]
lua.lua_tolstring.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
lua.lua_tolstring.restype = ctypes.c_char_p
lua.lua_close.argtypes = [ctypes.c_void_p]
setup = """
local callbacks={}
local report
package.preload['mp.utils']=function() return {format_json=function(value) report=value; return '{}' end} end
mp={register_script_message=function(name, callback) callbacks[name]=callback end,
    set_property=function() end}
"""
cases = [
    ('registered without execution', "assert(callbacks.mpv_alpha_eval and callbacks.mpv_alpha_probe);assert(report.epoch=='');assert(#report.results==0)"),
    ('probe round trip', "callbacks.mpv_alpha_probe('session');assert(report.epoch=='session' and report.ready)"),
    ('multiple instructions and shared global environment', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('local x=2; x=x+3; synthetic=x','s-1','s');assert(synthetic==5);assert(report.results[1].status=='ok')"),
    ('syntax failure does not execute source', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('local = invalid','s-1','s');assert(report.results[1].status=='syntax')"),
    ('runtime failure is redacted', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval(\"error('synthetic secret')\",'s-1','s');assert(report.results[1].status=='runtime');assert(report.results[1].id=='s-1');assert(report.results[1].error==nil)"),
    ('two rapid commands retain separate acknowledgements', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('return 1','s-1','s');callbacks.mpv_alpha_eval('return 2','s-2','s');assert(#report.results==2);assert(report.results[2].id=='s-2')"),
    ('old session cannot execute', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('error(1)','s-1','old');assert(#report.results==0)"),
    ('new session clears previous results', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('return 1','s-1','s');callbacks.mpv_alpha_probe('next');assert(#report.results==0)"),
    ('repeated probe never reexecutes code', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('synthetic=(synthetic or 0)+1','s-1','s');callbacks.mpv_alpha_probe('s');assert(synthetic==1 and #report.results==1)"),
    ('bounded acknowledgement mailbox', "callbacks.mpv_alpha_probe('s');for i=1,80 do callbacks.mpv_alpha_eval('return 1','s-'..i,'s') end;assert(#report.results==64 and report.results[64].id=='s-80')"),
    ('empty or invalid code fails without crashing bridge', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval('','s-1','s');callbacks.mpv_alpha_eval(nil,'s-2','s');assert(report.results[1].status=='syntax' and report.results[2].status=='syntax')"),
    ('standard Lua require available with original unrestricted environment', "callbacks.mpv_alpha_probe('s');callbacks.mpv_alpha_eval(\"local m=require('math');synthetic=m.sqrt(81)\",'s-1','s');assert(synthetic==9 and report.results[1].status=='ok')"),
]
for label, case in cases:
    state = lua.luaL_newstate()
    if not state:
        raise SystemExit('Lua state unavailable')
    try:
        lua.luaL_openlibs(state)
        code = (setup + '\n' + source + '\n' + case).encode()
        result = lua.luaL_loadstring(state, code)
        if result == 0:
            result = lua.lua_pcallk(state, 0, 0, 0, 0, None)
        if result:
            raise AssertionError(label + ': ' + (lua.lua_tolstring(state, -1, None) or b'Lua failed').decode())
        print('PASS:', label)
    finally:
        lua.lua_close(state)
print(f'{len(cases)} real-Lua tests passed (synthetic MPV API; not Android).')
