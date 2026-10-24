package com.shohankhan.callora.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shohankhan.callora.core.common.PhoneNumberValidator
import com.shohankhan.callora.core.common.ScheduleValidator
import com.shohankhan.callora.core.model.BuiltInProfileType
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.PhoneNumberValidationResult
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.SimForwardingRule
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.R
import com.shohankhan.callora.core.ui.VerificationStatusBadge
import com.shohankhan.callora.core.ui.messageRes
import com.shohankhan.callora.core.ui.titleRes
import java.util.UUID

/**
 * Screen 5: Smart Forwarding Profiles List.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileListScreen(
    profiles: List<SmartProfile>,
    subscriptions: List<SimSubscription>,
    onCreateNew: () -> Unit,
    onEditProfile: (String) -> Unit,
    onEditSchedule: (String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onActivateProfileOnSim: (SmartProfile, SimSubscription) -> Unit,
    onClearActiveProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.profiles_subtitle),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.profiles_advance_setup_notice),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                        Button(onClick = onCreateNew) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.profiles_create_new))
                        }
                        if (profiles.any { it.isSelectedActive }) {
                            OutlinedButton(onClick = onClearActiveProfile) {
                                Text(stringResource(R.string.profiles_deactivate_selection))
                            }
                        }
                    }
                }
            }
        }

        items(profiles, key = { it.id }) { profile ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                        AssistChip(
                            onClick = { onEditProfile(profile.id) },
                            label = {
                                Text(
                                    if (profile.isBuiltIn) {
                                        stringResource(R.string.profiles_builtin_badge)
                                    } else {
                                        stringResource(R.string.profiles_custom_badge)
                                    }
                                )
                            }
                        )
                        if (profile.isSelectedActive) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.profiles_active_selected_badge)) }
                            )
                        }
                        if (profile.schedule.enabled) {
                            AssistChip(
                                onClick = { onEditSchedule(profile.id) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        "${ScheduleValidator.formatMinuteOfDay(profile.schedule.startMinuteOfDay)}–${ScheduleValidator.formatMinuteOfDay(profile.schedule.endMinuteOfDay)}"
                                    )
                                }
                            )
                        }
                    }

                    Text(profile.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        profile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    VerificationStatusBadge(state = profile.lastVerifiedState)

                    if (profile.rules.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_no_rules_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        profile.rules.filter { it.enabled }.forEach { rule ->
                            Text(
                                text = "• ${rule.simDisplayLabel}: ${rule.serviceType.acronym} → ${rule.destinationNumber}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider()

                    // Per-SIM explicit activation buttons so rules never run silently on unintended SIMs
                    if (profile.rules.any { it.enabled } && subscriptions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                        ) {
                            subscriptions.forEach { sim ->
                                val hasRule = profile.rules.any {
                                    it.enabled && (it.simSignature == sim.stableSignature || it.simSlotIndex == sim.simSlotIndex)
                                }
                                if (hasRule) {
                                    Button(onClick = { onActivateProfileOnSim(profile, sim) }) {
                                        Text("${stringResource(R.string.profiles_activate_button)} (${sim.displayName})")
                                    }
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                    ) {
                        FilledTonalButton(onClick = { onEditProfile(profile.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_edit))
                        }
                        OutlinedButton(onClick = { onEditSchedule(profile.id) }) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.schedule_editor_title))
                        }
                        OutlinedButton(onClick = { onDuplicateProfile(profile.id) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_duplicate))
                        }
                        if (!profile.isBuiltIn) {
                            OutlinedButton(onClick = { onDeleteProfile(profile.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 6: Profile Editor Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(
    existingProfile: SmartProfile?,
    subscriptions: List<SimSubscription>,
    defaultCountryIso: String,
    onSave: (SmartProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable(existingProfile?.id) {
        mutableStateOf(existingProfile?.name.orEmpty())
    }
    var description by rememberSaveable(existingProfile?.id) {
        mutableStateOf(existingProfile?.description.orEmpty())
    }
    var lowBatteryPercent by rememberSaveable(existingProfile?.id) {
        mutableIntStateOf(existingProfile?.lowBatteryThresholdPercent ?: 15)
    }
    var enableLowBatteryHint by rememberSaveable(existingProfile?.id) {
        mutableStateOf(existingProfile?.lowBatteryThresholdPercent != null)
    }
    var chargingSuggest by rememberSaveable(existingProfile?.id) {
        mutableStateOf(existingProfile?.suggestWhenChargingStarts ?: false)
    }

    val effectiveSims = remember(subscriptions) {
        subscriptions.ifEmpty {
            listOf(
                SimSubscription(
                    subscriptionId = -1,
                    simSlotIndex = 0,
                    isEmbedded = false,
                    displayName = "SIM 1",
                    carrierName = "SIM 1",
                    mcc = "470",
                    mnc = "01"
                )
            )
        }
    }

    val editableRules = remember(existingProfile?.id, effectiveSims) {
        mutableStateListOf<SimForwardingRule>().apply {
            for (sim in effectiveSims) {
                val found = existingProfile?.rules?.firstOrNull {
                    it.simSignature == sim.stableSignature || it.simSlotIndex == sim.simSlotIndex
                }
                add(
                    found ?: SimForwardingRule(
                        simSignature = sim.stableSignature,
                        simSlotIndex = sim.simSlotIndex,
                        simDisplayLabel = sim.displayName,
                        enabled = false,
                        serviceType = ForwardingServiceType.UNREACHABLE,
                        destinationNumber = ""
                    )
                )
            }
        }
    }

    val allEnabledRulesValid = editableRules.all { rule ->
        !rule.enabled || PhoneNumberValidator.validate(rule.destinationNumber, defaultCountryIso) is PhoneNumberValidationResult.Valid
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        item {
            Text(
                text = if (existingProfile == null) {
                    stringResource(R.string.profile_editor_new_title)
                } else {
                    stringResource(R.string.profile_editor_edit_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.profile_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.profile_desc_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.profile_low_battery_label, lowBatteryPercent),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enableLowBatteryHint,
                            onCheckedChange = { enableLowBatteryHint = it }
                        )
                    }
                    if (enableLowBatteryHint) {
                        Slider(
                            value = lowBatteryPercent.toFloat(),
                            onValueChange = { lowBatteryPercent = it.toInt().coerceIn(5, 50) },
                            valueRange = 5f..50f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.profile_charging_suggest_label),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = chargingSuggest,
                            onCheckedChange = { chargingSuggest = it }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.profile_rules_section),
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(editableRules.size) { idx ->
            val rule = editableRules[idx]
            val validation = PhoneNumberValidator.validate(rule.destinationNumber, defaultCountryIso)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rule.enabled,
                            onCheckedChange = { checked ->
                                editableRules[idx] = rule.copy(enabled = checked)
                            }
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_add_rule_for_sim,
                                rule.simSlotIndex + 1,
                                rule.simDisplayLabel
                            ),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)) {
                        ForwardingServiceType.entries.forEach { st ->
                            FilterChip(
                                selected = rule.serviceType == st,
                                onClick = { editableRules[idx] = rule.copy(serviceType = st) },
                                label = { Text(st.acronym) }
                            )
                        }
                    }

                    Text(
                        text = stringResource(rule.serviceType.titleRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = rule.destinationNumber,
                        onValueChange = { updated ->
                            editableRules[idx] = rule.copy(destinationNumber = updated)
                        },
                        label = { Text(stringResource(R.string.destination_input_label)) },
                        placeholder = { Text(stringResource(R.string.destination_input_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            if (rule.enabled && validation is PhoneNumberValidationResult.Invalid) {
                                Text(
                                    text = stringResource(validation.issue.messageRes()),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                Button(
                    onClick = {
                        val normalizedRules = editableRules.map { r ->
                            val valid = PhoneNumberValidator.validate(r.destinationNumber, defaultCountryIso)
                            if (valid is PhoneNumberValidationResult.Valid) {
                                r.copy(destinationNumber = valid.e164Normalized)
                            } else {
                                r
                            }
                        }
                        val saved = SmartProfile(
                            id = existingProfile?.id ?: "custom_${UUID.randomUUID()}",
                            name = name.trim(),
                            description = description.trim(),
                            builtInType = existingProfile?.builtInType ?: BuiltInProfileType.CUSTOM,
                            isBuiltIn = existingProfile?.isBuiltIn ?: false,
                            rules = normalizedRules,
                            schedule = existingProfile?.schedule ?: ProfileSchedule(),
                            lowBatteryThresholdPercent = if (enableLowBatteryHint) lowBatteryPercent else null,
                            suggestWhenChargingStarts = chargingSuggest,
                            requiresConfirmation = true,
                            isSelectedActive = existingProfile?.isSelectedActive ?: false,
                            lastActivationAttemptMillis = existingProfile?.lastActivationAttemptMillis,
                            lastVerifiedState = existingProfile?.lastVerifiedState
                                ?: com.shohankhan.callora.core.model.ForwardingVerificationState.UNKNOWN_NETWORK_STATE
                        )
                        onSave(saved)
                    },
                    enabled = name.isNotBlank() && allEnabledRulesValid
                ) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/**
 * Screen 11: Schedule Editor Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorScreen(
    profile: SmartProfile,
    onSaveSchedule: (ProfileSchedule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enabled by rememberSaveable(profile.id) { mutableStateOf(profile.schedule.enabled) }
    var selectedDays by rememberSaveable(profile.id) { mutableStateOf(profile.schedule.daysOfWeek.toList()) }
    var startHour by rememberSaveable(profile.id) { mutableIntStateOf(profile.schedule.startMinuteOfDay / 60) }
    var endHour by rememberSaveable(profile.id) { mutableIntStateOf(profile.schedule.endMinuteOfDay / 60) }

    val dayLabels = listOf(
        7 to R.string.day_sun,
        1 to R.string.day_mon,
        2 to R.string.day_tue,
        3 to R.string.day_wed,
        4 to R.string.day_thu,
        5 to R.string.day_fri,
        6 to R.string.day_sat
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        item {
            Text(
                text = "${stringResource(R.string.schedule_editor_title)} — ${profile.name}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.schedule_workmanager_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_enable_label),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    Text(
                        text = stringResource(R.string.schedule_days_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)) {
                        dayLabels.forEach { (isoDay, resId) ->
                            val isSelected = isoDay in selectedDays
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedDays = if (isSelected && selectedDays.size > 1) {
                                        selectedDays - isoDay
                                    } else {
                                        (selectedDays + isoDay).distinct()
                                    }
                                },
                                label = { Text(stringResource(resId)) }
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.schedule_start_time,
                            ScheduleValidator.formatMinuteOfDay(startHour * 60)
                        )
                    )
                    Slider(
                        value = startHour.toFloat(),
                        onValueChange = { startHour = it.toInt().coerceIn(0, 23) },
                        valueRange = 0f..23f,
                        steps = 22
                    )

                    Text(
                        text = stringResource(
                            R.string.schedule_end_time,
                            ScheduleValidator.formatMinuteOfDay(endHour * 60)
                        )
                    )
                    Slider(
                        value = endHour.toFloat(),
                        onValueChange = { endHour = it.toInt().coerceIn(0, 23) },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                Button(
                    onClick = {
                        val safeEnd = if (endHour == startHour) (startHour + 1) % 24 else endHour
                        onSaveSchedule(
                            ProfileSchedule(
                                enabled = enabled,
                                daysOfWeek = selectedDays.toSet(),
                                startMinuteOfDay = startHour * 60,
                                endMinuteOfDay = safeEnd * 60,
                                expiresAtEpochMillis = null
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
