package com.shohankhan.callora.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PhoneForwarded
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shohankhan.callora.core.forwarding.RestorationStep
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.BatterySnapshot
import com.shohankhan.callora.core.model.ConfigurationHistoryEntry
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.SimDiscoverySnapshot
import com.shohankhan.callora.core.model.SimDiscoveryState
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile
import com.shohankhan.callora.core.telephony.BangladeshCarrierCatalog
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.HeroGradientBanner
import com.shohankhan.callora.core.ui.R
import com.shohankhan.callora.core.ui.SimIdentityChip
import com.shohankhan.callora.core.ui.VerificationStatusBadge
import com.shohankhan.callora.core.ui.badgeRes
import com.shohankhan.callora.core.ui.labelRes
import com.shohankhan.callora.core.ui.titleRes
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    settings: AppSettings,
    simSnapshot: SimDiscoverySnapshot,
    battery: BatterySnapshot,
    activeProfile: SmartProfile?,
    observedStates: List<ObservedSimForwardingState>,
    recentHistory: List<ConfigurationHistoryEntry>,
    restorationPlanPreview: List<RestorationStep>?,
    isBengali: Boolean,
    onRequestPhonePermission: () -> Unit,
    onRefreshSims: () -> Unit,
    onOpenSimDetail: (Int) -> Unit,
    onPrepareUnreachableQuick: () -> Unit,
    onCheckStatusQuick: () -> Unit,
    onOpenRestoreDialog: () -> Unit,
    onConfirmRestoreStep: (RestorationStep) -> Unit,
    onDismissRestoreDialog: () -> Unit,
    onAcknowledgeRebootReview: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onOpenCallSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        // 1. Overall Readiness or Warning Hero Status
        item {
            when {
                settings.pendingRebootReview || simSnapshot.subscriptionChangedWarning -> {
                    HeroGradientBanner(
                        title = stringResource(R.string.dashboard_hero_warning_reboot_title),
                        body = stringResource(R.string.dashboard_hero_warning_reboot_body),
                        actionButton = {
                            FilledTonalButton(onClick = onAcknowledgeRebootReview) {
                                Text(stringResource(R.string.action_confirm))
                            }
                        }
                    )
                }

                simSnapshot.state == SimDiscoveryState.PERMISSION_REQUIRED -> {
                    HeroGradientBanner(
                        title = stringResource(R.string.dashboard_hero_warning_perm_title),
                        body = stringResource(R.string.dashboard_hero_warning_perm_body),
                        actionButton = {
                            FilledTonalButton(onClick = onRequestPhonePermission) {
                                Text(stringResource(R.string.action_grant_permission))
                            }
                        }
                    )
                }

                else -> {
                    HeroGradientBanner(
                        title = stringResource(R.string.dashboard_hero_ready_title),
                        body = stringResource(R.string.dashboard_hero_ready_body)
                    )
                }
            }
        }

        // 2. SIM Overview Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_section_sims),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onRefreshSims) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_refresh_sims))
                }
            }
        }

        if (simSnapshot.subscriptions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(CalloraSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.sim_no_active_sims_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.sim_no_active_sims_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                            if (simSnapshot.state == SimDiscoveryState.PERMISSION_REQUIRED) {
                                Button(onClick = onRequestPhonePermission) {
                                    Text(stringResource(R.string.action_grant_permission))
                                }
                            }
                            OutlinedButton(onClick = onOpenCallSettings) {
                                Text(stringResource(R.string.action_open_call_settings))
                            }
                        }
                    }
                }
            }
        } else {
            items(simSnapshot.subscriptions, key = { it.subscriptionId }) { sim ->
                val cfnrcObserved = observedStates.firstOrNull {
                    it.simSignature == sim.stableSignature &&
                        it.serviceType == ForwardingServiceType.UNREACHABLE
                }
                DashboardSimCard(
                    sim = sim,
                    cfnrcState = cfnrcObserved,
                    isBengali = isBengali,
                    onClick = { onOpenSimDetail(sim.simSlotIndex) }
                )
            }
        }

        // 3. Active Profile vs Network Verification Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenProfiles),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_section_active_profile),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (activeProfile == null) {
                        Text(
                            text = stringResource(R.string.dashboard_no_active_profile),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.dashboard_no_active_profile_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = activeProfile.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        VerificationStatusBadge(state = activeProfile.lastVerifiedState)
                    }
                }
            }
        }

        // 4. Primary Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                Text(
                    text = stringResource(R.string.dashboard_section_quick_actions),
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Button(onClick = onPrepareUnreachableQuick) {
                        Icon(Icons.Outlined.PhoneForwarded, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.quick_prepare_unreachable))
                    }
                    FilledTonalButton(onClick = onCheckStatusQuick) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.quick_check_status))
                    }
                    OutlinedButton(onClick = onOpenRestoreDialog) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.quick_restore_intended))
                    }
                    OutlinedButton(onClick = onOpenFavorites) {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.quick_favorites))
                    }
                }
            }
        }

        // 5. Battery & Power Readiness
        item {
            val isLowBattery = settings.lowBatteryWarningEnabled &&
                battery.isBelowOrAtThreshold(settings.lowBatteryThresholdPercent)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLowBattery) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                battery.isCharging -> Icons.Outlined.BatteryChargingFull
                                isLowBattery -> Icons.Outlined.BatteryAlert
                                else -> Icons.Outlined.BatteryStd
                            },
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.dashboard_section_battery),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (battery.isCharging) {
                            stringResource(R.string.battery_status_charging, battery.levelPercent)
                        } else {
                            stringResource(R.string.battery_status_discharging, battery.levelPercent)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isLowBattery) {
                        Text(
                            text = stringResource(R.string.battery_low_warning, settings.lowBatteryThresholdPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = stringResource(R.string.battery_phone_off_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Recent Configuration Activity
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_section_recent_history),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onOpenHistory) {
                            Text(stringResource(R.string.dashboard_view_all_history))
                        }
                    }
                    if (recentHistory.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dashboard_empty_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentHistory.take(3).forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entry.simDisplayLabel} • ${entry.serviceType.acronym}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(entry.timestampMillis)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                VerificationStatusBadge(state = entry.verificationState)
                            }
                        }
                    }
                }
            }
        }

        // 7. Contextual Troubleshooting Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTroubleshooting),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                ) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dashboard_troubleshooting_card_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.dashboard_troubleshooting_card_body),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (restorationPlanPreview != null) {
        AlertDialog(
            onDismissRequest = onDismissRestoreDialog,
            title = { Text(stringResource(R.string.restore_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                    if (restorationPlanPreview.isEmpty()) {
                        Text(stringResource(R.string.restore_empty_plan))
                    } else {
                        Text(stringResource(R.string.restore_dialog_intro))
                        restorationPlanPreview.forEach { step ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(CalloraSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${step.sim.displayName} • ${stringResource(step.serviceType.titleRes())}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${stringResource(step.targetAction.labelRes())} ${step.targetDestination.orEmpty()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(onClick = { onConfirmRestoreStep(step) }) {
                                        Text(stringResource(R.string.action_confirm))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRestoreDialog) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSimCard(
    sim: SimSubscription,
    cfnrcState: ObservedSimForwardingState?,
    isBengali: Boolean,
    onClick: () -> Unit
) {
    val resolution = BangladeshCarrierCatalog.resolveForSubscription(sim)
    val carrier = resolution.config

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(CalloraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
            ) {
                SimIdentityChip(sim = sim)
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(carrier.verificationLevel.badgeRes())) }
                )
                if (sim.isRoaming) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(stringResource(R.string.sim_roaming_badge)) }
                    )
                }
            }

            Text(
                text = "${sim.displayName} — ${if (isBengali) carrier.displayNameBn else carrier.displayNameEn}",
                style = MaterialTheme.typography.titleMedium
            )

            if (!sim.maskedPhoneNumber.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.sim_masked_number, sim.maskedPhoneNumber!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (sim.isNetworkAvailable) {
                    stringResource(R.string.sim_network_online)
                } else {
                    stringResource(R.string.sim_network_offline)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (sim.isNetworkAvailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
            ) {
                Text(
                    text = "CFNRc (62):",
                    style = MaterialTheme.typography.labelLarge
                )
                VerificationStatusBadge(
                    state = cfnrcState?.verificationState ?: ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
                    observedActive = cfnrcState?.observedEnabled
                )
            }
        }
    }
}
