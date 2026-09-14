package com.ampgames.vidsaver.ui.downloads

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.data.download.DownloadEngine
import com.ampgames.vidsaver.data.download.DownloadPreferences
import com.ampgames.vidsaver.data.download.DownloadRepository
import com.ampgames.vidsaver.domain.download.DownloadStatus
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

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val preferences: DownloadPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<DownloadsMessage>(Channel.BUFFERED)
    val messages: Flow<DownloadsMessage> = _messages.receiveAsFlow()

    init {
        // A row still marked RUNNING belongs to a process that no longer exists.
        viewModelScope.launch { engine.recoverAfterProcessDeath() }
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                repository.observeAll(),
                preferences.settings,
            ) { downloads, settings ->
                val (active, finished) = downloads.partition { !it.statusEnum.isTerminal }
                DownloadsUiState(
                    // Active oldest-first so the queue reads top to bottom;
                    // finished newest-first so the latest save is at the top.
                    active = active.sortedBy { it.createdAt }.map { it.toUiItem() },
                    finished = finished.sortedByDescending { it.completedAt ?: it.createdAt }
                        .map { it.toUiItem() },
                    wifiOnly = settings.wifiOnly,
                    isLoading = false,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun onPause(id: Long) = engine.pause(id)

    fun onResume(id: Long) = engine.resume(id)

    fun onCancel(id: Long) = engine.cancel(id)

    /** Swipe-to-delete: removes the row and any file it produced. */
    fun onDelete(id: Long) {
        val item = currentItem(id)
        engine.deleteWithFile(id)
        emit(
            if (item?.status == DownloadStatus.COMPLETED) {
                R.string.downloads_deleted_with_file
            } else {
                R.string.downloads_removed
            },
        )
    }

    fun onRetry(id: Long) {
        engine.resume(id)
        emit(R.string.downloads_retrying)
    }

    fun onClearFinished() {
        viewModelScope.launch {
            repository.clearFinished()
            emit(R.string.downloads_cleared)
        }
    }

    fun onToggleWifiOnly() {
        viewModelScope.launch {
            val enabled = !_uiState.value.wifiOnly
            preferences.setWifiOnly(enabled)
            _uiState.update { it.copy(wifiOnly = enabled) }
            emit(
                if (enabled) R.string.downloads_wifi_only_on else R.string.downloads_wifi_only_off,
            )
            // Turning the restriction off may unblock a held queue.
            if (!enabled) engine.pump()
        }
    }

    private fun currentItem(id: Long): DownloadUiItem? {
        val state = _uiState.value
        return (state.active + state.finished).firstOrNull { it.id == id }
    }

    private fun emit(@StringRes resId: Int, vararg args: String) {
        viewModelScope.launch { _messages.send(DownloadsMessage(resId, args.toList())) }
    }
}
