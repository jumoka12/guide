package com.ampgames.vidsaver.data.browser.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.domain.browser.SearchEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browser_prefs",
)

data class BrowserSettings(
    val searchEngine: SearchEngine,
    val desktopMode: Boolean,
    val adBlockEnabled: Boolean,
)

@Singleton
class BrowserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfig: AppConfig,
) {

    val settings: Flow<BrowserSettings> = context.browserDataStore.data
        .catch { error ->
            // A corrupt preferences file must not take the browser down.
            if (error is IOException) {
                Timber.e(error, "Could not read browser preferences")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            BrowserSettings(
                searchEngine = SearchEngine.fromId(
                    prefs[KEY_SEARCH_ENGINE] ?: appConfig.browser.defaultSearchEngine,
                ),
                desktopMode = prefs[KEY_DESKTOP_MODE] ?: false,
                adBlockEnabled = prefs[KEY_AD_BLOCK] ?: true,
            )
        }

    suspend fun setSearchEngine(engine: SearchEngine) = edit { it[KEY_SEARCH_ENGINE] = engine.id }

    suspend fun setDesktopMode(enabled: Boolean) = edit { it[KEY_DESKTOP_MODE] = enabled }

    suspend fun setAdBlockEnabled(enabled: Boolean) = edit { it[KEY_AD_BLOCK] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.browserDataStore.edit(block) }
            .onFailure { Timber.e(it, "Could not write browser preferences") }
    }

    private companion object {
        val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val KEY_DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val KEY_AD_BLOCK = booleanPreferencesKey("ad_block_enabled")
    }
}
