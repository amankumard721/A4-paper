package com.a4sizepaper.typing.texttopdf.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.a4sizepaper.typing.texttopdf.BuildConfig
import com.facebook.ads.AdSettings
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** No ad request until UMP permits it and all mediation adapters finish initialization. */
data class AdsUiState(
    val requestReady: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val generation: Long = 0,
)

@MainThread
class AdsController(context: Context) {
    private val appContext = context.applicationContext
    private val consent = UserMessagingPlatform.getConsentInformation(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = AdsRequestGate(BuildConfig.ADS_ENABLED)
    private val mutableState = MutableStateFlow(AdsUiState())
    val state: StateFlow<AdsUiState> = mutableState.asStateFlow()
    private var closed = false
    private val invalidationListeners = mutableSetOf<() -> Unit>()

    private val frequency = appContext.getSharedPreferences("interstitial_frequency", Context.MODE_PRIVATE)
    private var interstitial: InterstitialAd? = null
    private var loadedAt = 0L
    private var loadingGeneration: Long? = null
    private var showing = false

    private fun preloadInterstitial() {
        val generation = gate.generation
        if (!canRequest(generation) || showing || loadingGeneration == generation ||
            BuildConfig.ADMOB_INTERSTITIAL_ID.isBlank()) return
        if (interstitial != null && SystemClock.elapsedRealtime() - loadedAt < 55 * 60_000L) return
        interstitial = null
        loadingGeneration = generation
        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (loadingGeneration != generation) return
                    loadingGeneration = null
                    if (canRequest(generation)) {
                        interstitial = ad
                        loadedAt = SystemClock.elapsedRealtime()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (loadingGeneration == generation) loadingGeneration = null
                }
            },
        )
    }

    fun afterSavedPdf(activity: Activity, onContinue: () -> Unit) =
        showAtTransition(activity, "completed", 2, onContinue)

    fun beforePdfTool(activity: Activity, onContinue: () -> Unit) =
        showAtTransition(activity, "tool_opens", 3, onContinue)

    private fun showAtTransition(
        activity: Activity,
        counter: String,
        threshold: Int,
        onContinue: () -> Unit,
    ) {
        if (showing) return
        if (!canRequest(gate.generation) || BuildConfig.ADMOB_INTERSTITIAL_ID.isBlank()) {
            onContinue()
            return
        }
        val completed = (frequency.getInt(counter, 0) + 1).coerceAtMost(threshold)
        frequency.edit().putInt(counter, completed).apply()
        val now = System.currentTimeMillis()
        val ad = interstitial
        if (completed < threshold || now - frequency.getLong("last_shown", 0) < 5 * 60_000L ||
            ad == null || SystemClock.elapsedRealtime() - loadedAt >= 55 * 60_000L ||
            activity.isFinishing || activity.isDestroyed ||
            (activity as? LifecycleOwner)?.lifecycle?.currentState != Lifecycle.State.RESUMED
        ) {
            preloadInterstitial()
            onContinue()
            return
        }

        interstitial = null
        showing = true
        fun finish() {
            if (!showing) return
            showing = false
            if (!closed && !activity.isDestroyed && !activity.isFinishing) {
                onContinue()
                preloadInterstitial()
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                frequency.edit().putInt("completed", 0).putInt("tool_opens", 0).putLong("last_shown", now).apply()
            }

            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = finish()
        }
        try {
            ad.show(activity)
        } catch (_: RuntimeException) {
            finish()
        }
    }

    /** Call when the app opens, or explicitly to retry a failed consent request. */
    fun start(activity: Activity) {
        if (closed || !BuildConfig.ADS_ENABLED || state.value.busy) return
        val token = beginConsent()
        scope.launch {
            if (!activity.awaitResumed() || closed) return@launch
            consent.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                {
                    scope.launch {
                        if (!activity.awaitResumed() || closed || token != gate.generation)
                            return@launch
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                            completeConsent(activity, token, error)
                        }
                    }
                },
                { error -> completeConsent(activity, token, error) },
            )
        }
    }

    fun showPrivacyOptions(activity: Activity) {
        if (closed || state.value.busy || !state.value.privacyOptionsRequired) return
        val token = beginConsent()
        scope.launch {
            if (!activity.awaitResumed() || closed) return@launch
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                completeConsent(activity, token, error)
            }
        }
    }

    private fun beginConsent(): Long {
        interstitial = null
        loadingGeneration = null
        val token = gate.beginConsent()
        // Stop SDK auto-refresh before presenting privacy choices.
        invalidationListeners.toList().forEach { it() }
        publish(busy = true, error = null)
        return token
    }

    private fun completeConsent(activity: Activity, token: Long, error: FormError?) {
        scope.launch {
            if (closed || activity.isDestroyed || activity.isFinishing) return@launch
            if (!gate.finishConsent(token, consent.canRequestAds())) return@launch
            val message =
                error?.let {
                    "Advertising privacy settings could not be updated. You can keep using the app and retry later."
                }
            publish(busy = gate.canInitialize, error = message)
            if (!gate.canInitialize) return@launch
            if (!activity.awaitResumed() || closed || token != gate.generation) return@launch
            try {
                SharedAdsSdk.initialize(appContext)
                if (closed || token != gate.generation) return@launch
                gate.initialized()
                publish(busy = false, error = message)
                preloadInterstitial()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!closed && token == gate.generation) {
                    publish(
                        busy = false,
                        error = "Ads are unavailable. You can keep using the app.",
                    )
                }
            }
        }
    }

    private fun publish(busy: Boolean, error: String?) {
        mutableState.value =
            AdsUiState(
                requestReady = gate.canRequest,
                privacyOptionsRequired =
                    consent.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
                busy = busy,
                error = error,
                generation = gate.generation,
            )
    }

    internal fun canRequest(generation: Long): Boolean =
        gate.canRequest && generation == gate.generation && consent.canRequestAds()

    internal fun onInvalidated(listener: () -> Unit): () -> Unit {
        invalidationListeners.add(listener)
        return { invalidationListeners.remove(listener) }
    }

    fun close() {
        if (closed) return
        closed = true
        gate.close()
        interstitial = null
        loadingGeneration = null
        invalidationListeners.toList().forEach { it() }
        invalidationListeners.clear()
        mutableState.value =
            state.value.copy(requestReady = false, busy = false, generation = gate.generation)
        scope.cancel()
    }
}

private suspend fun Activity.awaitResumed(): Boolean {
    if (isDestroyed || isFinishing) return false
    val owner = this as? LifecycleOwner ?: return false
    val state =
        owner.lifecycle.currentStateFlow.first {
            it == Lifecycle.State.DESTROYED || it.isAtLeast(Lifecycle.State.RESUMED)
        }
    return state != Lifecycle.State.DESTROYED && !isDestroyed && !isFinishing
}

/**
 * Process-wide initialization; no Activity is retained and recreated screens cannot initialize
 * twice.
 */
private object SharedAdsSdk {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var result: Deferred<Unit>? = null

    suspend fun initialize(context: Context) {
        val pending =
            synchronized(this) {
                result
                    ?: scope
                        .async {
                            // Conservative Meta setting; 0/0 delegates location determination to
                            // Meta.
                            // Not a substitute for UMP or dashboard Additional Consent
                            // configuration.
                            AdSettings.setDataProcessingOptions(arrayOf("LDU"), 0, 0)
                            suspendCancellableCoroutine<Unit> { continuation ->
                                MobileAds.initialize(context.applicationContext) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        }
                        .also { result = it }
            }
        try {
            pending.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            synchronized(this) { if (result === pending) result = null }
            throw failure
        }
    }
}
