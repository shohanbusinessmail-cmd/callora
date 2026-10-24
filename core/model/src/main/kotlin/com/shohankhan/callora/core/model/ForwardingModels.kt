package com.shohankhan.callora.core.model

/**
 * Standard 3GPP TS 22.030 Call Forwarding Supplementary Service categories.
 *
 * [UNREACHABLE] (CFNRc, GSM service code 62) is prioritized because Android apps cannot
 * execute code after power-off or complete battery depletion; CFNRc must be registered on
 * the carrier network while the phone is still operational.
 */
enum class ForwardingServiceType(
    val gsmServiceCode: String,
    val acronym: String,
    val supportsRingTimeout: Boolean = false
) {
    UNREACHABLE(gsmServiceCode = "62", acronym = "CFNRc"),
    BUSY(gsmServiceCode = "67", acronym = "CFB"),
    UNANSWERED(gsmServiceCode = "61", acronym = "CFNRy", supportsRingTimeout = true),
    UNCONDITIONAL(gsmServiceCode = "21", acronym = "CFU");

    companion object {
        fun fromServiceCode(code: String): ForwardingServiceType? =
            entries.firstOrNull { it.gsmServiceCode == code }
    }
}

enum class ForwardingActionType {
    ACTIVATE,
    DEACTIVATE,
    QUERY_STATUS,
    RESTORE_INTENDED
}

enum class ForwardingExecutionMethod {
    /** Opens system dialer with pre-filled MMI via Intent.ACTION_DIAL (requires user tap). */
    SAFE_DIALER_INTENT,

    /** Uses TelephonyManager.createForSubscriptionId(subId).sendUssdRequest(...) when supported. */
    DIRECT_USSD_API,

    /** Opens Android system Phone app Call Forwarding settings screen. */
    SYSTEM_CALL_SETTINGS
}

/**
 * Structured outcome codes for every call-forwarding operation.
 * Launching a dialer intent or submitting an MMI command is NEVER marked [SUCCESS_VERIFIED].
 */
enum class ForwardingResultCode {
    SUCCESS_VERIFIED,
    COMMAND_ACCEPTED_UNVERIFIED,
    FAILED,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    NETWORK_UNAVAILABLE,
    SIM_SELECTION_UNAVAILABLE,
    CANCELLED
}

/**
 * Truthful network vs. local state classification per subscription and service type.
 */
enum class ForwardingVerificationState {
    UNKNOWN_NETWORK_STATE,
    DESIRED_LOCAL_ONLY,
    OPERATION_IN_PROGRESS,
    COMMAND_SUBMITTED,
    COMMAND_ACCEPTED_UNVERIFIED,
    NETWORK_STATE_VERIFIED,
    FAILED_OPERATION,
    UNSUPPORTED_CAPABILITY;

    val isAuthoritativelyVerified: Boolean
        get() = this == NETWORK_STATE_VERIFIED
}

data class ForwardingRequest(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val simSignature: String,
    val carrierOperatorId: String,
    val serviceType: ForwardingServiceType,
    val actionType: ForwardingActionType,
    val destinationNumber: String? = null,
    val noReplyTimeoutSeconds: Int? = null,
    val preferredMethod: ForwardingExecutionMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
    val userConfirmedMultiSimSelection: Boolean = false
)

sealed interface ForwardingOperationResult {
    val resultCode: ForwardingResultCode
    val verificationState: ForwardingVerificationState
    val preparedMmiCode: String?
    val diagnosticCode: String?
    val timestampMillis: Long

    data class VerifiedSuccess(
        override val preparedMmiCode: String?,
        val carrierRawSummary: String,
        val observedActive: Boolean,
        val observedDestinationMasked: String?,
        override val timestampMillis: Long
    ) : ForwardingOperationResult {
        override val resultCode: ForwardingResultCode = ForwardingResultCode.SUCCESS_VERIFIED
        override val verificationState: ForwardingVerificationState =
            ForwardingVerificationState.NETWORK_STATE_VERIFIED
        override val diagnosticCode: String = "VERIFIED_BY_USSD_RESPONSE"
    }

    data class AcceptedUnverified(
        override val preparedMmiCode: String?,
        val executionMethod: ForwardingExecutionMethod,
        val simSlotSelectionGuaranteed: Boolean,
        val noteCode: String,
        override val timestampMillis: Long
    ) : ForwardingOperationResult {
        override val resultCode: ForwardingResultCode = ForwardingResultCode.COMMAND_ACCEPTED_UNVERIFIED
        override val verificationState: ForwardingVerificationState =
            if (executionMethod == ForwardingExecutionMethod.SAFE_DIALER_INTENT ||
                executionMethod == ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS
            ) {
                ForwardingVerificationState.COMMAND_SUBMITTED
            } else {
                ForwardingVerificationState.COMMAND_ACCEPTED_UNVERIFIED
            }
        override val diagnosticCode: String = noteCode
    }

    data class Failure(
        override val resultCode: ForwardingResultCode,
        override val verificationState: ForwardingVerificationState,
        override val preparedMmiCode: String?,
        override val diagnosticCode: String,
        val fallbackMmiCode: String? = preparedMmiCode,
        val suggestSystemCallSettings: Boolean = true,
        override val timestampMillis: Long
    ) : ForwardingOperationResult
}
