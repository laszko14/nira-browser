package com.prirai.android.nira.diagnostics.testlab

import kotlinx.serialization.Serializable

@Serializable enum class LabDomain { UI, PANELS, TABS, GECKO, VIDEO, RESOURCES, STABILITY }
@Serializable enum class TestType {
    COMPLEX, PANELS_GESTURES, TABS_GROUPS, GECKO_PAGES, VIDEO_DISCOVERY, RESOURCES, STABILITY, VIDEO_METADATA, FULL_AUDIT;
    fun includes(domain: LabDomain): Boolean = domain==LabDomain.UI || this==COMPLEX || this==FULL_AUDIT || when(this) {
        PANELS_GESTURES->domain==LabDomain.PANELS
        TABS_GROUPS->domain==LabDomain.TABS
        GECKO_PAGES->domain==LabDomain.GECKO
        VIDEO_DISCOVERY, VIDEO_METADATA->domain==LabDomain.VIDEO
        RESOURCES->domain==LabDomain.RESOURCES
        STABILITY->domain==LabDomain.STABILITY
        COMPLEX, FULL_AUDIT->true
    }
}
@Serializable enum class LabPhase { IDLE, PREPARING, RUNNING, FINALIZING, COMPLETED, PARTIAL, CANCELLED, FAILED }
@Serializable enum class OperationOutcome { SUCCESS, CANCELLED, FAILED, TIMEOUT, INTERRUPTED, UNKNOWN }
@Serializable enum class Coverage { NOT_SELECTED, NOT_TESTED, ATTEMPTED, CANCELLED_ONLY, OBSERVED_COMPLETION }
@Serializable enum class LabUnit { MILLISECONDS, BYTES, COUNT }
@Serializable enum class ObservationKind { CONFIRMED_INVARIANT_FAILURE, OBSERVED_PERFORMANCE_ANOMALY, SUSPECTED_CORRELATION, INSUFFICIENT_DATA, NOT_TESTED }
@Serializable enum class Availability { MEASURED, MEASURED_WITH_GAPS, NOT_OBSERVED, INSUFFICIENT_SAMPLES, NOT_SUPPORTED_BY_API, PERMISSION_DENIED, COLLECTOR_FAILED, NOT_EXPOSED_BY_PLATFORM }
@Serializable enum class LabMetric {
    ANDROID_UI_FRAMES, FRAME_CPU_DURATION, FRAME_OVERRUN, JAVA_HEAP, NATIVE_HEAP, PROCESS_PSS, PROCESS_USS, PROCESS_CPU, MAIN_HEARTBEAT, MAIN_HEARTBEAT_PENDING_AGE, HISTORICAL_EXITS, SYSTEM_WINDOW_ANIMATION_END, POPUP_WINDOW_FRAMES, GECKO_COMPOSITOR_FRAMES, APP_ENERGY
}
@Serializable enum class MetricSource {
    JANKSTATS_WINDOW, JANKSTATS_API24, JANKSTATS_API31, RUNTIME_HEAP, DEBUG_NATIVE_HEAP, DEBUG_MEMORY_INFO, PROCESS_CPU_TIME, MAIN_LOOPER_ACK, ELAPSED_PENDING_MAIN_MESSAGE, APPLICATION_EXIT_INFO, UNAVAILABLE
}
@Serializable enum class LabOperation(val domain:LabDomain,val needsAccept:Boolean=false) {
    LEFT_PANEL_OPEN(LabDomain.PANELS,true), LEFT_PANEL_CLOSE(LabDomain.PANELS,true),
    RIGHT_PANEL_OPEN(LabDomain.PANELS,true), RIGHT_PANEL_CLOSE(LabDomain.PANELS,true),
    PANEL_SCROLL(LabDomain.PANELS), PANEL_RESIZE(LabDomain.PANELS), GROUP_DRAG(LabDomain.TABS),
    GROUP_EXPAND(LabDomain.TABS), GROUP_COLLAPSE(LabDomain.TABS), GROUP_CREATE(LabDomain.TABS),
    GROUP_MOVE(LabDomain.TABS), GROUP_DELETE(LabDomain.TABS), TAB_DRAG(LabDomain.TABS), TAB_OPEN(LabDomain.TABS), LINK_OPEN(LabDomain.TABS),
    TAB_SWITCH(LabDomain.TABS), TAB_CLOSE(LabDomain.TABS), TAB_UNDO(LabDomain.TABS), TAB_MOVE(LabDomain.TABS),
    ORDER_SAVE(LabDomain.TABS), PROFILE_SWITCH(LabDomain.TABS), PRIVATE_SWITCH(LabDomain.TABS), SESSION_RESTORE(LabDomain.TABS),
    PAGE_LOAD(LabDomain.GECKO), HISTORY_NAVIGATION(LabDomain.GECKO), PAGE_SCROLL(LabDomain.GECKO),
    VIDEO_PANEL(LabDomain.VIDEO), VIDEO_EXTERNAL_REQUEST(LabDomain.VIDEO), VIDEO_MENU(LabDomain.VIDEO),
    TOOLBAR_HIDE(LabDomain.UI), TOOLBAR_SHOW(LabDomain.UI), TOOLBAR_MINIMIZE(LabDomain.UI), TOOLBAR_RESTORE(LabDomain.UI), MENU_OPEN(LabDomain.UI), FAB_OPEN(LabDomain.UI),
    ORIENTATION_CHANGE(LabDomain.UI), KEYBOARD_CHANGE(LabDomain.UI);
    val tag:String = name.lowercase(java.util.Locale.ROOT)
}
@Serializable enum class LabCode {
    FOREGROUND, BACKGROUND, OP_START, OP_ACCEPT, OP_FINISH, OP_CANCEL, USER_STUTTER, MENU_OPEN, INVARIANT_FAILURE, MAIN_STALL, MAIN_STALL_PENDING, SLOW_FRAME, COLLECTOR_ERROR, LOAD_START, LOAD_PROGRESS, LOAD_COMPLETE, LOAD_ERROR, LOAD_REDIRECT, URL_CHANGE_IDLE, SPA_NAVIGATION, ENGINE_SESSION_READY, ENGINE_SESSION_LOST, EXTENSION_READY, EXTENSION_FAILED, DETECTOR_BEFORE_PLAY, DETECTOR_AFTER_PLAY, DETECTOR_DOM, DETECTOR_NETWORK, DETECTOR_HLS, DETECTOR_DASH, DETECTOR_MP4, DETECTOR_OTHER, DETECTOR_DUPLICATE, DETECTOR_REJECTED, DETECTOR_STALE, DETECTOR_FRAME_MISMATCH, DETECTOR_QUEUE_LIMIT, DETECTOR_ABOUT_BLANK, DETECTOR_SRCDOC, DETECTOR_EARLY_REQUEST, DETECTOR_EARLY_BOUND, DETECTOR_AMBIGUOUS, DETECTOR_NO_SOURCE, DETECTOR_MANIFEST_VALID, DETECTOR_SCAN_ELEMENTS, DETECTOR_SCAN_YIELD, DETECTOR_IFRAME, DETECTOR_FRAME_REGISTERED, DETECTOR_SPA_NAVIGATION, DETECTOR_PLAYER_CONFIG, DETECTOR_NO_CANDIDATE, DETECTOR_UNAVAILABLE, DETECTOR_TITLE_INDIVIDUAL, DETECTOR_TITLE_STRUCTURED, DETECTOR_TITLE_UNKNOWN, DETECTOR_QUEUE_NATIVE_CAPACITY, DETECTOR_QUEUE_NATIVE_STALE, DETECTOR_QUEUE_NATIVE_RETRY, DETECTOR_QUEUE_NATIVE_REJECT, DETECTOR_QUEUE_DOM_ROOTS, DETECTOR_QUEUE_EARLY_EVICT, DETECTOR_QUEUE_NETWORK_RATE, DETECTOR_QUEUE_MANIFEST_CAPACITY, DETECTOR_QUEUE_ANDROID_RATE, DETECTOR_QUEUE_TITLE_CANDIDATE_DROPPED, JAVA_HEAP_GROWTH, NATIVE_HEAP_GROWTH, PSS_GROWTH, GECKO_TLS_ERROR, GECKO_BLOCKED, MEMORY_TRIM, ORIENTATION_CHANGED, REFRESH_RATE_CHANGED, VIDEO_PANEL_OPEN, VIDEO_MENU_OPEN, VIDEO_EXTERNAL_REQUEST, KEYBOARD_SHOWN, KEYBOARD_HIDDEN, STABILITY_ERROR, PROCESS_EXIT, PROCESS_EXIT_STATUS, PROCESS_EXIT_HISTORY_READ
}
@Serializable enum class LabError { INSTRUMENTATION_PROTOCOL, INVALID_SAMPLE, OPERATION_TIMEOUT, OPERATION_FAILED, COLLECTOR_FAILURE, EVENT_LIMIT, OPERATION_LIMIT }
@Serializable enum class LabInvariant { ORDER_NOT_PERSISTED, TAB_MEMBERSHIP_MISMATCH, UNREQUESTED_GROUP, SOURCE_GROUP_CHANGED, EXISTING_TABS_REORDERED, LINK_NOT_SOLO, UNDO_MISMATCH, PROFILE_MISMATCH, STALE_COMMIT, DUPLICATE_COMMIT, PANEL_STATE_MISMATCH }
@Serializable enum class Limitation {
    ANDROID_WINDOW_ONLY, MULTIPLE_WINDOW_FRAMES, PREDRAW_NOT_WINDOW_ANIMATION_END, NO_POPUP_WINDOW_FRAMES, NO_GECKO_COMPOSITOR, MAIN_PROCESS_RESOURCES_ONLY, NO_PRECISE_ENERGY, HISTOGRAM_PERCENTILES, NO_CRASH_DOES_NOT_PROVE_STABILITY, COVERAGE_GAPS, BOUNDED_TIMELINE, EXPORT_TRUNCATED
}

/** Opaque, process-local tokens; these never represent a tab, URL or profile. */
data class SessionToken internal constructor(internal val serial:Long)
data class OperationToken internal constructor(internal val session:Long,internal val serial:Long)
@Serializable data class LabIdentity(val versionCode:Int,val versionName:String) {
    init {
        require(versionCode>0)
        require(versionName.length in 1..64) { "App version too long" }
        require(Regex("[0-9]+(?:\\.[0-9]+){1,3}-alpha[0-9]+-clean").matches(versionName)) { "Unsupported app version format" }
    }
}
@Serializable data class LabEnvironment(val apiLevel:Int?=null,val logicalProcessors:Int?=null,val refreshRatesHz:List<Double> = emptyList(),val currentRefreshHz:Double?=null,val requestedRefreshHz:Double?=null) {
    init {
        require(apiLevel==null||apiLevel in 21..200)
        require(logicalProcessors==null||logicalProcessors in 1..1024)
        require(refreshRatesHz.size<=16 && refreshRatesHz.all{it.isFinite()&&it in 1.0..1000.0})
        require(currentRefreshHz==null || currentRefreshHz.isFinite()&&currentRefreshHz in 1.0..1000.0)
        require(requestedRefreshHz==null || requestedRefreshHz.isFinite()&&requestedRefreshHz in 1.0..1000.0)
    }
}
@Serializable data class LabEvent(val serial:Long,val offsetNanos:Long,val code:LabCode,val operation:LabOperation?=null,val value:Double?=null,val operationTags:List<LabOperation> = emptyList(),val count:Int=1)
@Serializable data class AnomalyMarker(val offsetNanos:Long,val nearbyEventSerials:List<Long>)
@Serializable data class Observation(val kind:ObservationKind,val code:LabCode,val offsetNanos:Long?,val value:Double?=null,val invariant:LabInvariant?=null,val operation:LabOperation?=null,val unit:LabUnit?=null,
    val threshold:Double?=null,val evidenceCount:Long?=null)
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
    val maxPendingHeartbeatMs:Double?=null,val pendingHeartbeatSamples:Long=0,val finalizationDelayMs:Double=0.0,
    val videoDiscovery:VideoDiscoverySummary?=null,
    val videoMetadata:VideoMetadataSummary?=null,
    val mediaFunnel:MediaFunnelSummary?=null,
)

@Serializable data class VideoDiscoveryFilm(
    val film:Int, val variants:Int, val titleSource:String, val status:String, val groupingUncertain:Boolean,
)

/** Only sanitized aggregates and temporary ordinals enter text/JSON exports. */
@Serializable data class VideoDiscoverySummary(
    val sources:Int, val groups:Int, val groupsWithVariants:Int, val uncertainGroups:Int,
    val groupsWithoutTitle:Int, val groupTitleConflicts:Int,
    val initialTitles:Int, val improvements:Int, val rejectedOrConflictingTitles:Int,
    val weakerRejected:Int, val navigations:Int, val droppedSources:Int,
    val titleKinds:Map<String,Int>, val formats:Map<String,Int>, val detectionOrigins:Map<String,Int>,
    val examples:List<VideoDiscoveryFilm>,
    val uiObservedSources:Int = 0, val uiObservedTitledSources:Int = 0,
    // Extra aggregate diagnostics for comparison with rendered tile grouping.
    // Defaults preserve compatibility with older serialized TestLab reports.
    val uiObservedGroups:Int = 0, val uiObservedTitledGroups:Int = 0,
    val uiObservedConflictedGroups:Int = 0,
)


/** Sanitized temporary IDs, never exported with URL, title, tokens or profile. */
@Serializable data class VideoMetadataFilm(
    val film: Int, val format: String, val durationStatus: String,
    val durationSource: String, val sizeStatus: String, val sizeSource: String,
    val durationSeconds: Int?, val sizeBytes: Long?, val uiDisplayed: Boolean,
    val updates: Int,
)

@Serializable data class VideoMetadataSummary(
    val observedSources: Int, val durationKnown: Int, val sizeExact: Int,
    val sizeEstimated: Int, val live: Int, val durationUnknown: Int,
    val sizeUnknown: Int, val uiDisplayed: Int, val updates: Int,
    val navigations: Int, val droppedSources: Int,
    val examples: List<VideoMetadataFilm>,
    // Anonymous presence counters, not proof that a thumbnail loaded or a quality is playable.
    val titlesAssociated: Int = 0, val titlesFallback: Int = 0,
    val titleConflicts: Int = 0, val postersAssociated: Int = 0,
    val resolutionKnown: Int = 0, val qualityKnown: Int = 0,
    val qualityDerived: Int = 0, val fpsKnown: Int = 0,
    val codecsKnown: Int = 0, val bitrateKnown: Int = 0,
    val variantsKnown: Int = 0,
)
