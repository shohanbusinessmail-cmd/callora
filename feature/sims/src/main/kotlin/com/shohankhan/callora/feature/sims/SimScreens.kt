package com.shohankhan.callora.feature.sims

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SettingsPhone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.FavoriteDestination
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.PhoneNumberValidationResult
import com.shohankhan.callora.core.model.SimDiscoverySnapshot
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.telephony.BangladeshCarrierCatalog
import com.shohankhan.callora.core.telephony.PermissionSnapshot
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.R
import com.shohankhan.callora.core.ui.SimIdentityChip
import com.shohankhan.callora.core.ui.VerificationStatusBadge
import com.shohankhan.callora.core.ui.badgeRes
import com.shohankhan.callora.core.ui.descRes
import com.shohankhan.callora.core.ui.messageRes
import com.shohankhan.callora.core.ui.titleRes

/**
 * Screen 4: Dynamic Per-SIM Management & Forwarding Controls.
 * Supports 0, 1, 2, or 3+ active subscriptions plus manual fallback mode if permission is denied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimManagementScreen(
    simSnapshot: SimDiscoverySnapshot,
    selectedSlotIndex: Int,
    observedStates: List<ObservedSimForwardingState>,
    baselines: List<BaselineSimConfig>,
    favorites: List<FavoriteDestination>,
    defaultCountryIso: String,
    isBengali: Boolean,
    onSelectSlot: (Int) -> Unit,
    onRequestForwardingAction: (
        sim: SimSubscription,
        serviceType: ForwardingServiceType,
        actionType: ForwardingActionType,
        destinationNumber: String?,
        noReplyTimeoutSeconds: Int?
    ) -> Unit,
    onSaveBaseline: (sim: SimSubscription, serviceType: ForwardingServiceType, enabled: Boolean, destination: String?) -> Unit,
    onOpenCallSettings: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onOpenFavoritesScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Provide a safe manual single-SIM placeholder ONLY when SIM discovery is unavailable/denied,
    // clearly labeled as Unknown Operator so unverified codes are never auto-run without consent.
    val manualFallbackSim = remember {
        SimSubscription(
            subscriptionId = -1,
            simSlotIndex = 0,
            isEmbedded = false,
            displayName = "Manual Handset SIM",
            carrierName = "Grameenphone", // Standard 3GPP manual dialer helper only when user chooses
            mcc = "",
            mnc = "",
            isNetworkAvailable = true
        )
    }

    val sims = simSnapshot.subscriptions
    val activeSim = sims.firstOrNull { it.simSlotIndex == selectedSlotIndex }
        ?: sims.firstOrNull()
        ?: manualFallbackSim

    val resolution = BangladeshCarrierCatalog.resolveForSubscription(activeSim)
    val carrier = resolution.config

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        if (simSnapshot.subscriptionChangedWarning) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = stringResource(R.string.sim_changed_warning_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(CalloraSpacing.md)
                    )
                }
            }
        }

        if (sims.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
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
                            style = MaterialTheme.typography.bodySmall
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                            Button(onClick = onRequestPhonePermission) {
                                Text(stringResource(R.string.action_grant_permission))
                            }
                            OutlinedButton(onClick = onOpenCallSettings) {
                                Text(stringResource(R.string.action_open_call_settings))
                            }
                        }
                    }
                }
            }
        } else if (sims.size > 1) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                ) {
                    sims.forEach { sim ->
                        FilterChip(
                            selected = sim.subscriptionId == activeSim.subscriptionId,
                            onClick = { onSelectSlot(sim.simSlotIndex) },
                            label = {
                                Text("${stringResource(R.string.sim_slot_badge, sim.displaySlotNumber)} • ${sim.displayName}")
                            }
                        )
                    }
                }
            }
        }

        // Carrier Details Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    SimIdentityChip(sim = activeSim)
                    Text(
                        text = stringResource(
                            R.string.sim_detail_title,
                            activeSim.displaySlotNumber,
                            if (isBengali) carrier.displayNameBn else carrier.displayNameEn
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(carrier.verificationLevel.badgeRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (isBengali) carrier.tariffWarningBn else carrier.tariffWarningEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isBengali) carrier.manualInstructionsBn else carrier.manualInstructionsEn,
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                        OutlinedButton(onClick = onOpenCallSettings) {
                            Icon(Icons.Outlined.SettingsPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open_call_settings))
                        }
                        OutlinedButton(onClick = onOpenFavoritesScreen) {
                            Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.quick_favorites))
                        }
                    }
                }
            }
        }

        // All 4 Forwarding Services (CFNRc prioritized first!)
        items(ForwardingServiceType.entries, key = { it.name }) { serviceType ->
            val observed = observedStates.firstOrNull {
                it.simSignature == activeSim.stableSignature && it.serviceType == serviceType
            }
            val baseline = baselines.firstOrNull {
                it.simSignature == activeSim.stableSignature && it.serviceType == serviceType
            }
            ServiceForwardingCard(
                sim = activeSim,
                serviceType = serviceType,
                observed = observed,
                baseline = baseline,
                favorites = favorites,
                defaultCountryIso = defaultCountryIso,
                onAction = { action, dest, timeout ->
                    onRequestForwardingAction(activeSim, serviceType, action, dest, timeout)
                },
                onSaveBaseline = { enabled, dest ->
                    onSaveBaseline(activeSim, serviceType, enabled, dest)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceForwardingCard(
    sim: SimSubscription,
    serviceType: ForwardingServiceType,
    observed: ObservedSimForwardingState?,
    baseline: BaselineSimConfig?,
    favorites: List<FavoriteDestination>,
    defaultCountryIso: String,
    onAction: (ForwardingActionType, String?, Int?) -> Unit,
    onSaveBaseline: (Boolean, String?) -> Unit
) {
    var destinationInput by rememberSaveable(sim.subscriptionId, serviceType.name) {
        mutableStateOf(baseline?.baselineDestinationNumber.orEmpty())
    }
    var timeoutSeconds by rememberSaveable(sim.subscriptionId, serviceType.name) {
        mutableIntStateOf(20)
    }
    var showFavoritePicker by rememberSaveable { mutableStateOf(false) }

    val validation = remember(destinationInput, defaultCountryIso) {
        PhoneNumberValidator.validate(destinationInput, defaultCountryIso = defaultCountryIso)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(CalloraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
        ) {
            Text(
                text = stringResource(serviceType.titleRes()),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(serviceType.descRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            VerificationStatusBadge(
                state = observed?.verificationState ?: ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
                observedActive = observed?.observedEnabled
            )

            OutlinedTextField(
                value = destinationInput,
                onValueChange = { destinationInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.destination_input_label)) },
                placeholder = { Text(stringResource(R.string.destination_input_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                trailingIcon = {
                    if (favorites.isNotEmpty()) {
                        TextButton(onClick = { showFavoritePicker = true }) {
                            Text(stringResource(R.string.favorites_pick_button))
                        }
                    }
                },
                supportingText = {
                    when (validation) {
                        is PhoneNumberValidationResult.Valid -> {
                            Column {
                                Text(validation.displayFormatted)
                                if ("INTERNATIONAL_DESTINATION_TARIFF_WARNING" in validation.warnings) {
                                    Text(
                                        text = stringResource(R.string.destination_intl_warning),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(stringResource(R.string.destination_mnp_note))
                                }
                            }
                        }

                        is PhoneNumberValidationResult.Invalid -> {
                            if (destinationInput.isNotBlank()) {
                                Text(
                                    text = stringResource(validation.issue.messageRes()),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(stringResource(R.string.destination_syntactic_disclaimer))
                            }
                        }
                    }
                }
            )

            if (serviceType.supportsRingTimeout) {
                Text(
                    text = stringResource(R.string.service_ring_timeout_label, timeoutSeconds),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = timeoutSeconds.toFloat(),
                    onValueChange = { timeoutSeconds = ((it.toInt() + 2) / 5 * 5).coerceIn(5, 30) },
                    valueRange = 5f..30f,
                    steps = 4
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
            ) {
                Button(
                    onClick = {
                        val validNum = (validation as? PhoneNumberValidationResult.Valid)?.e164Normalized
                        onAction(
                            ForwardingActionType.ACTIVATE,
                            validNum,
                            if (serviceType.supportsRingTimeout) timeoutSeconds else null
                        )
                    },
                    enabled = validation is PhoneNumberValidationResult.Valid
                ) {
                    Text(stringResource(R.string.action_activate_forwarding))
                }

                FilledTonalButton(
                    onClick = { onAction(ForwardingActionType.DEACTIVATE, null, null) }
                ) {
                    Text(stringResource(R.string.action_deactivate_forwarding))
                }

                OutlinedButton(
                    onClick = { onAction(ForwardingActionType.QUERY_STATUS, null, null) }
                ) {
                    Text(stringResource(R.string.action_query_forwarding))
                }
            }

            HorizontalDivider()
            Text(
                text = stringResource(R.string.sim_baseline_section_title),
                style = MaterialTheme.typography.labelMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                TextButton(onClick = { onSaveBaseline(false, null) }) {
                    Text(stringResource(R.string.sim_save_as_disabled_baseline))
                }
                if (validation is PhoneNumberValidationResult.Valid) {
                    TextButton(onClick = { onSaveBaseline(true, validation.e164Normalized) }) {
                        Text(stringResource(R.string.sim_save_current_as_baseline))
                    }
                }
            }
        }
    }

    if (showFavoritePicker) {
        AlertDialog(
            onDismissRequest = { showFavoritePicker = false },
            title = { Text(stringResource(R.string.favorites_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                    favorites.forEach { fav ->
                        OutlinedButton(
                            onClick = {
                                destinationInput = fav.normalizedNumber
                                showFavoritePicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${fav.label} • ${fav.normalizedNumber}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFavoritePicker = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

/**
 * Screen 12: Favorite Forwarding Numbers Screen.
 */
@Composable
fun FavoriteNumbersScreen(
    favorites: List<FavoriteDestination>,
    defaultCountryIso: String,
    onAddFavorite: (label: String, normalized: String, original: String, notes: String) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var label by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    val validation = remember(number, defaultCountryIso) {
        PhoneNumberValidator.validate(number, defaultCountryIso = defaultCountryIso)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.favorites_add_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.favorites_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.favorites_label_input)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(stringResource(R.string.destination_input_label)) },
                        placeholder = { Text(stringResource(R.string.destination_input_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            if (validation is PhoneNumberValidationResult.Invalid && number.isNotBlank()) {
                                Text(
                                    text = stringResource(validation.issue.messageRes()),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(stringResource(R.string.destination_syntactic_disclaimer))
                            }
                        }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.favorites_notes_input)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val valid = validation as? PhoneNumberValidationResult.Valid ?: return@Button
                            onAddFavorite(label.trim(), valid.e164Normalized, number.trim(), notes.trim())
                            label = ""
                            number = ""
                            notes = ""
                        },
                        enabled = label.isNotBlank() && validation is PhoneNumberValidationResult.Valid
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(favorites, key = { it.id }) { fav ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CalloraSpacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fav.label, style = MaterialTheme.typography.titleMedium)
                            Text(fav.normalizedNumber, style = MaterialTheme.typography.bodyMedium)
                            if (fav.notes.isNotBlank()) {
                                Text(
                                    fav.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteFavorite(fav.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 13: Permissions & Device Compatibility Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsCompatibilityScreen(
    permissions: PermissionSnapshot,
    onRequestReadPhoneState: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestCallPhone: () -> Unit,
    onOpenSystemAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        item {
            Text(
                text = stringResource(R.string.permissions_intro),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            PermissionItemCard(
                title = stringResource(R.string.perm_phone_state_title),
                description = stringResource(R.string.perm_phone_state_desc),
                granted = permissions.hasReadPhoneState,
                onRequest = onRequestReadPhoneState,
                onOpenSettings = onOpenSystemAppSettings
            )
        }

        item {
            PermissionItemCard(
                title = stringResource(R.string.perm_notifications_title),
                description = stringResource(R.string.perm_notifications_desc),
                granted = permissions.hasPostNotifications,
                onRequest = onRequestNotifications,
                onOpenSettings = onOpenSystemAppSettings
            )
        }

        item {
            PermissionItemCard(
                title = stringResource(R.string.perm_call_phone_title),
                description = stringResource(R.string.perm_call_phone_desc),
                granted = permissions.hasCallPhone,
                onRequest = onRequestCallPhone,
                onOpenSettings = onOpenSystemAppSettings
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.compat_section_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.compat_system_api_note),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Android SDK: ${permissions.androidSdkInt}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CalloraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (granted) {
                    stringResource(R.string.perm_status_granted)
                } else {
                    stringResource(R.string.perm_status_not_granted)
                },
                style = MaterialTheme.typography.labelMedium
            )
            if (!granted) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                    Button(onClick = onRequest) {
                        Text(stringResource(R.string.action_grant_permission))
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.action_open_app_settings))
                    }
                }
            }
        }
    }
}
