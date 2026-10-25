// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** All faces ship within the APK. Index zero preserves the existing system font. */
internal data class LuaTypeface(val name: String, val family: FontFamily)
internal val luaTypefaces: List<LuaTypeface> = listOf(
    LuaTypeface("Domyślna (systemowa)", FontFamily.Default),
    LuaTypeface("Roboto", FontFamily(Font(R.font.lua_roboto))),
    LuaTypeface("Roboto Condensed", FontFamily(Font(R.font.lua_robotocondensed))),
    LuaTypeface("Inter", FontFamily(Font(R.font.lua_inter))),
    LuaTypeface("Open Sans", FontFamily(Font(R.font.lua_opensans))),
    LuaTypeface("Lato", FontFamily(Font(R.font.lua_lato))),
    LuaTypeface("Montserrat", FontFamily(Font(R.font.lua_montserrat))),
    LuaTypeface("Poppins", FontFamily(Font(R.font.lua_poppins))),
    LuaTypeface("Nunito", FontFamily(Font(R.font.lua_nunito))),
    LuaTypeface("Rubik", FontFamily(Font(R.font.lua_rubik))),
    LuaTypeface("Work Sans", FontFamily(Font(R.font.lua_worksans))),
    LuaTypeface("Oswald", FontFamily(Font(R.font.lua_oswald))),
    LuaTypeface("Manrope", FontFamily(Font(R.font.lua_manrope))),
    LuaTypeface("Source Sans 3", FontFamily(Font(R.font.lua_sourcesans3))),
    LuaTypeface("Karla", FontFamily(Font(R.font.lua_karla))),
    LuaTypeface("Raleway", FontFamily(Font(R.font.lua_raleway))),
    LuaTypeface("Exo 2", FontFamily(Font(R.font.lua_exo2))),
    LuaTypeface("Archivo", FontFamily(Font(R.font.lua_archivo))),
    LuaTypeface("Titillium Web", FontFamily(Font(R.font.lua_titilliumweb))),
    LuaTypeface("JetBrains Mono", FontFamily(Font(R.font.lua_jetbrainsmono))),
    LuaTypeface("IBM Plex Sans", FontFamily(Font(R.font.lua_ibmplexsans))),
)
internal fun luaFontFamily(index: Int): FontFamily =
    luaTypefaces.getOrNull(index)?.family ?: FontFamily.Default
