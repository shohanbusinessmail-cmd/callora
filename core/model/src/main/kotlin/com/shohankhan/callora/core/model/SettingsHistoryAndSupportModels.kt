package com.shohankhan.callora.core.model

enum class AppLanguage(val languageTag: String, val nativeLabel: String) {
    ENGLISH(languageTag = "en", nativeLabel = "English"),
    BENGALI(languageTag = "bn", nativeLabel = "বাংলা");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            if (tag?.lowercase()?.startsWith("bn") == true) BENGALI else ENGLISH
    }
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val defaultCountryIso: String = "BD",
    val lowBatteryWarningEnabled: Boolean = true,
    val lowBatteryThresholdPercent: Int = 20,
    val chargingPromptEnabled: Boolean = false,
    val rebootRecoveryCheckEnabled: Boolean = true,
    val preferDirectUssdWhenPermitted: Boolean = false,
    val pendingRebootReview: Boolean = false,
    val lastKnownSimSignatures: Set<String> = emptySet()
)

data class BatterySnapshot(
    val levelPercent: Int,
    val isCharging: Boolean,
    val isBatterySaverOn: Boolean = false,
    val sampledAtMillis: Long = 0L
) {
    fun isBelowOrAtThreshold(threshold: Int): Boolean =
        levelPercent in 0..threshold && !isCharging
}

data class FavoriteDestination(
    val id: String,
    val label: String,
    val normalizedNumber: String,
    val originalInput: String,
    val notes: String = "",
    val createdAtMillis: Long
)

data class ConfigurationHistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val simSlotIndex: Int,
    val simDisplayLabel: String,
    val carrierName: String,
    val serviceType: ForwardingServiceType,
    val actionType: ForwardingActionType,
    val previousDestinationMasked: String?,
    val requestedDestinationMasked: String?,
    val executionMethod: ForwardingExecutionMethod,
    val resultCode: ForwardingResultCode,
    val verificationState: ForwardingVerificationState,
    val failureReasonCode: String? = null,
    val profileName: String? = null
)

enum class NumberValidationIssue {
    EMPTY,
    CONTAINS_MMI_INJECTION_CHARACTERS,
    TOO_SHORT,
    TOO_LONG,
    INVALID_BANGLADESH_MOBILE_PREFIX,
    INVALID_BANGLADESH_LENGTH,
    SELF_FORWARDING_LOOP,
    SHORT_CODE_OR_EMERGENCY_NUMBER,
    UNRECOGNIZED_FORMAT
}

sealed interface PhoneNumberValidationResult {
    data class Valid(
        val e164Normalized: String,
        val nationalDialable: String,
        val displayFormatted: String,
        val maskedPreview: String,
        val isBangladeshNumber: Boolean,
        val originalBtrcAllocationHint: String?,
        val warnings: List<String> = emptyList()
    ) : PhoneNumberValidationResult

    data class Invalid(
        val issue: NumberValidationIssue,
        val sanitizedInput: String
    ) : PhoneNumberValidationResult
}

data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long,
    val appVersion: String,
    val settings: AppSettings,
    val profiles: List<SmartProfile>,
    val favorites: List<FavoriteDestination>,
    val baselines: List<BaselineSimConfig>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

enum class TroubleshootingTopicId {
    PHONE_OFF_AND_BATTERY_REALITY,
    UNSUPPORTED_OPERATOR_SERVICE,
    INVALID_DESTINATION_NUMBER,
    INCORRECT_SIM_SELECTED,
    NETWORK_UNAVAILABLE_OR_VO_WIFI,
    PERMISSION_DENIED_RECOVERY,
    FAILED_USSD_OR_MMI_OPERATION,
    UNVERIFIED_STATUS_EXPLAINED,
    SIM_SWAP_OR_SLOT_CHANGE,
    REBOOT_RECOVERY_AND_UNEXPECTED_DIVERT
}
