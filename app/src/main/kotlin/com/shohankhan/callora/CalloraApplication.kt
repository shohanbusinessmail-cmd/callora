package com.shohankhan.callora

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shohankhan.callora.background.CalloraNotificationHelper
import com.shohankhan.callora.background.ReadinessCheckWorker
import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.forwarding.AndroidForwardingExecutor
import com.shohankhan.callora.core.forwarding.CalloraForwardingCoordinator
import com.shohankhan.callora.core.forwarding.ForwardingExecutor
import com.shohankhan.callora.core.storage.CalloraDatabase
import com.shohankhan.callora.core.storage.CalloraStorageRepository
import com.shohankhan.callora.core.storage.RoomCalloraStorageRepository
import com.shohankhan.callora.core.telephony.AndroidBatteryStateMonitor
import com.shohankhan.callora.core.telephony.AndroidPermissionStatusProvider
import com.shohankhan.callora.core.telephony.AndroidSubscriptionDataSource
import com.shohankhan.callora.core.telephony.BatteryStateSource
import com.shohankhan.callora.core.telephony.PermissionStatusProvider
import com.shohankhan.callora.core.telephony.SubscriptionDataSource
import java.util.concurrent.TimeUnit

interface AppContainer {
    val clock: CalloraClock
    val storage: CalloraStorageRepository
    val permissionStatusProvider: PermissionStatusProvider
    val subscriptionDataSource: SubscriptionDataSource
    val batteryStateSource: BatteryStateSource
    val forwardingExecutor: ForwardingExecutor
    val forwardingCoordinator: CalloraForwardingCoordinator
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val clock: CalloraClock = CalloraClock.SYSTEM

    private val database: CalloraDatabase by lazy {
        CalloraDatabase.build(appContext)
    }

    override val storage: CalloraStorageRepository by lazy {
        RoomCalloraStorageRepository(
            context = appContext,
            database = database,
            clock = clock
        )
    }

    override val permissionStatusProvider: PermissionStatusProvider by lazy {
        AndroidPermissionStatusProvider(appContext)
    }

    override val subscriptionDataSource: SubscriptionDataSource by lazy {
        AndroidSubscriptionDataSource(
            context = appContext,
            permissionStatusProvider = permissionStatusProvider,
            clock = clock
        )
    }

    override val batteryStateSource: BatteryStateSource by lazy {
        AndroidBatteryStateMonitor(
            context = appContext,
            clock = clock
        )
    }

    override val forwardingExecutor: ForwardingExecutor by lazy {
        AndroidForwardingExecutor(
            context = appContext,
            clock = clock
        )
    }

    override val forwardingCoordinator: CalloraForwardingCoordinator by lazy {
        CalloraForwardingCoordinator(
            subscriptionDataSource = subscriptionDataSource,
            permissionStatusProvider = permissionStatusProvider,
            storage = storage,
            executor = forwardingExecutor,
            clock = clock
        )
    }
}

class CalloraApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        CalloraNotificationHelper.ensureChannel(this)
        schedulePeriodicReadinessCheck(this)
    }

    companion object {
        private const val PERIODIC_READINESS_WORK = "callora_periodic_readiness_work"

        fun schedulePeriodicReadinessCheck(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<ReadinessCheckWorker>(30, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_READINESS_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: Exception) {
                // Safe no-op inside unit test environments where WorkManager isn't initialized
            }
        }
    }
}
