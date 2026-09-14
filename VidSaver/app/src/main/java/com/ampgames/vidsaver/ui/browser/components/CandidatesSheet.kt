package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.ampgames.vidsaver.core.format.ByteFormat
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import java.util.Locale

const val CANDIDATES_SHEET_TEST_TAG = "candidates_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesSheet(
    candidates: List<MediaCandidate>,
    onDownload: (MediaCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(CANDIDATES_SHEET_TEST_TAG),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.candidates_title, candidates.size),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.candidates_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.id }) { candidate ->
                    CandidateRow(candidate = candidate, onDownload = { onDownload(candidate) })
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: MediaCandidate,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (candidate.thumbnailUrl != null) {
            AsyncImage(
                model = candidate.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.suggestedFileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Text(
                text = describe(candidate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onDownload,
            modifier = Modifier.testTag("download_${candidate.id.hashCode()}"),
        ) {
            Text(stringResource(R.string.candidates_download))
        }
    }
}

/** "MP4 · 1080p · 24.3 MB", skipping whatever is unknown. */
private fun describe(candidate: MediaCandidate): String {
    val parts = buildList {
        add(if (candidate.type == MediaType.HLS) "HLS" else formatContainer(candidate))
        candidate.resolutionLabel?.let { add(it) }
        candidate.sizeBytes?.let { add(ByteFormat.size(it)) }
    }
    return parts.joinToString(" · ")
}

private fun formatContainer(candidate: MediaCandidate): String =
    candidate.suggestedFileName.substringAfterLast('.', "video").uppercase(Locale.US)

