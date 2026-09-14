package com.ampgames.vidsaver.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import com.ampgames.vidsaver.domain.player.DurationFormat

/**
 * One video in the grid.
 *
 * The thumbnail is a decoded frame from the file itself (Coil's
 * `VideoFrameDecoder`), taken a second in — frame zero is very often black.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryTile(
    video: GalleryVideo,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("gallery_tile_${video.id}"),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.uri)
                .videoFrameMillis(THUMBNAIL_FRAME_MS)
                .crossfade(true)
                .build(),
            contentDescription = video.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Scrim so white captions stay readable over a bright frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM),
        )

        Text(
            text = video.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        )

        if (video.durationMs > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = DurationFormat.position(video.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .testTag("gallery_check_${video.id}"),
            )
        }
    }
}

private const val THUMBNAIL_FRAME_MS = 1_000L
private val SCRIM = Color.Black.copy(alpha = 0.18f)
