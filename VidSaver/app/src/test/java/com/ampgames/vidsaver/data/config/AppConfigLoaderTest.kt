package com.ampgames.vidsaver.data.config

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigLoaderTest {

    @Test
    fun `shipped asset parses into the documented values`() {
        val config = AppConfigLoader.parse(shippedConfigJson())

        assertTrue(config.ads.enabled)
        assertEquals(2, config.ads.launchRouteMode)
        assertEquals(0, config.ads.postponeLaunchRouteSessions)
        assertTrue(config.ads.interstitialLaunchReadyOnly)
        assertEquals(40, config.ads.interstitialCappingMinutes)
        assertEquals(1, config.ads.happyMomentMode)
        assertEquals(2, config.ads.happyMomentCapping)
        assertEquals(1, config.ads.mrecRefreshAfterImpressions)
        assertEquals(1, config.ads.exitDialogMode)
        assertTrue(config.ads.postBiddingEnabled)

        assertEquals("MAX_INTERSTITIAL_ID", config.ads.maxUnits.interstitial)
        assertEquals("MAX_MREC_ID", config.ads.maxUnits.mrecBanner)
        assertEquals("MAX_NATIVE_ID", config.ads.maxUnits.mrecNative)
        assertEquals("MAX_EXIT_NATIVE_ID", config.ads.maxUnits.exitNative)
        assertEquals("MAX_SMALL_BANNER_ID", config.ads.maxUnits.smallBanner)

        assertEquals("/NETWORK_CODE/VidSaver_Interstitial", config.ads.gamUnits.interstitial)
        assertEquals("/NETWORK_CODE/VidSaver_Native", config.ads.gamUnits.native)
        assertEquals("/NETWORK_CODE/VidSaver_Exit_Native", config.ads.gamUnits.exitNative)

        assertEquals("revenuecat", config.iap.provider)
        assertEquals("premium", config.iap.entitlement)
        assertEquals("vs_monthly_7d_trial", config.iap.defaultSku)
        assertEquals(3, config.iap.offerOnResumeEvery)
        assertFalse(config.iap.offerNewInstallersOnly)
        assertTrue(config.iap.onboardingPaywall)

        assertEquals(1, config.engagement.rateUsMode)
        assertEquals(5, config.engagement.rateUsSessionStart)

        assertEquals("google", config.browser.defaultSearchEngine)
        assertEquals(7, config.browser.homeShortcuts.size)

        assertEquals(listOf("firebase"), config.analytics.providers)
        assertFalse(config.analytics.singularEnabled)

        assertEquals("https://ampgames.com/privacy", config.links.privacyPolicy)
        assertEquals("https://ampgames.com/terms", config.links.terms)
        assertEquals("support@ampgames.com", config.links.supportEmail)
    }

    /**
     * Compliance guard: the shipped config must block every Google-owned video
     * domain. Dropping one from the asset fails this test.
     */
    @Test
    fun `shipped asset blocks every google owned video domain`() {
        val blocked = AppConfigLoader.parse(shippedConfigJson()).browser.blockedDomains
        listOf("youtube.com", "youtu.be", "m.youtube.com", "music.youtube.com")
            .forEach { domain ->
                assertTrue("$domain must be blocked", blocked.contains(domain))
            }
    }

    @Test
    fun `malformed json falls back to defaults instead of throwing`() {
        val config = AppConfigLoader.parse("{ not valid json")
        assertEquals(AppConfig.DEFAULT, config)
    }

    @Test
    fun `unknown keys are ignored so a newer config still loads`() {
        val config = AppConfigLoader.parse(
            """{"ads":{"enabled":false,"some_future_flag":true},"unknown_section":{}}""",
        )
        assertFalse(config.ads.enabled)
        // Unspecified sections keep their defaults.
        assertEquals("premium", config.iap.entitlement)
    }

    @Test
    fun `missing sections fall back to per-field defaults`() {
        val config = AppConfigLoader.parse("{}")
        assertEquals(AppConfig.DEFAULT, config)
        assertEquals(40, config.ads.interstitialCappingMinutes)
    }

    private fun shippedConfigJson(): String {
        // Unit tests run with the module directory as the working directory.
        val candidates = listOf(
            File("src/main/assets/${AppConfigLoader.ASSET_PATH}"),
            File("app/src/main/assets/${AppConfigLoader.ASSET_PATH}"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate ${AppConfigLoader.ASSET_PATH}; looked in $candidates")
        return file.readText()
    }
}
