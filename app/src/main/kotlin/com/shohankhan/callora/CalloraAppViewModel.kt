package com.shohankhan.callora

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shohankhan.callora.core.common.BackupJsonCodec
import com.shohankhan.callora.core.common.GsmMmiCodec
import com.shohankhan.callora.core.forwarding.RestorationStep
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.AppThemeMode
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.BatterySnapshot
import com.shohankhan.callora.core.model.ConfigurationHistoryEntry
import com.shohankhan.callora.core.model.FavoriteDestination
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingOperationResult
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.SimDiscoverySnapshot
import com.shohankhan.callora.core.model.SimDiscoveryState
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile
import com.shohankhan.callora.core.telephony.BangladeshCarrierCatalog
import com.shohankhan.callora.core.telephony.PermissionSnapshot
import com.shohankhan.callora.core.ui.PendingConfirmationModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingOperationIntent(
    val confirmationModel: PendingConfirmationModel,
    val noReplyTimeoutSeconds: Int?,
    val profileToActivate: SmartProfile? = null
)

class CalloraAppViewModel(
    private val container: AppContainer
) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.storage.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    val profiles: StateFlow<List<SmartProfile>> = container.storage.profiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val favorites: StateFlow<List<FavoriteDestination>> = container.storage.favorites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val observedStates: StateFlow<List<ObservedSimForwardingState>> = container.storage.observedStates.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val baselines: StateFlow<List<BaselineSimConfig>> = container.storage.baselines.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val history: StateFlow<List<ConfigurationHistoryEntry>> = container.storage.history.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _simSnapshot = MutableStateFlow(
        SimDiscoverySnapshot(
            state = SimDiscoveryState.PERMISSION_REQUIRED,
            subscriptions = emptyList(),
            checkedAtMillis = container.clock.nowMillis()
        )
    )
    val simSnapshot: StateFlow<SimDiscoverySnapshot> = _simSnapshot.asStateFlow()

    val battery: StateFlow<BatterySnapshot> = container.batteryStateSource.batteryState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        container.batteryStateSource.readCurrentBattery()
    )

    private val _permissions = MutableStateFlow(container.permissionStatusProvider.currentPermissions())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _selectedSimSlot = MutableStateFlow(0)
    val selectedSimSlot: StateFlow<Int> = _selectedSimSlot.asStateFlow()

    private val _pendingOperation = MutableStateFlow<PendingOperationIntent?>(null)
    val pendingOperation: StateFlow<PendingOperationIntent?> = _pendingOperation.asStateFlow()

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    private val _lastOperationResult = MutableStateFlow<ForwardingOperationResult?>(null)
    val lastOperationResult: StateFlow<ForwardingOperationResult?> = _lastOperationResult.asStateFlow()

    private val _restorationPlan = MutableStateFlow<List<RestorationStep>?>(null)
    val restorationPlan: StateFlow<List<RestorationStep>?> = _restorationPlan.asStateFlow()

    private val _settingsBannerMessage = MutableStateFlow<String?>(null)
    val settingsBannerMessage: StateFlow<String?> = _settingsBannerMessage.asStateFlow()

    init {
        viewModelScope.launch {
            container.storage.ensureSeeded()
            refreshPermissionsAndSubscriptions()
        }
        viewModelScope.launch {
            container.subscriptionDataSource.discoverySnapshot.collect { snap ->
                _simSnapshot.value = snap
            }
        }
    }

    fun refreshPermissionsAndSubscriptions() {
        _permissions.value = container.permissionStatusProvider.currentPermissions()
        viewModelScope.launch {
            val known = container.storage.currentSettings().lastKnownSimSignatures
            val snap = container.subscriptionDataSource.refreshSubscriptions(known)
            _simSnapshot.value = snap
            if (snap.subscriptions.isNotEmpty() && !snap.subscriptionChangedWarning) {
                val sigs = snap.subscriptions.map { it.stableSignature }.toSet()
                container.storage.updateSettings { it.copy(lastKnownSimSignatures = sigs) }
            }
        }
    }

    fun selectLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(language = language) }
        }
        try {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.languageTag)
            )
        } catch (_: Exception) {
            // Safe no-op in pure unit-test environments without AppCompat host
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(onboardingCompleted = true) }
        }
    }

    fun selectTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(dynamicColorEnabled = enabled) }
        }
    }

    fun setLowBatteryWarning(enabled: Boolean) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(lowBatteryWarningEnabled = enabled) }
        }
    }

    fun setLowBatteryThreshold(percent: Int) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(lowBatteryThresholdPercent = percent.coerceIn(5, 50)) }
        }
    }

    fun setChargingPrompt(enabled: Boolean) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(chargingPromptEnabled = enabled) }
        }
    }

    fun setRebootRecoveryCheck(enabled: Boolean) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(rebootRecoveryCheckEnabled = enabled) }
        }
    }

    fun setPreferDirectUssd(enabled: Boolean) {
        viewModelScope.launch {
            container.storage.updateSettings { it.copy(preferDirectUssdWhenPermitted = enabled) }
        }
    }

    fun acknowledgeRebootOrSimWarning() {
        viewModelScope.launch {
            val sigs = _simSnapshot.value.subscriptions.map { it.stableSignature }.toSet()
            container.storage.updateSettings {
                it.copy(
                    pendingRebootReview = false,
                    lastKnownSimSignatures = if (sigs.isNotEmpty()) sigs else it.lastKnownSimSignatures
                )
            }
            refreshPermissionsAndSubscriptions()
        }
    }

    fun selectSimSlot(slotIndex: Int) {
        _selectedSimSlot.value = slotIndex
    }

    fun stageForwardingOperation(
        sim: SimSubscription,
        serviceType: ForwardingServiceType,
        actionType: ForwardingActionType,
        destinationNumber: String?,
        noReplyTimeoutSeconds: Int?,
        profile: SmartProfile? = null
    ) {
        val currentSettings = settings.value
        val carrier = BangladeshCarrierCatalog.resolveForSubscription(sim).config
        val method = when {
            !carrier.allowMmiDialPreparation -> ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS
            currentSettings.preferDirectUssdWhenPermitted && permissions.value.hasCallPhone ->
                ForwardingExecutionMethod.DIRECT_USSD_API
            else -> carrier.preferredMethod
        }

        val previewMmi = if (method == ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS) {
            null
        } else {
            (GsmMmiCodec.buildCommand(
                serviceType = serviceType,
                actionType = actionType,
                destinationNumber = destinationNumber,
                noReplyTimeoutSeconds = noReplyTimeoutSeconds,
                defaultCountryIso = currentSettings.defaultCountryIso
            ) as? GsmMmiCodec.MmiBuildResult.Success)?.mmiString
        }

        _pendingOperation.value = PendingOperationIntent(
            confirmationModel = PendingConfirmationModel(
                sim = sim,
                carrier = carrier,
                serviceType = serviceType,
                actionType = actionType,
                destinationNumber = destinationNumber,
                preparedMmiCode = previewMmi,
                executionMethod = method,
                isMultiSimDevice = _simSnapshot.value.subscriptions.size > 1,
                isBengali = currentSettings.language == AppLanguage.BENGALI
            ),
            noReplyTimeoutSeconds = noReplyTimeoutSeconds,
            profileToActivate = profile
        )
    }

    fun stageProfileActivation(profile: SmartProfile, sim: SimSubscription) {
        val firstRule = profile.rules.firstOrNull {
            it.enabled && (it.simSignature == sim.stableSignature || it.simSlotIndex == sim.simSlotIndex)
        } ?: return

        stageForwardingOperation(
            sim = sim,
            serviceType = firstRule.serviceType,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = firstRule.destinationNumber,
            noReplyTimeoutSeconds = firstRule.noReplyTimeoutSeconds,
            profile = profile
        )
    }

    fun cancelPendingOperation() {
        _pendingOperation.value = null
    }

    fun executeConfirmedOperation(multiSimConfirmed: Boolean) {
        val staged = _pendingOperation.value ?: return
        _pendingOperation.value = null

        viewModelScope.launch {
            _operationInProgress.value = true
            try {
                val result = if (staged.profileToActivate != null) {
                    val outcomes = container.forwardingCoordinator.activateProfileRule(
                        profile = staged.profileToActivate,
                        sim = staged.confirmationModel.sim,
                        userConfirmedMultiSimSelection = multiSimConfirmed
                    )
                    outcomes.lastOrNull()
                } else {
                    container.forwardingCoordinator.performOperation(
                        sim = staged.confirmationModel.sim,
                        serviceType = staged.confirmationModel.serviceType,
                        actionType = staged.confirmationModel.actionType,
                        destinationNumber = staged.confirmationModel.destinationNumber,
                        noReplyTimeoutSeconds = staged.noReplyTimeoutSeconds,
                        preferredMethod = staged.confirmationModel.executionMethod,
                        userConfirmedMultiSimSelection = multiSimConfirmed
                    )
                }
                _lastOperationResult.value = result
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun dismissOperationResult() {
        _lastOperationResult.value = null
    }

    fun copyMmi(code: String) {
        container.forwardingExecutor.copyMmiToClipboard("Callora MMI", code)
    }

    fun openSystemCallSettings() {
        container.forwardingExecutor.openSystemCallSettings()
    }

    fun openSafeRestorationPlan() {
        viewModelScope.launch {
            val plan = container.forwardingCoordinator.buildSafeRestorationPlan(_simSnapshot.value.subscriptions)
            _restorationPlan.value = plan
        }
    }

    fun dismissRestorationPlan() {
        _restorationPlan.value = null
    }

    fun confirmRestorationStep(step: RestorationStep) {
        _restorationPlan.value = null
        stageForwardingOperation(
            sim = step.sim,
            serviceType = step.serviceType,
            actionType = step.targetAction,
            destinationNumber = step.targetDestination,
            noReplyTimeoutSeconds = null
        )
    }

    fun saveBaseline(sim: SimSubscription, serviceType: ForwardingServiceType, enabled: Boolean, destination: String?) {
        viewModelScope.launch {
            container.storage.upsertBaseline(
                BaselineSimConfig(
                    simSignature = sim.stableSignature,
                    simSlotIndex = sim.simSlotIndex,
                    serviceType = serviceType,
                    shouldBeEnabled = enabled,
                    baselineDestinationNumber = destination,
                    updatedAtMillis = container.clock.nowMillis()
                )
            )
        }
    }

    fun saveProfile(profile: SmartProfile) {
        viewModelScope.launch {
            container.storage.upsertProfile(profile)
        }
    }

    fun updateProfileSchedule(profile: SmartProfile, schedule: ProfileSchedule) {
        viewModelScope.launch {
            container.storage.upsertProfile(profile.copy(schedule = schedule))
        }
    }

    fun duplicateProfile(profileId: String) {
        viewModelScope.launch {
            container.storage.duplicateProfile(profileId)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            container.storage.deleteProfile(profileId)
        }
    }

    fun clearActiveProfileSelection() {
        viewModelScope.launch {
            container.storage.setActiveProfile(null, ForwardingVerificationState.UNKNOWN_NETWORK_STATE)
        }
    }

    fun addFavorite(label: String, normalized: String, original: String, notes: String) {
        viewModelScope.launch {
            container.storage.upsertFavorite(
                FavoriteDestination(
                    id = UUID.randomUUID().toString(),
                    label = label,
                    normalizedNumber = normalized,
                    originalInput = original,
                    notes = notes,
                    createdAtMillis = container.clock.nowMillis()
                )
            )
        }
    }

    fun deleteFavorite(id: String) {
        viewModelScope.launch {
            container.storage.deleteFavorite(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.storage.clearHistory()
        }
    }

    suspend fun generateBackupJson(appVersion: String): String =
        container.storage.exportBackupJson(appVersion)

    suspend fun importBackupJson(rawJson: String): BackupJsonCodec.ImportValidationResult =
        container.storage.importBackupJson(rawJson)

    fun generateRedactedDiagnosticsJson(appVersion: String): String {
        val sims = _simSnapshot.value.subscriptions
        val carriers = sims.map { BangladeshCarrierCatalog.resolveForSubscription(it).config.operatorId }
        val outcomes = history.value.take(20).map { "${it.serviceType.acronym}:${it.resultCode.name}:${it.verificationState.name}" }
        return BackupJsonCodec.buildRedactedDiagnosticJson(
            appVersion = appVersion,
            sdkInt = permissions.value.androidSdkInt,
            languageTag = settings.value.language.languageTag,
            activeSimCount = sims.size,
            carrierIds = carriers,
            recentOutcomeCodes = outcomes,
            generatedAtMillis = container.clock.nowMillis()
        )
    }

    fun postSettingsBanner(message: String?) {
        _settingsBannerMessage.value = message
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CalloraAppViewModel(container) as T
                }
            }
    }
}
