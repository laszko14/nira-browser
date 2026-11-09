package com.prirai.android.nira.videosearch

import android.content.ClipData
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit diagnostics controls. No background recording and no automatic
 * export, upload, file copy, reset, preference changes or new permissions.
 */
internal class VideoSearchDiagnosticControls(
    private val activity: AppCompatActivity,
    private val privateMode: Boolean,
    private val owner: () -> VideoSearchScope?,
    private val notifyUser: (String) -> Unit,
) {
    val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = if (privateMode) "video-diagnostics-private" else "video-diagnostics-normal"
    }
    private val status = TextView(activity).apply {
        textSize = 12f
        text = activity.getString(R.string.video_diag_status_initial)
        tag = if (privateMode) "video-diag-private-state" else "video-diag-normal-state"
    }
    private val start = Button(activity).apply {
        text = activity.getString(R.string.video_diag_button_start)
        contentDescription = "Rozpocznij diagnostykę wyszukiwarki filmów"
        tag = if (privateMode) "video-diag-private-start" else "video-diag-normal-start"
    }
    private val stop = Button(activity).apply {
        text = activity.getString(R.string.video_diag_button_stop)
        contentDescription = "Zatrzymaj i dokończ zapis zdarzeń"
        tag = if (privateMode) "video-diag-private-stop" else "video-diag-normal-stop"
    }
    private val share = Button(activity).apply {
        text = activity.getString(R.string.video_diag_button_report)
        contentDescription = "Udostępnij zakończony raport diagnostyczny"
        tag = if (privateMode) "video-diag-private-share" else "video-diag-normal-share"
    }
    private var busy = false

    init {
        panel.addView(status)
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        actions.addView(start, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(stop, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(share, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(actions)
        start.setOnClickListener { confirmStart() }
        stop.setOnClickListener { finishByUser() }
        share.setOnClickListener { confirmShare() }
        // Do not query a private tab owner before this panel becomes active.
        start.isEnabled = false
        stop.isEnabled = false
        share.isEnabled = false
        if (!privateMode) refresh()
    }

    fun refresh() {
        val current = owner()
        val state = current?.let {
            VideoSearchBridgeHost.instance()?.diagnosticStatus(it)
        }
        start.isEnabled = current != null && !busy &&
            state?.recording != true && state?.stopping != true
        stop.isEnabled = current != null && !busy &&
            state?.recording == true && state?.stopping != true
        share.isEnabled = current != null && !busy &&
            state?.reportReady == true && state?.stopping != true
        status.text = when {
            current == null -> activity.getString(R.string.video_diag_status_unavailable)
            state?.stopping == true || busy ->
                activity.getString(R.string.video_diag_status_stopping)
            state?.recording == true ->
                activity.getString(R.string.video_diag_status_recording,
                    state.attempted, state.recorded, state.dropped)
            state?.reportReady == true ->
                activity.getString(
                    if (privateMode) R.string.video_diag_status_ready_private
                    else R.string.video_diag_status_ready_normal,
                    state.recorded, state.dropped,
                    if (state.failure == null) ""
                    else activity.getString(R.string.video_diag_status_failure_suffix))
            else -> activity.getString(
                if (privateMode) R.string.video_diag_status_off_private
                else R.string.video_diag_status_off_normal)
        }
    }

    private fun confirmStart() {
        val target = owner() ?: return
        if (busy) return
        MaterialAlertDialogBuilder(activity)
            .setTitle("Rozpocząć diagnostykę wyszukiwarki?")
            .setMessage(
                "Zapisywane mogą być adresy stron i filmów, frazy, tytuły, " +
                "informacje o elementach witryn oraz przyczyny odrzuceń. " +
                "Hasła, cookies i tokeny są filtrowane, ale nie wszystkie " +
                "nieznane sekrety da się niezawodnie rozpoznać. " +
                "Maksymalnie 45 minut, brak automatycznego wysyłania. " +
                (if (target is VideoSearchScope.Private)
                    "Tryb prywatny: tylko RAM, usuwany po końcu prywatnej sesji."
                else "Zwykły profil: lokalny plik noBackup.")
            )
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Rozpocznij") { _, _ ->
                if (target != owner()) return@setPositiveButton
                val components = activity.applicationContext.components
                VideoSearchBridgeHost.start(
                    activity.applicationContext, components.engine, components.store)
                val started = VideoSearchBridgeHost.instance()
                    ?.beginDiagnostics(target) == true
                notifyUser(if (started) "Rozpoczęto zapisywanie diagnostyki."
                    else "Nie uruchomiono. Sprawdź profil i aktywną sesję.")
                refresh()
            }.show()
    }

    private fun finishByUser() {
        val target = owner() ?: return
        if (busy) return
        val operation = VideoSearchBridgeHost.instance()
            ?.requestFinishDiagnostics(target) ?: run {
                notifyUser("Brak aktywnego nagrywania.")
                return
            }
        busy = true
        refresh()
        activity.lifecycleScope.launch {
            try {
                val recorder = operation.await()
                if (recorder != null) {
                    val closed = withContext(Dispatchers.IO) { recorder.awaitStopped(7000) }
                    if (owner() == target) notifyUser(
                        if (closed) "Nagrywanie zakończone. Raport gotowy."
                        else "Zatrzymano. Zapis raportu jest jeszcze w toku.")
                } else if (owner() == target)
                    notifyUser("Sesja została przerwana lub właściciel się zmienił.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (owner() == target) notifyUser("Nie udało się zakończyć raportu.")
            } finally {
                busy = false
                refresh()
            }
        }
    }

    private fun confirmShare() {
        val target = owner() ?: return
        if (busy) return
        val recorder = VideoSearchBridgeHost.instance()
            ?.diagnosticReport(target) ?: run {
                notifyUser("Najpierw zakończ nagrywanie.")
                return
            }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Udostępnić pełny raport?")
            .setMessage("Raport może zawierać prywatne adresy URL, tytuły " +
                "i frazy wyszukiwania. Sprawdź odbiorcę. " +
                (if (target is VideoSearchScope.Private)
                    "Prywatnie przesyłany jest strumień z RAM bez tworzenia kopii na dysku. " +
                    "Wymagana jest nadal aktywna prywatna sesja."
                else "Zwykły raport odczytywany jest z lokalnego pliku.") +
                " Dostęp zostanie ograniczony czasowo.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Wybierz odbiorcę") { _, _ ->
                if (target != owner()) return@setPositiveButton
                val current = VideoSearchBridgeHost.instance()?.diagnosticReport(target)
                if (recorder !== current) {
                    notifyUser("Raport nie należy już do tej sesji.")
                    return@setPositiveButton
                }
                val uri = VideoSearchDiagnosticReportAccess.offer(activity, recorder)
                    ?: run {
                        notifyUser("Raport jest niedostępny lub sesja wygasła.")
                        return@setPositiveButton
                    }
                val outgoing = Intent(Intent.ACTION_SEND).apply {
                    type = VideoSearchDiagnosticReportAccess.MIME
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(activity.contentResolver, "Raport Zavro Clean", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    activity.startActivity(Intent.createChooser(outgoing,
                        "Udostępnij raport Zavro Clean").addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: Exception) {
                    notifyUser("Brak dostępnej aplikacji do udostępnienia raportu.")
                }
            }.show()
    }
}
