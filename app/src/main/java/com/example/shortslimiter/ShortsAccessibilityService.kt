package com.example.shortslimiter

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
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

    private var wasOnShorts = false
    private var lastCountedAt = 0L
    private val minCountIntervalMs = 1200L
    private var lastRedirectAt = 0L
    private val redirectCooldownMs = 1500L
    private var limitMessageShown = false
    private var currentEvent: AccessibilityEvent? = null
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
        ServiceKeepAliveWorker.schedule(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        currentEvent = event
        val pkg = event.packageName?.toString() ?: return

        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetIfNewDay(prefs)

        // Browser mein YouTube Shorts detect karo
        if (isBrowser(pkg)) {
            val url = event.text?.joinToString("") ?: ""
            if (url.contains("youtube.com/shorts", ignoreCase = true)) {
                handleLimitCheck(prefs, pkg, isBrowser = true)
            }
            return
        }

        if (pkg != YOUTUBE_PACKAGE && pkg != INSTAGRAM_PACKAGE) return

        val onShorts = isShortsScreen(pkg)

        when {
            !onShorts && wasOnShorts -> {
                wasOnShorts = false
                limitMessageShown = false
            }
            onShorts -> {
                val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
                val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)

                if (count >= limit) {
                    handleLimitCheck(prefs, pkg, isBrowser = false)
                    wasOnShorts = true
                    return
                }

                // Naya Short count karo — sirf WIN_STATE par aur pehle se Shorts par the
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && wasOnShorts
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastCountedAt >= minCountIntervalMs) {
                        lastCountedAt = now
                        val newCount = count + 1
                        prefs.edit().putInt(PrefsKeys.KEY_COUNT, newCount).apply()
                        updateNotification(newCount, limit)
                        if (newCount >= limit) limitMessageShown = false
                    }
                }

                wasOnShorts = true
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            startService(
                Intent(applicationContext, ShortsAccessibilityService::class.java)
            )
        } catch (e: Exception) {}
    }

    private fun handleLimitCheck(
        prefs: SharedPreferences,
        pkg: String,
        isBrowser: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (now - lastRedirectAt > redirectCooldownMs) {
            lastRedirectAt = now
            if (!limitMessageShown) {
                limitMessageShown = true
                showToastMessage()
            }
            if (isBrowser) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                redirectToHome(pkg)
            }
        }
    }

    private fun isBrowser(pkg: String): Boolean {
        return pkg.contains("chrome", ignoreCase = true) ||
                pkg.contains("browser", ignoreCase = true) ||
                pkg.contains("firefox", ignoreCase = true) ||
                pkg.contains("opera", ignoreCase = true) ||
                pkg == "com.android.browser" ||
                pkg == "org.mozilla.firefox"
    }

    private fun isShortsScreen(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != pkg) return false
        return when (pkg) {
            YOUTUBE_PACKAGE -> nodeContains(root, "reel_recycler")
            INSTAGRAM_PACKAGE ->
                nodeContains(root, "clips_viewer") ||
                nodeContains(root, "reel_viewer") ||
                nodeContains(root, "reel") ||
                nodeContains(root, "reels") ||
                nodeContains(root, "clips")
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

    private fun redirectToHome(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.let { startActivity(it) }
        } catch (e: Exception) {}
        mainHandler.postDelayed({ clickHomeTab() }, 800)
    }

    private fun clickHomeTab() {
        val root = rootInActiveWindow ?: run {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        val homeNode = findClickableAncestorByLabel(root, "Home")
        if (homeNode != null) {
            homeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        val feedNode = findClickableAncestorByLabel(root, "Feed")
        if (feedNode != null) {
            feedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun findClickableAncestorByLabel(
        root: AccessibilityNodeInfo,
        label: String
    ): AccessibilityNodeInfo? {
        val match = findNodeByLabel(root, label, 0) ?: return null
        var current: AccessibilityNodeInfo? = match
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun findNodeByLabel(
        node: AccessibilityNodeInfo,
        label: String,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 14) return null
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()
        if (desc?.equals(label, ignoreCase = true) == true ||
            text?.equals(label, ignoreCase = true) == true
        ) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByLabel(child, label, depth + 1)
            if (found != null) return found
        }
        return null
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

    private fun buildNotification(
        channelId: String,
        count: Int,
        limit: Int
    ): Notification {
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
            wasOnShorts = false
        }
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val NOTIF_ID = 1001
    }
}
