package com.prirai.android.nira.diagnostics.testlab

import java.lang.ref.WeakReference

/** Passive adapter. No player, network, JS, filter or session-control APIs exist here. */
internal class TestLabMediaObserver(private val capture:LabCapture) {
    private data class Handoff(val owner:WeakReference<Any>,val operation:OperationToken,val ambiguous:Boolean=false)
    private val handoffs=ArrayList<Handoff>(16)
    private var player=WeakReference<Any>(null)
    private var playback:OperationToken?=null
    private var sampleOwner=WeakReference<Any>(null)
    private var previousPosition:Double?=null
    private var speedOwner=WeakReference<Any>(null)
    private var previousSpeed:Double?=null
    @Volatile private var closed=false
    val pendingHandoffs:Int @Synchronized get()=handoffs.size
    val waitingForPlayback:Boolean @Synchronized get()=playback!=null

    @Synchronized fun trace(code:String,owner:Any?) {
        if(closed)return
        val safe=traceCodes[code]?:return
        if(!capture.allows(safe.domain()))return
        capture.note(safe,httpStatus[code])
        when(code) {
            "prepare_sent"->if(owner!=null) {
                val overlapped=handoffs.any{it.owner.get()===owner}
                if(overlapped)capture.note(LabCode.HANDOFF_CORRELATION_AMBIGUOUS)
                handoffs.removeAll{entry->
                    if(entry.owner.get()==null||entry.owner.get()===owner){capture.end(entry.operation,OperationOutcome.INTERRUPTED);true}else false
                }
                if(handoffs.size>=16){capture.end(handoffs.removeAt(0).operation,OperationOutcome.INTERRUPTED);capture.error(LabError.OPERATION_LIMIT)}
                capture.operation(LabOperation.HANDOFF_ACK)?.let{handoffs.add(Handoff(WeakReference(owner),it,overlapped))}
            }
            "ack_ok","ack_failed","ack_timeout"->if(owner!=null) {
                val index=handoffs.indexOfFirst{it.owner.get()===owner}
                if(index>=0) {
                    val entry=handoffs.removeAt(index)
                    capture.end(entry.operation,if(entry.ambiguous)OperationOutcome.UNKNOWN else when(code){
                        "ack_ok"->OperationOutcome.SUCCESS;"ack_timeout"->OperationOutcome.TIMEOUT;else->OperationOutcome.FAILED
                    })
                }
            }
            "mpv_start_file"->if(owner!=null) {
                capture.end(playback,OperationOutcome.INTERRUPTED)
                player=WeakReference(owner);previousPosition=null
                playback=capture.operation(LabOperation.MPV_PLAYBACK)
            }
            "mpv_playback_started"->if(owner!=null) {
                if(player.get()!==owner){capture.end(playback,OperationOutcome.INTERRUPTED);player=WeakReference(owner);previousPosition=null;playback=null}
                val op=playback?:capture.operation(LabOperation.MPV_PLAYBACK,measureLatency=false)
                capture.end(op);playback=null
            }
            "mpv_timeout","mpv_end_before_load","mpv_auto_rollback","mpv_manual_error"->if(owner!=null&&player.get()===owner) {
                capture.end(playback,if(code=="mpv_timeout")OperationOutcome.TIMEOUT else OperationOutcome.FAILED);playback=null
            }
        }
    }
    @Synchronized fun position(owner:Any?,value:Double) {
        if(closed||owner==null||!capture.allows(LabDomain.VIDEO))return
        if(!value.isFinite()||value !in 0.0..9_007_199_254_740_991.0){capture.error(LabError.INVALID_SAMPLE);return}
        val newOwner=player.get()!==owner
        if(newOwner) {
            capture.end(playback,OperationOutcome.INTERRUPTED);player=WeakReference(owner);previousPosition=null
            playback=capture.operation(LabOperation.MPV_PLAYBACK,measureLatency=false)
        }
        val previous=previousPosition
        val difference=previous?.let{value-it}
        val increment=difference?.takeIf{it>0.0&&it<=5.0}
        val jump=difference!=null&&(difference<0.0||difference>5.0)
        val firstSampleFromOwner=sampleOwner.get()!==owner
        if(firstSampleFromOwner)sampleOwner=WeakReference(owner)
        capture.position(increment?:0.0,jump,firstSampleFromOwner)
        if(increment!=null) {
            capture.note(LabCode.MPV_POSITION_CHANGE,increment)
            capture.end(playback);playback=null
        } else if(jump)capture.note(LabCode.MPV_POSITION_DISCONTINUITY)
        previousPosition=value
    }
    @Synchronized fun speed(owner:Any?,value:Double) {
        if(closed||owner==null||!capture.allows(LabDomain.VIDEO))return
        if(!value.isFinite()||value !in 0.01..100.0){capture.error(LabError.INVALID_SAMPLE);return}
        val same=speedOwner.get()===owner
        val changed=same&&previousSpeed!=null&&value!=previousSpeed
        capture.speed(value,changed)
        if(!same||previousSpeed==null||changed)capture.note(if(changed)LabCode.MPV_SPEED_CHANGE else LabCode.MPV_SPEED_OBSERVED,value)
        if(!same)speedOwner=WeakReference(owner)
        previousSpeed=value
    }
    @Synchronized fun discovery(code:String,count:Int) {
        if(closed)return
        val safe=discoveryCodes[code]?:return
        capture.note(safe,count=count)
    }
    @Synchronized fun close() {
        closed=true
        handoffs.forEach{capture.end(it.operation,OperationOutcome.INTERRUPTED)};handoffs.clear()
        capture.end(playback,OperationOutcome.INTERRUPTED);playback=null
        player.clear();sampleOwner.clear();speedOwner.clear();previousPosition=null;previousSpeed=null
    }
    companion object {
        private val httpStatus=mapOf("mpv_http_401" to 401.0,"mpv_http_403" to 403.0,"mpv_http_404" to 404.0,"mpv_http_429" to 429.0,"mpv_http_5xx" to 500.0)
        private val traceCodes=mapOf(
            "extension_install_ok" to LabCode.EXTENSION_READY,"extension_install_failed" to LabCode.EXTENSION_FAILED,
            "extension_attach_failed" to LabCode.EXTENSION_FAILED,"session_handler_failed" to LabCode.EXTENSION_FAILED,
            "script_started" to LabCode.EXTENSION_READY,"auto_offered" to LabCode.AUTO_OFFER,
            "prepare_sent" to LabCode.PREPARE,"ack_ok" to LabCode.ACK_OK,"ack_failed" to LabCode.ACK_FAILED,"ack_timeout" to LabCode.ACK_TIMEOUT,
            "mpv_auto_rollback" to LabCode.ROLLBACK,"blob_candidate_found" to LabCode.BLOB_MATCH,
            "blob_candidate_missing" to LabCode.BLOB_MISSING,"blob_candidate_ambiguous" to LabCode.BLOB_AMBIGUOUS,
            "blob_frame_failed" to LabCode.FRAME_LOOKUP_FAILED,"inline_frame_unsupported" to LabCode.FRAME_UNSUPPORTED,
            "mpv_start_file" to LabCode.MPV_START_FILE,"mpv_file_loaded" to LabCode.MPV_FILE_LOADED,
            "mpv_playback_started" to LabCode.MPV_PLAYBACK_STARTED,"mpv_cache_wait" to LabCode.MPV_CACHE_WAIT,"mpv_cache_resume" to LabCode.MPV_CACHE_RESUME,
            "mpv_retry" to LabCode.MPV_RETRY,"mpv_retry_resume_attempt" to LabCode.MPV_RETRY,
            "mpv_http_401" to LabCode.MPV_HTTP_ERROR,"mpv_http_403" to LabCode.MPV_HTTP_ERROR,"mpv_http_404" to LabCode.MPV_HTTP_ERROR,
            "mpv_http_429" to LabCode.MPV_HTTP_ERROR,"mpv_http_5xx" to LabCode.MPV_HTTP_ERROR,
            "mpv_tls_error" to LabCode.MPV_TLS_ERROR,"mpv_tls_certificate_error" to LabCode.MPV_TLS_ERROR,
            "mpv_tls_handshake_error" to LabCode.MPV_TLS_ERROR,"mpv_tls_after_load" to LabCode.MPV_TLS_ERROR,
            "inline_open" to LabCode.MPV_INLINE,"inline_fullscreen" to LabCode.MPV_FULLSCREEN,"inline_return" to LabCode.MPV_RETURN,
            "inline_surface_visible" to LabCode.MPV_SURFACE_VISIBLE,"inline_surface_hidden" to LabCode.MPV_SURFACE_HIDDEN,
            "inline_position_preserved" to LabCode.MPV_POSITION_PRESERVED,
            "inline_unexpected_reload" to LabCode.MPV_UNEXPECTED_RELOAD,"mpv_audio_focus_failed" to LabCode.AUDIO_FOCUS_FAILED,
            "mpv_timeout" to LabCode.MPV_TIMEOUT,"mpv_end_before_load" to LabCode.MPV_END_BEFORE_LOAD,"mpv_manual_error" to LabCode.MPV_OPEN_FAILED,
        )
        private val discoveryCodes=mapOf(
            "before_play" to LabCode.DETECTOR_BEFORE_PLAY,"after_play" to LabCode.DETECTOR_AFTER_PLAY,
            "duplicate" to LabCode.DETECTOR_DUPLICATE,"native_rejected" to LabCode.DETECTOR_REJECTED,
            "queue_limit" to LabCode.DETECTOR_QUEUE_LIMIT,"frame_mismatch" to LabCode.DETECTOR_FRAME_MISMATCH,
            "spa_navigation" to LabCode.SPA_NAVIGATION,"iframe_about_blank" to LabCode.DETECTOR_ABOUT_BLANK,
            "iframe_srcdoc" to LabCode.DETECTOR_SRCDOC,"early_request_seen" to LabCode.DETECTOR_EARLY_REQUEST,
            "early_request_bound" to LabCode.DETECTOR_EARLY_BOUND,"early_request_expired" to LabCode.DETECTOR_STALE,
            "early_request_ambiguous" to LabCode.DETECTOR_AMBIGUOUS,"no_source" to LabCode.DETECTOR_NO_SOURCE,
            "manifest_invalid" to LabCode.DETECTOR_REJECTED,"manifest_valid" to LabCode.DETECTOR_MANIFEST_VALID,
        )
    }
}
