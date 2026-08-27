package com.example.statebugs.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.statebugs.data.DevLog
import com.example.statebugs.domain.model.Post
import com.example.statebugs.domain.model.Todo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWhatsWrong: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val postsLoaded by viewModel.postsLoaded.collectAsStateWithLifecycle()
    val todosLoaded by viewModel.todosLoaded.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val globalUserCount by viewModel.globalUserCount.collectAsStateWithLifecycle()
    val globalPostCount by viewModel.globalPostCount.collectAsStateWithLifecycle()

    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sheetInComposition by rememberSaveable { mutableStateOf(false) }

    val externalState = viewModel.showComposeSheet

    LaunchedEffect(externalState) {
        if (externalState) {
            sheetInComposition = true
            DevLog.d("LaunchedEffect(showComposeSheet=true) -> sheet added to composition")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                actions = {
                    IconButton(onClick = { viewModel.retry() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = onOpenWhatsWrong) {
                        Icon(Icons.Default.BugReport, contentDescription = "What's wrong?")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onFabClicked() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New post")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    FullScreenLoader(
                        caption = if (postsLoaded) {
                            "waiting for /users/1/todos"
                        } else {
                            "waiting for /users/1/posts"
                        }
                    )
                } else {
                    HomeContent(
                        userName = userName,
                        posts = posts,
                        todos = todos,
                        postsLoaded = postsLoaded,
                        todosLoaded = todosLoaded,
                        globalUserCount = globalUserCount,
                        globalPostCount = globalPostCount,
                        errorMessage = errorMessage,
                        onRetry = viewModel::retry
                    )
                }
            }
        }
    }

    if (sheetInComposition) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                sheetInComposition = false
                DevLog.d(
                    "sheet onDismissRequest -> removed from composition; " +
                        "showComposeSheet is STILL ${viewModel.showComposeSheet}, " +
                        "SheetState = ${sheetState.currentValue.name}"
                )
                DevLog.d("  !! DESYNC: external state says VISIBLE, SheetState says HIDDEN")
            }
        ) {
            NewPostSheetContent(
                externalState = externalState,
                sheetCurrentValue = sheetState.currentValue.name,
            )
        }
    }
}

@Composable
private fun HomeContent(
    userName: String?,
    posts: List<Post>,
    todos: List<Todo>,
    postsLoaded: Boolean,
    todosLoaded: Boolean,
    globalUserCount: Int?,
    globalPostCount: Int?,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GreetingHeader(userName = userName) }

        item {
            SummaryCardRow(
                postCount = posts.size,
                todoCount = todos.size,
                thirdLabel = "Users",
                thirdValue = globalUserCount?.toString() ?: "…"
            )
        }

        if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "Request failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
        }

        item {
            val feedSize = globalPostCount?.toString() ?: "…"
            SectionHeader(
                title = "Posts",
                trailing = if (postsLoaded) {
                    "${posts.size} of $feedSize in /posts"
                } else {
                    "not loaded"
                }
            )
        }

        if (posts.isEmpty()) {
            item { EmptySectionNote("GET /users/1/posts has not resolved yet") }
        } else {
            items(items = posts, key = { "post-" + it.id }) { post -> PostCard(post) }
        }

        item {
            Column {
                Spacer(Modifier.height(4.dp))
                SectionHeader(
                    title = "Todos",
                    trailing = if (todosLoaded) "${todos.size} loaded" else "not loaded"
                )
            }
        }

        if (todos.isEmpty()) {
            item { EmptySectionNote("GET /users/1/todos has not resolved yet") }
        } else {
            items(items = todos.take(6), key = { "todo-" + it.id }) { todo -> TodoRow(todo) }
        }
    }
}

@Composable
private fun EmptySectionNote(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewPostSheetContent(
    externalState: Boolean,
    sheetCurrentValue: String,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Text(
            text = "New post",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Body") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}
