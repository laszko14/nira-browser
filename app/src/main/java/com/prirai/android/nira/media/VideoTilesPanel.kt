package com.prirai.android.nira.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** The URL stays exclusively in this in-memory entry, never in saveable UI state. */
internal class VideoTileItem(val key: Long, val entry: MediaEntry, val title: String, val variantCount: Int)

private val panelShape = RoundedCornerShape(24.dp)
private val panelGradient = Brush.verticalGradient(listOf(Color(0xf51a1e24), Color(0xfa0c1014)))
private val accent = Color(0xff008dff)
private val secondaryInk = Color(0xffb6bcc4)

@Composable
internal fun VideoTilesPanel(
    entries: List<VideoTileItem>,
    settings: VideoTilesSettings,
    onSettingsChange: (VideoTilesSettings) -> Unit,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var page by rememberSaveable { mutableIntStateOf(0) }
    val editor = remember(landscape, settings) { VideoTilesEditor(settings) }
    val preview = editor.value
    val horizontal = rememberLazyGridState()
    val vertical = rememberScrollState()
    val geometry = VideoTilesGeometry(preview, LocalDensity.current.fontScale, landscape)
    fun commit(change: (VideoTilesSettings) -> VideoTilesSettings) {
        editor.update(change)
        onSettingsChange(editor.value)
    }
    BackHandler(enabled = page != 0) { page = 0 }
    Surface(color = Color.Transparent, contentColor = Color.White, shape = panelShape) {
        Column(Modifier.fillMaxSize().background(panelGradient, panelShape)
            .border(1.dp, Color(0xff42474f), panelShape).navigationBarsPadding().padding(horizontal = 10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 3.dp)
                .size(38.dp, 4.dp).background(Color(0xff747980), CircleShape))
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (page == 0) "Wykryte filmy • ${entries.size}" else "Ustawienia widoku",
                    Modifier.weight(1f).padding(start = 5.dp), fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, lineHeight = 20.sp)
                if (page == 0) {
                    HeaderAction("Zmień gęstość siatki", { commit { it.copy(widthDp = if (it.widthDp <= 120) 160 else 120) } }) {
                        Icon(Icons.Default.GridView, null, Modifier.size(22.dp))
                    }
                    HeaderAction("Dostosuj układ", { page = 1 }) { Icon(Icons.Default.OpenWith, null, Modifier.size(23.dp)) }
                    HeaderAction("Ustawienia widoku", { page = 2 }) { Icon(Icons.Default.Settings, null, Modifier.size(22.dp)) }
                    HeaderAction("Zamknij wykryte filmy", onClose) { Icon(Icons.Default.Close, null, Modifier.size(24.dp)) }
                } else {
                    HeaderAction("Wróć do filmów", { page = 0 }) { Icon(Icons.Default.Close, null, Modifier.size(24.dp)) }
                }
            }
            if (page != 0) {
                VideoTilesSettingsView(entries.firstOrNull(), preview, page == 2,
                    onPreview = editor::update, onCommit = ::commit,
                    // Accessibility setProgress invokes change and finish before recomposition.
                    onCommitPreview = { onSettingsChange(editor.value) }, onShowAll = { page = 2 }, onPlay = onPlay)
            } else if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Brak wykrytych filmów", color = secondaryInk) }
            } else {
                // The scroll viewport has finite height; the horizontal grid itself receives exact height.
                // Ten real rows overflow vertically, never shrink to fit, while columns scroll horizontally.
                Box(Modifier.weight(1f).fillMaxWidth().testTag("video-tiles-vertical").verticalScroll(vertical)) {
                    LazyHorizontalGrid(rows = GridCells.Fixed(geometry.rows), state = horizontal,
                        modifier = Modifier.fillMaxWidth().height(geometry.contentHeightDp.dp).testTag("video-tiles-grid"),
                        horizontalArrangement = Arrangement.spacedBy(preview.gapDp.dp),
                        verticalArrangement = Arrangement.spacedBy(preview.gapDp.dp)) {
                        items(entries, key = { it.key }, contentType = { "video" }) { item ->
                            VideoTile(item, preview, onPlay, Modifier.width(preview.widthDp.dp).fillMaxHeight())
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeaderAction(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = description }) { content() }
}

@Composable
private fun ColumnScope.VideoTilesSettingsView(
    item: VideoTileItem?, settings: VideoTilesSettings, showAll: Boolean,
    onPreview: ((VideoTilesSettings) -> VideoTilesSettings) -> Unit,
    onCommit: ((VideoTilesSettings) -> VideoTilesSettings) -> Unit,
    onCommitPreview: () -> Unit, onShowAll: () -> Unit, onPlay: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        val scroll = rememberScrollState()
        if (wide && showAll) {
            Row(Modifier.fillMaxSize().verticalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LayoutSettings(settings, onPreview, onCommitPreview, Modifier.weight(1.4f))
                PreviewSettings(item, settings, onCommit, onPlay, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LayoutSettings(settings, onPreview, onCommitPreview, Modifier.fillMaxWidth())
                if (showAll) PreviewSettings(item, settings, onCommit, onPlay, Modifier.fillMaxWidth())
                else TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) { Text("Podgląd i elementy kafelka", color = Color.White) }
            }
        }
    }
}

@Composable
private fun LayoutSettings(settings: VideoTilesSettings,
                           onPreview: ((VideoTilesSettings) -> VideoTilesSettings) -> Unit,
                           onCommitPreview: () -> Unit, modifier: Modifier) {
    Column(modifier.background(Color(0x441f252d), RoundedCornerShape(16.dp)).border(1.dp, Color(0xff30363d), RoundedCornerShape(16.dp)).padding(14.dp)) {
        SettingSlider("Liczba rzędów", "Ile poziomych rzędów kafelków ma być widocznych.", "${settings.rows} / 10",
            settings.rows.toFloat(), 1f..10f, 8, "video-tiles-rows", { value -> onPreview { it.copy(rows = value.roundToInt()) } }, onCommitPreview)
        HorizontalDivider(Modifier.padding(vertical = 15.dp), color = Color(0xff2b3037))
        SettingSlider("Szerokość kafelka", "Szerokość jednego filmu (wpływa na liczbę kolumn).", "${settings.widthDp} dp",
            settings.widthDp.toFloat(), 100f..280f, 0, "video-tiles-width", { value -> onPreview { it.copy(widthDp = value.roundToInt()) } }, onCommitPreview)
        HorizontalDivider(Modifier.padding(vertical = 15.dp), color = Color(0xff2b3037))
        SettingSlider("Odstęp między kafelkami", "Odstęp poziomy i pionowy między filmami.", "${settings.gapDp} dp",
            settings.gapDp.toFloat(), 2f..16f, 13, "video-tiles-gap", { value -> onPreview { it.copy(gapDp = value.roundToInt()) } }, onCommitPreview)
    }
}

@Composable
private fun SettingSlider(title: String, help: String, label: String, value: Float,
                          range: ClosedFloatingPointRange<Float>, steps: Int, tag: String,
                          onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(help, fontSize = 11.sp, lineHeight = 15.sp, color = secondaryInk)
        }
        Text(label, Modifier.background(Color(0xff2a3038), RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
    Slider(value, onChange, valueRange = range, steps = steps, onValueChangeFinished = onFinished,
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xff424a53)),
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = title })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (range.endInclusive == 10f) {
            (1..10).forEach { Text("$it", color = secondaryInk, fontSize = 10.sp) }
        } else {
            Text("${range.start.roundToInt()}", color = secondaryInk, fontSize = 10.sp)
            Text("${range.endInclusive.roundToInt()}", color = secondaryInk, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PreviewSettings(item: VideoTileItem?, settings: VideoTilesSettings,
                            onCommit: ((VideoTilesSettings) -> VideoTilesSettings) -> Unit,
                            onPlay: (Long) -> Unit, modifier: Modifier) {
    Column(modifier.background(Color(0x441b2027), RoundedCornerShape(16.dp)).border(1.dp, Color(0xff30363d), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text("Podgląd kafelka", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = minOf(settings.widthDp.toFloat(), maxWidth.value).coerceAtLeast(100f)
            val preview = settings.copy(widthDp = width.roundToInt())
            if (item != null) VideoTile(item, preview, onPlay,
                Modifier.width(width.dp).height(VideoTilesGeometry(preview, LocalDensity.current.fontScale,
                    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE).rowHeightDp.dp))
            else Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { Text("Brak filmu do podglądu", color = secondaryInk) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Elementy kafelka", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        VisibilitySetting("Miniatura z przyciskiem Play", settings.showThumbnail) { checked -> onCommit { it.copy(showThumbnail = checked) } }
        VisibilitySetting("Tytuł (maks. 2 wiersze)", settings.showTitle) { checked -> onCommit { it.copy(showTitle = checked) } }
        VisibilitySetting("Format pliku", settings.showFormat) { checked -> onCommit { it.copy(showFormat = checked) } }
        VisibilitySetting("Rozdzielczość", settings.showQuality) { checked -> onCommit { it.copy(showQuality = checked) } }
        VisibilitySetting("Rozmiar pliku", settings.showSize) { checked -> onCommit { it.copy(showSize = checked) } }
        VisibilitySetting("Menu akcji (⋮)", settings.showActions) { checked -> onCommit { it.copy(showActions = checked) } }
    }
}

@Composable
private fun VisibilitySetting(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = change)
        .padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = accent), modifier = Modifier.size(26.dp))
        Text(label, Modifier.padding(start = 7.dp), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun VideoTile(item: VideoTileItem, settings: VideoTilesSettings, onPlay: (Long) -> Unit, modifier: Modifier) {
    val entry = item.entry
    val geometry = VideoTilesGeometry(settings, LocalDensity.current.fontScale,
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    val playable = entry.playbackStatus !in setOf(MediaPlaybackStatus.EXPIRED, MediaPlaybackStatus.AUTH_ERROR, MediaPlaybackStatus.UNPLAYABLE)
    var menu by remember(item.key) { mutableStateOf(false) }
    Column(modifier.testTag("video-tile-${item.key}").semantics { if (entry.durationSeconds == null) contentDescription = "Czas nieznany" }) {
        val thumbnailShape = RoundedCornerShape(7.dp)
        val tint = when ((item.key % 3).toInt()) { 0 -> Color(0xff153c43); 1 -> Color(0xff152b37); else -> Color(0xff232b39) }
        Box(Modifier.fillMaxWidth().height(geometry.thumbnailHeightDp.dp).clip(thumbnailShape)
            .background(if (settings.showThumbnail) Brush.linearGradient(listOf(tint, Color(0xff090f15), tint.copy(alpha = 0.5f)))
                else Brush.verticalGradient(listOf(Color(0xff181e24), Color(0xff11161b))))
            .border(1.dp, Color(0xff46515c), thumbnailShape), contentAlignment = Alignment.Center) {
            if (settings.showThumbnail) Icon(Icons.Default.Movie, null, Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(38.dp), tint = Color.White.copy(alpha = 0.10f))
            OutlinedButton(onClick = { onPlay(entry.id) }, enabled = playable,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = geometry.playStartDp.dp).size(48.dp), shape = CircleShape, border = BorderStroke(1.5.dp, if (playable) Color(0xffdae8ea) else Color.Gray),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xd9091015), contentColor = Color.White), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.PlayArrow, "Odtwórz w MPV Alpha: ${item.title}", Modifier.size(30.dp))
            }
            if (settings.showActions) Box(Modifier.align(Alignment.TopEnd)) {
                // The 48dp menu and Play targets are disjoint even at minimum width.
                // Keep the visible 23dp ellipsis at the reference's upper-right position.
                Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = { menu = true })
                    .semantics { contentDescription = "Akcje filmu: ${item.title}" }) {
                    Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(23.dp)
                        .background(Color(0xdd05080b), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(19.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Odtwórz w MPV Alpha") }, enabled = playable,
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { menu = false; onPlay(entry.id) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Format: ${entry.format.name}") }, onClick = { menu = false })
                    DropdownMenuItem(text = { Text("Jakość: ${entry.quality?.takeIf { it.isNotBlank() } ?: "nieznana"}") }, onClick = { menu = false })
                    DropdownMenuItem(text = { Text(sizeText(entry) ?: "Rozmiar nieznany") }, onClick = { menu = false })
                    DropdownMenuItem(text = { Text(durationText(entry.durationSeconds) ?: "Czas nieznany") }, onClick = { menu = false })
                }
            }
            if (settings.showThumbnail) durationText(entry.durationSeconds)?.let { duration ->
                Text(duration, Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Color(0xe3000000), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
                    color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
            }
        }
        if (settings.showTitle) Box(Modifier.fillMaxWidth().height(geometry.titleHeightDp.dp).padding(top = 3.dp), contentAlignment = Alignment.TopStart) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        }
        if (geometry.badgeHeightDp > 0f) Row(Modifier.fillMaxWidth().height(geometry.badgeHeightDp.dp).padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
            if (settings.showFormat) TileBadge(entry.format.name, Color(0xff383e45), Modifier.weight(1f))
            if (settings.showQuality) TileBadge(entry.quality?.takeIf { it.isNotBlank() } ?: "Jakość nieznana", accent, Modifier.weight(1f))
            if (settings.showSize) TileBadge(sizeText(entry) ?: "Rozmiar nieznany", Color(0xff383e45), Modifier.weight(1f))
        }
    }
}

@Composable
private fun TileBadge(text: String, color: Color, modifier: Modifier) {
    Text(text, modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp, vertical = 3.dp),
        color = Color.White, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

internal fun durationText(seconds: Int?): String? {
    val value = seconds?.takeIf { it > 0 } ?: return null
    return if (value >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
        else String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60)
}

internal fun sizeText(entry: MediaEntry): String? {
    if (entry.format == MediaFormat.HLS || entry.format == MediaFormat.DASH) return null
    val bytes = entry.sizeBytes?.takeIf { it > 0 } ?: return null
    return when {
        bytes >= 1_000_000_000L -> String.format(Locale("pl", "PL"), "%.2f GB", bytes / 1e9)
        bytes >= 1_000_000L -> String.format(Locale("pl", "PL"), "%.1f MB", bytes / 1e6)
        else -> String.format(Locale("pl", "PL"), "%.0f KB", bytes / 1000.0)
    }
}
