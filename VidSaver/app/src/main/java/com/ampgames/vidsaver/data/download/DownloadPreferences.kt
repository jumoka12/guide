package com.ampgames.vidsaver.data.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "download_prefs",
)

data class DownloadSettings(
    /** Only run downloads on an unmetered connection. */
    val wifiOnly: Boolean = false,
)

@Singleton
class DownloadPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<DownloadSettings> = context.downloadDataStore.data
        .catch { error ->
            if (error is IOException) {
                Timber.e(error, "Could not read download preferences")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> DownloadSettings(wifiOnly = prefs[KEY_WIFI_ONLY] ?: false) }

    suspend fun current(): DownloadSettings = settings.first()

    suspend fun setWifiOnly(enabled: Boolean) {
        runCatching { context.downloadDataStore.edit { it[KEY_WIFI_ONLY] = enabled } }
            .onFailure { Timber.e(it, "Could not write download preferences") }
    }

    private companion object {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }
}
