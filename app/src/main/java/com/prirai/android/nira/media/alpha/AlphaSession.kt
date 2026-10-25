package com.prirai.android.nira.media.alpha

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AlphaPlaybackState(
    val ready:Boolean=false,
    val positionSeconds:Double?=null,
    val durationSeconds:Double?=null,
    val speed:Double?=null,
    val paused:Boolean?=null,
)
internal sealed interface AlphaCommand {
    data class Pause(val paused:Boolean):AlphaCommand
    data class SeekAbsolute(val seconds:Double):AlphaCommand
    data class SeekRelative(val seconds:Double):AlphaCommand
    data class Speed(val value:Double):AlphaCommand
}
internal enum class AlphaCommandResult { APPLIED, UNAVAILABLE, INVALID, FAILED }

/** UI-facing session contract. No engine, file, surface, URL or lifecycle operations. */
internal interface AlphaSession {
    val state:StateFlow<AlphaPlaybackState>
    val lua:AlphaLuaController? get()=null
    fun execute(command:AlphaCommand):AlphaCommandResult
}

/** Fed by Zavro's existing observer. Never registers a second native observer. */
internal class AlphaSessionAdapter(
    private val canControl:()->Boolean,
    override val lua:AlphaLuaController?=null,
    private val backend:(AlphaCommand)->Unit,
):AlphaSession {
    private val mutableState=MutableStateFlow(AlphaPlaybackState())
    override val state=mutableState.asStateFlow()
    private var invalidated=false

    @Synchronized fun onReady() {
        if (!invalidated) mutableState.value=mutableState.value.copy(ready=true)
    }
    @Synchronized fun onNumber(property:String,value:Double) {
        if (invalidated || !value.isFinite()) return
        val old=mutableState.value
        mutableState.value=when {
            property=="time-pos" && value>=0 -> old.copy(positionSeconds=value)
            property=="duration" && value>=0 -> old.copy(durationSeconds=value)
            property=="speed" && value>0 -> old.copy(speed=value)
            else -> old
        }
    }
    @Synchronized fun onFlag(property:String,value:Boolean) {
        if (!invalidated && property=="pause")
            mutableState.value=mutableState.value.copy(paused=value)
    }
    @Synchronized fun invalidate() {
        invalidated=true
        mutableState.value=AlphaPlaybackState()
    }
    @Synchronized override fun execute(command:AlphaCommand):AlphaCommandResult {
        // Production callback checks owner, readiness and main thread at execution time.
        if (invalidated || !canControl()) return AlphaCommandResult.UNAVAILABLE
        val valid=when(command) {
            is AlphaCommand.Pause -> true
            is AlphaCommand.Speed -> command.value.isFinite() && command.value in .5..3.0
            is AlphaCommand.SeekRelative -> command.seconds.isFinite() && command.seconds in -600.0..600.0
            is AlphaCommand.SeekAbsolute -> command.seconds.isFinite() &&
                mutableState.value.durationSeconds?.let { it>0 && command.seconds in 0.0..it }==true
        }
        if (!valid) return AlphaCommandResult.INVALID
        return try {
            backend(command)
            // Native observer remains the source of truth; no optimistic state mutation.
            AlphaCommandResult.APPLIED
        } catch (_:Exception) { AlphaCommandResult.FAILED }
          catch (_:LinkageError) { AlphaCommandResult.FAILED }
    }
}
