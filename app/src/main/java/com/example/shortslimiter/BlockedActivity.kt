package com.example.shortslimiter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.TextView

class BlockedActivity : Activity() {

    private val quotes = listOf(
        "ये टाइम रोने और अफ़सोस\nमनाने पर भी नहीं लौटेगा",
        "बस इतना ही! अब कुछ\nproductive करो 💪",
        "तुम्हारा वक्त कीमती है\nसही जगह लगाओ ⭐",
        "Shorts band, ab apna\nkaam shuru karo 🎯",
        "Chhod do Shorts, ab\nkuch naya seekho 📚"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val quoteView = findViewById<TextView>(R.id.blockedQuote)
        val iconView = findViewById<TextView>(R.id.blockedIcon)
        val timerView = findViewById<TextView>(R.id.blockedTimer)

        quoteView.text = quotes.random()

        val pulseAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        iconView.startAnimation(pulseAnim)

        var secondsLeft = 3
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                timerView.text = "$secondsLeft second mein redirect..."
                if (secondsLeft <= 0) {
                    goToYouTubeHome()
                    return
                }
                secondsLeft--
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    private fun goToYouTubeHome() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
        finish()
    }
}
