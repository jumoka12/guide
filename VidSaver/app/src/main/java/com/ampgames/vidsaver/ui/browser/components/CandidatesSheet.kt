package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.core.format.ByteFormat
import com.ampgames.vidsaver.domain.media.FileNames
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import java.util.Locale

const val CANDIDATES_SHEET_TEST_TAG = "candidates_sheet"

/**
 * The download sheet: a live preview of the selected file, the name it will be
 * saved under (editable), a grid of quality chips, and one Download button.
 *
 * Every file the page loaded is a chip. That reads as choice rather than as a
 * wall because the chips are small, the best one is selected up front, and the
 * preview shows what the chip actually is.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CandidatesSheet(
    candidates: List<MediaCandidate>,
    onDownload: (MediaCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    if (candidates.isEmpty()) {
        // The page navigated away under the open sheet; nothing left to offer.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var selected by remember(candidates) { mutableStateOf(candidates.first()) }
    // The name the person typed, if any; null means "use the candidate's".
    var customBase by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }

    val extension = selected.suggestedFileName.substringAfterLast('.', "")
    val defaultBase = selected.suggestedFileName.substringBeforeLast('.')
    val fileName = "${customBase ?: defaultBase}.$extension"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(CANDIDATES_SHEET_TEST_TAG),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            CandidatePreview(candidate = selected)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { renaming = true },
                    modifier = Modifier.testTag("candidates_rename"),
                ) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.candidates_rename))
                }
            }

            Text(
                text = stringResource(R.string.candidates_download_as),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                candidates.forEach { candidate ->
                    QualityChip(
                        candidate = candidate,
                        selected = candidate.id == selected.id,
                        onClick = { selected = candidate },
                    )
                }
            }

            Text(
                text = stringResource(R.string.candidates_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )

            Button(
                onClick = { onDownload(selected.copy(suggestedFileName = fileName)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(52.dp)
                    .testTag("candidates_download"),
            ) {
                Text(
                    text = stringResource(R.string.browser_download_available),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (renaming) {
        RenameDialog(
            initial = customBase ?: defaultBase,
            onConfirm = { typed ->
                customBase = FileNames.sanitize(typed).takeIf { typed.isNotBlank() }
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}

/** "1080P" over "22.8 MB" in an outlined tile; red when selected. */
@Composable
private fun QualityChip(
    candidate: MediaCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag("candidate_${candidate.id.hashCode()}"),
    ) {
        Text(
            text = qualityLabel(candidate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = candidate.sizeBytes?.let { ByteFormat.size(it) }
                ?: if (candidate.type == MediaType.HLS) {
                    stringResource(R.string.candidates_stream)
                } else {
                    stringResource(R.string.candidates_size_unknown)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (candidate.hasAudio == false) {
            Text(
                text = stringResource(R.string.candidates_no_audio),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** "1080P" when the height is known; the container ("MP4", "HLS") when not. */
private fun qualityLabel(candidate: MediaCandidate): String =
    candidate.resolutionLabel?.uppercase(Locale.US)
        ?: if (candidate.type == MediaType.HLS) {
            "HLS"
        } else {
            candidate.suggestedFileName.substringAfterLast('.', "video").uppercase(Locale.US)
        }

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.candidates_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.candidates_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
