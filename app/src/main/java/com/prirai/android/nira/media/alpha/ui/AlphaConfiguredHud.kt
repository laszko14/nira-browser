// Adapted from MPV Alpha v54 PlaybackInfoOverlay.kt (Apache-2.0), c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prirai.android.nira.media.alpha.*
import kotlin.math.roundToInt

/** Settings wiring from Alpha v54 PlaybackInfoOverlay; renderer remains HudBarContent. */
@Composable
internal fun AlphaConfiguredHud(
    snapshot:AlphaPreferences,state:AlphaPlaybackState,controlsVisible:Boolean,
    battery:Int,charging:Boolean,clock:String,
) {
    val p=AlphaRenderedValues(snapshot)
    if(!p.bool("show_playback_info_overlay",true) ||
        (!controlsVisible && !p.bool("alpha_hud_always_visible",true)))return
    val factor=p.int("alpha_hud_scale_percent",100)/100f
    val remaining=hudRemainingTimes(state.durationSeconds?.toFloat()?:Float.NaN,
        state.positionSeconds?.toFloat()?:Float.NaN,state.speed?.toFloat()?:Float.NaN)
    var layout=resolveHudLayout(
        p.text("alpha_hud_layout_v2",""),p.bool("alpha_hud_show_battery",true),
        p.bool("alpha_hud_show_clock",true),p.bool("alpha_hud_show_adjusted_remaining",true),
        p.bool("alpha_hud_show_remaining",true),p.bool("alpha_hud_show_speed",true),
    ).without(HudElement.DOWNLOAD) // No measured stream transfer is exposed by AlphaSession.
    if(battery<0)layout=layout.without(HudElement.BATTERY)
    if(clock.isEmpty())layout=layout.without(HudElement.CLOCK)
    if(remaining.originalSeconds==null)layout=layout.without(HudElement.REMAINING)
    if(remaining.adjustedSeconds==null)layout=layout.without(HudElement.ADJUSTED)
    if(state.speed==null)layout=layout.without(HudElement.SPEED).without(HudElement.ADJUSTED)
    if(layout.items.isEmpty())return
    fun color(key:String,fallback:Long):Color =
        runCatching { Color(android.graphics.Color.parseColor(p.text(key,"#"+fallback.toString(16)))) }
            .getOrDefault(Color(fallback))
    val weight=p.int("alpha_hud_font_weight_percent",
        if(p.bool("alpha_hud_bold_text",false))60 else 40)
    val outline=p.int("alpha_hud_outline_percent",
        if(p.bool("alpha_hud_high_contrast_text",false))50 else 0)
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        var measured by remember { mutableStateOf(IntSize.Zero) }
        val x=p.fraction("alpha_hud_position_x",1f)
        val y=p.fraction("alpha_hud_position_y",.03f)
        val travelX=(constraints.maxWidth-measured.width).coerceAtLeast(0)
        val travelY=(constraints.maxHeight-measured.height).coerceAtLeast(0)
        HudBarContent(
            model=HudBarModel(
                true,true,true,true,true,battery,charging,clock,
                remaining.adjustedSeconds?.let { "⏱ "+DateUtils.formatElapsedTime(it) }?:"",
                remaining.originalSeconds?.let { "↓ "+DateUtils.formatElapsedTime(it) }?:"",
                state.speed?.toFloat()?:0f,
                when(battery) {
                    in 0..9 -> Color(0xFFEF4444)
                    in 10..19 -> Color(0xFFF59E0B)
                    else -> color("alpha_hud_battery_color",0xFF4ADE80)
                },
                color("alpha_hud_clock_color",0xFFE2E8F0),
                color("alpha_hud_network_speed_color",0xFFA7F3D0),
                color("alpha_hud_remaining_color",0xFFFFFFFF),
                color("alpha_hud_speed_color",0xFF60E2F2),
                (16f*factor).sp,(18f*factor).dp,(3f*factor).dp,
                fontWeightForReadability(weight),outline>0,
                p.int("alpha_hud_item_gap_dp",12).dp,factor,
                outlineWidthDp=outlineWidthDpForReadability(outline)*factor,
                fontFamily=luaFontFamily(p.int("lua_button_typeface",0)),
                layout=layout,rowGap=(p.int("alpha_hud_row_gap_dp",4)*factor).dp,
                showIcons=p.bool("alpha_hud_show_icons",true),
            ),
            modifier=Modifier.offset { IntOffset((travelX*x).roundToInt(),(travelY*y).roundToInt()) }
                .onSizeChanged { measured=it }
                .clip(RoundedCornerShape(p.int("alpha_hud_corner_radius_dp",10).dp))
                .background(color("alpha_hud_background_color",0xFF10151C)
                    .copy(alpha=p.int("alpha_hud_background_opacity",70)/100f))
                .padding(p.int("alpha_hud_padding_dp",8).dp),
            maxWidth=maxWidth,
        )
    }
}
