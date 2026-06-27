package com.example.shortslimiter

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)

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
                .putLong(PrefsKeys.KEY_BLOCKED_UNTIL, 0)
                .apply()
            updateStatus()
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
        val blockedUntil = prefs.getLong(PrefsKeys.KEY_BLOCKED_UNTIL, 0)
        val now = System.currentTimeMillis()

        statusText.text = if (now < blockedUntil) {
            val minsLeft = (blockedUntil - now) / 60000 + 1
            "Aaj ki limit poori ho gayi.\nYouTube Shorts $minsLeft minute me firse milega."
        } else {
            "Aaj dekhe gaye Shorts: $count / $limit"
        }
    }

    companion object {
        const val VPN_REQUEST_CODE = 100
    }
}
