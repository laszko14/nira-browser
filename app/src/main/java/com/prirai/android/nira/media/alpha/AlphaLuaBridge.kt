package com.prirai.android.nira.media.alpha

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Adapted from Alpha v54 MpvConfigSync.kt, c6107ce14a9f1768c73f975c5d60b1d8bd71aaed
 * (Apache-2.0). Same loadstring/load + pcall semantics; adds redacted, correlated ACKs.
 * Only the owner calls install, BEFORE BaseMPVView.initialize(configDir, cacheDir).
 */
internal object AlphaLuaBridge {
    const val REFERENCE_COMMIT="c6107ce14a9f1768c73f975c5d60b1d8bd71aaed"
    const val FILE_NAME="mpv_alpha_buttons_bridge.lua"
    const val PROPERTY="user-data/mpvex/zavro_alpha_bridge"

    /** Replace only our fixed file atomically. Never touch unrelated configuration/scripts. */
    fun install(configDir:File):Boolean {
        var temporary:File?=null
        return try {
            val scripts=File(configDir,"scripts")
            require(scripts.mkdirs() || scripts.isDirectory)
            require(scripts.canonicalFile==File(configDir.canonicalFile,"scripts"))
            val target=File(scripts,FILE_NAME)
            require(target.canonicalFile==File(scripts.canonicalFile,FILE_NAME))
            val bytes=(source+"\n").toByteArray(Charsets.UTF_8)
            if(target.isFile && target.length()==bytes.size.toLong() && target.readBytes().contentEquals(bytes))
                return true
            temporary=File.createTempFile("alpha-bridge-",".tmp",scripts)
            FileOutputStream(temporary).use { it.write(bytes);it.fd.sync() }
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_:Exception) { false }
        finally { runCatching { temporary?.delete() } }
    }

    // Bounded cumulative mailbox survives mpv property-event coalescing on fast clicks.
    // No exception/source text, OSD, log messages or user button IDs enter this protocol.
    val source:String = """
        local utils = require 'mp.utils'
        local epoch = ""
        local results = {}
        local function publish()
            pcall(mp.set_property, "user-data/mpvex/zavro_alpha_bridge",
                utils.format_json({version=1, epoch=epoch, ready=true, results=results}))
        end
        mp.register_script_message("mpv_alpha_probe", function(session)
            if type(session) ~= "string" or #session > 64 or not session:match("^[%w%-]+$") then return end
            if epoch ~= session then epoch=session; results={} end
            publish()
        end)
        mp.register_script_message("mpv_alpha_eval", function(source, id, session)
            if session ~= epoch or epoch == "" or type(id) ~= "string" or #id > 96
                or not id:match("^[%w%-]+$") then return end
            local status = "syntax"
            if type(source) == "string" and #source > 0 and #source <= 1048576 then
                local compile = loadstring or load
                local compiled, fn = pcall(compile, source, "MPVAlphaButton")
                if compiled and type(fn) == "function" then
                    local ok = pcall(fn)
                    status = ok and "ok" or "runtime"
                end
            end
            results[#results+1] = {id=id, status=status}
            if #results > 64 then table.remove(results, 1) end
            publish()
        end)
        publish()
    """.trimIndent()
}
