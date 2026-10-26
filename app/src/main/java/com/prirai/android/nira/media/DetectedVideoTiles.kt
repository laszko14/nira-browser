package com.prirai.android.nira.media

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.roundToInt

/** Settings only; never store source URL, token, profile or browsing history. */
internal data class TileLayout(val rows: Int, val width: Int, val gap: Int) {
    fun safe() = TileLayout(rows.coerceIn(1, 10), width.coerceIn(100, 280), gap.coerceIn(2, 16))
}

internal object TilePreferences {
    private fun side(orientation: Int) =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) "land" else "portrait"

    fun load(context: Context, orientation: Int): TileLayout {
        val s = side(orientation)
        val prefs = context.getSharedPreferences("scw_preferences", Context.MODE_PRIVATE)
        return TileLayout(
            prefs.getInt("zavro_tiles_rows_$s", if (s == "land") 2 else 4),
            prefs.getInt("zavro_tiles_width_$s", if (s == "land") 180 else 160),
            prefs.getInt("zavro_tiles_gap_$s", 8)
        ).safe()
    }

    fun save(context: Context, orientation: Int, config: TileLayout) {
        val s = side(orientation)
        val v = config.safe()
        context.getSharedPreferences("scw_preferences", Context.MODE_PRIVATE).edit()
            .putInt("zavro_tiles_rows_$s", v.rows)
            .putInt("zavro_tiles_width_$s", v.width)
            .putInt("zavro_tiles_gap_$s", v.gap)
            .apply()
    }
}

private data class Tile(val entry: MediaEntry, val name: String)

private fun prepareTiles(entries: List<MediaEntry>): List<Tile> =
    entries.groupBy { it.groupId ?: "url:" + it.url }.values.mapIndexed { index, group ->
        val preferred = group.sortedWith(
            compareByDescending<MediaEntry> { MediaSource.NETWORK in it.sources }
                .thenByDescending { MediaSource.DOM in it.sources }
        )
        val entry = preferred.first()
        val name = preferred.filter { !it.title.isNullOrBlank() }.maxByOrNull {
            when (it.titleKind) { "video" -> 3; "link" -> 2; "page" -> 1; else -> 0 }
        }?.title?.trim()?.take(180)
            ?: runCatching { Uri.parse(entry.url).lastPathSegment }.getOrNull()
                ?.substringBeforeLast('.')?.replace('_', ' ')?.takeIf { it.length in 3..90 }
            ?: "Film " + (index + 1)
        Tile(entry, name)
    }

private fun sizeText(entry: MediaEntry): String? {
    if (entry.format == MediaFormat.HLS || entry.format == MediaFormat.DASH) return null
    val bytes = entry.sizeBytes?.takeIf { it > 0 } ?: return null
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.2f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1e6)
        else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1000.0)
    }
}

private fun durationText(seconds: Int?): String? {
    val s = seconds?.takeIf { it > 0 } ?: return null
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

@Composable
internal fun DetectedVideoTiles(
    snapshot: MediaUiSnapshot?,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val orientation = config.orientation
    val initial = remember(context, orientation) { TilePreferences.load(context, orientation) }
    var rows by remember(orientation) { mutableIntStateOf(initial.rows) }
    var width by remember(orientation) { mutableIntStateOf(initial.width) }
    var gap by remember(orientation) { mutableIntStateOf(initial.gap) }
    var editing by remember { mutableStateOf(false) }
    val tiles = remember(snapshot) { prepareTiles(snapshot?.entries.orEmpty()) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF161B20)).padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wykryte filmy · " + tiles.size, Modifier.weight(1f),
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { editing = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Ustawienia kafelków")
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij wykryte filmy")
                }
            }
            if (tiles.isEmpty()) {
                Text("Brak wykrytych filmów", Modifier.padding(16.dp))
            } else {
                // Rows are literal 1..10, not clipped to the visible height.
                // Finite-height horizontal lazy grid sits in a vertical scroll viewport.
                val tileHeight = (width * 9 / 16 + 106).coerceAtLeast(165)
                val wholeHeight = tileHeight * rows + gap * (rows - 1)
                val availableHeight = (config.screenHeightDp - 170).coerceAtLeast(180)
                Box(
                    Modifier.fillMaxWidth().height(wholeHeight.coerceAtMost(availableHeight).dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(rows),
                        modifier = Modifier.fillMaxWidth().height(wholeHeight.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap.dp),
                        verticalArrangement = Arrangement.spacedBy(gap.dp),
                    ) {
                        items(tiles, key = { it.entry.id }) { tile ->
                            TileCard(tile, width, tileHeight, onPlay)
                        }
                    }
                }
            }
        }

        if (editing) {
            var trialRows by remember(orientation) { mutableFloatStateOf(rows.toFloat()) }
            var trialWidth by remember(orientation) { mutableFloatStateOf(width.toFloat()) }
            var trialGap by remember(orientation) { mutableFloatStateOf(gap.toFloat()) }
            AlertDialog(
                onDismissRequest = { editing = false },
                title = { Text("Ustawienia widoku (" + if (orientation == Configuration.ORIENTATION_LANDSCAPE) "poziom)" else "pion)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Liczba rzędów: " + trialRows.roundToInt() + "/10")
                        Slider(value = trialRows, onValueChange = { trialRows = it }, valueRange = 1f..10f, steps = 8)
                        Text("Szerokość kafelka: " + trialWidth.roundToInt() + " dp")
                        Slider(value = trialWidth, onValueChange = { trialWidth = it }, valueRange = 100f..280f)
                        Text("Odstęp: " + trialGap.roundToInt() + " dp")
                        Slider(value = trialGap, onValueChange = { trialGap = it }, valueRange = 2f..16f)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        rows = trialRows.roundToInt().coerceIn(1, 10)
                        width = trialWidth.roundToInt().coerceIn(100, 280)
                        gap = trialGap.roundToInt().coerceIn(2, 16)
                        TilePreferences.save(context, orientation, TileLayout(rows, width, gap))
                        editing = false
                    }) { Text("Zapisz") }
                },
                dismissButton = { TextButton(onClick = { editing = false }) { Text("Anuluj") } }
            )
        }
    }
}

@Composable
private fun TileCard(tile: Tile, width: Int, height: Int, onPlay: (Long) -> Unit) {
    val entry = tile.entry
    val playable = entry.playbackStatus !in setOf(
        MediaPlaybackStatus.EXPIRED, MediaPlaybackStatus.AUTH_ERROR, MediaPlaybackStatus.UNPLAYABLE
    )
    val context = LocalContext.current
    val corner = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val poster = remember(entry.posterUrl) {
        entry.posterUrl?.takeIf {
            val u = runCatching { Uri.parse(it) }.getOrNull()
            u != null && (u.scheme == "https" || u.scheme == "http") &&
                !u.host.isNullOrBlank() && u.userInfo == null
        }
    }
    Column(
        modifier = Modifier.width(width.dp).height(height.dp).clip(corner)
            .background(Color(0xFF282E35)).padding(4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Color(0xFF111820)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.LightGray)
            if (poster != null) {
                val req = remember(poster) {
                    ImageRequest.Builder(context).data(poster).diskCachePolicy(CachePolicy.DISABLED).build()
                }
                AsyncImage(model = req, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            IconButton(
                onClick = { if (playable) onPlay(entry.id) },
                enabled = playable,
                modifier = Modifier.size(48.dp)
                    .background(Color(0xDD0C121A), androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                    .border(2.dp, if (playable) Color(0xFF67D9E0) else Color.Gray,
                        androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
            ) {
                Icon(Icons.Default.PlayArrow,
                    contentDescription = "Odtwórz w MPV Alpha: " + tile.name,
                    tint = if (playable) Color.White else Color.Gray)
            }
            durationText(entry.durationSeconds)?.let { duration ->
                Text(duration, modifier = Modifier.align(Alignment.BottomEnd)
                    .background(Color(0xD9000000)).padding(horizontal = 3.dp),
                    color = Color.White, fontSize = 10.sp)
            }
        }
        Text(tile.name, modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp, lineHeight = 13.sp, color = Color.White)
        val badges = buildList {
            add(entry.format.name)
            entry.quality?.takeIf { it.isNotBlank() }?.let { add(it.take(15)) }
            sizeText(entry)?.let { add(it) }
        }.joinToString(" · ")
        Text(badges, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xFFBDCAD6))
    }
}
