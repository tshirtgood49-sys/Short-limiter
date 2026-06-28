package com.example.shortslimiter

import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var editLimit: EditText
    private lateinit var resetUsesText: TextView
    private lateinit var quoteText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        editLimit = findViewById(R.id.editLimit)
        resetUsesText = findViewById(R.id.resetUsesText)
        quoteText = findViewById(R.id.quoteText)

        setupColorfulQuote()

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "List me 'Shorts Limiter' dhundo aur ON karo",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.btnVpnPermission).setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                Toast.makeText(this, "VPN permission already di hui hai", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            resetDailyUsesIfNewDay(prefs)

            if (!tryConsumeResetUse(prefs)) {
                Toast.makeText(
                    this,
                    "Aaj ke 4 reset/limit-set use ho gaye. Raat 12 baje phir milenge.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            prefs.edit().putInt(PrefsKeys.KEY_COUNT, 0).apply()
            updateStatus()
        }

        findViewById<Button>(R.id.btnSetLimit).setOnClickListener {
            val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            resetDailyUsesIfNewDay(prefs)

            val rawLimit = editLimit.text.toString().toIntOrNull()
            if (rawLimit == null || rawLimit <= 0) {
                Toast.makeText(this, "Sahi number daalo (1 ya usse zyada)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!tryConsumeResetUse(prefs)) {
                Toast.makeText(
                    this,
                    "Aaj ke 4 reset/limit-set use ho gaye. Raat 12 baje phir milenge.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val finalLimit = rawLimit.coerceAtMost(MAX_LIMIT)
            prefs.edit()
                .putInt(PrefsKeys.KEY_LIMIT, finalLimit)
                .putInt(PrefsKeys.KEY_COUNT, 0)
                .apply()

            val msg = if (rawLimit > MAX_LIMIT) {
                "Max limit $MAX_LIMIT hi allowed hai, isliye $MAX_LIMIT set kiya"
            } else {
                "Naya limit set hua: $finalLimit"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        findViewById<Button>(R.id.btnDebugIds).setOnClickListener {
            val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            val ids = prefs.getString(
                "debug_ids",
                "Koi data nahi mila.\nYouTube Shorts khol ke 2-3 baar scroll karo, fir yahan wapas aao."
            )
            android.app.AlertDialog.Builder(this)
                .setTitle("YouTube Screen ke IDs")
                .setMessage(ids)
                .setPositiveButton("OK", null)
                .show()
        }

        findViewById<Button>(R.id.btnEventLog).setOnClickListener {
            val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            val log = prefs.getString("event_log", "Koi event log nahi mila.")
            android.app.AlertDialog.Builder(this)
                .setTitle("Last 15 Accessibility Events")
                .setMessage(log)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        resetDailyUsesIfNewDay(prefs)
        updateStatus()
    }

    private fun updateStatus() {
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)
        val usesLeft = MAX_LIMIT_USES - prefs.getInt(PrefsKeys.KEY_RESET_USES_COUNT, 0)

        statusText.text = "Aaj dekhe gaye Shorts: $count / $limit"
        resetUsesText.text = "Aaj baki Reset/Set-Limit uses: ${usesLeft.coerceAtLeast(0)}/$MAX_LIMIT_USES"
        editLimit.setText(limit.toString())
    }

    /** Raat 12 baje (naya date) hote hi reset/limit-set ka quota wapas 4 kar deta hai. */
    private fun resetDailyUsesIfNewDay(prefs: SharedPreferences) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(PrefsKeys.KEY_RESET_USES_DATE, "") != today) {
            prefs.edit()
                .putString(PrefsKeys.KEY_RESET_USES_DATE, today)
                .putInt(PrefsKeys.KEY_RESET_USES_COUNT, 0)
                .apply()
        }
    }

    /** Quota mein se ek use consume karta hai. False return kare to quota khatam hai. */
    private fun tryConsumeResetUse(prefs: SharedPreferences): Boolean {
        val used = prefs.getInt(PrefsKeys.KEY_RESET_USES_COUNT, 0)
        if (used >= MAX_LIMIT_USES) return false
        prefs.edit().putInt(PrefsKeys.KEY_RESET_USES_COUNT, used + 1).apply()
        return true
    }

    private fun setupColorfulQuote() {
        val quote = "ये टाइम रोने और अफ़सोस मनाने पर भी नहीं लौटेगा"
        val colors = listOf(
            0xFFFFEB3B.toInt(), // yellow
            0xFFFF5252.toInt(), // red
            0xFF40C4FF.toInt(), // sky blue
            0xFF69F0AE.toInt(), // green
            0xFFFF4081.toInt(), // pink
            0xFFFFFFFF.toInt(), // white
            0xFFFFC107.toInt()  // amber
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
        const val VPN_REQUEST_CODE = 100
        const val MAX_LIMIT = 20
        const val MAX_LIMIT_USES = 4
    }
}
