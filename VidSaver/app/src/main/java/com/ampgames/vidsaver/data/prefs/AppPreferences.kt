package com.ampgames.vidsaver.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/**
 * Small app-wide counters the monetization triggers read: how many sessions
 * and resumes this install has had, which version it first opened, and when
 * an automatic paywall last showed.
 */
data class AppCounters(
    val sessionCount: Int = 0,
    val resumeCount: Int = 0,
    val firstOpenVersionCode: Int = 0,
    val lastAutoPaywallAt: Long = 0L,
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun counters(): AppCounters = runCatching {
        context.appDataStore.data.first().toCounters()
    }.getOrElse { error ->
        if (error !is IOException) throw error
        Timber.e(error, "Could not read app preferences")
        AppCounters()
    }

    /** Records a new session; returns the counters after the change. */
    suspend fun recordSession(currentVersionCode: Int): AppCounters = edit { prefs ->
        prefs[KEY_SESSIONS] = (prefs[KEY_SESSIONS] ?: 0) + 1
        if ((prefs[KEY_FIRST_OPEN_VERSION] ?: 0) == 0) prefs[KEY_FIRST_OPEN_VERSION] = currentVersionCode
    }

    /** Records a return to the foreground that is not the launch. */
    suspend fun recordResume(): AppCounters = edit { prefs ->
        prefs[KEY_RESUMES] = (prefs[KEY_RESUMES] ?: 0) + 1
    }

    suspend fun recordAutoPaywall(at: Long): AppCounters = edit { prefs ->
        prefs[KEY_LAST_AUTO_PAYWALL] = at
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): AppCounters =
        runCatching { context.appDataStore.edit(block).toCounters() }
            .getOrElse { error ->
                Timber.e(error, "Could not write app preferences")
                counters()
            }

    private fun Preferences.toCounters() = AppCounters(
        sessionCount = this[KEY_SESSIONS] ?: 0,
        resumeCount = this[KEY_RESUMES] ?: 0,
        firstOpenVersionCode = this[KEY_FIRST_OPEN_VERSION] ?: 0,
        lastAutoPaywallAt = this[KEY_LAST_AUTO_PAYWALL] ?: 0L,
    )

    private companion object {
        val KEY_SESSIONS = intPreferencesKey("session_count")
        val KEY_RESUMES = intPreferencesKey("resume_count")
        val KEY_FIRST_OPEN_VERSION = intPreferencesKey("first_open_version_code")
        val KEY_LAST_AUTO_PAYWALL = longPreferencesKey("last_auto_paywall_at")

    }
}
