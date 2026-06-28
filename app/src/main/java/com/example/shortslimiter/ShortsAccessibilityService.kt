package com.example.shortslimiter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShortsAccessibilityService : AccessibilityService() {

    private var lastCountedAt = 0L
    private val debounceMs = 1000L

    private var wasOnShorts = false
    private var lastRedirectAt = 0L
    private val redirectCooldownMs = 1500L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        logRawEvent(prefs, event)

        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return

        resetIfNewDay(prefs)
        captureDebugInfo(prefs)

        val onShorts = isShortsScreen()
        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limitReached = count > limit

        if (onShorts) {
            wasOnShorts = true

            if (!limitReached) {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    val now = System.currentTimeMillis()
                    if (now - lastCountedAt >= debounceMs) {
                        lastCountedAt = now
                        val newCount = count + 1
                        prefs.edit().putInt(PrefsKeys.KEY_COUNT, newCount).apply()

                        if (newCount > limit) {
                            blockShortsNow(prefs)
                        }
                    }
                }
            } else {
                blockShortsNow(prefs)
            }
        } else {
            if (wasOnShorts) {
                stopVpnBlock()
            }
            wasOnShorts = false
        }
    }

    override fun onInterrupt() {}

    // ---------- DEBUG: raw event log (sab apps ke events, koi filter nahi) ----------
    private fun logRawEvent(prefs: SharedPreferences, event: AccessibilityEvent) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val pkg = event.packageName?.toString() ?: "null"
        val typeName = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
            else -> "OTHER(${event.eventType})"
        }
        val line = "$time | $pkg | $typeName"

        val existing = prefs.getString("event_log", "") ?: ""
        val lines = existing.split("\n").filter { it.isNotBlank() }.toMutableList()
        lines.add(line)
        while (lines.size > 15) lines.removeAt(0)
        prefs.edit().putString("event_log", lines.joinToString("\n")).apply()
    }

    // ---------- DEBUG: ID list, sirf tab save hoga jab root genuinely YouTube ka ho ----------
    private fun captureDebugInfo(prefs: SharedPreferences) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != YOUTUBE_PACKAGE) return // race-condition guard

        val ids = LinkedHashSet<String>()
        collectIds(root, ids, 0)
        prefs.edit().putString("debug_ids", ids.take(60).joinToString("\n")).apply()
    }

    private fun collectIds(node: AccessibilityNodeInfo, ids: MutableSet<String>, depth: Int) {
        if (depth > 18) return
        node.viewIdResourceName?.let { ids.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectIds(child, ids, depth + 1)
        }
    }

    private fun blockShortsNow(prefs: SharedPreferences) {
        startVpnBlock()
        val now = System.currentTimeMillis()
        if (now - lastRedirectAt > redirectCooldownMs) {
            lastRedirectAt = now
            goToYouTubeHomeTab()
        }
    }

    private fun resetIfNewDay(prefs: SharedPreferences) {
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
        if (root.packageName?.toString() != YOUTUBE_PACKAGE) return false
        return nodeContains(root, "reel")
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

    private fun goToYouTubeHomeTab() {
        val root = rootInActiveWindow
        val homeNode = root?.let { findClickableAncestorByLabel(it, "Home") }

        if (homeNode != null && homeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return
        }
        backOutOfShorts(attemptsLeft = 3)
    }

    private fun backOutOfShorts(attemptsLeft: Int) {
        if (attemptsLeft <= 0) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({
            if (isShortsScreen()) {
                backOutOfShorts(attemptsLeft - 1)
            }
        }, 350)
    }

    private fun findClickableAncestorByLabel(
        root: AccessibilityNodeInfo,
        label: String
    ): AccessibilityNodeInfo? {
        val match = findNodeByLabel(root, label, depth = 0) ?: return null
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
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByLabel(child, label, depth + 1)
            if (found != null) return found
        }
        return null
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
