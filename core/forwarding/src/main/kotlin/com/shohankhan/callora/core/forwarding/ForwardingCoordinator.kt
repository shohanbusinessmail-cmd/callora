package com.shohankhan.callora.core.forwarding

import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.common.GsmMmiCodec
import com.shohankhan.callora.core.common.SensitiveDataRedactor
import com.shohankhan.callora.core.common.StateReconciler
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.ConfigurationHistoryEntry
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingOperationResult
import com.shohankhan.callora.core.model.ForwardingRequest
import com.shohankhan.callora.core.model.ForwardingResultCode
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.ReconciledSimServiceState
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile
import com.shohankhan.callora.core.storage.CalloraStorageRepository
import com.shohankhan.callora.core.telephony.BangladeshCarrierCatalog
import com.shohankhan.callora.core.telephony.PermissionStatusProvider
import com.shohankhan.callora.core.telephony.SubscriptionDataSource
import kotlinx.coroutines.flow.first
import java.util.UUID

interface ForwardingExecutor {
    suspend fun execute(
        request: ForwardingRequest,
        preparedMmiCode: String?,
        activeSimCount: Int
    ): ForwardingOperationResult

    fun copyMmiToClipboard(label: String, mmiCode: String): Boolean
    fun openSystemCallSettings(): Boolean
}

data class RestorationStep(
    val sim: SimSubscription,
    val serviceType: ForwardingServiceType,
    val targetAction: ForwardingActionType,
    val targetDestination: String?,
    val reasonCode: String
)

data class RebootRecoverySummary(
    val activeSubscriptions: List<SimSubscription>,
    val subscriptionChangedSinceLastRun: Boolean,
    val reconciledDifferences: List<ReconciledSimServiceState>,
    val requiresUserConfirmation: Boolean
)

/**
 * Coordinates pre-flight validation, carrier compatibility checks, execution via [ForwardingExecutor],
 * truthful state persistence, safe restoration planning, and reboot recovery reconciliation.
 */
class CalloraForwardingCoordinator(
    private val subscriptionDataSource: SubscriptionDataSource,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val storage: CalloraStorageRepository,
    private val executor: ForwardingExecutor,
    private val clock: CalloraClock = CalloraClock.SYSTEM
) {

    suspend fun performOperation(
        sim: SimSubscription,
        serviceType: ForwardingServiceType,
        actionType: ForwardingActionType,
        destinationNumber: String? = null,
        noReplyTimeoutSeconds: Int? = null,
        preferredMethod: ForwardingExecutionMethod? = null,
        userConfirmedMultiSimSelection: Boolean = false,
        profileName: String? = null
    ): ForwardingOperationResult {
        val now = clock.nowMillis()
        val settings = storage.currentSettings()
        val carrierResolution = BangladeshCarrierCatalog.resolveForSubscription(sim)
        val carrier = carrierResolution.config

        val chosenMethod = preferredMethod ?: when {
            !carrier.allowMmiDialPreparation -> ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS
            settings.preferDirectUssdWhenPermitted && permissionStatusProvider.currentPermissions().hasCallPhone ->
                ForwardingExecutionMethod.DIRECT_USSD_API
            else -> carrier.preferredMethod
        }

        // Check network availability before attempting network/dialer operations
        if (!sim.isNetworkAvailable && chosenMethod != ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS) {
            val fail = ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.NETWORK_UNAVAILABLE,
                verificationState = ForwardingVerificationState.FAILED_OPERATION,
                preparedMmiCode = null,
                diagnosticCode = "CELLULAR_NETWORK_UNAVAILABLE",
                timestampMillis = now
            )
            recordOutcome(sim, carrier.displayNameEn, serviceType, actionType, destinationNumber, chosenMethod, fail, profileName)
            return fail
        }

        // Never auto-prepare unverified MMI codes on operators where MMI codes are unverified
        if (chosenMethod != ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS && !carrier.allowMmiDialPreparation) {
            val unsupported = ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.UNSUPPORTED,
                verificationState = ForwardingVerificationState.UNSUPPORTED_CAPABILITY,
                preparedMmiCode = null,
                diagnosticCode = "OPERATOR_MMI_NOT_VERIFIED_USE_CALL_SETTINGS",
                suggestSystemCallSettings = true,
                timestampMillis = now
            )
            recordOutcome(sim, carrier.displayNameEn, serviceType, actionType, destinationNumber, chosenMethod, unsupported, profileName)
            return unsupported
        }

        val mmiCode: String? = if (chosenMethod == ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS) {
            null
        } else {
            when (val built = GsmMmiCodec.buildCommand(
                serviceType = serviceType,
                actionType = actionType,
                destinationNumber = destinationNumber,
                noReplyTimeoutSeconds = noReplyTimeoutSeconds,
                defaultCountryIso = settings.defaultCountryIso
            )) {
                is GsmMmiCodec.MmiBuildResult.Success -> built.mmiString
                is GsmMmiCodec.MmiBuildResult.Error -> {
                    val fail = ForwardingOperationResult.Failure(
                        resultCode = ForwardingResultCode.FAILED,
                        verificationState = ForwardingVerificationState.FAILED_OPERATION,
                        preparedMmiCode = null,
                        diagnosticCode = built.errorCode,
                        suggestSystemCallSettings = false,
                        timestampMillis = now
                    )
                    recordOutcome(sim, carrier.displayNameEn, serviceType, actionType, destinationNumber, chosenMethod, fail, profileName)
                    return fail
                }
            }
        }

        val snapshot = subscriptionDataSource.refreshSubscriptions(settings.lastKnownSimSignatures)
        // Verify the requested SIM is still present with the same signature (prevents wrong-SIM execution!)
        val liveMatch = snapshot.subscriptions.firstOrNull {
            it.subscriptionId == sim.subscriptionId && it.stableSignature == sim.stableSignature
        }
        if (snapshot.subscriptions.isNotEmpty() && liveMatch == null) {
            val simMismatch = ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.SIM_SELECTION_UNAVAILABLE,
                verificationState = ForwardingVerificationState.FAILED_OPERATION,
                preparedMmiCode = mmiCode,
                diagnosticCode = "SUBSCRIPTION_CHANGED_BEFORE_EXECUTION",
                timestampMillis = now
            )
            recordOutcome(sim, carrier.displayNameEn, serviceType, actionType, destinationNumber, chosenMethod, simMismatch, profileName)
            return simMismatch
        }

        val activeCount = snapshot.subscriptions.size.coerceAtLeast(1)
        if (activeCount > 1 &&
            chosenMethod == ForwardingExecutionMethod.SAFE_DIALER_INTENT &&
            !userConfirmedMultiSimSelection
        ) {
            val needConfirm = ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.SIM_SELECTION_UNAVAILABLE,
                verificationState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
                preparedMmiCode = mmiCode,
                diagnosticCode = "MULTI_SIM_CONFIRMATION_REQUIRED",
                timestampMillis = now
            )
            return needConfirm
        }

        val request = ForwardingRequest(
            subscriptionId = sim.subscriptionId,
            simSlotIndex = sim.simSlotIndex,
            simSignature = sim.stableSignature,
            carrierOperatorId = carrier.operatorId,
            serviceType = serviceType,
            actionType = actionType,
            destinationNumber = destinationNumber,
            noReplyTimeoutSeconds = noReplyTimeoutSeconds,
            preferredMethod = chosenMethod,
            userConfirmedMultiSimSelection = userConfirmedMultiSimSelection
        )

        val result = executor.execute(request, mmiCode, activeCount)
        recordOutcome(sim, carrier.displayNameEn, serviceType, actionType, destinationNumber, chosenMethod, result, profileName)
        return result
    }

    /**
     * Activates a profile's rules across matching active subscriptions ONLY when signatures match.
     * Never silently applies a rule to a swapped or unrecognized SIM.
     */
    suspend fun activateProfileRule(
        profile: SmartProfile,
        sim: SimSubscription,
        userConfirmedMultiSimSelection: Boolean
    ): List<ForwardingOperationResult> {
        val matchingRules = profile.rules.filter { rule ->
            rule.enabled && (
                rule.simSignature == sim.stableSignature ||
                    (rule.simSignature.isBlank() && rule.simSlotIndex == sim.simSlotIndex)
                )
        }
        if (matchingRules.isEmpty()) return emptyList()

        val outcomes = mutableListOf<ForwardingOperationResult>()
        for (rule in matchingRules) {
            val res = performOperation(
                sim = sim,
                serviceType = rule.serviceType,
                actionType = ForwardingActionType.ACTIVATE,
                destinationNumber = rule.destinationNumber,
                noReplyTimeoutSeconds = rule.noReplyTimeoutSeconds,
                userConfirmedMultiSimSelection = userConfirmedMultiSimSelection,
                profileName = profile.name
            )
            outcomes.add(res)
        }

        val aggregateVerification = when {
            outcomes.isNotEmpty() && outcomes.all { it.verificationState == ForwardingVerificationState.NETWORK_STATE_VERIFIED } ->
                ForwardingVerificationState.NETWORK_STATE_VERIFIED
            outcomes.any {
                it.verificationState == ForwardingVerificationState.COMMAND_SUBMITTED ||
                    it.verificationState == ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED
            } -> ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED
            outcomes.any { it.verificationState == ForwardingVerificationState.UNSUPPORTED_CAPABILITY } ->
                ForwardingVerificationState.UNSUPPORTED_CAPABILITY
            else -> ForwardingVerificationState.FAILED_OPERATION
        }
        storage.setActiveProfile(profile.id, aggregateVerification)
        return outcomes
    }

    /**
     * Builds a safe, explicit restoration plan per SIM based on saved baseline settings and
     * observed states — NEVER blindly issuing ##002# across all services!
     */
    suspend fun buildSafeRestorationPlan(subscriptions: List<SimSubscription>): List<RestorationStep> {
        val observed = storage.observedStates.first()
        val steps = mutableListOf<RestorationStep>()

        for (sim in subscriptions) {
            val baselines = storage.baselinesForSim(sim.stableSignature)
                .associateBy { it.serviceType }
            val simObserved = observed.filter { it.simSignature == sim.stableSignature }
                .associateBy { it.serviceType }

            for (service in ForwardingServiceType.entries) {
                val base: BaselineSimConfig? = baselines[service]
                val obs = simObserved[service]
                if (base != null) {
                    if (base.shouldBeEnabled && !base.baselineDestinationNumber.isNullOrBlank()) {
                        steps.add(
                            RestorationStep(
                                sim = sim,
                                serviceType = service,
                                targetAction = ForwardingActionType.ACTIVATE,
                                targetDestination = base.baselineDestinationNumber,
                                reasonCode = "RESTORE_SAVED_BASELINE_DESTINATION"
                            )
                        )
                    } else if (obs?.observedEnabled == true ||
                        obs?.verificationState == ForwardingVerificationState.COMMAND_SUBMITTED ||
                        obs?.verificationState == ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED
                    ) {
                        steps.add(
                            RestorationStep(
                                sim = sim,
                                serviceType = service,
                                targetAction = ForwardingActionType.DEACTIVATE,
                                targetDestination = null,
                                reasonCode = "DEACTIVATE_TO_MATCH_DISABLED_BASELINE"
                            )
                        )
                    }
                } else if (obs != null && (
                        obs.observedEnabled == true ||
                            obs.desiredEnabled == true ||
                            obs.verificationState == ForwardingVerificationState.COMMAND_SUBMITTED ||
                            obs.verificationState == ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED
                        )
                ) {
                    steps.add(
                        RestorationStep(
                            sim = sim,
                            serviceType = service,
                            targetAction = ForwardingActionType.DEACTIVATE,
                            targetDestination = null,
                            reasonCode = "DEACTIVATE_PREVIOUSLY_REQUESTED_SERVICE"
                        )
                    )
                }
            }
        }
        return steps
    }

    /**
     * Performs post-reboot or SIM-change reconciliation WITHOUT overwriting carrier settings automatically.
     */
    suspend fun reconcileAfterBootOrSimChange(): RebootRecoverySummary {
        val settings = storage.currentSettings()
        val snapshot = subscriptionDataSource.refreshSubscriptions(settings.lastKnownSimSignatures)
        val currentSignatures = snapshot.subscriptions.map { it.stableSignature }.toSet()

        if (snapshot.subscriptionChangedWarning) {
            storage.invalidateObservedStatesForChangedSims(currentSignatures)
        }

        val profiles = storage.profiles.first()
        val activeProfile = profiles.firstOrNull { it.isSelectedActive }
        val observed = storage.observedStates.first()

        val reconciled = StateReconciler.reconcile(
            subscriptions = snapshot.subscriptions,
            activeProfile = activeProfile,
            observedStates = observed,
            previousKnownSignatures = settings.lastKnownSimSignatures
        )

        val needsReview = snapshot.subscriptionChangedWarning ||
            reconciled.any {
                it.desiredRule != null &&
                    it.observedState?.verificationState != ForwardingVerificationState.NETWORK_STATE_VERIFIED
            }

        storage.updateSettings { current ->
            current.copy(
                pendingRebootReview = needsReview && activeProfile != null,
                lastKnownSimSignatures = if (currentSignatures.isNotEmpty()) currentSignatures else current.lastKnownSimSignatures
            )
        }

        return RebootRecoverySummary(
            activeSubscriptions = snapshot.subscriptions,
            subscriptionChangedSinceLastRun = snapshot.subscriptionChangedWarning,
            reconciledDifferences = reconciled,
            requiresUserConfirmation = needsReview
        )
    }

    private suspend fun recordOutcome(
        sim: SimSubscription,
        carrierDisplayName: String,
        serviceType: ForwardingServiceType,
        actionType: ForwardingActionType,
        requestedDestination: String?,
        method: ForwardingExecutionMethod,
        result: ForwardingOperationResult,
        profileName: String?
    ) {
        val existing = storage.observedStates.first().firstOrNull {
            it.simSignature == sim.stableSignature && it.serviceType == serviceType
        }
        val maskedRequested = requestedDestination?.let { SensitiveDataRedactor.maskPhoneNumber(it) }
            ?.takeIf { it.isNotBlank() }

        val updatedObserved = when (result) {
            is ForwardingOperationResult.VerifiedSuccess -> ObservedSimForwardingState(
                simSignature = sim.stableSignature,
                simSlotIndex = sim.simSlotIndex,
                serviceType = serviceType,
                verificationState = ForwardingVerificationState.NETWORK_STATE_VERIFIED,
                observedEnabled = result.observedActive,
                observedDestinationMasked = result.observedDestinationMasked,
                desiredEnabled = when (actionType) {
                    ForwardingActionType.ACTIVATE -> true
                    ForwardingActionType.DEACTIVATE -> false
                    else -> existing?.desiredEnabled
                },
                desiredDestinationMasked = if (actionType == ForwardingActionType.ACTIVATE) maskedRequested else existing?.desiredDestinationMasked,
                lastCheckedAtMillis = result.timestampMillis,
                lastResultCode = result.resultCode,
                diagnosticNote = result.diagnosticCode
            )

            is ForwardingOperationResult.AcceptedUnverified -> ObservedSimForwardingState(
                simSignature = sim.stableSignature,
                simSlotIndex = sim.simSlotIndex,
                serviceType = serviceType,
                verificationState = result.verificationState,
                observedEnabled = existing?.observedEnabled,
                observedDestinationMasked = existing?.observedDestinationMasked,
                desiredEnabled = when (actionType) {
                    ForwardingActionType.ACTIVATE -> true
                    ForwardingActionType.DEACTIVATE -> false
                    else -> existing?.desiredEnabled
                },
                desiredDestinationMasked = if (actionType == ForwardingActionType.ACTIVATE) maskedRequested else existing?.desiredDestinationMasked,
                lastCheckedAtMillis = result.timestampMillis,
                lastResultCode = result.resultCode,
                diagnosticNote = result.diagnosticCode
            )

            is ForwardingOperationResult.Failure -> ObservedSimForwardingState(
                simSignature = sim.stableSignature,
                simSlotIndex = sim.simSlotIndex,
                serviceType = serviceType,
                verificationState = result.verificationState,
                observedEnabled = existing?.observedEnabled,
                observedDestinationMasked = existing?.observedDestinationMasked,
                desiredEnabled = existing?.desiredEnabled,
                desiredDestinationMasked = existing?.desiredDestinationMasked,
                lastCheckedAtMillis = result.timestampMillis,
                lastResultCode = result.resultCode,
                diagnosticNote = result.diagnosticCode
            )
        }

        storage.upsertObservedState(updatedObserved)
        storage.appendHistory(
            ConfigurationHistoryEntry(
                id = UUID.randomUUID().toString(),
                timestampMillis = result.timestampMillis,
                simSlotIndex = sim.simSlotIndex,
                simDisplayLabel = sim.displayName,
                carrierName = carrierDisplayName,
                serviceType = serviceType,
                actionType = actionType,
                previousDestinationMasked = existing?.observedDestinationMasked ?: existing?.desiredDestinationMasked,
                requestedDestinationMasked = maskedRequested,
                executionMethod = method,
                resultCode = result.resultCode,
                verificationState = result.verificationState,
                failureReasonCode = (result as? ForwardingOperationResult.Failure)?.diagnosticCode,
                profileName = profileName
            )
        )
    }
}

/**
 * Deterministic fake [ForwardingExecutor] for unit and integration testing.
 * Clearly identified as a test double; never used in production builds.
 */
class FakeForwardingExecutor(
    private val clock: CalloraClock = CalloraClock.SYSTEM
) : ForwardingExecutor {

    var nextMode: Mode = Mode.DialerUnverified
    val executedRequests = mutableListOf<Pair<ForwardingRequest, String?>>()
    var lastCopiedMmi: String? = null
    var callSettingsOpenedCount: Int = 0

    sealed interface Mode {
        data object DialerUnverified : Mode
        data class DirectVerified(val active: Boolean, val maskedNumber: String?) : Mode
        data object DirectAmbiguousAcknowledgement : Mode
        data object DirectBlockedAndroid17NotAllowed : Mode
        data object DirectMissingPermission : Mode
        data object UserCancelled : Mode
        data class CarrierFailure(val code: String = "MMI_CODE_REJECTED") : Mode
    }

    override suspend fun execute(
        request: ForwardingRequest,
        preparedMmiCode: String?,
        activeSimCount: Int
    ): ForwardingOperationResult {
        executedRequests.add(request to preparedMmiCode)
        val now = clock.nowMillis()

        if (request.preferredMethod == ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS) {
            callSettingsOpenedCount++
            return ForwardingOperationResult.AcceptedUnverified(
                preparedMmiCode = null,
                executionMethod = ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS,
                simSlotSelectionGuaranteed = false,
                noteCode = "OPENED_SYSTEM_CALL_SETTINGS",
                timestampMillis = now
            )
        }

        return when (val mode = nextMode) {
            Mode.DialerUnverified -> ForwardingOperationResult.AcceptedUnverified(
                preparedMmiCode = preparedMmiCode,
                executionMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
                simSlotSelectionGuaranteed = activeSimCount == 1,
                noteCode = "OPENED_IN_SYSTEM_DIALER_UNVERIFIED",
                timestampMillis = now
            )

            is Mode.DirectVerified -> ForwardingOperationResult.VerifiedSuccess(
                preparedMmiCode = preparedMmiCode,
                carrierRawSummary = if (mode.active) "Call forwarding active" else "Call forwarding disabled",
                observedActive = mode.active,
                observedDestinationMasked = mode.maskedNumber,
                timestampMillis = now
            )

            Mode.DirectAmbiguousAcknowledgement -> ForwardingOperationResult.AcceptedUnverified(
                preparedMmiCode = preparedMmiCode,
                executionMethod = ForwardingExecutionMethod.DIRECT_USSD_API,
                simSlotSelectionGuaranteed = true,
                noteCode = "CARRIER_ACK_WITHOUT_AUTHORITATIVE_STATE",
                timestampMillis = now
            )

            Mode.DirectBlockedAndroid17NotAllowed -> ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.UNSUPPORTED,
                verificationState = ForwardingVerificationState.UNSUPPORTED_CAPABILITY,
                preparedMmiCode = preparedMmiCode,
                diagnosticCode = "USSD_ERROR_NOT_ALLOWED_USE_DIALER",
                fallbackMmiCode = preparedMmiCode,
                suggestSystemCallSettings = true,
                timestampMillis = now
            )

            Mode.DirectMissingPermission -> ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.PERMISSION_REQUIRED,
                verificationState = ForwardingVerificationState.FAILED_OPERATION,
                preparedMmiCode = preparedMmiCode,
                diagnosticCode = "CALL_PHONE_PERMISSION_REQUIRED",
                fallbackMmiCode = preparedMmiCode,
                timestampMillis = now
            )

            Mode.UserCancelled -> ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.CANCELLED,
                verificationState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
                preparedMmiCode = preparedMmiCode,
                diagnosticCode = "OPERATION_CANCELLED_BY_USER",
                suggestSystemCallSettings = false,
                timestampMillis = now
            )

            is Mode.CarrierFailure -> ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.FAILED,
                verificationState = ForwardingVerificationState.FAILED_OPERATION,
                preparedMmiCode = preparedMmiCode,
                diagnosticCode = mode.code,
                fallbackMmiCode = preparedMmiCode,
                timestampMillis = now
            )
        }
    }

    override fun copyMmiToClipboard(label: String, mmiCode: String): Boolean {
        lastCopiedMmi = mmiCode
        return true
    }

    override fun openSystemCallSettings(): Boolean {
        callSettingsOpenedCount++
        return true
    }
}
