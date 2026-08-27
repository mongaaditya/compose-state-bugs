package com.example.statebugs.data.remote

import com.example.statebugs.data.remote.dto.PostDto
import com.example.statebugs.data.remote.dto.TodoDto
import com.example.statebugs.data.remote.dto.UserDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * JSONPlaceholder — a public, no-auth fake REST API.
 *
 * Base URL: https://jsonplaceholder.typicode.com/
 *
 * (The original spec listed `api.jsonplaceholder.typicode.com`, which is not a real
 * host and fails DNS resolution. The canonical host has no `api.` prefix.)
 */
interface JsonPlaceholderApi {

    /** GET /users */
    @GET("users")
    suspend fun getUsers(): List<UserDto>

    /** GET /posts */
    @GET("posts")
    suspend fun getPosts(): List<PostDto>

    /** GET /users/{id}/posts — "API A" in the loading-jitter bug. */
    @GET("users/{userId}/posts")
    suspend fun getUserPosts(@Path("userId") userId: Int): List<PostDto>

    /** GET /users/{id}/todos — "API B" in the loading-jitter bug. */
    @GET("users/{userId}/todos")
    suspend fun getUserTodos(@Path("userId") userId: Int): List<TodoDto>
}
