package com.prirai.android.nira.media.alpha

import android.content.Context

/** Named PRIVATE store, never browser/default preferences. No script interpreter or startup hook. */
internal object AlphaLuaRepository {
    private var instance:AlphaLuaConfig?=null
    @Synchronized fun get(context:Context):AlphaLuaConfig {
        instance?.let { return it }
        val prefs=context.applicationContext.getSharedPreferences("mpv_alpha_panel_v1",Context.MODE_PRIVATE)
        val storage=object:AlphaLuaStorage {
            override fun read():String?=prefs.getString("snapshot",null)
            override fun backup():String?=prefs.getString("previous_snapshot",null)
            override fun importBackup():String?=prefs.getString("pre_import_snapshot",null)
            override fun writeImport(value:String,before:String):Boolean {
                val old=prefs.getString("snapshot",null)
                val oldImport=prefs.getString("pre_import_snapshot",null)
                val oldPrevious=prefs.getString("previous_snapshot",null)
                val ok=prefs.edit().putString("snapshot",value)
                    .putString("previous_snapshot",before).putString("pre_import_snapshot",before).commit()
                if(!ok) {
                    // SharedPreferences changes its in-memory map before disk completion.
                    // Restore that map too; disk writes remain whole-file atomic.
                    prefs.edit().putString("snapshot",old).putString("pre_import_snapshot",oldImport)
                        .putString("previous_snapshot",oldPrevious).commit()
                }
                return ok
            }
            override fun restoreImport(value:String):Boolean {
                val old=prefs.getString("snapshot",null)
                val before=prefs.getString("pre_import_snapshot",null)
                val previous=prefs.getString("previous_snapshot",null)
                val ok=prefs.edit().putString("snapshot",value).putString("previous_snapshot",old)
                    .remove("pre_import_snapshot").commit()
                if(!ok)prefs.edit().putString("snapshot",old).putString("pre_import_snapshot",before)
                    .putString("previous_snapshot",previous).commit()
                return ok
            }
            override fun write(value:String):Boolean {
                val old=prefs.getString("snapshot",null)
                val editor=prefs.edit().putString("snapshot",value)
                // Keep one previous revision, including corrupt data, for recovery.
                if(old!=null) {
                    if(runCatching { AlphaLuaConfig.decode(old) }.isSuccess)
                        editor.putString("previous_snapshot",old)
                    else editor.putString("corrupt_snapshot",old)
                }
                return editor.commit()
            }
        }
        return AlphaLuaConfig(storage).also { instance=it }
    }
}
