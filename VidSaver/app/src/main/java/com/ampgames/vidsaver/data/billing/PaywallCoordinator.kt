package com.ampgames.vidsaver.data.billing

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ampgames.vidsaver.BuildConfig
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.data.prefs.AppPreferences
import com.ampgames.vidsaver.di.ApplicationScope
import com.ampgames.vidsaver.domain.premium.PaywallGate
import com.ampgames.vidsaver.domain.premium.PaywallSource
import com.ampgames.vidsaver.domain.premium.PremiumState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Decides when the paywall appears and tells the UI to show it.
 *
 * Owns the session and resume counters (through the process lifecycle) and
 * applies [PaywallGate] to them. The UI only collects [requests] and
 * navigates; it never reasons about cadence itself. A request the person
 * made — from Settings or the crown — is always honoured.
 */
@Singleton
class PaywallCoordinator @Inject constructor(
    appConfig: AppConfig,
    private val premium: PremiumRepository,
    private val preferences: AppPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val gate = PaywallGate(
        launchRouteMode = appConfig.ads.launchRouteMode,
        postponeLaunchRouteSessions = appConfig.ads.postponeLaunchRouteSessions,
        offerOnResumeEvery = appConfig.iap.offerOnResumeEvery,
        offerNewInstallersOnly = appConfig.iap.offerNewInstallersOnly,
    )

    private val _requests = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 1)

    /** Emits once per paywall to show. Collected by the app shell. */
    val requests: SharedFlow<PaywallSource> = _requests.asSharedFlow()

    private var launched = false

    /** Registers for foreground transitions. Called once from the Application. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!launched) {
            launched = true
            onSessionStarted()
        } else {
            onResumed()
        }
    }

    /** The person asked. No gating beyond "not already premium". */
    fun request(source: PaywallSource) {
        scope.launch {
            if (premium.state.value.isPremium) return@launch
            _requests.emit(source)
        }
    }

    /**
     * A free install just started a download while another is running.
     * The download proceeds regardless — it queues behind the first — and
     * this is the moment to explain what premium adds.
     */
    fun onConcurrentDownload(othersRunning: Int) {
        scope.launch {
            val state = premium.state.value
            val counters = preferences.counters()
            if (!gate.offersAllowed(state, counters.firstOpenVersionCode, BuildConfig.VERSION_CODE)) return@launch
            if (!gate.shouldShowForConcurrentDownload(othersRunning, counters.lastAutoPaywallAt, now())) return@launch
            showAutomatically(PaywallSource.SECOND_DOWNLOAD)
        }
    }

    private fun onSessionStarted() {
        scope.launch {
            val counters = preferences.recordSession(BuildConfig.VERSION_CODE)
            val state = awaitKnownState() ?: return@launch
            if (!gate.offersAllowed(state, counters.firstOpenVersionCode, BuildConfig.VERSION_CODE)) return@launch
            if (gate.shouldShowOnLaunch(counters.sessionCount, counters.lastAutoPaywallAt, now())) {
                showAutomatically(PaywallSource.LAUNCH)
            }
        }
    }

    private fun onResumed() {
        scope.launch {
            val counters = preferences.recordResume()
            val state = awaitKnownState() ?: return@launch
            if (!gate.offersAllowed(state, counters.firstOpenVersionCode, BuildConfig.VERSION_CODE)) return@launch
            if (gate.shouldShowOnResume(counters.resumeCount, counters.lastAutoPaywallAt, now())) {
                showAutomatically(PaywallSource.RESUME)
            }
        }
    }

    private suspend fun showAutomatically(source: PaywallSource) {
        preferences.recordAutoPaywall(now())
        Timber.i("Paywall requested automatically: %s", source)
        _requests.emit(source)
    }

    /**
     * Waits briefly for the store to say whether this install is premium. A
     * paying customer must never see the paywall because the answer arrived
     * a second late; an offline free install simply gets no offer this time.
     */
    private suspend fun awaitKnownState(): PremiumState? =
        withTimeoutOrNull(STATE_TIMEOUT_MS) { premium.state.filter { it.isKnown }.first() }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val STATE_TIMEOUT_MS = 4_000L
    }
}
