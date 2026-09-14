package com.ampgames.vidsaver.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.premium.PlanKind
import com.ampgames.vidsaver.domain.premium.PremiumPlan
import com.ampgames.vidsaver.ui.theme.BrandGold
import com.ampgames.vidsaver.ui.util.findActivity

const val PAYWALL_SCREEN_TEST_TAG = "paywall_screen"
const val PAYWALL_CONTINUE_FREE_TEST_TAG = "paywall_continue_free"
const val PAYWALL_PURCHASE_TEST_TAG = "paywall_purchase"
const val PAYWALL_RESTORE_TEST_TAG = "paywall_restore"

/**
 * The paywall: one native screen, no web view.
 *
 * Every price, period and trial length on it comes from the store through
 * the plan model, and "Continue with free version" is on screen whatever the
 * state — loading, loaded, failed, purchasing. Closing is never hidden behind
 * a timer or a tiny target.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A purchase or restore that made the person premium closes the wall.
    LaunchedEffect(state.isPremium) {
        if (state.isPremium) onClose()
    }

    PaywallScreen(
        state = state,
        onSelectPlan = viewModel::selectPlan,
        onPurchase = { context.findActivity()?.let(viewModel::purchase) },
        onRestore = viewModel::restore,
        onRetry = viewModel::load,
        onMessageShown = viewModel::messageShown,
        onClose = onClose,
        onOpenUrl = onOpenUrl,
        modifier = modifier,
    )
}

@Composable
internal fun PaywallScreen(
    state: PaywallUiState,
    onSelectPlan: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(PAYWALL_SCREEN_TEST_TAG),
    ) {
        // Close is the first thing on the screen, top start, full-size target.
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("paywall_close")) {
                Icon(Icons.Filled.Close, stringResource(R.string.paywall_close))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = BrandGold,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            Benefits()

            Spacer(modifier = Modifier.height(20.dp))

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.loadError != null -> LoadError(state.loadError, onRetry)

                else -> {
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            selected = plan.id == state.selectedPlanId,
                            savingPercent = state.yearlySavingPercent.takeIf { plan.kind == PlanKind.YEARLY },
                            onClick = { onSelectPlan(plan.id) },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    state.selectedPlan?.let { Terms(it) }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Legal(onOpenUrl = onOpenUrl, privacyUrl = state.privacyUrl, termsUrl = state.termsUrl)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // The action area is pinned so "Continue with free version" is on
        // screen at every scroll position and in every state.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.message?.let { message ->
                Text(
                    text = if (message == PaywallViewModel.MESSAGE_NOTHING_TO_RESTORE) {
                        stringResource(R.string.paywall_nothing_to_restore)
                    } else {
                        message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp).clickable(onClick = onMessageShown),
                )
            }

            val plan = state.selectedPlan
            Button(
                onClick = onPurchase,
                enabled = plan != null && !state.isPurchasing && !state.isLoading && state.loadError == null,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag(PAYWALL_PURCHASE_TEST_TAG),
            ) {
                if (state.isPurchasing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        text = when {
                            plan == null -> stringResource(R.string.paywall_cta_continue)
                            plan.hasTrial -> stringResource(R.string.paywall_cta_trial)
                            plan.isOneTime -> stringResource(R.string.paywall_cta_lifetime)
                            else -> stringResource(R.string.paywall_cta_subscribe)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TextButton(
                    onClick = onRestore,
                    enabled = !state.isRestoring,
                    modifier = Modifier.testTag(PAYWALL_RESTORE_TEST_TAG),
                ) { Text(stringResource(R.string.paywall_restore)) }

                TextButton(
                    onClick = onClose,
                    modifier = Modifier.testTag(PAYWALL_CONTINUE_FREE_TEST_TAG),
                ) { Text(stringResource(R.string.paywall_continue_free)) }
            }
        }
    }
}

@Composable
private fun Benefits() {
    val items = listOf(
        R.string.paywall_benefit_no_ads,
        R.string.paywall_benefit_concurrent,
        R.string.paywall_benefit_hd,
        R.string.paywall_benefit_background,
    )
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { res ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PremiumPlan, selected: Boolean, savingPercent: Int?, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("plan_${plan.id}"),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when (plan.kind) {
                            PlanKind.MONTHLY -> R.string.paywall_plan_monthly
                            PlanKind.YEARLY -> R.string.paywall_plan_yearly
                            PlanKind.LIFETIME -> R.string.paywall_plan_lifetime
                            PlanKind.OTHER -> R.string.paywall_plan_other
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (savingPercent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.paywall_save_percent, savingPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = when {
                    plan.hasTrial -> pluralStringResource(R.plurals.paywall_trial_days, plan.trialDays, plan.trialDays)
                    plan.isOneTime -> stringResource(R.string.paywall_pay_once)
                    else -> stringResource(R.string.paywall_billed_per_period, periodLabel(plan))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = plan.priceFormatted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The exact terms of the selected plan: what is charged, when, and how to stop it. */
@Composable
private fun Terms(plan: PremiumPlan) {
    val text = when {
        plan.hasTrial -> stringResource(
            R.string.paywall_terms_trial,
            pluralStringResource(R.plurals.paywall_trial_days_inline, plan.trialDays, plan.trialDays),
            plan.priceFormatted,
            periodLabel(plan),
        )
        plan.isOneTime -> stringResource(R.string.paywall_terms_lifetime, plan.priceFormatted)
        else -> stringResource(R.string.paywall_terms_subscription, plan.priceFormatted, periodLabel(plan))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("paywall_terms"),
    )
}

@Composable
private fun periodLabel(plan: PremiumPlan): String = stringResource(
    when (plan.kind) {
        PlanKind.MONTHLY -> R.string.paywall_period_month
        PlanKind.YEARLY -> R.string.paywall_period_year
        else -> when (plan.periodDays) {
            null -> R.string.paywall_period_month
            in 1..8 -> R.string.paywall_period_week
            in 9..45 -> R.string.paywall_period_month
            else -> R.string.paywall_period_year
        }
    },
)

@Composable
private fun LoadError(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = stringResource(R.string.paywall_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.paywall_retry)) }
    }
}

@Composable
private fun Legal(onOpenUrl: (String) -> Unit, privacyUrl: String, termsUrl: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.paywall_legal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row {
            TextButton(onClick = { onOpenUrl(privacyUrl) }) {
                Text(stringResource(R.string.settings_privacy_policy), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { onOpenUrl(termsUrl) }) {
                Text(stringResource(R.string.settings_terms), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
