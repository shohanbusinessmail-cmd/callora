package com.shohankhan.callora.core.model

enum class BuiltInProfileType {
    PHONE_UNREACHABLE,
    BATTERY_EMERGENCY,
    CHARGING_MODE,
    WORK_MODE,
    TRAVEL_MODE,
    TEMPORARY_FORWARDING,
    CUSTOM
}

data class SimForwardingRule(
    val simSignature: String,
    val simSlotIndex: Int,
    val simDisplayLabel: String,
    val enabled: Boolean,
    val serviceType: ForwardingServiceType,
    val destinationNumber: String,
    val noReplyTimeoutSeconds: Int = 20
)

data class ProfileSchedule(
    val enabled: Boolean = false,
    /** ISO-8601 day numbers: 1 (Monday) .. 7 (Sunday). */
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Minutes since midnight (0..1439). */
    val startMinuteOfDay: Int = 9 * 60,
    /** Minutes since midnight (0..1439). */
    val endMinuteOfDay: Int = 18 * 60,
    val expiresAtEpochMillis: Long? = null
)

data class SmartProfile(
    val id: String,
    val name: String,
    val description: String,
    val builtInType: BuiltInProfileType = BuiltInProfileType.CUSTOM,
    val isBuiltIn: Boolean = false,
    val rules: List<SimForwardingRule> = emptyList(),
    val schedule: ProfileSchedule = ProfileSchedule(),
    val lowBatteryThresholdPercent: Int? = null,
    val suggestWhenChargingStarts: Boolean = false,
    val requiresConfirmation: Boolean = true,
    val isSelectedActive: Boolean = false,
    val lastActivationAttemptMillis: Long? = null,
    val lastVerifiedState: ForwardingVerificationState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
    val updatedAtMillis: Long = 0L
)

/**
 * User's saved baseline ("normal") intended forwarding configuration per SIM.
 * Used by "Restore normal forwarding" so Callora NEVER blindly wipes all forwarding
 * rules without consulting the user's intended baseline and asking confirmation.
 */
data class BaselineSimConfig(
    val simSignature: String,
    val simSlotIndex: Int,
    val serviceType: ForwardingServiceType,
    val shouldBeEnabled: Boolean,
    val baselineDestinationNumber: String? = null,
    val updatedAtMillis: Long
)

/**
 * Last observed or submitted state for a SIM + service category.
 */
data class ObservedSimForwardingState(
    val simSignature: String,
    val simSlotIndex: Int,
    val serviceType: ForwardingServiceType,
    val verificationState: ForwardingVerificationState,
    val observedEnabled: Boolean? = null,
    val observedDestinationMasked: String? = null,
    val desiredEnabled: Boolean? = null,
    val desiredDestinationMasked: String? = null,
    val lastCheckedAtMillis: Long? = null,
    val lastResultCode: ForwardingResultCode? = null,
    val diagnosticNote: String? = null
)

enum class ReconciliationDiscrepancy {
    IN_SYNC_VERIFIED,
    UNVERIFIED_PENDING_CONFIRMATION,
    UNKNOWN_NEEDS_STATUS_CHECK,
    MISMATCH_DESIRED_VS_VERIFIED,
    SIM_CHANGED_REQUIRES_REVIEW,
    NO_DESIRED_RULE
}

data class ReconciledSimServiceState(
    val sim: SimSubscription,
    val serviceType: ForwardingServiceType,
    val desiredRule: SimForwardingRule?,
    val observedState: ObservedSimForwardingState?,
    val discrepancy: ReconciliationDiscrepancy
)
