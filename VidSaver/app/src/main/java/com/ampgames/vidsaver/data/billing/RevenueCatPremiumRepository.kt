package com.ampgames.vidsaver.data.billing

import android.app.Activity
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.di.ApplicationScope
import com.ampgames.vidsaver.domain.premium.PlanKind
import com.ampgames.vidsaver.domain.premium.PremiumOffering
import com.ampgames.vidsaver.domain.premium.PremiumPlan
import com.ampgames.vidsaver.domain.premium.PremiumState
import com.ampgames.vidsaver.domain.premium.PurchaseOutcome
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.StoreProduct
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * [PremiumRepository] over the RevenueCat SDK.
 *
 * The SDK is configured by the Application before this is used; see
 * [VidSaverApplication.initBilling]. Everything a person reads on the paywall
 * comes out of the store's own product data through [toPlan], so the screen
 * cannot show a price, period or trial the store will not honour.
 */
class RevenueCatPremiumRepository(
    private val appConfig: AppConfig,
    @ApplicationScope private val scope: CoroutineScope,
) : PremiumRepository {

    private val _state = MutableStateFlow(PremiumState.UNKNOWN)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    override val isAvailable: Boolean = true

    /** Packages from the last loaded offering, by product id, for [purchase]. */
    private val packages = ConcurrentHashMap<String, Package>()

    private val entitlementId: String get() = appConfig.iap.entitlement

    /** Call once the SDK is configured: subscribes to changes and reads the cache. */
    fun start() {
        Purchases.sharedInstance.updatedCustomerInfoListener =
            com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { info -> publish(info) }
        scope.launch {
            runCatching { Purchases.sharedInstance.awaitCustomerInfo(CacheFetchPolicy.CACHED_OR_FETCHED) }
                .onSuccess(::publish)
                .onFailure { Timber.w(it, "Could not read entitlements at start") }
        }
    }

    override suspend fun refresh() {
        runCatching { Purchases.sharedInstance.awaitCustomerInfo(CacheFetchPolicy.FETCH_CURRENT) }
            .onSuccess(::publish)
            .onFailure { Timber.w(it, "Could not refresh entitlements") }
    }

    override suspend fun offering(): Result<PremiumOffering> = runCatching {
        val offerings = Purchases.sharedInstance.awaitOfferings()
        val offering: Offering = offerings.current
            ?: offerings.all[DEFAULT_OFFERING]
            ?: error("No current offering is configured in RevenueCat")

        val plans = offering.availablePackages.mapNotNull { pkg ->
            pkg.product.toPlan(pkg.packageType)?.also { packages[it.id] = pkg }
        }.sortedBy { it.kind.ordinal }
        if (plans.isEmpty()) error("The current offering has no packages")

        val default = plans.firstOrNull { it.id == appConfig.iap.defaultSku }?.id ?: plans.first().id
        PremiumOffering(plans = plans, defaultPlanId = default)
    }.onFailure { Timber.w(it, "Could not load the offering") }

    override suspend fun purchase(activity: Activity, planId: String): PurchaseOutcome {
        val pkg = packages[planId]
            ?: return PurchaseOutcome.Failed("Plan $planId is not loaded; open the paywall again")
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            publish(result.customerInfo)
            if (_state.value.isPremium) PurchaseOutcome.Success else PurchaseOutcome.Failed("The store did not grant the entitlement")
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) {
                PurchaseOutcome.Cancelled
            } else {
                Timber.w(e, "Purchase failed: %s", e.code)
                PurchaseOutcome.Failed(e.message ?: e.code.name)
            }
        } catch (e: PurchasesException) {
            Timber.w(e, "Purchase failed: %s", e.code)
            PurchaseOutcome.Failed(e.message ?: e.code.name)
        }
    }

    override suspend fun restore(): Result<PremiumState> = runCatching {
        publish(Purchases.sharedInstance.awaitRestore())
        _state.value
    }.onFailure { Timber.w(it, "Restore failed") }

    private fun publish(info: CustomerInfo) {
        _state.value = info.toPremiumState(entitlementId)
    }

    private fun CustomerInfo.toPremiumState(entitlement: String): PremiumState {
        val active = entitlements[entitlement]?.takeIf { it.isActive }
        return PremiumState(
            isPremium = active != null,
            isKnown = true,
            productId = active?.productIdentifier,
            expiresAt = active?.expirationDate?.time,
            willRenew = active?.willRenew ?: false,
            managementUrl = managementURL?.toString(),
        )
    }

    /**
     * A plan as the paywall will present it. Null for a product the store did
     * not price, which happens when a SKU exists in RevenueCat but not yet in
     * Play; such a plan must not be shown at all.
     */
    private fun StoreProduct.toPlan(packageType: PackageType): PremiumPlan? {
        val price = price
        if (price.formatted.isBlank()) return null
        val kind = when (packageType) {
            PackageType.MONTHLY -> PlanKind.MONTHLY
            PackageType.ANNUAL -> PlanKind.YEARLY
            PackageType.LIFETIME -> PlanKind.LIFETIME
            else -> when {
                id.contains("month", ignoreCase = true) -> PlanKind.MONTHLY
                id.contains("year", ignoreCase = true) || id.contains("annual", ignoreCase = true) -> PlanKind.YEARLY
                id.contains("lifetime", ignoreCase = true) -> PlanKind.LIFETIME
                else -> PlanKind.OTHER
            }
        }
        val trial = subscriptionOptions?.freeTrial?.freePhase?.billingPeriod?.toDays() ?: 0
        return PremiumPlan(
            id = id.substringBefore(':'), // "sku:basePlan" for subscriptions
            kind = kind,
            priceFormatted = price.formatted,
            priceMicros = price.amountMicros,
            currencyCode = price.currencyCode,
            trialDays = trial,
            periodDays = period?.toDays(),
        )
    }

    private fun Period.toDays(): Int = when (unit) {
        Period.Unit.DAY -> value
        Period.Unit.WEEK -> value * 7
        Period.Unit.MONTH -> value * 30
        Period.Unit.YEAR -> value * 365
        else -> 0
    }

    private companion object {
        const val DEFAULT_OFFERING = "default"
    }
}
