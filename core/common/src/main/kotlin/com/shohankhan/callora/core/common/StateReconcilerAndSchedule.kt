package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.ReconciledSimServiceState
import com.shohankhan.callora.core.model.ReconciliationDiscrepancy
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile

/**
 * Reconciles desired local profile configuration against observed carrier network status
 * and subscription changes (such as SIM replacement or slot swaps after reboot).
 */
object StateReconciler {

    fun detectSubscriptionChange(
        previousKnownSignatures: Set<String>,
        currentSubscriptions: List<SimSubscription>
    ): Boolean {
        if (previousKnownSignatures.isEmpty()) return false
        val currentSignatures = currentSubscriptions.map { it.stableSignature }.toSet()
        return currentSignatures != previousKnownSignatures
    }

    fun reconcile(
        subscriptions: List<SimSubscription>,
        activeProfile: SmartProfile?,
        observedStates: List<ObservedSimForwardingState>,
        previousKnownSignatures: Set<String> = emptySet()
    ): List<ReconciledSimServiceState> {
        val observedIndex = observedStates.associateBy { it.simSignature to it.serviceType }
        val simChangedGlobally = previousKnownSignatures.isNotEmpty() &&
            subscriptions.any { it.stableSignature !in previousKnownSignatures }

        val results = mutableListOf<ReconciledSimServiceState>()
        for (sim in subscriptions) {
            val simIsUnfamiliar = previousKnownSignatures.isNotEmpty() &&
                sim.stableSignature !in previousKnownSignatures

            for (serviceType in ForwardingServiceType.entries) {
                val desiredRule = activeProfile?.rules?.firstOrNull { rule ->
                    rule.enabled &&
                        rule.serviceType == serviceType &&
                        (rule.simSignature == sim.stableSignature ||
                            (rule.simSignature.isBlank() && rule.simSlotIndex == sim.simSlotIndex))
                }

                // Never match a rule bound to a different SIM signature in the same slot silently!
                val slotConflictRule = activeProfile?.rules?.firstOrNull { rule ->
                    rule.enabled &&
                        rule.serviceType == serviceType &&
                        rule.simSlotIndex == sim.simSlotIndex &&
                        rule.simSignature.isNotBlank() &&
                        rule.simSignature != sim.stableSignature
                }

                val observed = observedIndex[sim.stableSignature to serviceType]

                val discrepancy = when {
                    slotConflictRule != null || (simIsUnfamiliar && simChangedGlobally && desiredRule != null) ->
                        ReconciliationDiscrepancy.SIM_CHANGED_REQUIRES_REVIEW

                    desiredRule == null -> {
                        if (observed?.verificationState == ForwardingVerificationState.NETWORK_STATE_VERIFIED &&
                            observed.observedEnabled == true
                        ) {
                            ReconciliationDiscrepancy.MISMATCH_DESIRED_VS_VERIFIED
                        } else {
                            ReconciliationDiscrepancy.NO_DESIRED_RULE
                        }
                    }

                    observed == null || observed.verificationState == ForwardingVerificationState.UNKNOWN_NETWORK_STATE ||
                        observed.verificationState == ForwardingVerificationState.DESIRED_LOCAL_ONLY ->
                        ReconciliationDiscrepancy.UNKNOWN_NEEDS_STATUS_CHECK

                    observed.verificationState == ForwardingVerificationState.COMMAND_SUBMITTED ||
                        observed.verificationState == ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED ->
                        ReconciliationDiscrepancy.UNVERIFIED_PENDING_CONFIRMATION

                    observed.verificationState == ForwardingVerificationState.NETWORK_STATE_VERIFIED -> {
                        val expectedMasked = SensitiveDataRedactor.maskPhoneNumber(desiredRule.destinationNumber)
                        val destMatches = observed.observedDestinationMasked == null ||
                            observed.observedDestinationMasked == expectedMasked
                        if (observed.observedEnabled == true && destMatches) {
                            ReconciliationDiscrepancy.IN_SYNC_VERIFIED
                        } else {
                            ReconciliationDiscrepancy.MISMATCH_DESIRED_VS_VERIFIED
                        }
                    }

                    else -> ReconciliationDiscrepancy.MISMATCH_DESIRED_VS_VERIFIED
                }

                results.add(
                    ReconciledSimServiceState(
                        sim = sim,
                        serviceType = serviceType,
                        desiredRule = desiredRule,
                        observedState = observed,
                        discrepancy = discrepancy
                    )
                )
            }
        }
        return results
    }
}

object ScheduleValidator {

    sealed interface ScheduleValidationResult {
        data object Valid : ScheduleValidationResult
        data class Invalid(val reasonCode: String) : ScheduleValidationResult
    }

    fun validate(schedule: ProfileSchedule, nowEpochMillis: Long): ScheduleValidationResult {
        if (!schedule.enabled) return ScheduleValidationResult.Valid
        if (schedule.daysOfWeek.isEmpty() || schedule.daysOfWeek.any { it !in 1..7 }) {
            return ScheduleValidationResult.Invalid("INVALID_DAYS_OF_WEEK")
        }
        if (schedule.startMinuteOfDay !in 0..1439 || schedule.endMinuteOfDay !in 0..1439) {
            return ScheduleValidationResult.Invalid("INVALID_MINUTE_OF_DAY")
        }
        if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) {
            return ScheduleValidationResult.Invalid("ZERO_DURATION_WINDOW")
        }
        val expires = schedule.expiresAtEpochMillis
        if (expires != null && expires <= nowEpochMillis) {
            return ScheduleValidationResult.Invalid("EXPIRATION_IN_PAST")
        }
        return ScheduleValidationResult.Valid
    }

    fun isWithinActiveWindow(
        schedule: ProfileSchedule,
        isoDayOfWeek: Int,
        minuteOfDay: Int,
        nowEpochMillis: Long
    ): Boolean {
        if (!schedule.enabled) return false
        if (schedule.expiresAtEpochMillis != null && nowEpochMillis >= schedule.expiresAtEpochMillis!!) {
            return false
        }
        val start = schedule.startMinuteOfDay
        val end = schedule.endMinuteOfDay
        return if (start < end) {
            isoDayOfWeek in schedule.daysOfWeek && minuteOfDay in start until end
        } else {
            // Overnight window (e.g., 22:00 -> 07:00)
            val prevDay = if (isoDayOfWeek == 1) 7 else isoDayOfWeek - 1
            (isoDayOfWeek in schedule.daysOfWeek && minuteOfDay >= start) ||
                (prevDay in schedule.daysOfWeek && minuteOfDay < end)
        }
    }

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, 1439)
        val h = safe / 60
        val m = safe % 60
        return "%02d:%02d".format(h, m)
    }
}
