package com.example.statebugs.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.statebugs.data.DebugSettings
import com.example.statebugs.data.DevLog
import com.example.statebugs.data.repository.HomeRepository
import com.example.statebugs.domain.model.Post
import com.example.statebugs.domain.model.Todo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository = HomeRepository()
) : ViewModel() {

    /** The single global loader that BOTH requests fight over. This is the jitter. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()

    private val _postsLoaded = MutableStateFlow(false)
    val postsLoaded: StateFlow<Boolean> = _postsLoaded.asStateFlow()

    private val _todosLoaded = MutableStateFlow(false)
    val todosLoaded: StateFlow<Boolean> = _todosLoaded.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _globalPostCount = MutableStateFlow<Int?>(null)
    val globalPostCount: StateFlow<Int?> = _globalPostCount.asStateFlow()

    private val _globalUserCount = MutableStateFlow<Int?>(null)
    val globalUserCount: StateFlow<Int?> = _globalUserCount.asStateFlow()

    var showComposeSheet by mutableStateOf(false)

    fun onFabClicked() {
        showComposeSheet = true
    }

    private var loadJob: Job? = null

    init {
        loadHome()
        loadHeaderMetadata()
    }

    fun retry() = loadHome()

    private fun loadHome() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {

            _errorMessage.value = null
            _postsLoaded.value = false
            _todosLoaded.value = false
            _posts.value = emptyList()
            _todos.value = emptyList()

            _isLoading.value = true

            val postsResult = runCatching { repository.getUserPosts() }
            if (postsResult.isFailure) {
                postsResult.exceptionOrNull()?.rethrowIfCancellation()
                _isLoading.value = false
                _errorMessage.value = postsResult.exceptionOrNull()?.friendlyMessage()
                DevLog.d("posts request FAILED: ${_errorMessage.value}")
                return@launch
            }

            _posts.value = postsResult.getOrDefault(emptyList())
            _postsLoaded.value = true

            _isLoading.value = false

            delay(100) //This is intentionally added to mimic UI lag

            _isLoading.value = true
            val todosResult = runCatching { repository.getUserTodos() }
            if (todosResult.isFailure) {
                todosResult.exceptionOrNull()?.rethrowIfCancellation()
                _isLoading.value = false
                _errorMessage.value = todosResult.exceptionOrNull()?.friendlyMessage()
                return@launch
            }

            _todos.value = todosResult.getOrDefault(emptyList())
            _todosLoaded.value = true

            _isLoading.value = false
        }
    }

    private fun loadHeaderMetadata() {
        viewModelScope.launch {
            runCatching { repository.getUsers() }
                .onSuccess { users ->
                    _globalUserCount.value = users.size
                    _userName.value = users.firstOrNull { it.id == HomeRepository.DEFAULT_USER_ID }?.name
                }
            runCatching { repository.getAllPosts() }
                .onSuccess { _globalPostCount.value = it.size }
        }
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }

    private fun Throwable.friendlyMessage(): String =
        when (this) {
            is java.net.UnknownHostException -> "No network / DNS failure: $message"
            else -> this::class.java.simpleName + ": " + (message ?: "unknown error")
        }
}
