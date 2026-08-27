package com.example.statebugs.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One line in the in-app developer log. */
data class DevLogLine(
    val elapsedMs: Long,
    val message: String
)

/**
 * DEVELOPMENT ONLY.
 *
 * Mirrors every Logcat line into an in-app buffer so the "What's wrong?" screen can show
 * the exact interleaving of requests and loading-flag flips without the reader needing a
 * laptop and `adb logcat` open.
 */
object DevLog {

    const val TAG = "HOME"

    private var startedAt = System.currentTimeMillis()

    private val _lines = MutableStateFlow<List<DevLogLine>>(emptyList())
    val lines: StateFlow<List<DevLogLine>> = _lines.asStateFlow()

    fun restart() {
        startedAt = System.currentTimeMillis()
        _lines.value = emptyList()
    }

    fun d(message: String) {
        Log.d(TAG, message)
        val line = DevLogLine(
            elapsedMs = System.currentTimeMillis() - startedAt,
            message = message
        )
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
    }

    private const val MAX_LINES = 60
}
