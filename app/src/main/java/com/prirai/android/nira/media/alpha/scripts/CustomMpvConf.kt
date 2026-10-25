package com.prirai.android.nira.media.alpha.scripts

import java.io.InputStream

internal data class CustomConfPlan(val values:Map<String,String>,val skipped:Int,val limited:Int)
/** No includes/profiles, network/auth/TLS/script options, AF/VF, or arbitrary passthrough. */
internal object CustomMpvConf {
    private val booleans=setOf("cache","cache-pause","cache-pause-initial","osd-on-seek")
    fun parse(input:InputStream):CustomConfPlan {
        val text=CustomLuaStore.decode(CustomLuaStore.read(input,65536),65536).removePrefix("\uFEFF")
        val values=linkedMapOf<String,String>();val seen=mutableSetOf<String>();var skipped=0;var limited=0
        for(line in text.lines()) {
            val l=line.trim();if(l.isEmpty() || l.startsWith("#"))continue
            if(l.startsWith("[") || !l.contains('='))throw CustomImportException("INVALID_CONF")
            val key=l.substringBefore('=').trim();val raw=l.substringAfter('=').trim()
            if(!seen.add(key))throw CustomImportException("DUPLICATE_OPTION")
            val value=normalize(key,raw)
            if(value==null)skipped++ else {values[key]=value;if(raw!=value)limited++}
        }
        return CustomConfPlan(values,skipped,limited)
    }
    fun validate(values:Map<String,String>):Boolean=runCatching {
        values.size<=16 && values.all {(k,v)->normalize(k,v)==v}
    }.getOrDefault(false)
    private fun normalize(key:String,raw:String):String? {
        fun invalid():Nothing=throw CustomImportException("INVALID_OPTION")
        if(key in booleans) return raw.takeIf {it in setOf("yes","no")}?:invalid()
        return when(key) {
            "cache-secs","cache-pause-wait","network-timeout","speed" -> {
                val n=raw.toDoubleOrNull()?.takeIf {it.isFinite()}?:invalid()
                val range=when(key){"cache-secs"->1.0..300.0;"cache-pause-wait"->0.0..10.0;"network-timeout"->1.0..60.0;else->0.5..3.0}
                val v=n.coerceIn(range)
                if(v%1.0==0.0)v.toLong().toString() else v.toString()
            }
            "demuxer-max-bytes","demuxer-max-back-bytes" -> {
                val m=Regex("([0-9]+)(MiB|KiB|B)?").matchEntire(raw)?:invalid()
                val n=m.groupValues[1].toLongOrNull()?:invalid()
                val unit=when(m.groupValues[2]){"MiB"->1048576L;"KiB"->1024L;else->1L}
                if(n<=0 || n>Long.MAX_VALUE/unit)invalid()
                val max=if(key=="demuxer-max-bytes")256 else 64
                val bytes=(n*unit).coerceAtMost(max*1048576L)
                if(bytes%1048576L==0L)"${bytes/1048576L}MiB" else "${bytes}B"
            }
            "demuxer-seekable-cache" -> raw.takeIf {it in setOf("yes","no","auto")}?:invalid()
            "hwdec" -> raw.takeIf {it in setOf("no","mediacodec","mediacodec,no")}?:invalid()
            "video-sync" -> raw.takeIf {it=="audio"}?:invalid()
            else -> null
        }
    }
}
