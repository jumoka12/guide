package com.ampgames.vidsaver.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed view of `assets/config/app_config.json`.
 *
 * Every monetization and engagement behaviour in the app reads from this object;
 * nothing about ad cadence, paywall triggers or blocked domains is hardcoded in
 * feature code. Every field carries a default so a partial or future config
 * still parses.
 */
@Serializable
data class AppConfig(
    val ads: AdsConfig = AdsConfig(),
    val iap: IapConfig = IapConfig(),
    val engagement: EngagementConfig = EngagementConfig(),
    val browser: BrowserConfig = BrowserConfig(),
    val analytics: AnalyticsConfig = AnalyticsConfig(),
    val links: LinksConfig = LinksConfig(),
) {
    companion object {
        /** Used when the asset is missing or unparseable. Mirrors the shipped JSON. */
        val DEFAULT: AppConfig = AppConfig()
    }
}

@Serializable
data class AdsConfig(
    val enabled: Boolean = true,
    /** 0 = never, 1 = first session only, 2 = every launch. */
    @SerialName("launch_route_mode") val launchRouteMode: Int = 2,
    @SerialName("postpone_launch_route_sessions") val postponeLaunchRouteSessions: Int = 0,
    @SerialName("interstitial_launch_ready_only") val interstitialLaunchReadyOnly: Boolean = true,
    @SerialName("interstitial_capping_minutes") val interstitialCappingMinutes: Int = 40,
    /** 0 = off, 1 = interstitial after a download completes. */
    @SerialName("happy_moment_mode") val happyMomentMode: Int = 1,
    @SerialName("happy_moment_capping") val happyMomentCapping: Int = 2,
    @SerialName("mrec_refresh_after_impressions") val mrecRefreshAfterImpressions: Int = 1,
    /** 0 = plain exit dialog, 1 = exit dialog with a native ad. */
    @SerialName("exit_dialog_mode") val exitDialogMode: Int = 1,
    @SerialName("post_bidding_enabled") val postBiddingEnabled: Boolean = true,
    @SerialName("max_units") val maxUnits: MaxUnits = MaxUnits(),
    @SerialName("gam_units") val gamUnits: GamUnits = GamUnits(),
)

@Serializable
data class MaxUnits(
    val interstitial: String = "",
    @SerialName("mrec_banner") val mrecBanner: String = "",
    @SerialName("mrec_native") val mrecNative: String = "",
    @SerialName("exit_native") val exitNative: String = "",
    @SerialName("small_banner") val smallBanner: String = "",
)

@Serializable
data class GamUnits(
    val interstitial: String = "",
    val native: String = "",
    @SerialName("exit_native") val exitNative: String = "",
)

@Serializable
data class IapConfig(
    val provider: String = "revenuecat",
    val entitlement: String = "premium",
    @SerialName("default_sku") val defaultSku: String = "vs_monthly_7d_trial",
    /** Show the paywall on every N-th app resume. 0 disables the trigger. */
    @SerialName("offer_on_resume_every") val offerOnResumeEvery: Int = 3,
    @SerialName("offer_new_installers_only") val offerNewInstallersOnly: Boolean = false,
    @SerialName("onboarding_paywall") val onboardingPaywall: Boolean = true,
)

@Serializable
data class EngagementConfig(
    /** 0 = never ask for a review, 1 = Google In-App Review. */
    @SerialName("rateus_mode") val rateUsMode: Int = 1,
    @SerialName("rateus_session_start") val rateUsSessionStart: Int = 5,
)

@Serializable
data class BrowserConfig(
    /**
     * Domains the browser refuses to load. YouTube and other Google-owned video
     * domains are hard-blocked in code as well (see `YouTubeBlocker`); this list
     * cannot be used to re-enable them.
     */
    @SerialName("blocked_domains")
    val blockedDomains: List<String> = listOf(
        "youtube.com",
        "youtu.be",
        "m.youtube.com",
        "music.youtube.com",
    ),
    @SerialName("home_shortcuts")
    val homeShortcuts: List<String> = listOf(
        "tiktok.com",
        "instagram.com",
        "x.com",
        "facebook.com",
        "pinterest.com",
        "vimeo.com",
        "dailymotion.com",
    ),
    @SerialName("default_search_engine") val defaultSearchEngine: String = "google",
)

@Serializable
data class AnalyticsConfig(
    val providers: List<String> = listOf("firebase"),
    @SerialName("singular_enabled") val singularEnabled: Boolean = false,
)

@Serializable
data class LinksConfig(
    @SerialName("privacy_policy") val privacyPolicy: String = "https://ampgames.com/privacy",
    val terms: String = "https://ampgames.com/terms",
    @SerialName("support_email") val supportEmail: String = "support@ampgames.com",
)
