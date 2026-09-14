package com.ampgames.vidsaver.ui.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ampgames.vidsaver.domain.premium.PlanKind
import com.ampgames.vidsaver.domain.premium.PremiumPlan
import com.ampgames.vidsaver.ui.theme.VidSaverTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The paywall's policy promises, checked against the stateless screen: the
 * store's price and trial length are on screen, and the free-tier exit is
 * visible and usable in every state, including while plans are still loading
 * and after they failed to load.
 */
class PaywallScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val monthly = PremiumPlan("vs_monthly_7d_trial", PlanKind.MONTHLY, "$4.99", 4_990_000L, "USD", trialDays = 7, periodDays = 30)
    private val yearly = PremiumPlan("vs_yearly", PlanKind.YEARLY, "$29.99", 29_990_000L, "USD", periodDays = 365)
    private val lifetime = PremiumPlan("vs_lifetime", PlanKind.LIFETIME, "$59.99", 59_990_000L, "USD")

    private fun show(state: PaywallUiState, onClose: () -> Unit = {}, onSelect: (String) -> Unit = {}) {
        composeRule.setContent {
            VidSaverTheme {
                PaywallScreen(
                    state = state,
                    onSelectPlan = onSelect,
                    onPurchase = {},
                    onRestore = {},
                    onRetry = {},
                    onMessageShown = {},
                    onClose = onClose,
                    onOpenUrl = {},
                )
            }
        }
    }

    @Test
    fun pricesAndTrialComeFromTheStoreAndAreVisible() {
        show(
            PaywallUiState(
                isLoading = false,
                plans = listOf(monthly, yearly, lifetime),
                selectedPlanId = monthly.id,
                yearlySavingPercent = 49,
            ),
        )
        composeRule.onNodeWithText("$4.99").assertIsDisplayed()
        composeRule.onNodeWithText("$29.99").assertIsDisplayed()
        composeRule.onNodeWithText("$59.99").assertIsDisplayed()
        composeRule.onNodeWithText("7-day free trial").assertIsDisplayed()
        composeRule.onNodeWithTag("paywall_terms").assertIsDisplayed()
        composeRule.onNodeWithTag(PAYWALL_PURCHASE_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun theFreeExitIsAlwaysVisibleAndWorks() {
        var closed = 0
        show(PaywallUiState(isLoading = true), onClose = { closed++ })
        composeRule.onNodeWithTag(PAYWALL_CONTINUE_FREE_TEST_TAG).assertIsDisplayed().performClick()
        assertEquals(1, closed)
    }

    @Test
    fun aFailedLoadStillOffersTheFreeExitAndRestore() {
        show(PaywallUiState(isLoading = false, loadError = "No store SDK key configured"))
        composeRule.onNodeWithTag(PAYWALL_CONTINUE_FREE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PAYWALL_RESTORE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun tappingAPlanSelectsIt() {
        var selected: String? = null
        show(
            PaywallUiState(isLoading = false, plans = listOf(monthly, yearly), selectedPlanId = monthly.id),
            onSelect = { selected = it },
        )
        composeRule.onNodeWithTag("plan_vs_yearly").performClick()
        assertEquals("vs_yearly", selected)
    }
}
