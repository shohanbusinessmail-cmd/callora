package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.PhoneNumberValidationResult

/**
 * 3GPP TS 22.030 Annex B Supplementary Service MMI command builder and conservative
 * USSD/MMI response parser.
 */
object GsmMmiCodec {

    private val VALID_NO_REPLY_TIMEOUTS = setOf(5, 10, 15, 20, 25, 30)

    sealed interface MmiBuildResult {
        data class Success(val mmiString: String, val normalizedDestination: String?) : MmiBuildResult
        data class Error(val errorCode: String) : MmiBuildResult
    }

    fun normalizeNoReplyTimeout(seconds: Int?): Int {
        if (seconds == null) return 20
        val clamped = seconds.coerceIn(5, 30)
        val rounded = ((clamped + 2) / 5) * 5
        return rounded.coerceIn(5, 30)
    }

    fun buildCommand(
        serviceType: ForwardingServiceType,
        actionType: ForwardingActionType,
        destinationNumber: String? = null,
        noReplyTimeoutSeconds: Int? = null,
        preferNationalFormatForBd: Boolean = true,
        defaultCountryIso: String = "BD"
    ): MmiBuildResult {
        val sc = serviceType.gsmServiceCode
        return when (actionType) {
            ForwardingActionType.ACTIVATE -> {
                val rawDest = destinationNumber?.trim().orEmpty()
                val validation = PhoneNumberValidator.validate(rawDest, defaultCountryIso = defaultCountryIso)
                if (validation !is PhoneNumberValidationResult.Valid) {
                    return MmiBuildResult.Error("INVALID_DESTINATION")
                }
                val dialable = if (validation.isBangladeshNumber && preferNationalFormatForBd) {
                    validation.nationalDialable
                } else {
                    validation.e164Normalized
                }
                val mmi = if (serviceType == ForwardingServiceType.UNANSWERED && noReplyTimeoutSeconds != null) {
                    val timeout = normalizeNoReplyTimeout(noReplyTimeoutSeconds)
                    if (timeout !in VALID_NO_REPLY_TIMEOUTS) {
                        return MmiBuildResult.Error("INVALID_NO_REPLY_TIMEOUT")
                    }
                    "**$sc*$dialable**$timeout#"
                } else {
                    "**$sc*$dialable#"
                }
                MmiBuildResult.Success(mmiString = mmi, normalizedDestination = validation.e164Normalized)
            }

            ForwardingActionType.DEACTIVATE,
            ForwardingActionType.RESTORE_INTENDED -> {
                MmiBuildResult.Success(mmiString = "##$sc#", normalizedDestination = null)
            }

            ForwardingActionType.QUERY_STATUS -> {
                MmiBuildResult.Success(mmiString = "*#$sc#", normalizedDestination = null)
            }
        }
    }

    fun buildEraseAllConditionalCommand(): String = "##004#"

    fun buildEraseAllCommand(): String = "##002#"

    sealed interface ParsedCarrierResponse {
        data class AuthoritativeStatus(
            val isEnabled: Boolean,
            val extractedDestinationMasked: String?,
            val sanitizedSummary: String
        ) : ParsedCarrierResponse

        data class AmbiguousAcknowledgement(
            val sanitizedSummary: String
        ) : ParsedCarrierResponse

        data class CarrierError(
            val reasonCode: String,
            val sanitizedSummary: String
        ) : ParsedCarrierResponse
    }

    /**
     * Parses a carrier USSD/MMI text message conservatively.
     * Never marks a vague or empty string as [ParsedCarrierResponse.AuthoritativeStatus].
     */
    fun parseCarrierResponse(rawMessage: String?): ParsedCarrierResponse {
        val trimmed = rawMessage?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ParsedCarrierResponse.CarrierError("EMPTY_USSD_RESPONSE", "")
        }

        val lower = trimmed.lowercase()
        val redacted = SensitiveDataRedactor.redactFreeText(trimmed)

        val errorMarkers = listOf(
            "connection problem",
            "invalid mmi",
            "mmi code error",
            "not supported",
            "not allowed",
            "service unavailable",
            "error",
            "failed",
            "ব্যর্থ",
            "ত্রুটি"
        )
        if (errorMarkers.any { lower.contains(it) }) {
            return ParsedCarrierResponse.CarrierError("CARRIER_REPORTED_ERROR", redacted)
        }

        val disabledMarkers = listOf(
            "not forwarded",
            "disabled",
            "deactivated",
            "erasure was successful",
            "erasure successful",
            "service has been disabled",
            "voice: not forwarded",
            "বন্ধ",
            "নিষ্ক্রিয়"
        )
        if (disabledMarkers.any { lower.contains(it) }) {
            return ParsedCarrierResponse.AuthoritativeStatus(
                isEnabled = false,
                extractedDestinationMasked = null,
                sanitizedSummary = redacted
            )
        }

        val phoneMatch = Regex("""(\+?\d[\d\s\-]{7,}\d)""").find(trimmed)?.value
        val enabledMarkers = listOf(
            "registration was successful",
            "registration successful",
            "voice:",
            "forwarded to",
            "call forwarding",
            "active for",
            "diverted to",
            "চালু"
        )
        if (enabledMarkers.any { lower.contains(it) } && (phoneMatch != null || lower.contains("registration"))) {
            return ParsedCarrierResponse.AuthoritativeStatus(
                isEnabled = true,
                extractedDestinationMasked = phoneMatch?.let { SensitiveDataRedactor.maskPhoneNumber(it) },
                sanitizedSummary = redacted
            )
        }

        return ParsedCarrierResponse.AmbiguousAcknowledgement(sanitizedSummary = redacted)
    }
}
