package com.prirai.android.nira.privacy.cookies

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.Engine
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Per-origin retention: timestamp advances only after Gecko acknowledges deletion. */
class CookiePolicyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        sweepPolicies(CookiePolicyRepository.getInstance(applicationContext)) { applicationContext.components.engine }
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        internal suspend fun sweepPolicies(repo: CookiePolicyRepository, engineProvider: () -> Engine): Int {
            val policies = repo.getAll()
            val now = System.currentTimeMillis()
            var engine: Engine? = null
            var swept = 0
            var failed = false
            for (policy in policies) {
                if (now < policy.savedAt + policy.hoursUntilClear.toLong() * HOUR_MILLIS) continue
                val host = policy.origin.tryGetHostFromUrl().orEmpty()
                if (host.isEmpty()) {
                    Log.w(TAG, "invalid_cookie_origin")
                    continue
                }
                try {
                    // Gecko runtime/session work belongs to Main. Empty/not-due sweeps do not initialize it.
                    withContext(Dispatchers.Main.immediate) {
                        val target = engine ?: engineProvider().also { engine = it }
                        suspendCancellableCoroutine<Unit> { continuation ->
                            val settled = AtomicBoolean(false)
                            continuation.invokeOnCancellation { settled.set(true) }
                            target.clearData(
                                data = Engine.BrowsingData.select(Engine.BrowsingData.COOKIES, Engine.BrowsingData.DOM_STORAGES),
                                host = host,
                                onSuccess = { if (settled.compareAndSet(false, true)) continuation.resume(Unit) },
                                onError = { if (settled.compareAndSet(false, true)) continuation.resumeWithException(it) }
                            )
                        }
                    }
                    // A setting edited while Gecko was clearing must keep its newer window and stamp.
                    repo.markSweptNow(policy)
                    swept++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "cookie_clear_failed type=${error.javaClass.simpleName}")
                    failed = true
                }
            }
            if (failed) throw IOException("Cookie sweep requires retry")
            return swept
        }
        private const val TAG = "CookiePolicyWorker"
        private const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
