package com.ampgames.vidsaver.ui.gallery

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.data.gallery.GalleryRepository
import com.ampgames.vidsaver.data.player.PlayerPreferences
import com.ampgames.vidsaver.domain.gallery.GallerySelection
import com.ampgames.vidsaver.domain.gallery.GallerySort
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class GalleryUiState(
    val videos: List<GalleryVideo> = emptyList(),
    val selection: GallerySelection = GallerySelection.EMPTY,
    val sort: GallerySort = GallerySort.DEFAULT,
    val isLoading: Boolean = true,
    val renaming: GalleryVideo? = null,
) {
    val isEmpty: Boolean get() = !isLoading && videos.isEmpty()
    val inSelectionMode: Boolean get() = selection.isActive
    val selectedCount: Int get() = selection.count
    val allSelected: Boolean
        get() = videos.isNotEmpty() && selection.selectedIds.containsAll(videos.map { it.id })
}

data class GalleryMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
    val id: Long = System.nanoTime(),
)

/** Asks the screen to fire a share sheet; the ViewModel never touches Intents. */
data class ShareRequest(val videos: List<GalleryVideo>, val id: Long = System.nanoTime())

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: GalleryRepository,
    private val preferences: PlayerPreferences,
) : ViewModel() {

    private val selection = MutableStateFlow(GallerySelection.EMPTY)
    private val renaming = MutableStateFlow<GalleryVideo?>(null)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _messages = Channel<GalleryMessage>(Channel.BUFFERED)
    val messages: Flow<GalleryMessage> = _messages.receiveAsFlow()

    private val _shareRequests = Channel<ShareRequest>(Channel.BUFFERED)
    val shareRequests: Flow<ShareRequest> = _shareRequests.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.videos,
                preferences.settings,
                selection,
                renaming,
            ) { videos, settings, currentSelection, renameTarget ->
                val sorted = settings.gallerySort.apply(videos)
                GalleryUiState(
                    videos = sorted,
                    // Drop ids for videos that no longer exist, so the counter
                    // never claims more than is really selected.
                    selection = currentSelection.retaining(sorted.map { it.id }),
                    sort = settings.gallerySort,
                    isLoading = false,
                    renaming = renameTarget?.let { target -> sorted.firstOrNull { it.id == target.id } },
                )
            }.collect { state ->
                _uiState.value = state
                if (state.selection != selection.value) selection.value = state.selection
            }
        }
    }

    // --------------------------------------------------------------- selection

    fun onVideoClicked(video: GalleryVideo): GalleryVideo? {
        // In selection mode a tap extends the selection instead of opening.
        return if (_uiState.value.inSelectionMode) {
            selection.update { it.toggle(video.id) }
            null
        } else {
            video
        }
    }

    fun onVideoLongPressed(video: GalleryVideo) {
        selection.update { it.toggle(video.id) }
    }

    fun onToggleSelectAll() {
        val ids = _uiState.value.videos.map { it.id }
        selection.update { it.toggleAll(ids) }
    }

    fun onClearSelection() {
        selection.value = GallerySelection.EMPTY
    }

    // ----------------------------------------------------------------- actions

    fun onShareSelected() {
        val videos = _uiState.value.selection.resolve(_uiState.value.videos)
        if (videos.isEmpty()) return
        viewModelScope.launch { _shareRequests.send(ShareRequest(videos)) }
    }

    fun onDeleteSelected() {
        val videos = _uiState.value.selection.resolve(_uiState.value.videos)
        if (videos.isEmpty()) return

        viewModelScope.launch {
            val outcome = repository.delete(videos)
            selection.value = GallerySelection.EMPTY
            when {
                outcome.allSucceeded -> emit(R.string.gallery_deleted, outcome.deleted.toString())
                outcome.deleted > 0 -> emit(
                    R.string.gallery_deleted_partial,
                    outcome.deleted.toString(),
                    outcome.failed.size.toString(),
                )

                else -> emit(R.string.gallery_delete_failed)
            }
        }
    }

    fun onRenameRequested() {
        val videos = _uiState.value.selection.resolve(_uiState.value.videos)
        // Renaming is a single-item action; the button is only offered for one.
        renaming.value = videos.singleOrNull()
    }

    fun onRenameDismissed() {
        renaming.value = null
    }

    fun onRenameConfirmed(newBaseName: String) {
        val video = renaming.value ?: return
        renaming.value = null
        viewModelScope.launch {
            repository.rename(video, newBaseName).fold(
                onSuccess = {
                    selection.value = GallerySelection.EMPTY
                    emit(R.string.gallery_renamed)
                },
                onFailure = { error ->
                    Timber.w(error, "Could not rename %s", video.displayName)
                    emit(R.string.gallery_rename_failed)
                },
            )
        }
    }

    fun onSortSelected(sort: GallerySort) {
        viewModelScope.launch { preferences.setGallerySort(sort) }
    }

    private fun emit(@StringRes resId: Int, vararg args: String) {
        viewModelScope.launch { _messages.send(GalleryMessage(resId, args.toList())) }
    }
}
