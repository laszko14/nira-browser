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
