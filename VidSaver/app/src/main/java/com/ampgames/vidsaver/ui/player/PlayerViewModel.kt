package com.ampgames.vidsaver.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ampgames.vidsaver.data.gallery.GalleryRepository
import com.ampgames.vidsaver.data.player.PlayerPreferences
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import com.ampgames.vidsaver.domain.player.DurationFormat
import com.ampgames.vidsaver.domain.player.PlaybackSpeed
import com.ampgames.vidsaver.domain.player.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val playlist: Playlist = Playlist.EMPTY,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,
    val backgroundAudio: Boolean = false,
    val controlsVisible: Boolean = true,
    val isLoading: Boolean = true,
    /** Non-null while a gesture is in flight, e.g. "+00:15" or "Volume 60%". */
    val gestureOverlay: String? = null,
) {
    val current: GalleryVideo? get() = playlist.current
    val title: String get() = current?.displayName.orEmpty()
    val hasNext: Boolean get() = playlist.hasNext
    val hasPrevious: Boolean get() = playlist.hasPrevious
    val positionLabel: String get() = DurationFormat.positionOfDuration(positionMs, durationMs)

    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Holds what the player *shows*. The player itself lives in
 * [com.ampgames.vidsaver.data.player.PlaybackService] and is reached through a
 * MediaController, so playback survives this ViewModel being cleared.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: GalleryRepository,
    private val preferences: PlayerPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val startVideoId: Long = savedStateHandle.get<String>(ARG_VIDEO_ID)?.toLongOrNull() ?: -1L

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = preferences.settings.first()
            _uiState.update {
                it.copy(speed = settings.speed, backgroundAudio = settings.backgroundAudio)
            }
        }

        viewModelScope.launch {
            repository.videos.collect { videos ->
                val sorted = preferences.settings.first().gallerySort.apply(videos)
                _uiState.update { state ->
                    val playlist = if (state.playlist.items.isEmpty()) {
                        Playlist.startingAt(sorted, startVideoId)
                    } else {
                        // Keep the same video playing when the library changes
                        // underneath us.
                        state.playlist.syncedWith(sorted)
                    }
                    state.copy(playlist = playlist, isLoading = false)
                }
            }
        }
    }

    // ------------------------------------------------- reported by the player

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
    }

    fun onPositionChanged(positionMs: Long, durationMs: Long, bufferedMs: Long) {
        _uiState.update {
            it.copy(
                positionMs = positionMs,
                durationMs = durationMs.coerceAtLeast(0L),
                bufferedMs = bufferedMs,
            )
        }
    }

    /** The player advanced on its own; keep the playlist index in step. */
    fun onMediaItemIndexChanged(index: Int) {
        _uiState.update { it.copy(playlist = it.playlist.jumpTo(index)) }
    }

    // ------------------------------------------------------------ user intent

    fun onNext() {
        _uiState.update { it.copy(playlist = it.playlist.next()) }
    }

    fun onPrevious() {
        _uiState.update { it.copy(playlist = it.playlist.previous()) }
    }

    fun onCycleSpeed() {
        val next = _uiState.value.speed.next()
        _uiState.update { it.copy(speed = next) }
        viewModelScope.launch { preferences.setSpeed(next) }
    }

    fun onToggleBackgroundAudio() {
        val enabled = !_uiState.value.backgroundAudio
        _uiState.update { it.copy(backgroundAudio = enabled) }
        viewModelScope.launch { preferences.setBackgroundAudio(enabled) }
    }

    fun onToggleControls() {
        _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun onControlsVisibilityChanged(visible: Boolean) {
        _uiState.update { it.copy(controlsVisible = visible) }
    }

    fun onGestureOverlay(text: String?) {
        _uiState.update { it.copy(gestureOverlay = text) }
    }

    companion object {
        const val ARG_VIDEO_ID = "videoId"
    }
}
