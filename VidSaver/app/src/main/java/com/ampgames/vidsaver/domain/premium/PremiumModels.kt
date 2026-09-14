package com.ampgames.vidsaver.domain.premium

/**
 * Whether this install has the `premium` entitlement.
 *
 * [isKnown] is false until the store has answered once: on a cold start with
 * no network the app must neither flash a paywall at a paying customer nor
 * show ads to one, so callers that gate on premium wait for a known state.
 */
data class PremiumState(
    val isPremium: Boolean = false,
    val isKnown: Boolean = false,
    /** Store product behind the entitlement, when active. */
    val productId: String? = null,
    /** Epoch millis when the entitlement lapses; null for lifetime or unknown. */
    val expiresAt: Long? = null,
    val willRenew: Boolean = false,
    /** Where the store lets the person manage the subscription. */
    val managementUrl: String? = null,
) {
    companion object {
        val UNKNOWN = PremiumState()
        val FREE = PremiumState(isPremium = false, isKnown = true)
    }
}

enum class PlanKind { MONTHLY, YEARLY, LIFETIME, OTHER }

/**
 * One purchasable plan as the paywall shows it. Every field a person reads on
 * the screen — price, trial, period — comes from the store, never from a
 * string in the app, so the paywall can never claim a price the store does
 * not charge.
 */
data class PremiumPlan(
    /** The store product id, e.g. `vs_monthly_7d_trial`. */
    val id: String,
    val kind: PlanKind,
    /** Localised price for one billing period, e.g. "$4.99". */
    val priceFormatted: String,
    /** Price in micro-units of [currencyCode], for comparisons. */
    val priceMicros: Long,
    val currencyCode: String,
    /** Days of free trial before the first charge, 0 when there is none. */
    val trialDays: Int = 0,
    /** Length of one billing period in days; null for a one-time purchase. */
    val periodDays: Int? = null,
) {
    val hasTrial: Boolean get() = trialDays > 0
    val isOneTime: Boolean get() = kind == PlanKind.LIFETIME || periodDays == null
}

/** The plans currently on offer, in display order. */
data class PremiumOffering(
    val plans: List<PremiumPlan>,
    /** The plan preselected on the paywall, from `iap.default_sku` when present. */
    val defaultPlanId: String?,
) {
    /** Yearly price per month, as a percentage saved against monthly, when both exist. */
    fun yearlySavingPercent(): Int? {
        val monthly = plans.firstOrNull { it.kind == PlanKind.MONTHLY } ?: return null
        val yearly = plans.firstOrNull { it.kind == PlanKind.YEARLY } ?: return null
        if (monthly.currencyCode != yearly.currencyCode || monthly.priceMicros <= 0) return null
        val yearlyPerMonth = yearly.priceMicros / 12.0
        val saving = 1.0 - yearlyPerMonth / monthly.priceMicros
        return (saving * 100).toInt().takeIf { it in 1..99 }
    }
}

/** Why the paywall is being shown; logged and used for copy. */
enum class PaywallSource {
    LAUNCH,
    RESUME,
    SECOND_DOWNLOAD,
    SETTINGS,
    HOME_CROWN,
    ONBOARDING,
}

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}
