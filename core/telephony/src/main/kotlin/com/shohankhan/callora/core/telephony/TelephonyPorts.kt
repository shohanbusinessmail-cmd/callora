package com.shohankhan.callora.core.telephony

import com.shohankhan.callora.core.model.BatterySnapshot
import com.shohankhan.callora.core.model.SimDiscoverySnapshot
import com.shohankhan.callora.core.model.SimDiscoveryState
import com.shohankhan.callora.core.model.SimSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionSnapshot(
    val hasReadPhoneState: Boolean,
    val hasCallPhone: Boolean,
    val hasPostNotifications: Boolean,
    val androidSdkInt: Int,
    val isVoiceCapableDevice: Boolean
)

interface SubscriptionDataSource {
    val discoverySnapshot: Flow<SimDiscoverySnapshot>
    suspend fun refreshSubscriptions(knownSignatures: Set<String> = emptySet()): SimDiscoverySnapshot
}

interface BatteryStateSource {
    val batteryState: Flow<BatterySnapshot>
    fun readCurrentBattery(): BatterySnapshot
}

interface PermissionStatusProvider {
    fun currentPermissions(): PermissionSnapshot
}

/**
 * Deterministic in-memory telephony test double clearly identified as a fake/mock implementation
 * for unit and integration testing (does NOT prove live physical carrier compatibility).
 */
class FakeTelephonyPlatform(
    initialPermissions: PermissionSnapshot = PermissionSnapshot(
        hasReadPhoneState = true,
        hasCallPhone = false,
        hasPostNotifications = true,
        androidSdkInt = 35,
        isVoiceCapableDevice = true
    ),
    initialSubscriptions: List<SimSubscription> = emptyList(),
    initialBattery: BatterySnapshot = BatterySnapshot(levelPercent = 82, isCharging = false, sampledAtMillis = 1_000L)
) : SubscriptionDataSource, BatteryStateSource, PermissionStatusProvider {

    private var permissions: PermissionSnapshot = initialPermissions
    private var subscriptions: List<SimSubscription> = initialSubscriptions
    private var nowMillis: Long = 1_000L

    private val _discovery = MutableStateFlow(computeSnapshot(emptySet()))
    override val discoverySnapshot: StateFlow<SimDiscoverySnapshot> = _discovery.asStateFlow()

    private val _battery = MutableStateFlow(initialBattery)
    override val batteryState: StateFlow<BatterySnapshot> = _battery.asStateFlow()

    fun setPermissions(updated: PermissionSnapshot) {
        permissions = updated
        _discovery.value = computeSnapshot(emptySet())
    }

    fun setSubscriptions(updated: List<SimSubscription>, knownSignatures: Set<String> = emptySet(), timestamp: Long = nowMillis) {
        nowMillis = timestamp
        subscriptions = updated
        _discovery.value = computeSnapshot(knownSignatures)
    }

    fun setBattery(updated: BatterySnapshot) {
        _battery.value = updated
    }

    override suspend fun refreshSubscriptions(knownSignatures: Set<String>): SimDiscoverySnapshot {
        val snap = computeSnapshot(knownSignatures)
        _discovery.value = snap
        return snap
    }

    override fun readCurrentBattery(): BatterySnapshot = _battery.value

    override fun currentPermissions(): PermissionSnapshot = permissions

    private fun computeSnapshot(knownSignatures: Set<String>): SimDiscoverySnapshot {
        if (!permissions.isVoiceCapableDevice) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.UNAVAILABLE,
                subscriptions = emptyList(),
                checkedAtMillis = nowMillis
            )
        }
        if (!permissions.hasReadPhoneState) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.PERMISSION_REQUIRED,
                subscriptions = emptyList(),
                checkedAtMillis = nowMillis
            )
        }
        if (subscriptions.isEmpty()) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.NO_ACTIVE_SIM,
                subscriptions = emptyList(),
                checkedAtMillis = nowMillis
            )
        }
        val ordered = subscriptions.sortedWith(compareBy({ it.simSlotIndex }, { it.subscriptionId }))
        val changed = knownSignatures.isNotEmpty() && ordered.map { it.stableSignature }.toSet() != knownSignatures
        return SimDiscoverySnapshot(
            state = SimDiscoveryState.READY,
            subscriptions = ordered,
            checkedAtMillis = nowMillis,
            subscriptionChangedWarning = changed
        )
    }
}
