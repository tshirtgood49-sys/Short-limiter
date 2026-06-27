package com.example.shortslimiter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShortsAccessibilityService : AccessibilityService() {

    private var lastCountedAt = 0L
    private val debounceMs = 800L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return

        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetIfNewDay(prefs)

        val blockedUntil = prefs.getLong(PrefsKeys.KEY_BLOCKED_UNTIL, 0)
        val now = System.currentTimeMillis()

        if (now < blockedUntil) {
            exitYouTube()
            return
        } else if (blockedUntil != 0L) {
            stopVpnBlock()
            prefs.edit().putLong(PrefsKeys.KEY_BLOCKED_UNTIL, 0).apply()
        }

        if (!isShortsScreen()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (now - lastCountedAt < debounceMs) return
                lastCountedAt = now

                val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0) + 1
                prefs.edit().putInt(PrefsKeys.KEY_COUNT, count).apply()

                val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
                if (count > limit) {
                    triggerBlock(prefs)
                }
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    private fun resetIfNewDay(prefs: android.content.SharedPreferences) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(PrefsKeys.KEY_DATE, "") != today) {
            prefs.edit()
                .putString(PrefsKeys.KEY_DATE, today)
                .putInt(PrefsKeys.KEY_COUNT, 0)
                .apply()
        }
    }

    private fun isShortsScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        return nodeContains(root, "reel_recycler") || nodeHasText(root, "Shorts", depth = 0)
    }

    private fun nodeContains(node: AccessibilityNodeInfo, idFragment: String, depth: Int = 0): Boolean {
        if (depth > 12) return false
        val viewId = node.viewIdResourceName
        if (viewId != null && viewId.contains(idFragment, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (nodeContains(child, idFragment, depth + 1)) return true
        }
        return false
    }

    private fun nodeHasText(node: AccessibilityNodeInfo, text: String, depth: Int): Boolean {
        if (depth > 8) return false
        val desc = node.contentDescription?.toString()
        if (desc != null && desc.equals(text, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (nodeHasText(child, text, depth + 1)) return true
        }
        return false
    }

    private fun exitYouTube() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun triggerBlock(prefs: android.content.SharedPreferences) {
        val until = System.currentTimeMillis() + PrefsKeys.BLOCK_DURATION_MS
        prefs.edit().putLong(PrefsKeys.KEY_BLOCKED_UNTIL, until).apply()

        exitYouTube()
        startVpnBlock()

        Toast.makeText(
            this,
            "Aaj ki Shorts limit poori! YouTube ka internet kuch der ke liye band.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startVpnBlock() {
        startService(Intent(this, ShortsVpnService::class.java))
    }

    private fun stopVpnBlock() {
        val intent = Intent(this, ShortsVpnService::class.java)
        intent.action = ShortsVpnService.ACTION_STOP
        startService(intent)
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
