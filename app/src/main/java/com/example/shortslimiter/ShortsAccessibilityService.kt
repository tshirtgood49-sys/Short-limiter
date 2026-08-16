package com.example.shortslimiter

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShortsAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != YOUTUBE_PACKAGE && pkg != INSTAGRAM_PACKAGE) return

        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)

        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val typeStr = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "SCROLL"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WIN_STATE"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WIN_CONTENT"
            else -> "OTHER(${event.eventType})"
        }

        val log = "$time|$typeStr|" +
            "dX=${event.scrollDeltaX}|" +
            "dY=${event.scrollDeltaY}|" +
            "sX=${event.scrollX}|" +
            "sY=${event.scrollY}|" +
            "from=${event.fromIndex}|" +
            "pkg=${pkg.replace("com.google.android.", "").replace("com.", "")}"

        val existing = prefs.getString("scroll_log", "") ?: ""
        val lines = existing.split("\n").filter { it.isNotBlank() }.toMutableList()
        lines.add(log)
        while (lines.size > 25) lines.removeAt(0)
        prefs.edit().putString("scroll_log", lines.joinToString("\n")).apply()
    }

    override fun onInterrupt() {}

    private fun startForegroundNotification() {
        val channelId = "shorts_limiter_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Shorts Limiter",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Debug mode active" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("🛡️ Shorts Limiter - DEBUG")
            .setContentText("Scroll data capture ho raha hai")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val NOTIF_ID = 1001
    }
}
