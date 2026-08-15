package com.example.shortslimiter

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShortsAccessibilityService : AccessibilityService() {

    private var lastCountedAt = 0L
    private val minCountIntervalMs = 1500L
    private var lastRedirectAt = 0L
    private val redirectCooldownMs = 1000L
    private var limitMessageShown = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val motivationMessages = listOf(
        "Bas itna hi! Ab kuch productive karo 💪",
        "Time qeemti hai - kuch naya seekho aaj ⭐",
        "Shorts band, ab apna kaam shuru karo 🎯",
        "Chhod do Shorts, kitaab ya kaam pe focus karo 📚",
        "Tumhara waqt important hai - sahi jagah lagao 🌟"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != YOUTUBE_PACKAGE && pkg != INSTAGRAM_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetIfNewDay(prefs)

        val onShorts = isShortsScreen(pkg)

        if (!onShorts) {
            // Shorts se bahar aa gaye — message flag reset karo
            limitMessageShown = false
            return
        }

        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limitReached = count >= limit

        if (limitReached) {
            // Limit cross — turant redirect, koi delay nahi
            val now = System.currentTimeMillis()
            if (now - lastRedirectAt > redirectCooldownMs) {
                lastRedirectAt = now

                // Sirf ek baar message dikhao
                if (!limitMessageShown) {
                    limitMessageShown = true
                    showToastMessage()
                }

                // Turant YouTube Home par bhejo
                redirectToYouTubeHome()
            }
            return
        }

        // Count badhao
        val now = System.currentTimeMillis()
        if (now - lastCountedAt < minCountIntervalMs) return
        lastCountedAt = now

        val newCount = count + 1
        prefs.edit().putInt(PrefsKeys.KEY_COUNT, newCount).apply()
        updateNotification(newCount, limit)

        if (newCount >= limit) {
            limitMessageShown = false // Reset taaki pehli baar message dikhe
        }
    }

    override fun onInterrupt() {}

    private fun isShortsScreen(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != pkg) return false
        return when (pkg) {
            YOUTUBE_PACKAGE -> nodeContains(root, "reel_recycler")
            INSTAGRAM_PACKAGE -> nodeContains(root, "clips_viewer") ||
                    nodeContains(root, "reel_viewer") ||
                    nodeContains(root, "reels")
            else -> false
        }
    }

    private fun nodeContains(
        node: AccessibilityNodeInfo,
        idFragment: String,
        depth: Int = 0
    ): Boolean {
        if (depth > 6) return false
        val viewId = node.viewIdResourceName
        if (viewId != null && viewId.contains(idFragment, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (nodeContains(child, idFragment, depth + 1)) return true
        }
        return false
    }

    private fun redirectToYouTubeHome() {
        try {
            val deepLink = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.youtube.com/")
                setPackage(YOUTUBE_PACKAGE)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            startActivity(deepLink)
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun showToastMessage() {
        val msg = motivationMessages.random()
        mainHandler.post {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "shorts_limiter_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Shorts Limiter",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shorts tracking active" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
        startForeground(NOTIF_ID, buildNotification(channelId, count, limit))
    }

    private fun updateNotification(count: Int, limit: Int) {
        val channelId = "shorts_limiter_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(channelId, count, limit))
    }

    private fun buildNotification(channelId: String, count: Int, limit: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("🛡️ Shorts Limiter Active")
            .setContentText("Aaj dekhe: $count / $limit Shorts")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun resetIfNewDay(prefs: SharedPreferences) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(PrefsKeys.KEY_DATE, "") != today) {
            prefs.edit()
                .putString(PrefsKeys.KEY_DATE, today)
                .putInt(PrefsKeys.KEY_COUNT, 0)
                .apply()
            limitMessageShown = false
        }
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val NOTIF_ID = 1001
    }
}
