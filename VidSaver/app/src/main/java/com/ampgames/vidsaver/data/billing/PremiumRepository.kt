package com.ampgames.vidsaver.data.billing

import android.app.Activity
import com.ampgames.vidsaver.domain.premium.PremiumOffering
import com.ampgames.vidsaver.domain.premium.PremiumState
import com.ampgames.vidsaver.domain.premium.PurchaseOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's one view of whether the person is premium and how to become it.
 *
 * Implemented over RevenueCat in production and by a no-op when the SDK key is
 * missing, so a debug build without keys still runs — as free, with a
 * paywall that explains it cannot load plans.
 */
interface PremiumRepository {

    /** App-wide premium state. Ads and download limits read this. */
    val state: StateFlow<PremiumState>

    /** False when no store SDK is configured; the paywall says so. */
    val isAvailable: Boolean

    /** Asks the store for the current entitlement; safe to call often. */
    suspend fun refresh()

    /** The plans to show, straight from the store. */
    suspend fun offering(): Result<PremiumOffering>

    /** Runs the store's purchase flow for [planId] on top of [activity]. */
    suspend fun purchase(activity: Activity, planId: String): PurchaseOutcome

    /** Re-checks purchases made on another device or before a reinstall. */
    suspend fun restore(): Result<PremiumState>
}
