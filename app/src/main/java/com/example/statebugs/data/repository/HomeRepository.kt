package com.example.statebugs.data.repository

import android.util.Log
import com.example.statebugs.data.DebugSettings
import com.example.statebugs.data.remote.JsonPlaceholderApi
import com.example.statebugs.data.remote.NetworkModule
import com.example.statebugs.data.remote.dto.toDomain
import com.example.statebugs.domain.model.Post
import com.example.statebugs.domain.model.Todo
import com.example.statebugs.domain.model.User
import kotlinx.coroutines.delay

/**
 * Repository layer. Talks to Retrofit, maps DTOs to domain models, and knows nothing
 * about Compose or loading flags.
 */
class HomeRepository(
    private val api: JsonPlaceholderApi = NetworkModule.api
) {

    /** API A — GET /users/{userId}/posts */
    suspend fun getUserPosts(userId: Int = DEFAULT_USER_ID): List<Post> {
        simulateLatency(DebugSettings.postsDelayMs.value, "posts")
        return api.getUserPosts(userId).map { it.toDomain() }
    }

    /** API B — GET /users/{userId}/todos */
    suspend fun getUserTodos(userId: Int = DEFAULT_USER_ID): List<Todo> {
        simulateLatency(DebugSettings.todosDelayMs.value, "todos")
        return api.getUserTodos(userId).map { it.toDomain() }
    }

    /** GET /users — used for the header greeting. */
    suspend fun getUsers(): List<User> = api.getUsers().map { it.toDomain() }

    /** GET /posts — used for the global "feed size" summary card. */
    suspend fun getAllPosts(): List<Post> = api.getPosts().map { it.toDomain() }

    /**
     * ── DEVELOPMENT / TESTING DELAY ─────────────────────────────────────────────
     * Artificial latency, configurable from the in-app Network Simulation panel.
     * Real networks are fast enough that the loading jitter in [HomeViewModel] would
     * flicker past unnoticed. Delete this call to get honest timings.
     * ────────────────────────────────────────────────────────────────────────────
     */
    private suspend fun simulateLatency(ms: Long, label: String) {
        if (ms <= 0L) return
        Log.d(TAG, "DEV DELAY: sleeping ${ms}ms before $label request")
        delay(ms)
    }

    companion object {
        const val DEFAULT_USER_ID = 1
        private const val TAG = "HOME"
    }
}
