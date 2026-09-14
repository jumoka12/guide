package com.ampgames.vidsaver.data.browser.adblock

import android.content.Context
import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.di.ApplicationScope
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.data.browser.db.AllowlistDao
import com.ampgames.vidsaver.data.browser.db.AllowlistEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Decides whether a WebView subresource request should be blocked.
 *
 * The blocklist is parsed off the main thread at startup; until it is ready
 * [shouldBlock] returns false, so the first page load is never delayed waiting
 * on it. Blocking is per-site overridable: an allowlisted page host disables
 * blocking for everything that page loads.
 */
@Singleton
class AdBlocker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val allowlistDao: AllowlistDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _blocklist = MutableStateFlow(HostBlocklist.EMPTY)
    val blocklist: StateFlow<HostBlocklist> = _blocklist

    /**
     * Page hosts the user has switched blocking off for. Kept as a snapshot set
     * because [shouldBlock] is called from the WebView's network thread and must
     * not suspend.
     */
    private val allowlistedHosts: StateFlow<Set<String>> = allowlistDao.observeAll()
        .map { entries -> entries.map { it.host }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val parsed = withContext(ioDispatcher) {
            runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                    HostBlocklist.parse(reader.lineSequence())
                }
            }.getOrElse { error ->
                Timber.e(error, "Could not load %s; ad blocking disabled", ASSET_PATH)
                HostBlocklist.EMPTY
            }
        }
        Timber.d("Ad blocklist loaded with %d hosts", parsed.size)
        _blocklist.value = parsed
    }

    /**
     * @param requestUrl the subresource being requested.
     * @param pageUrl the URL of the page making the request, used for the
     *   per-site allowlist. A blocked host is still allowed when it *is* the
     *   page the user navigated to, so blocking can never strand the user on a
     *   blank page.
     */
    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val requestHost = Urls.host(requestUrl) ?: return false
        val pageHost = pageUrl?.let { Urls.host(it) }

        if (pageHost != null && isAllowlisted(pageHost)) return false
        if (pageHost != null && requestHost == pageHost) return false

        return _blocklist.value.blocksHost(requestHost)
    }

    fun isAllowlisted(host: String): Boolean {
        val allowed = allowlistedHosts.value
        if (allowed.isEmpty()) return false
        return Urls.hostAndParents(host).any { it in allowed }
    }

    suspend fun setAllowlisted(host: String, allowlisted: Boolean) {
        if (allowlisted) allowlistDao.insert(AllowlistEntry(host)) else allowlistDao.delete(host)
    }

    private companion object {
        const val ASSET_PATH = "hosts_blocklist.txt"
    }
}
