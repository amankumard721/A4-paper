package com.a4sizepaper.typing.texttopdf.ads

/** Invalidates in-flight work whenever consent changes or its owner closes. */
internal class AdsRequestGate(private val enabled: Boolean) {
    var generation: Long = 0
        private set

    private var consentAllowsAds = false
    private var sdkReady = false
    private var closed = false
    val canRequest: Boolean
        get() = enabled && !closed && consentAllowsAds && sdkReady

    val canInitialize: Boolean
        get() = enabled && !closed && consentAllowsAds

    fun beginConsent(): Long {
        generation++
        consentAllowsAds = false
        return generation
    }

    fun finishConsent(token: Long, allowed: Boolean): Boolean {
        if (closed || token != generation) return false
        consentAllowsAds = allowed
        return true
    }

    fun initialized() {
        if (!closed) sdkReady = true
    }

    fun close() {
        closed = true
        consentAllowsAds = false
        generation++
    }
}
