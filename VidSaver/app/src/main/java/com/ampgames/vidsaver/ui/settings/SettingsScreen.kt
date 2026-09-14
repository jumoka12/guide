package com.ampgames.vidsaver.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ampgames.vidsaver.BuildConfig
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.theme.VidSaverTheme
import timber.log.Timber

const val SETTINGS_SCREEN_TEST_TAG = "settings_screen"

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        uiState = uiState,
        onOpenUrl = { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "No handler for %s", url)
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(SETTINGS_SCREEN_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.titleLarge,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        SettingsRow(
            title = stringResource(R.string.settings_privacy_policy),
            subtitle = uiState.privacyPolicyUrl,
            onClick = { onOpenUrl(uiState.privacyPolicyUrl) },
        )
        SettingsRow(
            title = stringResource(R.string.settings_terms),
            subtitle = uiState.termsUrl,
            onClick = { onOpenUrl(uiState.termsUrl) },
        )
        SettingsRow(
            title = stringResource(R.string.settings_support),
            subtitle = uiState.supportEmail,
            onClick = { onOpenUrl("mailto:${uiState.supportEmail}") },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    VidSaverTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                privacyPolicyUrl = "https://ampgames.com/privacy",
                termsUrl = "https://ampgames.com/terms",
                supportEmail = "support@ampgames.com",
            ),
            onOpenUrl = {},
        )
    }
}
