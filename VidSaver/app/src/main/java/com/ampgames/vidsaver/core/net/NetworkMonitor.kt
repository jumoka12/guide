package com.ampgames.vidsaver.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

data class NetworkState(
    val connected: Boolean,
    /** False on cellular or any other connection the system reports as metered. */
    val unmetered: Boolean,
) {
    companion object {
        val DISCONNECTED = NetworkState(connected = false, unmetered = false)
    }
}

/**
 * Current connectivity, used to honour the Wi-Fi-only download preference and
 * to hold downloads while offline.
 *
 * "Unmetered" comes from [NetworkCapabilities.NET_CAPABILITY_NOT_METERED] rather
 * than a transport check, so a metered Wi-Fi hotspot is correctly treated as
 * metered and an unmetered Ethernet connection is not excluded.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService()

    fun current(): NetworkState {
        val manager = connectivityManager ?: return NetworkState.DISCONNECTED
        val capabilities = runCatching {
            manager.getNetworkCapabilities(manager.activeNetwork)
        }.getOrNull() ?: return NetworkState.DISCONNECTED
        return capabilities.toState()
    }

    /** True when downloads are allowed to run right now. */
    fun allowsDownload(wifiOnly: Boolean): Boolean {
        val state = current()
        return state.connected && (!wifiOnly || state.unmetered)
    }

    val state: Flow<NetworkState> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(NetworkState.DISCONNECTED)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(capabilities.toState())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { manager.registerNetworkCallback(request, callback) }
            .onFailure { Timber.w(it, "Could not register the network callback") }

        trySend(current())

        awaitClose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }.conflate().distinctUntilChanged()

    private fun NetworkCapabilities.toState() = NetworkState(
        connected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
