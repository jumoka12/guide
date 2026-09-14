package com.ampgames.vidsaver.data.billing

import android.app.Activity
import com.ampgames.vidsaver.domain.premium.PremiumOffering
import com.ampgames.vidsaver.domain.premium.PremiumState
import com.ampgames.vidsaver.domain.premium.PurchaseOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stands in when `REVENUECAT_KEY` is empty: the install is free, the state is
 * known, and the paywall reports that plans cannot be loaded. Nothing here
 * pretends to sell anything.
 */
class NoBillingPremiumRepository : PremiumRepository {

    override val state: StateFlow<PremiumState> = MutableStateFlow(PremiumState.FREE)

    override val isAvailable: Boolean = false

    override suspend fun refresh() = Unit

    override suspend fun offering(): Result<PremiumOffering> =
        Result.failure(IllegalStateException("No store SDK key configured (REVENUECAT_KEY)"))

    override suspend fun purchase(activity: Activity, planId: String): PurchaseOutcome =
        PurchaseOutcome.Failed("Purchases are not available in this build")

    override suspend fun restore(): Result<PremiumState> = Result.success(PremiumState.FREE)
}
