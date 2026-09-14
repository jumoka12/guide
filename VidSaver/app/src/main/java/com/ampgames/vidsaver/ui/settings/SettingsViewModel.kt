package com.ampgames.vidsaver.ui.settings

import androidx.lifecycle.ViewModel
import com.ampgames.vidsaver.data.config.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val privacyPolicyUrl: String = "",
    val termsUrl: String = "",
    val supportEmail: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    appConfig: AppConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            privacyPolicyUrl = appConfig.links.privacyPolicy,
            termsUrl = appConfig.links.terms,
            supportEmail = appConfig.links.supportEmail,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
}
