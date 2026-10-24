package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.NumberValidationIssue
import com.shohankhan.callora.core.model.PhoneNumberValidationResult

/**
 * Validates and normalizes Bangladesh (+880) and international phone numbers for call forwarding.
 *
 * Security & Honesty Guarantees:
 * - Converts Bengali numerals (০-৯) to ASCII digits before validation.
 * - Rejects MMI control/injection characters (*, #, ;, ,, %) to prevent MMI command injection.
 * - Blocks emergency and operator short codes (e.g., 999, 112, 911, 121).
 * - Syntactic validation only checks plausibility; it never claims a number is active or reachable.
 */
object PhoneNumberValidator {

    private val FORBIDDEN_MMI_CHARS = setOf('*', '#', ';', ',', '%', 'p', 'P', 'w', 'W')
    private val EMERGENCY_AND_SHORT_CODES = setOf(
        "999", "112", "911", "100", "101", "102", "106", "109", "121", "123", "158", "333", "16263"
    )
    private val VALID_BD_MOBILE_NDC = setOf("13", "14", "15", "16", "17", "18", "19")

    fun normalizeBengaliDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            if (ch in '০'..'৯') {
                append(('0'.code + (ch.code - '০'.code)).toChar())
            } else {
                append(ch)
            }
        }
    }

    fun validate(
        rawInput: String,
        defaultCountryIso: String = "BD",
        ownNumberToAvoidLoop: String? = null
    ): PhoneNumberValidationResult {
        val digitNormalized = normalizeBengaliDigits(rawInput).trim()
        if (digitNormalized.isEmpty()) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.EMPTY, "")
        }

        if (digitNormalized.any { it in FORBIDDEN_MMI_CHARS }) {
            return PhoneNumberValidationResult.Invalid(
                NumberValidationIssue.CONTAINS_MMI_INJECTION_CHARACTERS,
                digitNormalized
            )
        }

        // Strip formatting whitespace, hyphens, parentheses, and dots while preserving leading '+'
        val cleaned = buildString {
            digitNormalized.forEachIndexed { index, ch ->
                when {
                    ch == '+' && index == 0 -> append(ch)
                    ch in '0'..'9' -> append(ch)
                    ch == ' ' || ch == '-' || ch == '(' || ch == ')' || ch == '.' || ch == '\u00A0' -> Unit
                    else -> return PhoneNumberValidationResult.Invalid(
                        NumberValidationIssue.UNRECOGNIZED_FORMAT,
                        digitNormalized
                    )
                }
            }
        }

        val digitsOnly = cleaned.removePrefix("+")
        if (digitsOnly.isEmpty()) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.EMPTY, cleaned)
        }

        if (digitsOnly in EMERGENCY_AND_SHORT_CODES || digitsOnly.length <= 5) {
            val issue = if (digitsOnly in EMERGENCY_AND_SHORT_CODES) {
                NumberValidationIssue.SHORT_CODE_OR_EMERGENCY_NUMBER
            } else {
                NumberValidationIssue.TOO_SHORT
            }
            return PhoneNumberValidationResult.Invalid(issue, cleaned)
        }

        val canonicalPlus = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") && cleaned.length > 4 -> "+" + cleaned.drop(2)
            defaultCountryIso.equals("BD", ignoreCase = true) && cleaned.startsWith("880") && cleaned.length >= 12 -> "+$cleaned"
            defaultCountryIso.equals("BD", ignoreCase = true) && cleaned.startsWith("0") -> "+880" + cleaned.drop(1)
            defaultCountryIso.equals("BD", ignoreCase = true) && cleaned.length == 10 && cleaned.startsWith("1") -> "+880$cleaned"
            else -> null
        }

        if (canonicalPlus == null) {
            return PhoneNumberValidationResult.Invalid(
                NumberValidationIssue.UNRECOGNIZED_FORMAT,
                cleaned
            )
        }

        val e164Digits = canonicalPlus.removePrefix("+")
        if (e164Digits.length < 8) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.TOO_SHORT, cleaned)
        }
        if (e164Digits.length > 15) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.TOO_LONG, cleaned)
        }

        if (canonicalPlus.startsWith("+880")) {
            return validateBangladeshE164(canonicalPlus, cleaned, ownNumberToAvoidLoop)
        }

        // International E.164 destination
        if (e164Digits[0] == '0') {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.UNRECOGNIZED_FORMAT, cleaned)
        }

        if (isSameNumber(canonicalPlus, ownNumberToAvoidLoop)) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.SELF_FORWARDING_LOOP, cleaned)
        }

        return PhoneNumberValidationResult.Valid(
            e164Normalized = canonicalPlus,
            nationalDialable = canonicalPlus,
            displayFormatted = formatInternational(canonicalPlus),
            maskedPreview = SensitiveDataRedactor.maskPhoneNumber(canonicalPlus),
            isBangladeshNumber = false,
            originalBtrcAllocationHint = null,
            warnings = listOf("INTERNATIONAL_DESTINATION_TARIFF_WARNING")
        )
    }

    private fun validateBangladeshE164(
        e164: String,
        cleanedOriginal: String,
        ownNumberToAvoidLoop: String?
    ): PhoneNumberValidationResult {
        val nationalWithoutZero = e164.removePrefix("+880")
        if (nationalWithoutZero.isEmpty()) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.INVALID_BANGLADESH_LENGTH, cleanedOriginal)
        }

        if (nationalWithoutZero.startsWith("1")) {
            // Bangladesh mobile series: +880 1X XXXX XXXX (10 digits after +880, 11 digits with trunk 0)
            if (nationalWithoutZero.length != 10) {
                return PhoneNumberValidationResult.Invalid(
                    NumberValidationIssue.INVALID_BANGLADESH_LENGTH,
                    cleanedOriginal
                )
            }
            val ndc = nationalWithoutZero.substring(0, 2)
            if (ndc !in VALID_BD_MOBILE_NDC) {
                return PhoneNumberValidationResult.Invalid(
                    NumberValidationIssue.INVALID_BANGLADESH_MOBILE_PREFIX,
                    cleanedOriginal
                )
            }
            if (isSameNumber(e164, ownNumberToAvoidLoop)) {
                return PhoneNumberValidationResult.Invalid(
                    NumberValidationIssue.SELF_FORWARDING_LOOP,
                    cleanedOriginal
                )
            }
            val national = "0$nationalWithoutZero"
            val formatted = "+880 ${nationalWithoutZero.substring(0, 2)} ${nationalWithoutZero.substring(2, 6)}-${nationalWithoutZero.substring(6)}"
            return PhoneNumberValidationResult.Valid(
                e164Normalized = e164,
                nationalDialable = national,
                displayFormatted = formatted,
                maskedPreview = SensitiveDataRedactor.maskPhoneNumber(e164),
                isBangladeshNumber = true,
                originalBtrcAllocationHint = originalOperatorHintForNdc(ndc),
                warnings = listOf("MNP_PREFIX_NOT_GUARANTEED")
            )
        }

        // Bangladesh PSTN / IPTSP numbers (e.g., 02XXXXXXXX, 096XXXXXXXX): 8 to 10 digits after +880
        if (nationalWithoutZero.length !in 8..10) {
            return PhoneNumberValidationResult.Invalid(
                NumberValidationIssue.INVALID_BANGLADESH_LENGTH,
                cleanedOriginal
            )
        }
        if (isSameNumber(e164, ownNumberToAvoidLoop)) {
            return PhoneNumberValidationResult.Invalid(NumberValidationIssue.SELF_FORWARDING_LOOP, cleanedOriginal)
        }
        val national = "0$nationalWithoutZero"
        return PhoneNumberValidationResult.Valid(
            e164Normalized = e164,
            nationalDialable = national,
            displayFormatted = "+880 $nationalWithoutZero",
            maskedPreview = SensitiveDataRedactor.maskPhoneNumber(e164),
            isBangladeshNumber = true,
            originalBtrcAllocationHint = "PSTN_OR_IPTSP",
            warnings = emptyList()
        )
    }

    private fun originalOperatorHintForNdc(ndc: String): String? = when (ndc) {
        "17", "13" -> "GRAMEENPHONE_ORIGINAL_SERIES"
        "18" -> "ROBI_ORIGINAL_SERIES"
        "16" -> "AIRTEL_ROBI_ORIGINAL_SERIES"
        "19", "14" -> "BANGLALINK_ORIGINAL_SERIES"
        "15" -> "TELETALK_ORIGINAL_SERIES"
        else -> null
    }

    private fun isSameNumber(candidateE164: String, otherRaw: String?): Boolean {
        if (otherRaw.isNullOrBlank()) return false
        val otherDigits = normalizeBengaliDigits(otherRaw).filter { it.isDigit() }
        val candidateDigits = candidateE164.filter { it.isDigit() }
        if (otherDigits.length < 8) return false
        return candidateDigits.takeLast(10) == otherDigits.takeLast(10)
    }

    private fun formatInternational(e164: String): String {
        if (e164.length <= 7) return e164
        val cc = e164.take(4)
        val rest = e164.drop(4)
        return "$cc $rest"
    }
}
