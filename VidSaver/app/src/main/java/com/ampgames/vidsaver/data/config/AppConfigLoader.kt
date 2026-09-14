package com.ampgames.vidsaver.data.config

import android.content.Context
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Loads [AppConfig] from the packaged asset. Parsing never throws: a missing or
 * malformed config falls back to [AppConfig.DEFAULT] so the app still starts.
 */
class AppConfigLoader(private val context: Context) {

    fun load(): AppConfig {
        val raw = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            Timber.e(error, "Could not read %s, using defaults", ASSET_PATH)
            return AppConfig.DEFAULT
        }
        return parse(raw)
    }

    companion object {
        const val ASSET_PATH = "config/app_config.json"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** Exposed for unit tests so parsing can be exercised without a Context. */
        fun parse(raw: String): AppConfig = runCatching {
            json.decodeFromString(AppConfig.serializer(), raw)
        }.getOrElse { error ->
            Timber.e(error, "Could not parse app config, using defaults")
            AppConfig.DEFAULT
        }
    }
}
