package com.ampgames.vidsaver.ui.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ampgames.vidsaver.data.billing.PremiumRepository
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.domain.premium.PaywallSource
import com.ampgames.vidsaver.domain.premium.PremiumOffering
import com.ampgames.vidsaver.domain.premium.PremiumPlan
import com.ampgames.vidsaver.domain.premium.PurchaseOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class PaywallUiState(
    val source: PaywallSource = PaywallSource.SETTINGS,
    val isLoading: Boolean = true,
    val plans: List<PremiumPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val yearlySavingPercent: Int? = null,
    /** Store unavailable or offering failed; the paywall says so, no dead button. */
    val loadError: String? = null,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val isPremium: Boolean = false,
    /** A one-shot message: purchase failed, nothing to restore, restored. */
    val message: String? = null,
    val privacyUrl: String = "",
    val termsUrl: String = "",
) {
    val selectedPlan: PremiumPlan? get() = plans.firstOrNull { it.id == selectedPlanId }
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val premium: PremiumRepository,
    appConfig: AppConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PaywallUiState(
            source = savedStateHandle.get<String>(ARG_SOURCE)
                ?.let { runCatching { PaywallSource.valueOf(it) }.getOrNull() }
                ?: PaywallSource.SETTINGS,
            privacyUrl = appConfig.links.privacyPolicy,
            termsUrl = appConfig.links.terms,
        ),
    )
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        Timber.i("paywall_view source=%s", _uiState.value.source)
        viewModelScope.launch {
            premium.state.collect { state -> _uiState.update { it.copy(isPremium = state.isPremium) } }
        }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            premium.offering()
                .onSuccess { offering -> _uiState.update { it.applyOffering(offering) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, loadError = error.message ?: "Could not load plans") }
                }
        }
    }

    fun selectPlan(planId: String) {
        _uiState.update { it.copy(selectedPlanId = planId) }
    }

    fun purchase(activity: Activity) {
        val plan = _uiState.value.selectedPlan ?: return
        if (_uiState.value.isPurchasing) return
        _uiState.update { it.copy(isPurchasing = true, message = null) }
        viewModelScope.launch {
            when (val outcome = premium.purchase(activity, plan.id)) {
                PurchaseOutcome.Success -> {
                    Timber.i(if (plan.hasTrial) "trial_start %s" else "purchase %s", plan.id)
                    _uiState.update { it.copy(isPurchasing = false, isPremium = true) }
                }
                PurchaseOutcome.Cancelled -> _uiState.update { it.copy(isPurchasing = false) }
                is PurchaseOutcome.Failed -> _uiState.update {
                    it.copy(isPurchasing = false, message = outcome.message)
                }
            }
        }
    }

    fun restore() {
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(isRestoring = true, message = null) }
        viewModelScope.launch {
            premium.restore()
                .onSuccess { state ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            isPremium = state.isPremium,
                            message = if (state.isPremium) null else MESSAGE_NOTHING_TO_RESTORE,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isRestoring = false, message = error.message ?: "Restore failed") }
                }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun PaywallUiState.applyOffering(offering: PremiumOffering) = copy(
        isLoading = false,
        plans = offering.plans,
        selectedPlanId = selectedPlanId ?: offering.defaultPlanId,
        yearlySavingPercent = offering.yearlySavingPercent(),
        loadError = null,
    )

    companion object {
        const val ARG_SOURCE = "source"

        /** Sentinel the screen maps to a string resource. */
        const val MESSAGE_NOTHING_TO_RESTORE = "nothing_to_restore"
    }
}
