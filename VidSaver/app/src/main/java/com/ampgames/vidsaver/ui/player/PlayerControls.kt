package com.ampgames.vidsaver.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R

const val PLAYER_CONTROLS_TEST_TAG = "player_controls"

/**
 * The overlay: title and back at the top, transport in the middle, scrubber and
 * secondary actions at the bottom. Fades rather than popping, and sits above the
 * gesture layer so buttons win over drags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleBackgroundAudio: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.controlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM)
                .testTag(PLAYER_CONTROLS_TEST_TAG),
        ) {
            TopRow(
                title = state.title,
                onBack = onBack,
                onEnterPip = onEnterPip,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            TransportRow(
                isPlaying = state.isPlaying,
                hasNext = state.hasNext,
                hasPrevious = state.hasPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier.align(Alignment.Center),
            )

            BottomRow(
                state = state,
                onSeekTo = onSeekTo,
                onCycleSpeed = onCycleSpeed,
                onToggleBackgroundAudio = onToggleBackgroundAudio,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun TopRow(
    title: String,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("player_back")) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.player_back),
                tint = Color.White,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        IconButton(onClick = onEnterPip, modifier = Modifier.testTag("player_pip")) {
            Icon(
                Icons.Filled.PictureInPictureAlt,
                stringResource(R.string.player_pip),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.testTag("player_previous"),
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                stringResource(R.string.player_previous),
                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
        }

        IconButton(onClick = onPlayPause, modifier = Modifier.testTag("player_play_pause")) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.player_pause else R.string.player_play,
                ),
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }

        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.testTag("player_next"),
        ) {
            Icon(
                Icons.Filled.SkipNext,
                stringResource(R.string.player_next),
                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomRow(
    state: PlayerUiState,
    onSeekTo: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleBackgroundAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Slider(
            value = state.progressFraction,
            onValueChange = onSeekTo,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.testTag("player_scrubber"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.positionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onToggleBackgroundAudio,
                modifier = Modifier.testTag("player_background_audio"),
            ) {
                Icon(
                    imageVector = if (state.backgroundAudio) {
                        Icons.Filled.Headphones
                    } else {
                        Icons.Outlined.Headphones
                    },
                    contentDescription = stringResource(R.string.player_background_audio),
                    tint = if (state.backgroundAudio) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White
                    },
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                onClick = onCycleSpeed,
                modifier = Modifier.testTag("player_speed"),
            ) {
                Text(
                    text = state.speed.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.35f)
