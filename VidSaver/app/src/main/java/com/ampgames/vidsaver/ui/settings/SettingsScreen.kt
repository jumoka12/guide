package com.ampgames.vidsaver.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.ampgames.vidsaver.ui.theme.BrandGold
import java.text.DateFormat
import java.util.Date
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
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { resId -> snackbarHostState.showSnackbar(context.getString(resId)) }
    }
    Box(modifier = modifier.fillMaxSize()) {
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
            onOpenPaywall = onOpenPaywall,
            onRestore = viewModel::restorePurchases,
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onOpenUrl: (String) -> Unit,
    onOpenPaywall: () -> Unit,
    onRestore: () -> Unit,
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

        PremiumRow(uiState = uiState, onOpenPaywall = onOpenPaywall, onRestore = onRestore, onOpenUrl = onOpenUrl)
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

/**
 * Premium status and the way in or out of it. A premium install sees where
 * to manage the subscription (Google Play) and no upsell; a free one sees
 * what premium adds and a way to restore an earlier purchase.
 */
@Composable
private fun PremiumRow(uiState: SettingsUiState, onOpenPaywall: () -> Unit, onRestore: () -> Unit, onOpenUrl: (String) -> Unit) {
    val premium = uiState.premium
    val subtitle = when {
        !uiState.billingAvailable -> stringResource(R.string.settings_premium_unavailable)
        premium.isPremium && premium.expiresAt == null -> stringResource(R.string.settings_premium_lifetime)
        premium.isPremium && premium.expiresAt != null -> stringResource(
            R.string.settings_premium_active_until,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(premium.expiresAt)),
        )
        else -> stringResource(R.string.settings_premium_free)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !premium.isPremium && uiState.billingAvailable, onClick = onOpenPaywall)
            .padding(vertical = 12.dp)
            .testTag("settings_premium"),
    ) {
        Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = BrandGold,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_premium_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (premium.isPremium && premium.managementUrl != null) {
        SettingsRow(
            title = stringResource(R.string.settings_manage_subscription),
            subtitle = stringResource(R.string.settings_manage_subscription_hint),
            onClick = { onOpenUrl(premium.managementUrl) },
            modifier = Modifier.testTag("settings_manage_subscription"),
        )
    }
    if (!premium.isPremium && uiState.billingAvailable) {
        TextButton(onClick = onRestore, enabled = !uiState.isRestoring, modifier = Modifier.testTag("settings_restore")) {
            Text(stringResource(R.string.settings_restore_purchases))
        }
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
            onOpenPaywall = {},
            onRestore = {},
        )
    }
}
