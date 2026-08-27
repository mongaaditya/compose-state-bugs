package com.example.statebugs.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugSettings {

    /** Simulated latency added to GET /users/{id}/posts, in milliseconds. */
    private val _postsDelayMs = MutableStateFlow(DEFAULT_POSTS_DELAY_MS)
    val postsDelayMs: StateFlow<Long> = _postsDelayMs.asStateFlow()

    /** Simulated latency added to GET /users/{id}/todos, in milliseconds. */
    private val _todosDelayMs = MutableStateFlow(DEFAULT_TODOS_DELAY_MS)
    val todosDelayMs: StateFlow<Long> = _todosDelayMs.asStateFlow()


    const val DEFAULT_POSTS_DELAY_MS = 1_500L
    const val DEFAULT_TODOS_DELAY_MS = 2_000L
}
