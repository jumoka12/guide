package com.ampgames.vidsaver.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.download.DownloadStatus

/**
 * One download. Swiping in either direction deletes it — the confirmation is the
 * snackbar the ViewModel posts, not a dialog, because a dialog on every swipe
 * makes bulk cleanup miserable.
 */
@Composable
fun DownloadRow(
    item: DownloadUiItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.testTag("download_row_${item.id}"),
        backgroundContent = { DeleteBackground() },
    ) {
        DownloadRowContent(
            item = item,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.downloads_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun DownloadRowContent(
    item: DownloadUiItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = item.canOpen, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )

            if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PAUSED) {
                if (item.isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { item.progressFraction ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }

            Text(
                text = statusLine(item),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.errorRes != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        DownloadActions(
            item = item,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun DownloadActions(
    item: DownloadUiItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            item.canPause -> {
                TextButton(onClick = onPause, modifier = Modifier.testTag("pause_${item.id}")) {
                    Text(stringResource(R.string.downloads_pause))
                }
                TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_${item.id}")) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            }

            item.status == DownloadStatus.FAILED -> {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_${item.id}")) {
                    Text(stringResource(R.string.downloads_retry))
                }
            }

            item.canResume -> {
                TextButton(onClick = onResume, modifier = Modifier.testTag("resume_${item.id}")) {
                    Text(stringResource(R.string.downloads_resume))
                }
            }
        }
    }
}

@Composable
private fun statusLine(item: DownloadUiItem): String {
    item.errorRes?.let { return stringResource(it) }

    return when (item.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.downloads_status_queued)
        DownloadStatus.RUNNING -> listOfNotNull(
            item.sizeLine,
            item.speedLine,
            item.remainingLine?.let { stringResource(R.string.downloads_remaining, it) },
        ).joinToString(" · ")

        DownloadStatus.PAUSED ->
            "${stringResource(R.string.downloads_status_paused)} · ${item.sizeLine}"

        DownloadStatus.RETRY_SCHEDULED -> stringResource(R.string.downloads_status_retrying)
        DownloadStatus.COMPLETED -> item.sizeLine
        DownloadStatus.CANCELLED -> stringResource(R.string.downloads_status_cancelled)
        DownloadStatus.FAILED -> stringResource(R.string.download_error_unknown)
    }
}
