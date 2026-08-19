package com.example.shortslimiter

import android.content.Context
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ServiceKeepAliveWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // Accessibility service ON hai ya nahi check karo
        val isEnabled = isAccessibilityEnabled()

        if (!isEnabled) {
            // Service band hai — notification dikhao user ko
            showReminderNotification()
        }

        return Result.success()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedService =
            "${context.packageName}/${ShortsAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedService)
    }

    private fun showReminderNotification() {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val channelId = "keepalive_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Shorts Limiter Alert",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        )
        val pi = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Shorts Limiter band ho gayi!")
            .setContentText("Tap karke wapas ON karo — Shorts limit kaam nahi kar rahi")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(2001, notification)
    }

    companion object {
        private const val WORK_NAME = "shorts_limiter_keepalive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceKeepAliveWorker>(
                15, TimeUnit.MINUTES // Har 15 min check
            ).setConstraints(
                Constraints.Builder().build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
