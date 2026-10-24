package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.NumberValidationIssue
import com.shohankhan.callora.core.model.PhoneNumberValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PhoneNumberValidatorTest {

    @Test
    fun `validates all active Bangladesh mobile prefixes in national and E164 formats`() {
        val prefixes = listOf("013", "014", "015", "016", "017", "018", "019")
        for (p in prefixes) {
            val local = "${p}12345678"
            val result = PhoneNumberValidator.validate(local, defaultCountryIso = "BD")
            val valid = assertIs<PhoneNumberValidationResult.Valid>(result)
            assertEquals("+88$local", valid.e164Normalized)
            assertEquals(local, valid.nationalDialable)
            assertTrue(valid.isBangladeshNumber)
            assertTrue("MNP_PREFIX_NOT_GUARANTEED" in valid.warnings)
        }
    }

    @Test
    fun `normalizes Bengali Unicode digits seamlessly`() {
        val bengaliInput = "০১৭১২-৩৪৫৬৭৮"
        val result = PhoneNumberValidator.validate(bengaliInput, defaultCountryIso = "BD")
        val valid = assertIs<PhoneNumberValidationResult.Valid>(result)
        assertEquals("+8801712345678", valid.e164Normalized)
        assertEquals("01712345678", valid.nationalDialable)
    }

    @Test
    fun `rejects defunct or unassigned Bangladesh mobile prefixes and wrong lengths`() {
        val citycell = PhoneNumberValidator.validate("01112345678", defaultCountryIso = "BD")
        val invalidPrefix = assertIs<PhoneNumberValidationResult.Invalid>(citycell)
        assertEquals(NumberValidationIssue.INVALID_BANGLADESH_MOBILE_PREFIX, invalidPrefix.issue)

        val tooShortBd = PhoneNumberValidator.validate("0171234567", defaultCountryIso = "BD")
        val invalidLen = assertIs<PhoneNumberValidationResult.Invalid>(tooShortBd)
        assertEquals(NumberValidationIssue.INVALID_BANGLADESH_LENGTH, invalidLen.issue)
    }

    @Test
    fun `blocks MMI command injection characters in pasted phone numbers`() {
        val attacks = listOf(
            "01712345678#*33*0000#",
            "**21*01712345678#",
            "01712345678,1234",
            "01712345678;99",
            "01712345678p1"
        )
        for (attack in attacks) {
            val res = PhoneNumberValidator.validate(attack)
            val invalid = assertIs<PhoneNumberValidationResult.Invalid>(res)
            assertEquals(NumberValidationIssue.CONTAINS_MMI_INJECTION_CHARACTERS, invalid.issue)
        }
    }

    @Test
    fun `blocks emergency numbers and operator short codes`() {
        for (code in listOf("999", "112", "911", "121", "16263")) {
            val res = PhoneNumberValidator.validate(code)
            val invalid = assertIs<PhoneNumberValidationResult.Invalid>(res)
            assertEquals(NumberValidationIssue.SHORT_CODE_OR_EMERGENCY_NUMBER, invalid.issue)
        }
    }

    @Test
    fun `detects self-forwarding loops across national and E164 formats`() {
        val res = PhoneNumberValidator.validate(
            rawInput = "01811223344",
            defaultCountryIso = "BD",
            ownNumberToAvoidLoop = "+880 1811-223344"
        )
        val invalid = assertIs<PhoneNumberValidationResult.Invalid>(res)
        assertEquals(NumberValidationIssue.SELF_FORWARDING_LOOP, invalid.issue)
    }

    @Test
    fun `accepts plausible international E164 destination with tariff warning`() {
        val uk = PhoneNumberValidator.validate("+44 7911 123456", defaultCountryIso = "BD")
        val valid = assertIs<PhoneNumberValidationResult.Valid>(uk)
        assertEquals("+447911123456", valid.e164Normalized)
        assertFalse(valid.isBangladeshNumber)
        assertTrue("INTERNATIONAL_DESTINATION_TARIFF_WARNING" in valid.warnings)
    }

    @Test
    fun `masks phone numbers and redacts free text without exposing full subscriber digits`() {
        val masked = SensitiveDataRedactor.maskPhoneNumber("+8801712345678")
        assertFalse(masked.contains("123456"))
        assertTrue(masked.endsWith("78"))

        val redacted = SensitiveDataRedactor.redactFreeText("Voice forwarded to +8801911998877 active")
        assertFalse(redacted.contains("998877"))
        assertTrue(redacted.endsWith("77 active"))
    }
}
