package com.prirai.android.nira.diagnostics.testlab

import kotlinx.serialization.Serializable

@Serializable enum class LabDomain { UI, PANELS, TABS, GECKO, VIDEO, RESOURCES, STABILITY }
@Serializable enum class TestType {
    COMPLEX, PANELS_GESTURES, TABS_GROUPS, GECKO_PAGES, VIDEO_MPV, RESOURCES, STABILITY;
    fun includes(domain: LabDomain): Boolean = domain==LabDomain.UI || this==COMPLEX || when(this) {
        PANELS_GESTURES->domain==LabDomain.PANELS
        TABS_GROUPS->domain==LabDomain.TABS
        GECKO_PAGES->domain==LabDomain.GECKO
        VIDEO_MPV->domain==LabDomain.VIDEO
        RESOURCES->domain==LabDomain.RESOURCES
        STABILITY->domain==LabDomain.STABILITY
        COMPLEX->true
    }
}
@Serializable enum class LabPhase { IDLE, PREPARING, RUNNING, FINALIZING, COMPLETED, PARTIAL, CANCELLED, FAILED }
@Serializable enum class OperationOutcome { SUCCESS, CANCELLED, FAILED, TIMEOUT, INTERRUPTED, UNKNOWN }
@Serializable enum class Coverage { NOT_SELECTED, NOT_TESTED, ATTEMPTED, CANCELLED_ONLY, OBSERVED_COMPLETION }
@Serializable enum class ObservationKind { CONFIRMED_INVARIANT_FAILURE, OBSERVED_PERFORMANCE_ANOMALY, SUSPECTED_CORRELATION, INSUFFICIENT_DATA, NOT_TESTED }
@Serializable enum class Availability { MEASURED, MEASURED_WITH_GAPS, NOT_OBSERVED, INSUFFICIENT_SAMPLES, NOT_SUPPORTED_BY_API, PERMISSION_DENIED, COLLECTOR_FAILED, NOT_EXPOSED_BY_PLATFORM }
@Serializable enum class LabMetric { ANDROID_UI_FRAMES, FRAME_OVERRUN, JAVA_HEAP, NATIVE_HEAP, PROCESS_PSS, PROCESS_USS, PROCESS_CPU, MAIN_HEARTBEAT, GECKO_COMPOSITOR_FRAMES, MPV_PRESENTED_FRAMES, APP_ENERGY, DUAL_AUDIO }
@Serializable enum class MetricSource { JANKSTATS_WINDOW, JANKSTATS_API31, RUNTIME_HEAP, DEBUG_NATIVE_HEAP, DEBUG_MEMORY_INFO, PROCESS_CPU_TIME, MAIN_LOOPER_ACK, UNAVAILABLE }
@Serializable enum class LabOperation(val domain:LabDomain,val needsAccept:Boolean=false) {
    LEFT_PANEL_OPEN(LabDomain.PANELS,true), LEFT_PANEL_CLOSE(LabDomain.PANELS,true),
    RIGHT_PANEL_OPEN(LabDomain.PANELS,true), RIGHT_PANEL_CLOSE(LabDomain.PANELS,true),
    PANEL_SCROLL(LabDomain.PANELS), PANEL_RESIZE(LabDomain.PANELS), GROUP_DRAG(LabDomain.TABS),
    GROUP_EXPAND(LabDomain.TABS), GROUP_COLLAPSE(LabDomain.TABS), GROUP_CREATE(LabDomain.TABS),
    GROUP_MOVE(LabDomain.TABS), TAB_OPEN(LabDomain.TABS), LINK_OPEN(LabDomain.TABS),
    TAB_SWITCH(LabDomain.TABS), TAB_CLOSE(LabDomain.TABS), TAB_UNDO(LabDomain.TABS), TAB_MOVE(LabDomain.TABS),
    ORDER_SAVE(LabDomain.TABS), PROFILE_SWITCH(LabDomain.TABS), PRIVATE_SWITCH(LabDomain.TABS), SESSION_RESTORE(LabDomain.TABS),
    PAGE_LOAD(LabDomain.GECKO), HISTORY_NAVIGATION(LabDomain.GECKO), PAGE_SCROLL(LabDomain.GECKO),
    HANDOFF_ACK(LabDomain.VIDEO), MPV_PLAYBACK(LabDomain.VIDEO), MPV_INLINE(LabDomain.VIDEO), MPV_FULLSCREEN(LabDomain.VIDEO),
    TOOLBAR_HIDE(LabDomain.UI), TOOLBAR_SHOW(LabDomain.UI), MENU_OPEN(LabDomain.UI), FAB_OPEN(LabDomain.UI),
    ORIENTATION_CHANGE(LabDomain.UI), KEYBOARD_CHANGE(LabDomain.UI);
    val tag:String = name.lowercase(java.util.Locale.ROOT)
}
@Serializable enum class LabCode {
    FOREGROUND, BACKGROUND, OP_START, OP_ACCEPT, OP_FINISH, OP_CANCEL, USER_STUTTER,
    MENU_OPEN, INVARIANT_FAILURE, MAIN_STALL, SLOW_FRAME, COLLECTOR_ERROR,
    LOAD_START, LOAD_PROGRESS, LOAD_COMPLETE, LOAD_ERROR, LOAD_REDIRECT, SPA_NAVIGATION,
    ENGINE_SESSION_READY, ENGINE_SESSION_LOST, EXTENSION_READY, EXTENSION_FAILED,
    DETECTOR_BEFORE_PLAY, DETECTOR_AFTER_PLAY, DETECTOR_DOM, DETECTOR_NETWORK,
    DETECTOR_HLS, DETECTOR_DASH, DETECTOR_MP4, DETECTOR_OTHER, DETECTOR_DUPLICATE,
    DETECTOR_REJECTED, DETECTOR_STALE, DETECTOR_FRAME_MISMATCH, DETECTOR_QUEUE_LIMIT,
    AUTO_OFFER, PREPARE, ACK_OK, ACK_FAILED, ACK_TIMEOUT, ROLLBACK,
    FRAME_ELEMENT_MISMATCH, BLOB_MATCH, BLOB_MISSING, BLOB_AMBIGUOUS,
    MPV_START_FILE, MPV_FILE_LOADED, MPV_PLAYBACK_STARTED, MPV_CACHE_WAIT, MPV_CACHE_RESUME,
    MPV_RETRY, MPV_HTTP_ERROR, MPV_TLS_ERROR, MPV_POSITION_CHANGE, MPV_SPEED_CHANGE,
    MPV_INLINE, MPV_FULLSCREEN, MPV_RETURN, MPV_UNEXPECTED_RELOAD, AUDIO_FOCUS_FAILED,
    LUA_ERROR, DSP_ERROR, MEMORY_TRIM, ORIENTATION_CHANGED, REFRESH_RATE_CHANGED,
    KEYBOARD_SHOWN, KEYBOARD_HIDDEN, STABILITY_ERROR, PROCESS_EXIT,
}
@Serializable enum class LabError { INSTRUMENTATION_PROTOCOL, INVALID_SAMPLE, OPERATION_TIMEOUT, OPERATION_FAILED, COLLECTOR_FAILURE, EVENT_LIMIT, OPERATION_LIMIT }
@Serializable enum class LabInvariant { ORDER_NOT_PERSISTED, TAB_MEMBERSHIP_MISMATCH, UNREQUESTED_GROUP, SOURCE_GROUP_CHANGED, LINK_NOT_SOLO, UNDO_MISMATCH, PROFILE_MISMATCH, STALE_COMMIT, DUPLICATE_COMMIT, PANEL_STATE_MISMATCH }
@Serializable enum class Limitation { ANDROID_WINDOW_ONLY, NO_GECKO_COMPOSITOR, NO_MPV_PRESENTED_FRAMES, MAIN_PROCESS_RESOURCES_ONLY, NO_AUDIO_PATH_MEASUREMENT, NO_PRECISE_ENERGY, HISTOGRAM_PERCENTILES, NO_CRASH_DOES_NOT_PROVE_STABILITY, COVERAGE_GAPS, BOUNDED_TIMELINE, EXPORT_TRUNCATED }

/** Opaque, process-local tokens; these never represent a tab, URL or profile. */
data class SessionToken internal constructor(internal val serial:Long)
data class OperationToken internal constructor(internal val session:Long,internal val serial:Long)
@Serializable data class LabIdentity(val versionCode:Int,val versionName:String) {
    init {
        require(versionCode>0)
        require(versionName.length in 1..64) { "App version too long" }
        require(Regex("[0-9]+(?:\\.[0-9]+){1,3}-alpha[0-9]+-mpv[0-9]+").matches(versionName)) { "Unsupported app version format" }
    }
}
@Serializable data class LabEnvironment(val apiLevel:Int?=null,val logicalProcessors:Int?=null,val refreshRatesHz:List<Double> = emptyList()) {
    init {
        require(apiLevel==null||apiLevel in 21..200)
        require(logicalProcessors==null||logicalProcessors in 1..1024)
        require(refreshRatesHz.size<=16 && refreshRatesHz.all{it.isFinite()&&it in 1.0..1000.0})
    }
}
@Serializable data class LabEvent(val serial:Long,val offsetNanos:Long,val code:LabCode,val operation:LabOperation?=null,val value:Double?=null,val operationTags:List<LabOperation> = emptyList(),val count:Int=1)
@Serializable data class AnomalyMarker(val offsetNanos:Long,val nearbyEventSerials:List<Long>)
@Serializable data class Observation(val kind:ObservationKind,val code:LabCode,val offsetNanos:Long?,val value:Double?=null,val invariant:LabInvariant?=null)
@Serializable data class LatencySummary(val samples:Long,val meanMs:Double,val maxMs:Double,val p95Ms:Double?,val histogramStepMs:Double=1.0)
@Serializable data class OperationSummary(
    val operation:LabOperation,val started:Long,val accepted:Long,val succeeded:Long,val cancelled:Long,
    val failed:Long,val timedOut:Long,val interrupted:Long,val unknown:Long,val latency:LatencySummary?,
    val taggedFrames:Long,val taggedJank:Long,val maxTaggedFrameMs:Double?,val completionProofs:Map<CompletionProof,Long>,
)
@Serializable data class TestLabReport(
    val schemaVersion:Int=1,val testType:TestType,val appVersion:LabIdentity,val durationMs:Double,val status:LabPhase,
    val environment:LabEnvironment,val metricAvailability:Map<LabMetric,Availability>,val metricSources:Map<LabMetric,MetricSource>,
    val frameMetrics:FrameSummary?,val operationMetrics:List<OperationSummary>,val resourceMetrics:ResourceSummary,
    val errorCounts:Map<LabError,Long>,val eventCounts:Map<LabCode,Long>,val timeline:List<LabEvent>,
    val anomalyMarkers:List<AnomalyMarker>,val anomalies:List<Observation>,val testCoverage:Map<LabOperation,Coverage>,
    val foregroundMs:Double,val noCoverageMs:Double,val evictedEvents:Long,val droppedOperations:Long,
    val incompleteOperations:Int,val maxHeartbeatDelayMs:Double?,val heartbeatSamples:Long,
    val limitations:List<Limitation>,val exportTruncated:Boolean=false,
)
