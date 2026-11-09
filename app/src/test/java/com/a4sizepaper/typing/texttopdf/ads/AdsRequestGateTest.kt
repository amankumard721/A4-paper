package com.a4sizepaper.typing.texttopdf.ads

import org.junit.Assert.*
import org.junit.Test

class AdsRequestGateTest {
    @Test
    fun consentAndAdapterInitializationAreBothRequired() {
        val gate = AdsRequestGate(true)
        val token = gate.beginConsent()
        assertFalse(gate.canInitialize)
        assertFalse(gate.canRequest)
        assertTrue(gate.finishConsent(token, true))
        assertTrue(gate.canInitialize)
        assertFalse(gate.canRequest)
        gate.initialized()
        assertTrue(gate.canRequest)
    }

    @Test
    fun openingPrivacyChoicesImmediatelyInvalidatesExistingAds() {
        val gate = AdsRequestGate(true)
        gate.finishConsent(gate.beginConsent(), true)
        gate.initialized()
        assertTrue(gate.canRequest)
        val token = gate.beginConsent()
        assertFalse(gate.canRequest)
        gate.finishConsent(token, false)
        assertFalse(gate.canRequest)
    }

    @Test
    fun oldConsentCallbackCannotUndoNewPrivacyDecision() {
        val gate = AdsRequestGate(true)
        val old = gate.beginConsent()
        val current = gate.beginConsent()
        gate.finishConsent(current, false)
        gate.initialized()
        assertFalse(gate.finishConsent(old, true))
        assertFalse(gate.canRequest)
    }

    @Test
    fun lateInitializationCannotBypassConsentRevocation() {
        val gate = AdsRequestGate(true)
        gate.finishConsent(gate.beginConsent(), true)
        gate.finishConsent(gate.beginConsent(), false)
        gate.initialized()
        assertFalse(gate.canRequest)
    }

    @Test
    fun disposedOwnerCannotBeRevivedByCallbacks() {
        val gate = AdsRequestGate(true)
        val token = gate.beginConsent()
        gate.close()
        assertFalse(gate.finishConsent(token, true))
        gate.initialized()
        assertFalse(gate.canInitialize)
        assertFalse(gate.canRequest)
    }

    @Test
    fun disabledReleaseBuildNeverInitializesOrRequests() {
        val gate = AdsRequestGate(false)
        gate.finishConsent(gate.beginConsent(), true)
        gate.initialized()
        assertFalse(gate.canInitialize)
        assertFalse(gate.canRequest)
    }
}
