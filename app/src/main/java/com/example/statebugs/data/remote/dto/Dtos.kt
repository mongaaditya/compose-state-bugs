package com.example.statebugs.data.remote.dto

import com.example.statebugs.domain.model.Post
import com.example.statebugs.domain.model.Todo
import com.example.statebugs.domain.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostDto(
    @SerialName("id") val id: Int,
    @SerialName("userId") val userId: Int,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String
)

@Serializable
data class TodoDto(
    @SerialName("id") val id: Int,
    @SerialName("userId") val userId: Int,
    @SerialName("title") val title: String,
    @SerialName("completed") val completed: Boolean
)

@Serializable
data class UserDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("username") val username: String,
    @SerialName("email") val email: String
)

fun PostDto.toDomain() = Post(id = id, userId = userId, title = title, body = body)

fun TodoDto.toDomain() = Todo(id = id, userId = userId, title = title, completed = completed)

fun UserDto.toDomain() = User(id = id, name = name, username = username, email = email)
