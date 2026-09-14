package com.ampgames.vidsaver.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ampgames.vidsaver.domain.gallery.GallerySort
import com.ampgames.vidsaver.domain.player.PlaybackSpeed
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_prefs",
)

data class PlayerSettings(
    /** Keep audio playing when the app leaves the foreground. */
    val backgroundAudio: Boolean = false,
    val speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,
    val gallerySort: GallerySort = GallerySort.DEFAULT,
)

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<PlayerSettings> = context.playerDataStore.data
        .catch { error ->
            if (error is IOException) {
                Timber.e(error, "Could not read player preferences")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            PlayerSettings(
                backgroundAudio = prefs[KEY_BACKGROUND_AUDIO] ?: false,
                speed = prefs[KEY_SPEED]?.let { PlaybackSpeed.nearest(it) } ?: PlaybackSpeed.DEFAULT,
                gallerySort = GallerySort.fromId(prefs[KEY_GALLERY_SORT]),
            )
        }

    suspend fun setBackgroundAudio(enabled: Boolean) = edit { it[KEY_BACKGROUND_AUDIO] = enabled }

    suspend fun setSpeed(speed: PlaybackSpeed) = edit { it[KEY_SPEED] = speed.value }

    suspend fun setGallerySort(sort: GallerySort) = edit { it[KEY_GALLERY_SORT] = sort.id }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.playerDataStore.edit(block) }
            .onFailure { Timber.e(it, "Could not write player preferences") }
    }

    private companion object {
        val KEY_BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
        val KEY_SPEED = floatPreferencesKey("playback_speed")
        val KEY_GALLERY_SORT = stringPreferencesKey("gallery_sort")
    }
}
