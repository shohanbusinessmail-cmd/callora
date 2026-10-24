package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.AppThemeMode
import com.shohankhan.callora.core.model.BackupPayload
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.FavoriteDestination
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.ReconciliationDiscrepancy
import com.shohankhan.callora.core.model.SimForwardingRule
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MmiScheduleBackupAndReconcilerTest {

    @Test
    fun `builds standard 3GPP MMI codes for CFNRc CFB CFNRy and CFU`() {
        val cfnrc = GsmMmiCodec.buildCommand(
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "+8801712345678"
        )
        val okCfnrc = assertIs<GsmMmiCodec.MmiBuildResult.Success>(cfnrc)
        assertEquals("**62*01712345678#", okCfnrc.mmiString)

        val cfnry = GsmMmiCodec.buildCommand(
            serviceType = ForwardingServiceType.UNANSWERED,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01812345678",
            noReplyTimeoutSeconds = 23 // rounds to nearest 5s step = 25s
        )
        val okCfnry = assertIs<GsmMmiCodec.MmiBuildResult.Success>(cfnry)
        assertEquals("**61*01812345678**25#", okCfnry.mmiString)

        val deact = GsmMmiCodec.buildCommand(
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.DEACTIVATE
        )
        assertEquals("##62#", assertIs<GsmMmiCodec.MmiBuildResult.Success>(deact).mmiString)

        val query = GsmMmiCodec.buildCommand(
            serviceType = ForwardingServiceType.UNCONDITIONAL,
            actionType = ForwardingActionType.QUERY_STATUS
        )
        assertEquals("*#21#", assertIs<GsmMmiCodec.MmiBuildResult.Success>(query).mmiString)
    }

    @Test
    fun `parses carrier USSD responses conservatively`() {
        val active = GsmMmiCodec.parseCarrierResponse("Call forwarding Voice: +8801712345678")
        val authActive = assertIs<GsmMmiCodec.ParsedCarrierResponse.AuthoritativeStatus>(active)
        assertTrue(authActive.isEnabled)
        assertFalse(authActive.sanitizedSummary.contains("123456"))

        val disabled = GsmMmiCodec.parseCarrierResponse("Call forwarding Voice: Not forwarded")
        val authDisabled = assertIs<GsmMmiCodec.ParsedCarrierResponse.AuthoritativeStatus>(disabled)
        assertFalse(authDisabled.isEnabled)

        val vague = GsmMmiCodec.parseCarrierResponse("Request received by network.")
        assertIs<GsmMmiCodec.ParsedCarrierResponse.AmbiguousAcknowledgement>(vague)

        val err = GsmMmiCodec.parseCarrierResponse("Connection problem or invalid MMI code.")
        assertIs<GsmMmiCodec.ParsedCarrierResponse.CarrierError>(err)
    }

    @Test
    fun `validates daytime and overnight schedules accurately`() {
        val bdWorkSchedule = ProfileSchedule(
            enabled = true,
            daysOfWeek = setOf(1, 2, 3, 4, 7), // Sun-Thu
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60
        )
        assertIs<ScheduleValidator.ScheduleValidationResult.Valid>(
            ScheduleValidator.validate(bdWorkSchedule, nowEpochMillis = 1_000L)
        )
        // Sunday 10:30 -> active
        assertTrue(ScheduleValidator.isWithinActiveWindow(bdWorkSchedule, isoDayOfWeek = 7, minuteOfDay = 630, nowEpochMillis = 1_000L))
        // Friday 10:30 -> inactive
        assertFalse(ScheduleValidator.isWithinActiveWindow(bdWorkSchedule, isoDayOfWeek = 5, minuteOfDay = 630, nowEpochMillis = 1_000L))

        // Overnight schedule: 22:00 Mon -> 06:00 Tue
        val overnight = ProfileSchedule(
            enabled = true,
            daysOfWeek = setOf(1),
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60
        )
        assertTrue(ScheduleValidator.isWithinActiveWindow(overnight, isoDayOfWeek = 2, minuteOfDay = 5 * 60, nowEpochMillis = 1_000L))
        assertFalse(ScheduleValidator.isWithinActiveWindow(overnight, isoDayOfWeek = 2, minuteOfDay = 7 * 60, nowEpochMillis = 1_000L))
    }

    @Test
    fun `round-trips valid backup JSON and resets active verification flags on import`() {
        val sim = SimSubscription(
            subscriptionId = 1,
            simSlotIndex = 0,
            isEmbedded = false,
            displayName = "GP SIM",
            carrierName = "Grameenphone",
            mcc = "470",
            mnc = "01"
        )
        val payload = BackupPayload(
            exportedAtMillis = 50_000L,
            appVersion = "0.9.0",
            settings = AppSettings(
                onboardingCompleted = true,
                language = AppLanguage.BENGALI,
                themeMode = AppThemeMode.DARK,
                lowBatteryThresholdPercent = 25
            ),
            profiles = listOf(
                SmartProfile(
                    id = "custom_1",
                    name = "অফিস প্রোফাইল",
                    description = "ব্যস্ত থাকলে অফিসে ফরওয়ার্ড",
                    isSelectedActive = true,
                    lastVerifiedState = ForwardingVerificationState.NETWORK_STATE_VERIFIED,
                    rules = listOf(
                        SimForwardingRule(
                            simSignature = sim.stableSignature,
                            simSlotIndex = 0,
                            simDisplayLabel = "GP SIM",
                            enabled = true,
                            serviceType = ForwardingServiceType.UNREACHABLE,
                            destinationNumber = "+8801712345678"
                        )
                    )
                )
            ),
            favorites = listOf(
                FavoriteDestination(
                    id = "fav_1",
                    label = "Backup Robi",
                    normalizedNumber = "+8801812345678",
                    originalInput = "01812345678",
                    createdAtMillis = 40_000L
                )
            ),
            baselines = listOf(
                BaselineSimConfig(
                    simSignature = sim.stableSignature,
                    simSlotIndex = 0,
                    serviceType = ForwardingServiceType.UNREACHABLE,
                    shouldBeEnabled = false,
                    baselineDestinationNumber = null,
                    updatedAtMillis = 40_000L
                )
            )
        )

        val encoded = BackupJsonCodec.encodeBackup(payload)
        val decoded = BackupJsonCodec.decodeAndValidateBackup(encoded)
        val valid = assertIs<BackupJsonCodec.ImportValidationResult.Valid>(decoded)

        assertEquals(AppLanguage.BENGALI, valid.payload.settings.language)
        assertEquals(1, valid.payload.profiles.size)
        // Crucial safety invariant: imported profile is NEVER auto-marked active or verified!
        assertFalse(valid.payload.profiles.first().isSelectedActive)
        assertEquals(
            ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
            valid.payload.profiles.first().lastVerifiedState
        )
    }

    @Test
    fun `rejects tampered or malicious backup JSON`() {
        val badSyntax = BackupJsonCodec.decodeAndValidateBackup("{not valid json")
        assertIs<BackupJsonCodec.ImportValidationResult.Invalid>(badSyntax)

        val badThreshold = """
            {
              "format": "callora-backup-v1",
              "schemaVersion": 1,
              "exportedAtMillis": 1000,
              "appVersion": "0.9.0",
              "settings": { "themeMode": "DARK", "lowBatteryThresholdPercent": 99 },
              "profiles": [],
              "favorites": [],
              "baselines": []
            }
        """.trimIndent()
        val rejected = assertIs<BackupJsonCodec.ImportValidationResult.Invalid>(
            BackupJsonCodec.decodeAndValidateBackup(badThreshold)
        )
        assertEquals("INVALID_BATTERY_THRESHOLD", rejected.errorCode)
    }

    @Test
    fun `reconciles SIM swap conflicts without matching wrong SIM silently`() {
        val originalGpSim = SimSubscription(
            subscriptionId = 1,
            simSlotIndex = 0,
            isEmbedded = false,
            displayName = "SIM 1",
            carrierName = "Grameenphone",
            mcc = "470",
            mnc = "01"
        )
        val swappedRobiSimInSlot0 = SimSubscription(
            subscriptionId = 9,
            simSlotIndex = 0,
            isEmbedded = false,
            displayName = "SIM 1",
            carrierName = "Robi",
            mcc = "470",
            mnc = "02"
        )
        val activeProfile = SmartProfile(
            id = "p1",
            name = "Unreachable GP",
            description = "",
            isSelectedActive = true,
            rules = listOf(
                SimForwardingRule(
                    simSignature = originalGpSim.stableSignature,
                    simSlotIndex = 0,
                    simDisplayLabel = "SIM 1",
                    enabled = true,
                    serviceType = ForwardingServiceType.UNREACHABLE,
                    destinationNumber = "+8801812345678"
                )
            )
        )

        val reconciled = StateReconciler.reconcile(
            subscriptions = listOf(swappedRobiSimInSlot0),
            activeProfile = activeProfile,
            observedStates = emptyList<ObservedSimForwardingState>(),
            previousKnownSignatures = setOf(originalGpSim.stableSignature)
        )

        val cfnrcEntry = reconciled.first { it.serviceType == ForwardingServiceType.UNREACHABLE }
        assertEquals(ReconciliationDiscrepancy.SIM_CHANGED_REQUIRES_REVIEW, cfnrcEntry.discrepancy)
    }
}
