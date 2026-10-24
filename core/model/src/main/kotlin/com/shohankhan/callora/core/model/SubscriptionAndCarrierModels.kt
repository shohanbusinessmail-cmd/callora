package com.shohankhan.callora.core.model

/**
 * Dynamic representation of an active cellular subscription (physical SIM or eSIM).
 * Supports 0, 1, 2, or 3+ active subscriptions without hardcoding dual-SIM assumptions.
 */
data class SimSubscription(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val isEmbedded: Boolean,
    val displayName: String,
    val carrierName: String,
    val mcc: String,
    val mnc: String,
    val carrierId: Int? = null,
    val maskedPhoneNumber: String? = null,
    val isNetworkAvailable: Boolean = true,
    val isRoaming: Boolean = false
) {
    val mccMnc: String
        get() = if (mcc.isNotBlank() && mnc.isNotBlank()) {
            "$mcc-${mnc.padStart(2, '0')}"
        } else {
            ""
        }

    /** Display slot number (1-based) for accessible UI badges. */
    val displaySlotNumber: Int
        get() = if (simSlotIndex >= 0) simSlotIndex + 1 else 1

    /**
     * Composite signature used to detect SIM swaps or slot reordering across reboots
     * without storing raw ICCID/IMSI identifiers.
     */
    val stableSignature: String
        get() = buildString {
            append("slot=").append(simSlotIndex)
            append("|esim=").append(isEmbedded)
            append("|net=").append(mccMnc.ifBlank { "unknown" })
            append("|carrier=").append(carrierName.trim().lowercase())
        }
}

enum class SimDiscoveryState {
    PERMISSION_REQUIRED,
    NO_ACTIVE_SIM,
    READY,
    UNAVAILABLE
}

data class SimDiscoverySnapshot(
    val state: SimDiscoveryState,
    val subscriptions: List<SimSubscription>,
    val checkedAtMillis: Long,
    val subscriptionChangedWarning: Boolean = false
)

enum class CarrierVerificationLevel {
    /** Published explicitly in an official carrier GSM code table (e.g., Grameenphone). */
    OFFICIAL_CODE_TABLE_VERIFIED,

    /** Official carrier page specifies handset Call Settings (e.g., Teletalk). */
    OFFICIAL_HANDSET_SETTINGS_VERIFIED,

    /** Standard 3GPP TS 22.030 GSM MMI codes; behavior depends on plan/IMS provisioning. */
    STANDARD_3GPP_PROVISIONING_DEPENDENT,

    /** Unknown/foreign operator; never auto-prepare unverified carrier codes. */
    UNVERIFIED_FALLBACK
}

data class CarrierConfig(
    val operatorId: String,
    val displayNameEn: String,
    val displayNameBn: String,
    val mccMncCodes: Set<String>,
    val originalBtrcPrefixes: Set<String>,
    val supportedServices: Set<ForwardingServiceType>,
    val verificationLevel: CarrierVerificationLevel,
    val preferredMethod: ForwardingExecutionMethod,
    val allowMmiDialPreparation: Boolean,
    val activationMmiTemplates: Map<ForwardingServiceType, String>,
    val deactivationMmiTemplates: Map<ForwardingServiceType, String>,
    val statusQueryMmiTemplates: Map<ForwardingServiceType, String>,
    val cancelAllMmiCode: String?,
    val verificationSourceTitle: String,
    val verificationSourceUrl: String?,
    val lastVerifiedIsoDate: String,
    val customerCareShortCode: String?,
    val tariffWarningEn: String,
    val tariffWarningBn: String,
    val knownRestrictionsEn: List<String>,
    val knownRestrictionsBn: List<String>,
    val manualInstructionsEn: String,
    val manualInstructionsBn: String
)
