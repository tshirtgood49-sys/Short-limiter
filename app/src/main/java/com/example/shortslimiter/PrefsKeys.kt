package com.example.shortslimiter

object PrefsKeys {
    const val PREFS_NAME = "shorts_prefs"
    const val KEY_COUNT = "shorts_count"
    const val KEY_DATE = "shorts_date"
    const val KEY_LIMIT = "shorts_limit"
    const val KEY_RESET_USES_COUNT = "reset_uses_count"
    const val KEY_RESET_USES_DATE = "reset_uses_date"
    const val KEY_BLOCKED_UNTIL = "blocked_until"

    const val DEFAULT_LIMIT = 5
    const val BLOCK_DURATION_MS = 30 * 60 * 1000L
}
