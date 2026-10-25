package com.prirai.android.nira.media.alpha.scripts

import android.content.Context
import java.io.File

internal object CustomLuaRepository {
    @Volatile private var instance:CustomLuaStore?=null
    fun importedCount():Int=instance?.state?.value?.scripts?.size?:0
    @Synchronized fun get(context:Context):CustomLuaStore = instance?:CustomLuaStore(
        File(context.applicationContext.filesDir,"alpha-custom-private-v1")).also {instance=it}
}
