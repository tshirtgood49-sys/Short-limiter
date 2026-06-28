package com.example.shortslimiter

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var editLimit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        editLimit = findViewById(R.id.editLimit)

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
            prefs.edit()
                .putInt(PrefsKeys.KEY_COUNT, 0)
                .apply()
            updateStatus()
        }

        findViewById<Button>(R.id.btnSetLimit).setOnClickListener {
            val rawLimit = editLimit.text.toString().toIntOrNull()
            if (rawLimit == null || rawLimit <= 0) {
                Toast.makeText(this, "Sahi number daalo (1 ya usse zyada)", Toast.LENGTH_SHORT).show()
            } else {
                val finalLimit = rawLimit.coerceAtMost(MAX_LIMIT)
                val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
                prefs.edit()
                    .putInt(PrefsKeys.KEY_LIMIT, finalLimit)
                    .putInt(PrefsKeys.KEY_COUNT, 0)
                    .apply()

                val msg = if (rawLimit > MAX_LIMIT) {
                    "Max limit $MAX_LIMIT hi allowed hai, isliye $MAX_LIMIT set kiya (ginti reset hui)"
                } else {
                    "Naya limit set hua: $finalLimit (ginti bhi reset hui)"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                updateStatus()
            }
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
        updateStatus()
    }

    private fun updateStatus() {
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        val count = prefs.getInt(PrefsKeys.KEY_COUNT, 0)
        val limit = prefs.getInt(PrefsKeys.KEY_LIMIT, PrefsKeys.DEFAULT_LIMIT)

        statusText.text = "Aaj dekhe gaye Shorts: $count / $limit"
        editLimit.setText(limit.toString())
    }

    companion object {
        const val VPN_REQUEST_CODE = 100
        const val MAX_LIMIT = 20
    }
}
