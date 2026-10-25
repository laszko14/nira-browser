package com.prirai.android.nira.media.alpha

import java.io.File
import com.prirai.android.nira.media.alpha.scripts.CustomLuaRuntime

/** config=no also disables mpv's ~~ lookup (options/path.c). Restore only the explicit
 * app config root for Lua, without loading mpv.conf or scanning any scripts directory.
 * Original source and the bridge ACK protocol remain untouched.
 */
internal object AlphaLuaEnvironment {
    fun source(config:File,property:String):String=template
        .replace("@CONFIG@",CustomLuaRuntime.quote(config.canonicalPath))
        .replace("@FEEDBACK@",CustomLuaRuntime.quote(property))
    const val BRIDGE_PROPERTY="user-data/zavro-feedback/bridge"
    val template:String="""
        do
            local mp = require('mp')
            local root = @CONFIG@
            local feedback_property = @FEEDBACK@
            local f = {sent=0, accepted=0, errors=0, paths=0, open_failed=0,
                opened=0, removed=0, shader_set=0, shader_failed=0, view_failed=0}
            mp._zavro_feedback = f
            local native_command = mp.command_native
            mp.command_native = function(args, ...)
                local name=type(args)=='table' and (args[1] or args.name)
                local text=type(args)=='table' and (args[2] or args.text)
                if name=='expand-path' and type(text)=='string' and text:sub(1,3)=='~~/' then
                    f.paths=f.paths+1
                    return root .. '/' .. text:sub(4)
                end
                return native_command(args, ...)
            end
            local commandv = mp.commandv
            mp.commandv = function(command, ...)
                if command ~= 'show-text' then return commandv(command, ...) end
                f.sent=f.sent+1
                local r=table.pack(pcall(commandv, command, ...))
                if r[1] and r[2]==true then f.accepted=f.accepted+1 else f.errors=f.errors+1 end
                if not r[1] then error(r[2],0) end
                return table.unpack(r,2,r.n)
            end
            local baseline = {}
            local view_keys = {keepaspect=true,panscan=true,['video-aspect-override']=true,
                ['video-zoom']=true,['video-pan-x']=true,['video-pan-y']=true,
                ['video-scale-x']=true,['video-scale-y']=true}
            local setter = mp.set_property_native
            mp.set_property_native = function(key,value,...)
                if view_keys[key] and baseline[key]==nil then
                    local ok,before=pcall(mp.get_property_native,key)
                    if ok and before~=nil then baseline[key]=before end
                end
                local r=table.pack(pcall(setter,key,value,...))
                if key=='glsl-shaders' then
                    if r[1] and r[2]==true then f.shader_set=f.shader_set+1 else f.shader_failed=f.shader_failed+1 end
                elseif type(key)=='string' and (key:match('^video%-') or key=='keepaspect' or key=='panscan') then
                    if not r[1] or r[2]~=true then f.view_failed=f.view_failed+1 end
                end
                if not r[1] then error(r[2],0) end
                return table.unpack(r,2,r.n)
            end
            local function owned(path)
                return type(path)=='string' and path:sub(1,#root+1)==root..'/'
                    and path:sub(#root+2):match('^rex_ambient_plus_v4_%d+_%d+%.glsl$') ~= nil
            end
            local open = io.open
            io.open = function(path,mode,...)
                local r=table.pack(open(path,mode,...))
                if owned(path) and mode=='wb' then
                    if r[1] then f.opened=f.opened+1 else f.open_failed=f.open_failed+1 end
                end
                return table.unpack(r,1,r.n)
            end
            local remove = os.remove
            os.remove = function(path,...)
                local r=table.pack(remove(path,...))
                if owned(path) and r[1]==true then f.removed=f.removed+1 end
                return table.unpack(r,1,r.n)
            end
            -- Only counters. Never retain OSD text, paths, source, or error strings.
            mp._zavro_collect_feedback = function()
                local found,restored=false,true
                for key,before in pairs(baseline) do
                    found=true
                    local ok,value=pcall(mp.get_property_native,key)
                    if not ok or value~=before then restored=false end
                end
                f.view_restored=found and restored
                return f
            end
            mp.register_script_message('zavro_feedback_probe',function()
                pcall(setter,feedback_property,mp._zavro_collect_feedback())
            end)
        end
    """.trimIndent()
}
