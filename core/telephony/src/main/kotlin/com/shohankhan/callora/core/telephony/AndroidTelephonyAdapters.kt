package com.shohankhan.callora.core.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.ServiceState
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.common.SensitiveDataRedactor
import com.shohankhan.callora.core.common.StateReconciler
import com.shohankhan.callora.core.model.BatterySnapshot
import com.shohankhan.callora.core.model.SimDiscoverySnapshot
import com.shohankhan.callora.core.model.SimDiscoveryState
import com.shohankhan.callora.core.model.SimSubscription
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidPermissionStatusProvider(
    private val context: Context
) : PermissionStatusProvider {

    override fun currentPermissions(): PermissionSnapshot {
        val hasReadPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = context.packageManager
        val isVoiceCapable = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

        return PermissionSnapshot(
            hasReadPhoneState = hasReadPhone,
            hasCallPhone = hasCallPhone,
            hasPostNotifications = hasPostNotifications,
            androidSdkInt = Build.VERSION.SDK_INT,
            isVoiceCapableDevice = isVoiceCapable
        )
    }
}

class AndroidSubscriptionDataSource(
    private val context: Context,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val clock: CalloraClock = CalloraClock.SYSTEM
) : SubscriptionDataSource {

    private val latestManualSnapshot = MutableStateFlow<SimDiscoverySnapshot?>(null)

    override val discoverySnapshot: Flow<SimDiscoverySnapshot> = callbackFlow {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        trySend(readSnapshotInternal(emptySet()))

        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                trySend(readSnapshotInternal(emptySet()))
            }
        }

        var registered = false
        if (subscriptionManager != null && permissionStatusProvider.currentPermissions().hasReadPhoneState) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    subscriptionManager.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
                } else {
                    @Suppress("DEPRECATION")
                    subscriptionManager.addOnSubscriptionsChangedListener(listener)
                }
                registered = true
            } catch (_: SecurityException) {
                registered = false
            }
        }

        awaitClose {
            if (registered && subscriptionManager != null) {
                try {
                    subscriptionManager.removeOnSubscriptionsChangedListener(listener)
                } catch (_: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }.distinctUntilChanged()

    override suspend fun refreshSubscriptions(knownSignatures: Set<String>): SimDiscoverySnapshot {
        val snapshot = readSnapshotInternal(knownSignatures)
        latestManualSnapshot.value = snapshot
        return snapshot
    }

    @SuppressLint("MissingPermission")
    private fun readSnapshotInternal(knownSignatures: Set<String>): SimDiscoverySnapshot {
        val perms = permissionStatusProvider.currentPermissions()
        val now = clock.nowMillis()
        if (!perms.isVoiceCapableDevice) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.UNAVAILABLE,
                subscriptions = emptyList(),
                checkedAtMillis = now
            )
        }
        if (!perms.hasReadPhoneState) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.PERMISSION_REQUIRED,
                subscriptions = emptyList(),
                checkedAtMillis = now
            )
        }

        val subManager = context.getSystemService(SubscriptionManager::class.java)
            ?: return SimDiscoverySnapshot(
                state = SimDiscoveryState.UNAVAILABLE,
                subscriptions = emptyList(),
                checkedAtMillis = now
            )

        val airplaneModeOn = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }

        val activeList: List<SubscriptionInfo> = try {
            subManager.activeSubscriptionInfoList ?: emptyList()
        } catch (_: SecurityException) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.PERMISSION_REQUIRED,
                subscriptions = emptyList(),
                checkedAtMillis = now
            )
        } catch (_: Exception) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.UNAVAILABLE,
                subscriptions = emptyList(),
                checkedAtMillis = now
            )
        }

        if (activeList.isEmpty()) {
            return SimDiscoverySnapshot(
                state = SimDiscoveryState.NO_ACTIVE_SIM,
                subscriptions = emptyList(),
                checkedAtMillis = now
            )
        }

        val baseTelephony = context.getSystemService(TelephonyManager::class.java)
        val mapped = activeList.map { info ->
            mapSubscriptionInfo(info, baseTelephony, airplaneModeOn)
        }.sortedWith(compareBy({ it.simSlotIndex }, { it.subscriptionId }))

        val changed = StateReconciler.detectSubscriptionChange(knownSignatures, mapped)
        return SimDiscoverySnapshot(
            state = SimDiscoveryState.READY,
            subscriptions = mapped,
            checkedAtMillis = now,
            subscriptionChangedWarning = changed
        )
    }

    @SuppressLint("MissingPermission")
    private fun mapSubscriptionInfo(
        info: SubscriptionInfo,
        baseTelephony: TelephonyManager?,
        airplaneModeOn: Boolean
    ): SimSubscription {
        val subId = info.subscriptionId
        val subTelephony = baseTelephony?.createForSubscriptionId(subId)

        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.mccString.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.mcc.takeIf { it > 0 }?.toString().orEmpty()
        }

        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.mncString.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.mnc.takeIf { it >= 0 }?.toString().orEmpty()
        }

        val isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.isEmbedded
        } else {
            false
        }

        val carrierId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.carrierId.takeIf { it != TelephonyManager.UNKNOWN_CARRIER_ID }
        } else {
            null
        }

        // Never store or expose unmasked subscriber phone numbers; mask immediately if available.
        val rawLegacyNumber = try {
            @Suppress("DEPRECATION")
            info.number?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
        val maskedNumber = rawLegacyNumber?.let { SensitiveDataRedactor.maskPhoneNumber(it) }
            ?.takeIf { it.isNotBlank() }

        val inService = if (airplaneModeOn) {
            false
        } else {
            try {
                val serviceState = subTelephony?.serviceState
                serviceState == null || serviceState.state == ServiceState.STATE_IN_SERVICE
            } catch (_: SecurityException) {
                true
            }
        }

        val isRoaming = try {
            subTelephony?.isNetworkRoaming ?: (info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE)
        } catch (_: SecurityException) {
            false
        }

        val carrierLabel = info.carrierName?.toString()?.trim().orEmpty()
        val displayLabel = info.displayName?.toString()?.trim().orEmpty()
            .ifBlank { carrierLabel.ifBlank { "SIM ${info.simSlotIndex + 1}" } }

        return SimSubscription(
            subscriptionId = subId,
            simSlotIndex = info.simSlotIndex,
            isEmbedded = isEmbedded,
            displayName = displayLabel,
            carrierName = carrierLabel.ifBlank { displayLabel },
            mcc = mcc,
            mnc = mnc,
            carrierId = carrierId,
            maskedPhoneNumber = maskedNumber,
            isNetworkAvailable = inService,
            isRoaming = isRoaming
        )
    }
}

class AndroidBatteryStateMonitor(
    private val context: Context,
    private val clock: CalloraClock = CalloraClock.SYSTEM
) : BatteryStateSource {

    override val batteryState: Flow<BatterySnapshot> = callbackFlow {
        trySend(readCurrentBattery())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    trySend(parseBatteryIntent(intent))
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Ignore if already unregistered
            }
        }
    }.distinctUntilChanged { a, b ->
        a.levelPercent == b.levelPercent &&
            a.isCharging == b.isCharging &&
            a.isBatterySaverOn == b.isBatterySaverOn
    }

    override fun readCurrentBattery(): BatterySnapshot {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (sticky != null) {
            parseBatteryIntent(sticky)
        } else {
            val bm = context.getSystemService(BatteryManager::class.java)
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
            val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bm?.isCharging ?: false
            } else {
                false
            }
            val pm = context.getSystemService(PowerManager::class.java)
            BatterySnapshot(
                levelPercent = pct,
                isCharging = charging,
                isBatterySaverOn = pm?.isPowerSaveMode ?: false,
                sampledAtMillis = clock.nowMillis()
            )
        }
    }

    private fun parseBatteryIntent(intent: Intent): BatterySnapshot {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100) / scale).coerceIn(0, 100) else 100
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        val pm = context.getSystemService(PowerManager::class.java)
        return BatterySnapshot(
            levelPercent = pct,
            isCharging = isCharging,
            isBatterySaverOn = pm?.isPowerSaveMode ?: false,
            sampledAtMillis = clock.nowMillis()
        )
    }
}
