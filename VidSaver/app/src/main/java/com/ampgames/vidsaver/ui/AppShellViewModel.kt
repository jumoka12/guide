package com.ampgames.vidsaver.ui

import androidx.lifecycle.ViewModel
import com.ampgames.vidsaver.data.billing.PaywallCoordinator
import com.ampgames.vidsaver.domain.premium.PaywallSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Hands the app shell the coordinator's paywall requests; nothing else. */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    coordinator: PaywallCoordinator,
) : ViewModel() {
    val paywallRequests: Flow<PaywallSource> = coordinator.requests
}
