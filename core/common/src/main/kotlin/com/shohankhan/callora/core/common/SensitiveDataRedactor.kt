package com.shohankhan.callora.core.common

/**
 * Redacts phone numbers and telephony identifiers so full subscriber numbers,
 * ICCIDs, or IMSIs are never logged or exported by default.
 */
object SensitiveDataRedactor {

    private val PHONE_LIKE_SEQUENCE = Regex("""\+?\d[\d\s\-()]{6,}\d""")

    fun maskPhoneNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        val normalized = PhoneNumberValidator.normalizeBengaliDigits(number).trim()
        val digits = normalized.filter { it.isDigit() }
        if (digits.length < 4) return "••••"
        val lastTwo = digits.takeLast(2)
        return when {
            normalized.startsWith("+880") && digits.length >= 13 -> {
                val ndc = digits.substring(3, 5)
                "+880 $ndc•• •••• $lastTwo"
            }
            digits.length == 11 && digits.startsWith("01") -> {
                val prefix = digits.substring(0, 3)
                "$prefix•• •••• $lastTwo"
            }
            normalized.startsWith("+") && digits.length >= 8 -> {
                val head = digits.take(3)
                "+$head •••• $lastTwo"
            }
            else -> "•••• •••• $lastTwo"
        }
    }

    fun redactFreeText(text: String): String =
        PHONE_LIKE_SEQUENCE.replace(text) { match ->
            maskPhoneNumber(match.value)
        }
}
