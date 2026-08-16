package com.example.shortslimiter

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resetUsesText: TextView
    private lateinit var quoteText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resetUsesText = findViewById(R.id.resetUsesText)
        quoteText = findViewById(R.id.quoteText)

        setupColorfulQuote()

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // Accessibility check — agar band hai to banner dikhao
        checkAccessibilityStatus()

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "List me 'Shorts Limiter' dhundo aur ON karo",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            resetDailyUsesIfNewDay(prefs)
            if (!tryConsumeResetUse(prefs)) {
                Toast.makeText(
                    this,
                    "Aaj ke 4 reset use ho gaye. Raat 12 baje phir milenge.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            prefs.edit().putInt(PrefsKeys.KEY_COUNT, 0).apply()
            updateStatus()
            Toast.makeText(this, "Ginti reset ho gayi ✅", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn5).setOnClickListener { setLimit(5) }
        findViewById<Button>(R.id.btn15).setOnClickListener { setLimit(15) }
        findViewById<Button>(R.id.btn25).setOnClickListener { setLimit(25) }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetDailyUsesIfNewDay(prefs)
        updateStatus()
        checkAccessibilityStatus()
    }

    private fun checkAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        val btn = findViewById<Button>(R.id.btnAccessibility)
        if (enabled) {
            btn.text = "✅ Accessibility ON hai"
        } else {
            btn.text = "⚠️ Accessibility OFF hai - Tap karo"
            Toast.makeText(
                this,
                "⚠️ Shorts Limiter band hai! Accessibility ON karo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService =
            "$packageName/${ShortsAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedService)
    }

    private fun setLimit(limit: Int) {
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetDailyUsesIfNewDay(prefs)
        if (!tryConsumeResetUse(prefs)) {
            Toast.makeText(
                this,
                "Aaj ke 4 set-limit use ho gaye. Raat 12 baje phir milenge.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        prefs.edit()
            .putInt(PrefsKeys.KEY_LIMIT, limit)
            .putInt(PrefsKeys.KEY_COUNT, 0)
            .apply()
        Toast.makeText(this, "Limit set: $limit Shorts/din ✅", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
        val usesLeft = MAX_LIMIT_USES -
                prefs.getInt(PrefsKeys.KEY_RESET_USES_COUNT, 0)
        statusText.text = "Aaj dekhe: $count / $limit Shorts"
        resetUsesText.text =
            "Reset/Set uses baki: ${usesLeft.coerceAtLeast(0)}/$MAX_LIMIT_USES"
    }

    private fun resetDailyUsesIfNewDay(prefs: SharedPreferences) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(PrefsKeys.KEY_RESET_USES_DATE, "") != today) {
            prefs.edit()
                .putString(PrefsKeys.KEY_RESET_USES_DATE, today)
                .putInt(PrefsKeys.KEY_RESET_USES_COUNT, 0)
                .apply()
        }
    }

    private fun tryConsumeResetUse(prefs: SharedPreferences): Boolean {
        val used = prefs.getInt(PrefsKeys.KEY_RESET_USES_COUNT, 0)
        if (used >= MAX_LIMIT_USES) return false
        prefs.edit().putInt(PrefsKeys.KEY_RESET_USES_COUNT, used + 1).apply()
        return true
    }

    private fun setupColorfulQuote() {
        val quote = "ये टाइम रोने और अफ़सोस मनाने पर भी नहीं लौटेगा"
        val colors = listOf(
            0xFFFFEB3B.toInt(),
            0xFFFF5252.toInt(),
            0xFF40C4FF.toInt(),
            0xFF69F0AE.toInt(),
            0xFFFF4081.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFC107.toInt()
        )
        val words = quote.split(" ")
        val builder = SpannableStringBuilder()
        words.forEachIndexed { index, word ->
            val start = builder.length
            builder.append(word)
            val end = builder.length
            builder.setSpan(
                ForegroundColorSpan(colors[index % colors.size]),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (index != words.lastIndex) builder.append(" ")
        }
        quoteText.text = builder
    }

    companion object {
        const val MAX_LIMIT_USES = 4
    }
}
