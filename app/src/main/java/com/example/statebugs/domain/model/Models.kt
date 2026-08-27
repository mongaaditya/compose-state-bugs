package com.example.statebugs.domain.model

/**
 * Plain domain models. Everything the UI renders comes from a real HTTP response —
 * there is no hardcoded sample data anywhere in the API-driven sections.
 */
data class Post(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String
)

data class Todo(
    val id: Int,
    val userId: Int,
    val title: String,
    val completed: Boolean
)

data class User(
    val id: Int,
    val name: String,
    val username: String,
    val email: String
)

/**
 * The single screen-level state a *correctly* architected Home screen would expose:
 *
 *     val uiState: StateFlow<HomeUiState>
 *
 * INTENTIONALLY UNUSED. It ships here as the target shape for the exercise in README.md.
 * The buggy [com.example.statebugs.ui.home.HomeViewModel] instead exposes six independent
 * flows, which is what makes "loader on top of already-loaded posts" representable at all.
 */
sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Success(
        val data: HomeData
    ) : HomeUiState

    data class Error(
        val message: String
    ) : HomeUiState
}

/**
 * The shape a *correctly* architected Home screen would publish in one atomic update.
 *
 * NOTE: the buggy implementation deliberately does NOT use this. It leaks posts and
 * todos to the UI as separate, independently-updating flows. This type is kept here so
 * the intended target design is visible in the codebase.
 */
data class HomeData(
    val posts: List<Post>,
    val todos: List<Todo>
)
