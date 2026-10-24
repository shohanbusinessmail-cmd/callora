package com.shohankhan.callora.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PhoneForwarded
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsPhone
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shohankhan.callora.core.model.CarrierConfig
import com.shohankhan.callora.core.model.CarrierVerificationLevel
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingOperationResult
import com.shohankhan.callora.core.model.ForwardingResultCode
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.NumberValidationIssue
import com.shohankhan.callora.core.model.SimSubscription

@StringRes
fun ForwardingServiceType.titleRes(): Int = when (this) {
    ForwardingServiceType.UNREACHABLE -> R.string.service_unreachable_title
    ForwardingServiceType.BUSY -> R.string.service_busy_title
    ForwardingServiceType.UNANSWERED -> R.string.service_unanswered_title
    ForwardingServiceType.UNCONDITIONAL -> R.string.service_unconditional_title
}

@StringRes
fun ForwardingServiceType.descRes(): Int = when (this) {
    ForwardingServiceType.UNREACHABLE -> R.string.service_unreachable_desc
    ForwardingServiceType.BUSY -> R.string.service_busy_desc
    ForwardingServiceType.UNANSWERED -> R.string.service_unanswered_desc
    ForwardingServiceType.UNCONDITIONAL -> R.string.service_unconditional_desc
}

@StringRes
fun ForwardingActionType.labelRes(): Int = when (this) {
    ForwardingActionType.ACTIVATE -> R.string.action_activate_forwarding
    ForwardingActionType.DEACTIVATE -> R.string.action_deactivate_forwarding
    ForwardingActionType.QUERY_STATUS -> R.string.action_query_forwarding
    ForwardingActionType.RESTORE_INTENDED -> R.string.action_restore_forwarding
}

@StringRes
fun CarrierVerificationLevel.badgeRes(): Int = when (this) {
    CarrierVerificationLevel.OFFICIAL_CODE_TABLE_VERIFIED -> R.string.sim_carrier_verification_official
    CarrierVerificationLevel.OFFICIAL_HANDSET_SETTINGS_VERIFIED -> R.string.sim_carrier_verification_handset
    CarrierVerificationLevel.STANDARD_3GPP_PROVISIONING_DEPENDENT -> R.string.sim_carrier_verification_3gpp
    CarrierVerificationLevel.UNVERIFIED_FALLBACK -> R.string.sim_carrier_verification_unverified
}

@StringRes
fun NumberValidationIssue.messageRes(): Int = when (this) {
    NumberValidationIssue.EMPTY -> R.string.error_number_empty
    NumberValidationIssue.CONTAINS_MMI_INJECTION_CHARACTERS -> R.string.error_number_mmi_chars
    NumberValidationIssue.SHORT_CODE_OR_EMERGENCY_NUMBER -> R.string.error_number_short_code
    NumberValidationIssue.INVALID_BANGLADESH_MOBILE_PREFIX -> R.string.error_number_bd_prefix
    NumberValidationIssue.INVALID_BANGLADESH_LENGTH -> R.string.error_number_bd_length
    NumberValidationIssue.SELF_FORWARDING_LOOP -> R.string.error_number_self_loop
    NumberValidationIssue.TOO_SHORT,
    NumberValidationIssue.TOO_LONG,
    NumberValidationIssue.UNRECOGNIZED_FORMAT -> R.string.error_number_format
}

/**
 * Distinguishes subscriptions using slot number, eSIM label, AND distinct corner geometry
 * so color is never the sole means of distinguishing SIM 1 vs SIM 2 vs SIM 3+.
 */
@Composable
fun SimIdentityChip(
    sim: SimSubscription,
    modifier: Modifier = Modifier
) {
    val shape: Shape = when (sim.displaySlotNumber % 3) {
        1 -> RoundedCornerShape(8.dp)
        2 -> CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
        else -> RoundedCornerShape(50)
    }
    val text = if (sim.isEmbedded) {
        stringResource(R.string.sim_esim_badge, sim.displaySlotNumber)
    } else {
        stringResource(R.string.sim_slot_badge, sim.displaySlotNumber)
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.SimCard,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Communicates verification status via distinct icon + localized text + tonal surface,
 * never relying solely on red/green color.
 */
@Composable
fun VerificationStatusBadge(
    state: ForwardingVerificationState,
    observedActive: Boolean? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.3f

    val (icon, text, bg, fg) = when (state) {
        ForwardingVerificationState.NETWORK_STATE_VERIFIED -> {
            val label = if (observedActive == false) {
                stringResource(R.string.state_verified_inactive)
            } else {
                stringResource(R.string.state_verified_active)
            }
            Quad(
                Icons.Outlined.CheckCircle,
                label,
                if (isDark) CalloraColors.StatusVerifiedBgDark else CalloraColors.StatusVerifiedBgLight,
                if (isDark) CalloraColors.StatusVerifiedFgDark else CalloraColors.StatusVerifiedFgLight
            )
        }

        ForwardingVerificationState.COMMAND_SUBMITTED -> Quad(
            Icons.Outlined.Schedule,
            stringResource(R.string.state_command_submitted),
            if (isDark) CalloraColors.StatusUnverifiedBgDark else CalloraColors.StatusUnverifiedBgLight,
            if (isDark) CalloraColors.StatusUnverifiedFgDark else CalloraColors.StatusUnverifiedFgLight
        )

        ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED -> Quad(
            Icons.Outlined.WarningAmber,
            stringResource(R.string.state_accepted_unverified),
            if (isDark) CalloraColors.StatusUnverifiedBgDark else CalloraColors.StatusUnverifiedBgLight,
            if (isDark) CalloraColors.StatusUnverifiedFgDark else CalloraColors.StatusUnverifiedFgLight
        )

        ForwardingVerificationState.OPERATION_IN_PROGRESS -> Quad(
            Icons.Outlined.PhoneForwarded,
            stringResource(R.string.state_in_progress),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )

        ForwardingVerificationState.FAILED_OPERATION -> Quad(
            Icons.Outlined.ErrorOutline,
            stringResource(R.string.state_failed),
            if (isDark) CalloraColors.StatusErrorBgDark else CalloraColors.StatusErrorBgLight,
            if (isDark) CalloraColors.StatusErrorFgDark else CalloraColors.StatusErrorFgLight
        )

        ForwardingVerificationState.UNSUPPORTED_CAPABILITY -> Quad(
            Icons.Outlined.Block,
            stringResource(R.string.state_unsupported),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )

        ForwardingVerificationState.DESIRED_LOCAL_ONLY -> Quad(
            Icons.Outlined.Schedule,
            stringResource(R.string.state_desired_local),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )

        ForwardingVerificationState.UNKNOWN_NETWORK_STATE -> Quad(
            Icons.Outlined.HelpOutline,
            stringResource(R.string.state_unknown),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = fg
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun HeroGradientBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(CalloraColors.HeroGradient)
                .padding(CalloraSpacing.lg)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.90f)
            )
            if (actionButton != null) {
                Spacer(Modifier.height(CalloraSpacing.md))
                actionButton()
            }
        }
    }
}

data class PendingConfirmationModel(
    val sim: SimSubscription,
    val carrier: CarrierConfig,
    val serviceType: ForwardingServiceType,
    val actionType: ForwardingActionType,
    val destinationNumber: String?,
    val preparedMmiCode: String?,
    val executionMethod: ForwardingExecutionMethod,
    val isMultiSimDevice: Boolean,
    val isBengali: Boolean
)

/**
 * Screen 7: Explicit Activation / Operation Confirmation Dialog.
 * Displays selected SIM, forwarding condition, destination, expected effect,
 * carrier charge warning, verification capability, and multi-SIM acknowledgement.
 */
@Composable
fun ForwardingConfirmationDialog(
    model: PendingConfirmationModel,
    onConfirm: (multiSimConfirmed: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var multiSimChecked by rememberSaveable(model.sim.subscriptionId, model.serviceType, model.actionType) {
        mutableStateOf(!model.isMultiSimDevice || model.executionMethod != ForwardingExecutionMethod.SAFE_DIALER_INTENT)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.confirm_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
            ) {
                SimIdentityChip(sim = model.sim)
                InfoRow(
                    label = stringResource(R.string.confirm_label_sim),
                    value = "${model.sim.displayName} (${if (model.isBengali) model.carrier.displayNameBn else model.carrier.displayNameEn})"
                )
                InfoRow(
                    label = stringResource(R.string.confirm_label_condition),
                    value = stringResource(model.serviceType.titleRes())
                )
                InfoRow(
                    label = stringResource(R.string.confirm_label_action),
                    value = stringResource(model.actionType.labelRes())
                )
                if (!model.destinationNumber.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(R.string.confirm_label_destination),
                        value = model.destinationNumber
                    )
                }
                if (!model.preparedMmiCode.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(R.string.confirm_label_mmi_preview),
                        value = model.preparedMmiCode,
                        monospace = true
                    )
                }

                HorizontalDivider()

                val effectText = when (model.actionType) {
                    ForwardingActionType.ACTIVATE -> stringResource(
                        R.string.confirm_effect_activate,
                        model.destinationNumber.orEmpty()
                    )
                    ForwardingActionType.DEACTIVATE,
                    ForwardingActionType.RESTORE_INTENDED -> stringResource(
                        R.string.confirm_effect_deactivate,
                        model.serviceType.acronym,
                        model.sim.displaySlotNumber
                    )
                    ForwardingActionType.QUERY_STATUS -> stringResource(
                        R.string.confirm_effect_query,
                        model.serviceType.acronym,
                        model.sim.displaySlotNumber
                    )
                }
                Text(
                    text = effectText,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Carrier-specific tariff & provisioning notice
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (model.isBengali) model.carrier.tariffWarningBn else model.carrier.tariffWarningEn,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(CalloraSpacing.sm)
                    )
                }

                val verificationNote = when (model.executionMethod) {
                    ForwardingExecutionMethod.SAFE_DIALER_INTENT -> stringResource(R.string.confirm_verification_dialer_note)
                    ForwardingExecutionMethod.DIRECT_USSD_API -> stringResource(R.string.confirm_verification_ussd_note)
                    ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS -> stringResource(R.string.confirm_verification_settings_note)
                }
                InfoRow(
                    label = stringResource(R.string.confirm_label_verification),
                    value = verificationNote
                )

                if (model.isMultiSimDevice && model.executionMethod == ForwardingExecutionMethod.SAFE_DIALER_INTENT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = multiSimChecked,
                            onCheckedChange = { multiSimChecked = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                R.string.confirm_multisim_checkbox,
                                model.sim.displaySlotNumber,
                                model.sim.displayName
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(multiSimChecked) },
                enabled = multiSimChecked
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Screen 8: Forwarding Operation Progress Dialog.
 */
@Composable
fun ForwardingProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.progress_sheet_title)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text(
                    text = stringResource(R.string.progress_sheet_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {}
    )
}

/**
 * Screen 9: Truthful Operation Result & Verification Details Dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForwardingResultDialog(
    result: ForwardingOperationResult,
    onCopyMmi: (String) -> Unit,
    onOpenCallSettings: () -> Unit,
    onCheckStatusNow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
            ) {
                val observedActive = (result as? ForwardingOperationResult.VerifiedSuccess)?.observedActive
                VerificationStatusBadge(
                    state = result.verificationState,
                    observedActive = observedActive
                )

                val summaryMessage = when (result) {
                    is ForwardingOperationResult.VerifiedSuccess -> {
                        if (result.observedActive) {
                            stringResource(
                                R.string.result_verified_active_banner,
                                result.observedDestinationMasked ?: result.carrierRawSummary
                            )
                        } else {
                            stringResource(R.string.result_verified_inactive_banner)
                        }
                    }

                    is ForwardingOperationResult.AcceptedUnverified -> {
                        stringResource(R.string.result_unverified_honest_banner)
                    }

                    is ForwardingOperationResult.Failure -> {
                        if (result.resultCode == ForwardingResultCode.UNSUPPORTED) {
                            stringResource(R.string.result_unsupported_banner, result.diagnosticCode)
                        } else {
                            stringResource(R.string.result_failed_banner, result.diagnosticCode)
                        }
                    }
                }

                Text(
                    text = summaryMessage,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!result.preparedMmiCode.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(R.string.confirm_label_mmi_preview),
                        value = result.preparedMmiCode!!,
                        monospace = true
                    )
                }

                Text(
                    text = stringResource(R.string.result_next_steps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                ) {
                    if (!result.preparedMmiCode.isNullOrBlank()) {
                        OutlinedButton(onClick = { onCopyMmi(result.preparedMmiCode!!) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_copy_code))
                        }
                    }
                    OutlinedButton(onClick = onCheckStatusNow) {
                        Text(stringResource(R.string.action_check_status))
                    }
                    OutlinedButton(onClick = onOpenCallSettings) {
                        Icon(Icons.Outlined.SettingsPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_open_call_settings))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (monospace) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}
