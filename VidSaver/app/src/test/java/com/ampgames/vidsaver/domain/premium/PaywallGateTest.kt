package com.ampgames.vidsaver.domain.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallGateTest {

    private val now = 10_000_000L
    private fun gate(
        launchRouteMode: Int = 2,
        postpone: Int = 0,
        resumeEvery: Int = 3,
        newInstallersOnly: Boolean = false,
    ) = PaywallGate(
        launchRouteMode = launchRouteMode,
        postponeLaunchRouteSessions = postpone,
        offerOnResumeEvery = resumeEvery,
        offerNewInstallersOnly = newInstallersOnly,
        minAutoIntervalMs = 60_000L,
    )

    @Test
    fun `automatic offers never reach a premium or unknown install`() {
        val g = gate()
        assertFalse(g.offersAllowed(PremiumState(isPremium = true, isKnown = true), 1, 1))
        assertFalse("unknown: the store has not answered yet", g.offersAllowed(PremiumState.UNKNOWN, 1, 1))
        assertTrue(g.offersAllowed(PremiumState.FREE, 1, 1))
    }

    @Test
    fun `new-installers-only limits offers to installs that first opened this version`() {
        val g = gate(newInstallersOnly = true)
        assertTrue(g.offersAllowed(PremiumState.FREE, firstOpenVersionCode = 7, currentVersionCode = 7))
        assertFalse(g.offersAllowed(PremiumState.FREE, firstOpenVersionCode = 3, currentVersionCode = 7))
    }

    @Test
    fun `launch paywall follows launch_route_mode`() {
        assertFalse(gate(launchRouteMode = 0).shouldShowOnLaunch(1, 0, now))
        assertFalse("mode 1 is the interstitial alone", gate(launchRouteMode = 1).shouldShowOnLaunch(1, 0, now))
        assertTrue(gate(launchRouteMode = 2).shouldShowOnLaunch(1, 0, now))
        assertTrue(gate(launchRouteMode = 3).shouldShowOnLaunch(1, 0, now))
        assertTrue(gate(launchRouteMode = 2).launchInterstitialFollowsPaywall())
        assertFalse(gate(launchRouteMode = 3).launchInterstitialFollowsPaywall())
    }

    @Test
    fun `postponed sessions skip the launch paywall`() {
        val g = gate(postpone = 2)
        assertFalse(g.shouldShowOnLaunch(session = 1, lastAutoShownAt = 0, now = now))
        assertFalse(g.shouldShowOnLaunch(session = 2, lastAutoShownAt = 0, now = now))
        assertTrue(g.shouldShowOnLaunch(session = 3, lastAutoShownAt = 0, now = now))
    }

    @Test
    fun `resume paywall fires on every N-th resume and never on zero`() {
        val g = gate(resumeEvery = 3)
        assertFalse(g.shouldShowOnResume(0, 0, now))
        assertFalse(g.shouldShowOnResume(1, 0, now))
        assertFalse(g.shouldShowOnResume(2, 0, now))
        assertTrue(g.shouldShowOnResume(3, 0, now))
        assertTrue(g.shouldShowOnResume(6, 0, now))
        assertFalse("0 disables the trigger", gate(resumeEvery = 0).shouldShowOnResume(3, 0, now))
    }

    @Test
    fun `two automatic paywalls cannot stack within the interval`() {
        val g = gate()
        val justShown = now - 10_000L
        assertFalse(g.shouldShowOnLaunch(1, justShown, now))
        assertFalse(g.shouldShowOnResume(3, justShown, now))
        assertFalse(g.shouldShowForConcurrentDownload(1, justShown, now))
        val longAgo = now - 600_000L
        assertTrue(g.shouldShowOnLaunch(1, longAgo, now))
    }

    @Test
    fun `a second concurrent download prompts, a first does not`() {
        val g = gate()
        assertFalse(g.shouldShowForConcurrentDownload(othersRunning = 0, lastAutoShownAt = 0, now = now))
        assertTrue(g.shouldShowForConcurrentDownload(othersRunning = 1, lastAutoShownAt = 0, now = now))
    }

    @Test
    fun `premium unlocks concurrent downloads`() {
        assertEquals(1, PaywallGate.concurrentDownloadsFor(PremiumState.FREE))
        assertEquals(3, PaywallGate.concurrentDownloadsFor(PremiumState(isPremium = true, isKnown = true)))
    }

    @Test
    fun `yearly saving is computed from store prices`() {
        val monthly = PremiumPlan("m", PlanKind.MONTHLY, "$4.99", 4_990_000L, "USD", trialDays = 7, periodDays = 30)
        val yearly = PremiumPlan("y", PlanKind.YEARLY, "$29.99", 29_990_000L, "USD", periodDays = 365)
        val lifetime = PremiumPlan("l", PlanKind.LIFETIME, "$59.99", 59_990_000L, "USD")
        val offering = PremiumOffering(listOf(monthly, yearly, lifetime), defaultPlanId = "m")
        assertEquals(49, offering.yearlySavingPercent())
        assertTrue(monthly.hasTrial)
        assertTrue(lifetime.isOneTime)
        assertNull(PremiumOffering(listOf(lifetime), null).yearlySavingPercent())
    }
}
