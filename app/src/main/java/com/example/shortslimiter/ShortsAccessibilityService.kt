package com.example.shortslimiter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
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
    private val debounceMs = 800L

    private var wasOnShorts = false
    private var vpnIsActive = false
    private var lastRedirectAt = 0L
    private val redirectCooldownMs = 1500L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return

        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetIfNewDay(prefs)

        val onShorts = isShortsScreen()
        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limitReached = count > limit

        if (onShorts) {
            wasOnShorts = true

            if (!limitReached) {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
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
                    else -> {}
                }
            } else {
                blockShortsNow(prefs)
            }
        } else {
            if (wasOnShorts && vpnIsActive) {
                stopVpnBlock()
            }
            wasOnShorts = false
        }
    }

    override fun onInterrupt() {}

    private fun blockShortsNow(prefs: SharedPreferences) {
        if (!vpnIsActive) {
            startVpnBlock()
            Toast.makeText(
                this,
                "Aaj ki Shorts limit poori! Shorts ka internet band kiya gaya.",
                Toast.LENGTH_SHORT
            ).show()
        }

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

    /**
     * YouTube ke andar "Home" tab par bhejta hai.
     * Step 1: "Home" label wala node dhoondho, aur agar wo khud clickable
     *         nahi hai to uske parents me se sabse pehla clickable wala dhoondo.
     * Step 2: Agar kuch na mile to 2-3 baar back press karke Shorts se bahar nikalo.
     */
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
        vpnIsActive = true
        startService(Intent(this, ShortsVpnService::class.java))
    }

    private fun stopVpnBlock() {
        vpnIsActive = false
        val intent = Intent(this, ShortsVpnService::class.java)
        intent.action = ShortsVpnService.ACTION_STOP
        startService(intent)
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
                     
        
