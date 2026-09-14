package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.core.format.ByteFormat
import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import java.util.Locale

const val CANDIDATES_SHEET_TEST_TAG = "candidates_sheet"

/**
 * Two tiers, not one wall. Videos the page itself named (a `<video>` element,
 * an `og:video` tag) are the headline; every other file the page pulled over the
 * network sits folded under "Other files". A feed that streams one clip as
 * twelve quality tiers now reads as one clip with twelve alternatives, which is
 * what it is.
 *
 * When the page named nothing, everything is headline: there is no better
 * signal to rank by.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesSheet(
    candidates: List<MediaCandidate>,
    onDownload: (MediaCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = candidates.filter { it.isPrimary }.ifEmpty { candidates }
    val others = if (primary.size == candidates.size) emptyList() else candidates - primary.toSet()
    var showOthers by remember(candidates) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(CANDIDATES_SHEET_TEST_TAG),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.candidates_title, primary.size),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.candidates_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 520.dp),
            ) {
                items(primary, key = { it.id }) { candidate ->
                    CandidateRow(candidate = candidate, onDownload = { onDownload(candidate) })
                }

                if (others.isNotEmpty()) {
                    item(key = "others_toggle") {
                        OthersToggle(
                            count = others.size,
                            expanded = showOthers,
                            onClick = { showOthers = !showOthers },
                        )
                    }
                    if (showOthers) {
                        items(others, key = { it.id }) { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                onDownload = { onDownload(candidate) },
                                compact = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OthersToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("candidates_others"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.candidates_other_files, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: MediaCandidate,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(url = candidate.thumbnailUrl, width = if (compact) 56.dp else 88.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleFor(candidate),
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            Text(
                text = describe(candidate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        FilledTonalButton(
            onClick = onDownload,
            modifier = Modifier.testTag("download_${candidate.id.hashCode()}"),
        ) {
            Text(stringResource(R.string.candidates_download))
        }
    }
}

/** The poster when the page gave one; a quiet play glyph on a tile when not. */
@Composable
private fun Thumbnail(url: String?, width: Dp) {
    val shape = MaterialTheme.shapes.small
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(width)
                .aspectRatio(16f / 9f)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** The page's title for the video, else the site it came from; never a file name. */
private fun titleFor(candidate: MediaCandidate): String =
    candidate.title?.takeIf { it.isNotBlank() }
        ?: Urls.host(candidate.pageUrl)?.removePrefix("www.")
        ?: Urls.host(candidate.url)?.removePrefix("www.")
        ?: candidate.suggestedFileName

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
