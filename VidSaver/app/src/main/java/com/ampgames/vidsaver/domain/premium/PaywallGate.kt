package com.ampgames.vidsaver.domain.premium

/**
 * When the paywall shows itself. Every rule here reads from config; nothing
 * about cadence is hardcoded in the screens that call it.
 *
 * Automatic triggers (launch, resume, second download) never fire for a
 * premium install, never fire twice within [minAutoIntervalMs], and respect
 * `offer_new_installers_only`. A person asking for the paywall (Settings, the
 * crown) always gets it.
 *
 * `ads.launch_route_mode` is shared with the launch interstitial:
 *
 * | mode | at launch |
 * |------|-----------|
 * | 0    | nothing |
 * | 1    | interstitial only |
 * | 2    | paywall, then the interstitial if the paywall was skipped |
 * | 3    | paywall only |
 */
class PaywallGate(
    private val launchRouteMode: Int,
    private val postponeLaunchRouteSessions: Int,
    private val offerOnResumeEvery: Int,
    private val offerNewInstallersOnly: Boolean,
    private val minAutoIntervalMs: Long = DEFAULT_MIN_AUTO_INTERVAL_MS,
) {

    /** Whether automatic offers apply to this install at all. */
    fun offersAllowed(state: PremiumState, firstOpenVersionCode: Int, currentVersionCode: Int): Boolean {
        if (!state.isKnown || state.isPremium) return false
        if (offerNewInstallersOnly && firstOpenVersionCode != currentVersionCode) return false
        return true
    }

    /**
     * @param session 1-based count of app sessions including this one.
     * @param lastAutoShownAt epoch millis of the last automatic paywall, 0 if never.
     */
    fun shouldShowOnLaunch(session: Int, lastAutoShownAt: Long, now: Long): Boolean {
        if (launchRouteMode != MODE_PAYWALL_THEN_INTERSTITIAL && launchRouteMode != MODE_PAYWALL_ONLY) return false
        if (session <= postponeLaunchRouteSessions) return false
        return now - lastAutoShownAt >= minAutoIntervalMs
    }

    /** @param resume 1-based count of returns to the foreground, excluding the launch. */
    fun shouldShowOnResume(resume: Int, lastAutoShownAt: Long, now: Long): Boolean {
        if (offerOnResumeEvery <= 0 || resume <= 0) return false
        if (resume % offerOnResumeEvery != 0) return false
        return now - lastAutoShownAt >= minAutoIntervalMs
    }

    /** A free install starting a download while another is already running. */
    fun shouldShowForConcurrentDownload(othersRunning: Int, lastAutoShownAt: Long, now: Long): Boolean {
        if (othersRunning < 1) return false
        return now - lastAutoShownAt >= minAutoIntervalMs
    }

    /** Whether the launch interstitial (Phase 6) follows the launch paywall. */
    fun launchInterstitialFollowsPaywall(): Boolean = launchRouteMode == MODE_PAYWALL_THEN_INTERSTITIAL

    companion object {
        const val MODE_NONE = 0
        const val MODE_INTERSTITIAL_ONLY = 1
        const val MODE_PAYWALL_THEN_INTERSTITIAL = 2
        const val MODE_PAYWALL_ONLY = 3

        /** Launch and a resume a few seconds later must not stack two paywalls. */
        const val DEFAULT_MIN_AUTO_INTERVAL_MS = 2 * 60 * 1000L

        /** Free installs run one download at a time; premium runs several. */
        const val FREE_CONCURRENT_DOWNLOADS = 1
        const val PREMIUM_CONCURRENT_DOWNLOADS = 3

        fun concurrentDownloadsFor(state: PremiumState): Int =
            if (state.isPremium) PREMIUM_CONCURRENT_DOWNLOADS else FREE_CONCURRENT_DOWNLOADS
    }
}
