package com.shohankhan.callora.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shohankhan.callora.CalloraApplication
import com.shohankhan.callora.MainActivity
import com.shohankhan.callora.core.common.ScheduleValidator
import com.shohankhan.callora.core.ui.R
import kotlinx.coroutines.flow.first
import java.util.Calendar

object CalloraNotificationHelper {
    const val CHANNEL_ID = "callora_readiness_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_alerts_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyIfPermitted(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call_forward)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}

/**
 * Triggered on BOOT_COMPLETED. Enqueues [RebootReconciliationWorker] to inspect SIM state
 * and prompt the user if reconciliation is needed — NEVER modifies carrier settings automatically.
 */
class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val work = OneTimeWorkRequestBuilder<RebootReconciliationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "callora_boot_reconciliation",
                ExistingWorkPolicy.REPLACE,
                work
            )
        } catch (_: Exception) {
            // Ignore if WorkManager unavailable
        }
    }
}

class RebootReconciliationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CalloraApplication ?: return Result.success()
        val settings = app.container.storage.currentSettings()
        if (!settings.rebootRecoveryCheckEnabled) return Result.success()

        val summary = app.container.forwardingCoordinator.reconcileAfterBootOrSimChange()
        if (summary.requiresUserConfirmation) {
            CalloraNotificationHelper.notifyIfPermitted(
                context = applicationContext,
                notificationId = 1001,
                title = applicationContext.getString(R.string.notif_reboot_review_title),
                body = applicationContext.getString(R.string.notif_reboot_review_body)
            )
        }
        return Result.success()
    }
}

/**
 * Deferred periodic check for low-battery CFNRc preparation warnings and scheduled profile windows.
 * Respects Android background execution limits and requires user confirmation for carrier changes.
 */
class ReadinessCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CalloraApplication ?: return Result.success()
        val settings = app.container.storage.currentSettings()
        val battery = app.container.batteryStateSource.readCurrentBattery()

        if (settings.lowBatteryWarningEnabled &&
            battery.isBelowOrAtThreshold(settings.lowBatteryThresholdPercent)
        ) {
            CalloraNotificationHelper.notifyIfPermitted(
                context = applicationContext,
                notificationId = 1002,
                title = applicationContext.getString(R.string.notif_low_battery_title, battery.levelPercent),
                body = applicationContext.getString(R.string.notif_low_battery_body)
            )
        }

        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val now = app.container.clock.nowMillis()

        val profiles = app.container.storage.profiles.first()
        val matchingScheduled = profiles.firstOrNull { profile ->
            !profile.isSelectedActive &&
                ScheduleValidator.isWithinActiveWindow(profile.schedule, dow, minuteOfDay, now)
        }
        if (matchingScheduled != null) {
            CalloraNotificationHelper.notifyIfPermitted(
                context = applicationContext,
                notificationId = 1003,
                title = applicationContext.getString(R.string.notif_schedule_prompt_title, matchingScheduled.name),
                body = applicationContext.getString(R.string.notif_schedule_prompt_body, matchingScheduled.name)
            )
        }

        return Result.success()
    }
}
