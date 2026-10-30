package com.prirai.android.nira.webapp

/**
 * Monotonic generation for PWA intents. URL/profile lookup, fragment install,
 * and task-description icon must all belong to the most recently accepted
 * request, even if earlier IO completes later.
 */
internal class WebAppLaunchGate {
    data class Ticket(val generation: Long, val url: String)
    private var generation = 0L

    fun accept(url: String): Ticket = Ticket(++generation, url)
    fun isCurrent(ticket: Ticket): Boolean = ticket.generation == generation
}
