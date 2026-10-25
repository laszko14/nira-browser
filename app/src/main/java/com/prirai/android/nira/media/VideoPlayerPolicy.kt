package com.prirai.android.nira.media

import android.content.Context
import android.net.Uri

/**
 * Private and profile-aware routing: site overrides never record URLs, tokens,
 * query strings or private browsing history. AUTO is explicit opt-in; MPV keeps
 * the existing manual detected-video button behaviour on upgrades.
 */
internal object VideoPlayerPolicy {
    enum class Mode { AUTO, MPV, WEB, EXTERNAL }
    private const val PREFS = "zavro_x_mpv_routing_v1132"
    private const val GLOBAL = "global"
    private const val SITE_PREFIX = "site:"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun host(pageUrl: String): String? {
        val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http", "https")) return null
        return uri.host?.lowercase(java.util.Locale.ROOT)?.takeIf {
            it.isNotBlank() && it.length <= 253
        }
    }

    private fun read(raw: String?): Mode? =
        Mode.entries.firstOrNull { it.name == raw }

    fun global(context: Context): Mode =
        read(prefs(context).getString(GLOBAL, null)) ?: Mode.MPV

    fun setGlobal(context: Context, value: Mode) {
        prefs(context).edit().putString(GLOBAL, value.name).apply()
    }

    fun siteOverride(context: Context, pageUrl: String, isPrivate: Boolean): Mode? =
        if (isPrivate) null else host(pageUrl)?.let {
            read(prefs(context).getString(SITE_PREFIX + it, null))
        }

    fun resolve(context: Context, pageUrl: String, isPrivate: Boolean): Mode =
        siteOverride(context, pageUrl, isPrivate) ?: global(context)

    fun setSite(context: Context, pageUrl: String, isPrivate: Boolean, value: Mode?) {
        if (isPrivate) return // Never persist hosts visited in private browsing.
        val site = host(pageUrl) ?: return
        val editor = prefs(context).edit()
        if (value == null) editor.remove(SITE_PREFIX + site)
        else editor.putString(SITE_PREFIX + site, value.name)
        editor.apply()
    }
}
