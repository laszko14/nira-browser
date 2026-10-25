// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

/**
 * Stable IDs are persisted instead of translated labels. A blank preference means that
 * the user has not yet customized the HUD and legacy per-item switches still apply.
 * The literal "|" represents an intentionally empty HUD.
 */
internal enum class HudElement(val id: String, val label: String) {
  BATTERY("battery", "Bateria"),
  CLOCK("clock", "Zegar"),
  ADJUSTED("adjusted", "Pozostało przy aktualnej prędkości"),
  REMAINING("remaining", "Pozostało przy 1×"),
  SPEED("speed", "Prędkość odtwarzania"),
  DOWNLOAD("download", "Pobieranie mpv"),
}

internal data class HudLayout(
  val first: List<HudElement>,
  val second: List<HudElement>,
) {
  val items: List<HudElement> get() = first + second

  fun encode(): String = listOf(first, second)
    .joinToString("|") { row -> row.joinToString(",") { it.id } }

  fun without(item: HudElement): HudLayout =
    HudLayout(first.filterNot { it == item }, second.filterNot { it == item })

  fun insert(item: HudElement, row: Int, index: Int = Int.MAX_VALUE): HudLayout {
    val current = without(item)
    val target = if (row == 1) current.second else current.first
    val at = index.coerceIn(0, target.size)
    val added = target.toMutableList().also { it.add(at, item) }
    return if (row == 1) current.copy(second = added) else current.copy(first = added)
  }

  fun move(item: HudElement, row: Int, displacement: Int): HudLayout {
    val target = if (row == 1) second else first
    val oldIndex = target.indexOf(item)
    if (oldIndex < 0) return this
    val newIndex = (oldIndex + displacement).coerceIn(0, target.lastIndex)
    if (newIndex == oldIndex) return this
    return insert(item, row, newIndex)
  }
}

internal fun parseHudLayout(raw: String): HudLayout? {
  if (raw.isBlank()) return null
  val sections = raw.split('|')
  if (sections.size != 2) return null
  val available = HudElement.entries.associateBy { it.id }
  val used = mutableSetOf<HudElement>()
  fun parse(section: String): List<HudElement> = section.split(',')
    .mapNotNull { available[it.trim().lowercase(java.util.Locale.ROOT)] }
    .filter { used.add(it) }
  return HudLayout(parse(sections[0]), parse(sections[1]))
}

/** Old HUD appears in precisely its original order until the user opens the new editor. */
internal fun legacyHudLayout(
  showBattery: Boolean,
  showClock: Boolean,
  showAdjustedRemaining: Boolean,
  showRemaining: Boolean,
  showSpeed: Boolean,
): HudLayout = HudLayout(
  first = listOfNotNull(
    HudElement.BATTERY.takeIf { showBattery },
    HudElement.CLOCK.takeIf { showClock },
    HudElement.ADJUSTED.takeIf { showAdjustedRemaining },
    HudElement.REMAINING.takeIf { showRemaining },
    HudElement.SPEED.takeIf { showSpeed },
  ),
  second = emptyList(),
)

internal fun resolveHudLayout(
  serialized: String,
  showBattery: Boolean,
  showClock: Boolean,
  showAdjustedRemaining: Boolean,
  showRemaining: Boolean,
  showSpeed: Boolean,
): HudLayout = parseHudLayout(serialized) ?: legacyHudLayout(
  showBattery, showClock, showAdjustedRemaining, showRemaining, showSpeed,
)
