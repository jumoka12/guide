package com.ampgames.vidsaver.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.data.billing.PremiumRepository
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.domain.premium.PremiumState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val privacyPolicyUrl: String = "",
    val termsUrl: String = "",
    val supportEmail: String = "",
    val premium: PremiumState = PremiumState.UNKNOWN,
    val billingAvailable: Boolean = true,
    val isRestoring: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    appConfig: AppConfig,
    private val premiumRepository: PremiumRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            privacyPolicyUrl = appConfig.links.privacyPolicy,
            termsUrl = appConfig.links.terms,
            supportEmail = appConfig.links.supportEmail,
            billingAvailable = premiumRepository.isAvailable,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            premiumRepository.state.collect { state -> _uiState.update { it.copy(premium = state) } }
        }
    }

    fun restorePurchases() {
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            val result = premiumRepository.restore()
            _uiState.update { it.copy(isRestoring = false) }
            emit(
                when {
                    result.isFailure -> R.string.msg_restore_failed
                    result.getOrNull()?.isPremium == true -> R.string.msg_restore_done
                    else -> R.string.msg_restore_nothing
                },
            )
        }
    }

    private fun emit(@StringRes resId: Int) {
        viewModelScope.launch { _messages.send(resId) }
    }
}
