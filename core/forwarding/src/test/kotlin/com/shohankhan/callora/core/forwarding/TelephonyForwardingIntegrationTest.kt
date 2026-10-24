package com.shohankhan.callora.core.forwarding

import com.shohankhan.callora.core.common.SensitiveDataRedactor
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingOperationResult
import com.shohankhan.callora.core.model.ForwardingResultCode
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.SimDiscoveryState
import com.shohankhan.callora.core.model.SimForwardingRule
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.model.SmartProfile
import com.shohankhan.callora.core.storage.InMemoryCalloraStorageRepository
import com.shohankhan.callora.core.telephony.BangladeshCarrierCatalog
import com.shohankhan.callora.core.telephony.FakeTelephonyPlatform
import com.shohankhan.callora.core.telephony.PermissionSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Deterministic mocked/fake telephony integration and lifecycle test suite.
 *
 * NOTE: These tests use [FakeTelephonyPlatform] and [FakeForwardingExecutor] to verify
 * Callora's domain logic, state machine, multi-SIM safety, and error handling deterministically.
 * Mocked tests do NOT prove live physical carrier compatibility on real Bangladesh SIM networks.
 */
class TelephonyForwardingIntegrationTest {

    private fun gpSim(subId: Int = 101, slot: Int = 0, networkOk: Boolean = true) = SimSubscription(
        subscriptionId = subId,
        simSlotIndex = slot,
        isEmbedded = false,
        displayName = "GP Primary",
        carrierName = "Grameenphone",
        mcc = "470",
        mnc = "01",
        isNetworkAvailable = networkOk
    )

    private fun robiSim(subId: Int = 102, slot: Int = 1) = SimSubscription(
        subscriptionId = subId,
        simSlotIndex = slot,
        isEmbedded = false,
        displayName = "Robi Secondary",
        carrierName = "Robi",
        mcc = "470",
        mnc = "02"
    )

    private fun banglalinkEsim(subId: Int = 103, slot: Int = 2) = SimSubscription(
        subscriptionId = subId,
        simSlotIndex = slot,
        isEmbedded = true,
        displayName = "Banglalink eSIM",
        carrierName = "Banglalink",
        mcc = "470",
        mnc = "03"
    )

    @Test
    fun `single SIM dialer intent never claims verified carrier success`(): Unit = runBlocking {
        val sim = gpSim()
        val platform = FakeTelephonyPlatform(initialSubscriptions = listOf(sim))
        val storage = InMemoryCalloraStorageRepository()
        val executor = FakeForwardingExecutor().apply { nextMode = FakeForwardingExecutor.Mode.DialerUnverified }
        val coordinator = CalloraForwardingCoordinator(platform, platform, storage, executor)

        val result = coordinator.performOperation(
            sim = sim,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01812345678",
            preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT
        )

        assertIs<ForwardingOperationResult.AcceptedUnverified>(result)
        assertEquals(ForwardingResultCode.COMMAND_ACCEPTED_UNVERIFIED, result.resultCode)
        assertEquals(ForwardingVerificationState.COMMAND_SUBMITTED, result.verificationState)
        assertFalse(result.verificationState.isAuthoritativelyVerified)
        assertEquals("**62*01812345678#", result.preparedMmiCode)

        val history = storage.history.first()
        assertEquals(1, history.size)
        assertFalse(history.first().requestedDestinationMasked!!.contains("123456"))
    }

    @Test
    fun `dual SIM and triple eSIM devices block unconfirmed dialer handoff to prevent wrong SIM execution`(): Unit = runBlocking {
        val s1 = gpSim()
        val s2 = robiSim()
        val s3 = banglalinkEsim()
        val platform = FakeTelephonyPlatform(initialSubscriptions = listOf(s1, s2, s3))
        val storage = InMemoryCalloraStorageRepository()
        val executor = FakeForwardingExecutor()
        val coordinator = CalloraForwardingCoordinator(platform, platform, storage, executor)

        val snapshot = platform.refreshSubscriptions()
        assertEquals(3, snapshot.subscriptions.size)
        assertTrue(snapshot.subscriptions[2].isEmbedded)

        // Without multi-SIM user confirmation, dialer execution is blocked safely
        val blocked = coordinator.performOperation(
            sim = s2,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01712345678",
            preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
            userConfirmedMultiSimSelection = false
        )
        assertEquals(ForwardingResultCode.SIM_SELECTION_UNAVAILABLE, blocked.resultCode)
        assertEquals(0, executor.executedRequests.size)

        // With explicit multi-SIM confirmation, command proceeds to dialer
        val allowed = coordinator.performOperation(
            sim = s2,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01712345678",
            preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
            userConfirmedMultiSimSelection = true
        )
        assertEquals(ForwardingResultCode.COMMAND_ACCEPTED_UNVERIFIED, allowed.resultCode)
        assertEquals(1, executor.executedRequests.size)
    }

    @Test
    fun `detects missing SIM permission denial and airplane mode accurately`(): Unit = runBlocking {
        val platform = FakeTelephonyPlatform(
            initialPermissions = PermissionSnapshot(
                hasReadPhoneState = false,
                hasCallPhone = false,
                hasPostNotifications = false,
                androidSdkInt = 35,
                isVoiceCapableDevice = true
            )
        )
        assertEquals(SimDiscoveryState.PERMISSION_REQUIRED, platform.refreshSubscriptions().state)

        platform.setPermissions(
            PermissionSnapshot(
                hasReadPhoneState = true,
                hasCallPhone = false,
                hasPostNotifications = true,
                androidSdkInt = 35,
                isVoiceCapableDevice = true
            )
        )
        assertEquals(SimDiscoveryState.NO_ACTIVE_SIM, platform.refreshSubscriptions().state)

        val offlineSim = gpSim(networkOk = false)
        platform.setSubscriptions(listOf(offlineSim))
        val storage = InMemoryCalloraStorageRepository()
        val coordinator = CalloraForwardingCoordinator(platform, platform, storage, FakeForwardingExecutor())

        val offlineResult = coordinator.performOperation(
            sim = offlineSim,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01812345678"
        )
        assertEquals(ForwardingResultCode.NETWORK_UNAVAILABLE, offlineResult.resultCode)
    }

    @Test
    fun `teletalk and unknown operators never auto-prepare unverified MMI codes`(): Unit = runBlocking {
        val teletalkSim = SimSubscription(
            subscriptionId = 201,
            simSlotIndex = 0,
            isEmbedded = false,
            displayName = "Teletalk",
            carrierName = "Teletalk",
            mcc = "470",
            mnc = "04"
        )
        val foreignSim = SimSubscription(
            subscriptionId = 202,
            simSlotIndex = 1,
            isEmbedded = false,
            displayName = "Roaming Carrier",
            carrierName = "UnknownTel",
            mcc = "310",
            mnc = "260"
        )

        assertEquals(
            BangladeshCarrierCatalog.OP_TELETALK,
            BangladeshCarrierCatalog.resolveForSubscription(teletalkSim).config.operatorId
        )
        assertEquals(
            BangladeshCarrierCatalog.OP_UNKNOWN,
            BangladeshCarrierCatalog.resolveForSubscription(foreignSim).config.operatorId
        )

        val platform = FakeTelephonyPlatform(initialSubscriptions = listOf(teletalkSim, foreignSim))
        val storage = InMemoryCalloraStorageRepository()
        val executor = FakeForwardingExecutor()
        val coordinator = CalloraForwardingCoordinator(platform, platform, storage, executor)

        // Attempting forced MMI dial preparation on unknown carrier returns UNSUPPORTED
        val unsupported = coordinator.performOperation(
            sim = foreignSim,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01712345678",
            preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
            userConfirmedMultiSimSelection = true
        )
        assertEquals(ForwardingResultCode.UNSUPPORTED, unsupported.resultCode)

        // Default preferred method on Teletalk safely opens System Call Settings
        val settingsHandoff = coordinator.performOperation(
            sim = teletalkSim,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01712345678"
        )
        assertEquals(ForwardingResultCode.COMMAND_ACCEPTED_UNVERIFIED, settingsHandoff.resultCode)
        assertEquals(1, executor.callSettingsOpenedCount)
    }

    @Test
    fun `handles Android 17 QPR2 USSD_ERROR_NOT_ALLOWED and authoritative USSD verification`(): Unit = runBlocking {
        val sim = gpSim()
        val platform = FakeTelephonyPlatform(
            initialPermissions = PermissionSnapshot(
                hasReadPhoneState = true,
                hasCallPhone = true,
                hasPostNotifications = true,
                androidSdkInt = 36,
                isVoiceCapableDevice = true
            ),
            initialSubscriptions = listOf(sim)
        )
        val storage = InMemoryCalloraStorageRepository()
        val executor = FakeForwardingExecutor()
        val coordinator = CalloraForwardingCoordinator(platform, platform, storage, executor)

        // 1. Simulated Android 17 QPR2 restriction
        executor.nextMode = FakeForwardingExecutor.Mode.DirectBlockedAndroid17NotAllowed
        val restricted = coordinator.performOperation(
            sim = sim,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.ACTIVATE,
            destinationNumber = "01812345678",
            preferredMethod = ForwardingExecutionMethod.DIRECT_USSD_API
        )
        val failRestricted = assertIs<ForwardingOperationResult.Failure>(restricted)
        assertEquals(ForwardingResultCode.UNSUPPORTED, failRestricted.resultCode)
        assertEquals("**62*01812345678#", failRestricted.fallbackMmiCode)

        // 2. Authoritative USSD status query response marks NETWORK_STATE_VERIFIED
        executor.nextMode = FakeForwardingExecutor.Mode.DirectVerified(
            active = true,
            maskedNumber = SensitiveDataRedactor.maskPhoneNumber("+8801812345678")
        )
        val verified = coordinator.performOperation(
            sim = sim,
            serviceType = ForwardingServiceType.UNREACHABLE,
            actionType = ForwardingActionType.QUERY_STATUS,
            preferredMethod = ForwardingExecutionMethod.DIRECT_USSD_API
        )
        assertEquals(ForwardingResultCode.SUCCESS_VERIFIED, verified.resultCode)
        assertEquals(ForwardingVerificationState.NETWORK_STATE_VERIFIED, verified.verificationState)
    }

    @Test
    fun `safe restoration and reboot reconciliation never overwrite carrier settings silently`(): Unit = runBlocking {
        val sim = gpSim()
        val platform = FakeTelephonyPlatform(initialSubscriptions = listOf(sim))
        val storage = InMemoryCalloraStorageRepository()
        storage.ensureSeeded()
        val executor = FakeForwardingExecutor()
        val coordinator = CalloraForwardingCoordinator(platform, platform, storage, executor)

        // Save user's intended baseline: CFNRc normally forwarded to voicemail/backup +8801911223344
        storage.upsertBaseline(
            BaselineSimConfig(
                simSignature = sim.stableSignature,
                simSlotIndex = 0,
                serviceType = ForwardingServiceType.UNREACHABLE,
                shouldBeEnabled = true,
                baselineDestinationNumber = "+8801911223344",
                updatedAtMillis = 2_000L
            )
        )

        val plan = coordinator.buildSafeRestorationPlan(listOf(sim))
        assertEquals(1, plan.size)
        assertEquals(ForwardingActionType.ACTIVATE, plan.first().targetAction)
        assertEquals("+8801911223344", plan.first().targetDestination)
        // Building restoration plan executes ZERO commands automatically
        assertEquals(0, executor.executedRequests.size)

        // Activate a profile locally, then simulate SIM swap + device reboot
        val profile = SmartProfile(
            id = "custom_reboot",
            name = "Emergency",
            description = "",
            isSelectedActive = true,
            rules = listOf(
                SimForwardingRule(
                    simSignature = sim.stableSignature,
                    simSlotIndex = 0,
                    simDisplayLabel = sim.displayName,
                    enabled = true,
                    serviceType = ForwardingServiceType.UNREACHABLE,
                    destinationNumber = "+8801812345678"
                )
            )
        )
        storage.upsertProfile(profile)
        storage.setActiveProfile(profile.id, ForwardingVerificationState.COMMAND_SUBMITTED)
        storage.updateSettings { it.copy(lastKnownSimSignatures = setOf(sim.stableSignature)) }

        // Simulate SIM replacement in Slot 0 after reboot
        val replacedSim = robiSim(subId = 555, slot = 0)
        platform.setSubscriptions(listOf(replacedSim), knownSignatures = setOf(sim.stableSignature), timestamp = 9_000L)

        val rebootSummary = coordinator.reconcileAfterBootOrSimChange()
        assertTrue(rebootSummary.subscriptionChangedSinceLastRun)
        assertTrue(rebootSummary.requiresUserConfirmation)
        assertTrue(storage.currentSettings().pendingRebootReview)
        // Reboot recovery executed ZERO carrier commands automatically!
        assertEquals(0, executor.executedRequests.size)
    }
}
