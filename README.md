# Compose State Bugs

A deliberately broken Jetpack Compose app. Its only purpose is to **reproduce two of the
most common state-management mistakes in Compose codebases** in a form you can open,
poke at, and watch fail on a real device.

Both bugs are present on purpose. **Nothing here is fixed.** Every intentional mistake is
marked with an `INTENTIONAL BUG` comment at the exact line where it happens, and the fixes
are described (not implemented) at the bottom of this file — implementing them is the
exercise.

---

## 1. Project overview

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| State | `ViewModel` + `StateFlow` + `mutableStateOf` |
| Async | Kotlin Coroutines |
| Network | Retrofit 2 + OkHttp 4 + kotlinx.serialization |
| Navigation | Navigation Compose (2 destinations) |
| API | [JSONPlaceholder](https://jsonplaceholder.typicode.com) — public, no auth |
| min / target SDK | 24 / 35 |

### Screens

**Home** — a realistic dashboard: "Good morning" + user name + avatar, three summary
cards, a Posts list, a Todos list, a `+` FAB that opens a `ModalBottomSheet`, and an
always-visible debug strip showing the live sheet state.

**What's wrong?** (bug icon, top-right of Home) — the Network Simulation panel, an in-app
write-up of both bugs, and a live event log that mirrors Logcat.

### Build

Open the project folder in Android Studio (Ladybug or newer) and let it sync — that is
the shortest path, and Studio will generate the Gradle wrapper for you.

The wrapper JAR is a binary and is not checked in, so `./gradlew` does not exist yet. If
you prefer the command line, create it once with a local Gradle install:

```
gradle wrapper --gradle-version 8.9
./gradlew :app:installDebug
```

---

## 2. Bug #1 — Bottom Sheet State Desynchronization

**Where:** `ui/home/HomeScreen.kt`, plus the external flag in `ui/home/HomeViewModel.kt`.

Three different things claim to know whether the sheet is open:

| Source of truth | Owner | Written by |
|---|---|---|
| `viewModel.showComposeSheet` | `HomeViewModel` (`mutableStateOf`) | the FAB |
| `sheetInComposition` | `HomeScreen` (`rememberSaveable`) | `onDismissRequest` |
| `sheetState.currentValue` | Compose `SheetState` | Compose itself |

The FAB writes to #1. Dismissal clears #2. They are never reconciled, so they drift.

```kotlin
// HomeViewModel
var showComposeSheet by mutableStateOf(false)

fun onFabClicked() {
    showComposeSheet = true          // <-- unconditional assignment
}

// HomeScreen
LaunchedEffect(externalState) {      // <-- keyed on a flag that is never reset
    if (externalState) sheetInComposition = true
}

if (sheetInComposition) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
            sheetInComposition = false   // INTENTIONAL BUG: clears the mirror,
                                         // never the flag the FAB reads
        }
    ) { /* ... */ }
}
```

### State machine as shipped

```
showComposeSheet = false
        ↓  FAB
showComposeSheet = true      →  LaunchedEffect key changed  →  sheet visible
        ↓  Android Back
sheet removed from composition
SheetState = Hidden
showComposeSheet = true      ←  STILL TRUE
        ↓  FAB
showComposeSheet = true → true
        ↓
LaunchedEffect key UNCHANGED  →  effect never re-runs  →  nothing happens
```

---

## 3. Bug #2 — Sequential API Calls + Global Loading Jitter

**Where:** `HomeViewModel.loadHome()`.

The Home screen needs **both** responses before it is meaningfully loaded:

* **API A** — `GET /users/1/posts`
* **API B** — `GET /users/1/todos`

They do not depend on each other, but they are awaited one after the other, and each one
independently drives the same screen-wide `isLoading` flag:

```kotlin
_isLoading.value = true            // loader covers the whole screen
val posts = repository.getUserPosts()
_posts.value = posts
_isLoading.value = false           // INTENTIONAL BUG: premature, todos still missing

_isLoading.value = true            // INTENTIONAL BUG: loader flashes a second time
val todos = repository.getUserTodos()
_todos.value = todos
_isLoading.value = false
```

There is no `async { }` / `await()` anywhere in the load path — that absence *is* the bug.

The ViewModel exposes **six** independently-mutable flows (`isLoading`, `posts`, `todos`,
`postsLoaded`, `todosLoaded`, `errorMessage`) instead of one screen state, so the UI is
forced to render combinations that correspond to no real moment in the load.

### What you see

```
┌───────────────────────┐
│       LOADING         │   isLoading = true, waiting on /posts
└───────────────────────┘
          ↓
┌───────────────────────┐
│ Header                │
│ Posts        ✓        │   isLoading = false — screen looks "done"
│ Todos      (empty)    │   …but half the data is still in flight
└───────────────────────┘
          ↓
┌───────────────────────┐
│       LOADING         │   isLoading = true again — the jitter
└───────────────────────┘
          ↓
┌───────────────────────┐
│ Header                │
│ Posts        ✓        │
│ Todos        ✓        │   complete
└───────────────────────┘
```

Total time is `postsDelay + todosDelay + gap`. A concurrent implementation would take
`max(postsDelay, todosDelay)`. With the defaults that is **4400 ms instead of 2000 ms**.

---

## 4. How to reproduce

### Bug #1

1. Launch the app, wait for Home to finish loading.
2. Press the **`+` FAB**. The bottom sheet opens.
   Debug strip: `External state: true` · `Sheet state: Expanded` · `In composition: true`.
3. Press the **Android Back button**. The sheet disappears.
4. The debug strip turns red:
   ```
   External state: true
   Sheet state:    Hidden
   In composition: false
   ```
5. Press the **FAB** again. **Nothing happens.** The log records
   `true -> true : no state change, Compose has nothing to react to`.
6. To recover, tap **"Force showComposeSheet = false"** on the debug strip (or use
   **"Close (clears flag)"** inside the sheet next time — that button is the only path
   that clears the external flag, which is why it *does* allow reopening).

### Bug #2

1. Open **What's wrong?** and confirm the defaults: posts `1500 ms`, todos `2000 ms`,
   gap `900 ms`, Sequential API mode `ON`.
2. Go back to Home and tap the **Reload** icon in the top bar.
3. Watch: full-screen `LOADING` → Home with posts but an empty Todos section →
   full-screen `LOADING` again → complete Home.
4. Re-open **What's wrong?** and read the live event log for the exact interleaving.
5. Set the **inter-request gap to 0 ms** and reload: the partial frame stops being drawn.
   The bug is still there — it has just become invisible. That is why it survives code
   review on fast networks.

---

## 5. Why the bugs happen

### Bug #1 — two sources of truth, and an idempotent write

Compose recomposes on *state change*, not on *assignment*. `showComposeSheet = true` when
the value is already `true` is not a change: the snapshot system sees no write, no
recomposition is scheduled, and `LaunchedEffect(externalState)` keeps the same key so it
never restarts. The sheet's real visibility lives in `SheetState`, which Compose moved to
`Hidden` all by itself during the Back gesture — and nothing propagated that fact back to
the flag the FAB reads.

The general rule being violated: **the state that opens a piece of UI must be the exact
same state that closes it.** A mirror of that state is a second source of truth, and two
sources of truth for one visual fact will always drift.

### Bug #2 — per-request loading flags instead of screen-level state

`isLoading` is a *screen-level* concept: "is the Home screen ready?" But it is written by
*request-level* code, twice, by two callers that know nothing about each other. Neither
one can answer the screen-level question, so each answers a narrower one and the screen
believes it.

Two compounding mistakes:

1. **Serialising independent I/O.** Nothing in the todos request depends on the posts
   response, so awaiting them in order just adds their latencies together.
2. **Modelling one screen with many booleans.** With six independent flows there are
   2⁶ representable combinations and only three legal ones. The illegal states are not
   theoretical — the app renders one of them every single load.

---

## 6. How the fixed implementation would solve them

> Not implemented in this build, on purpose.

### Fix for Bug #1 — one source of truth

```kotlin
// HomeViewModel
var showComposeSheet by mutableStateOf(false)

// HomeScreen — delete `sheetInComposition` entirely.
if (viewModel.showComposeSheet) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { viewModel.showComposeSheet = false }
    ) { /* ... */ }
}
```

`onDismissRequest` fires for every dismissal path — Back, scrim tap, swipe-down — so the
flag always ends up `false` and the sheet always leaves composition when hidden. Never
leave the external state `true` while `SheetState` is `Hidden`.

Resulting lifecycle:

```
false → true → sheet visible → Back → false → FAB → true → sheet visible again
```

### Fix for Bug #2 — structured concurrency + one sealed state

```kotlin
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val data: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

fun load() = viewModelScope.launch {
    _uiState.value = HomeUiState.Loading
    _uiState.value = try {
        coroutineScope {
            val posts = async { repository.getUserPosts() }
            val todos = async { repository.getUserTodos() }
            HomeUiState.Success(
                HomeData(posts = posts.await(), todos = todos.await())
            )
        }
    } catch (e: Exception) {
        HomeUiState.Error(e.message ?: "Unknown error")
    }
}
```

* Both requests start immediately; the screen takes `max(a, b)`, not `a + b`.
* Exactly **one** state publication after both complete — no intermediate frame exists to
  render, so `Loading → Partial → Loading → Success` becomes unrepresentable.
* `coroutineScope` gives structured concurrency: if one request fails, the other is
  cancelled and the screen goes to a single `Error` state rather than showing half a
  dashboard next to an error card.
* Delete `isLoading`, `postsLoaded`, `todosLoaded` and `errorMessage`. The point of the
  sealed interface is that those flags stop being expressible.

`HomeData` and `HomeUiState` already exist in `domain/model/Models.kt`, deliberately
unused, as the target shape to build towards.

---

## 7. Architecture

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Compose)                                                 │
│   HomeScreen.kt        ← BUG 1 lives here                    │
│   WhatsWrongScreen.kt     debug panel + explanations         │
└──────────────────────────────┬───────────────────────────────┘
                               │ StateFlow / mutableStateOf
┌──────────────────────────────┴───────────────────────────────┐
│ ViewModel                                                    │
│   HomeViewModel.kt     ← BUG 2 lives here                    │
│                          owns ALL screen state               │
└──────────────────────────────┬───────────────────────────────┘
                               │ suspend fun
┌──────────────────────────────┴───────────────────────────────┐
│ Repository                                                   │
│   HomeRepository.kt      DTO → domain mapping, dev latency   │
└──────────────────────────────┬───────────────────────────────┘
                               │ Retrofit
┌──────────────────────────────┴───────────────────────────────┐
│ Network                                                      │
│   JsonPlaceholderApi.kt  NetworkModule.kt (OkHttp + kotlinx) │
└──────────────────────────────────────────────────────────────┘
```

Note that the repository and network layers are **clean**. Both bugs are orchestration
mistakes in the layers above them — which is exactly why this class of bug is so common:
the data layer looks fine in review.

### Source layout

```
app/src/main/java/com/example/statebugs/
├── MainActivity.kt
├── data/
│   ├── DebugSettings.kt          dev-only latency + mode knobs
│   ├── DevLog.kt                 Logcat mirror for the in-app log
│   ├── remote/
│   │   ├── JsonPlaceholderApi.kt
│   │   ├── NetworkModule.kt
│   │   └── dto/Dtos.kt
│   └── repository/HomeRepository.kt
├── domain/model/Models.kt        Post, Todo, User, HomeData, (target) shape
└── ui/
    ├── debug/WhatsWrongScreen.kt
    ├── home/{HomeScreen, HomeViewModel, HomeComponents}.kt
    ├── navigation/AppNavHost.kt
    └── theme/Theme.kt
```

---

## 8. API endpoints used

Base URL: `https://jsonplaceholder.typicode.com/`

> The original brief listed `https://api.jsonplaceholder.typicode.com` — that host does
> not exist and every request would die with `UnknownHostException`. The canonical host
> has no `api.` prefix.

| Endpoint | Used for | Part of the bug demo? |
|---|---|---|
| `GET /users/1/posts` | Posts section (**API A**) | yes — first sequential call |
| `GET /users/1/todos` | Todos section (**API B**) | yes — second sequential call |
| `GET /users` | header greeting + "Users" summary card | no, loaded off the critical path |
| `GET /posts` | "N of 100 in /posts" in the Posts section header | no, loaded off the critical path |

All four are real HTTP requests. No hardcoded sample data is used anywhere in the
API-driven sections of the UI.

---

## 9. How to change network delays

**At runtime (no rebuild):** Home → bug icon → **Network Simulation**.

| Slider | Range | Default |
|---|---|---|
| Posts delay | 0–5000 ms | 1500 ms |
| Todos delay | 0–5000 ms | 2000 ms |
| Inter-request gap | 0–5000 ms | 900 ms |

**Defaults in code:** `data/DebugSettings.kt`

```kotlin
const val DEFAULT_POSTS_DELAY_MS = 1_500L
const val DEFAULT_TODOS_DELAY_MS = 2_000L
const val DEFAULT_INTER_REQUEST_GAP_MS = 900L
```

The delay itself is applied in `HomeRepository.simulateLatency()`, clearly fenced off as
a development-only `delay()`. Set every slider to 0 to get honest network timings.

**About the inter-request gap:** it is a *development aid*, not part of the bug. Without
it, `isLoading = false` and the following `isLoading = true` land in the same main-thread
turn, Compose never gets a frame between them, and the half-loaded screen is never
actually drawn. The gap holds that frame long enough to see. Setting it to 0 is itself
instructive: the bug is fully intact, just invisible.

---

## 10. Expected Logcat output

Filter on tag `HOME`. Every line is also mirrored into the in-app live event log with a
relative timestamp.

### Buggy mode — as shipped (sequential)

```
HOME: ──── reload ────
HOME: load started (sequential mode = true)
HOME: global loading: false -> true
HOME: posts request started
HOME: DEV DELAY: sleeping 1500ms before posts request
HOME: posts request completed (10 items)
HOME: global loading: true -> false  <-- PREMATURE, todos not loaded yet
HOME: UI state = partial (posts only)
HOME: global loading: false -> true  <-- JITTER, loader shown a 2nd time
HOME: todos request started
HOME: DEV DELAY: sleeping 2000ms before todos request
HOME: todos request completed (20 items)
HOME: global loading: true -> false
HOME: UI state = complete (assembled from 2 independent flows)
```

Note the shape: `posts started → posts completed → todos started → todos completed`, with
the loading flag flipping four times. Wall clock ≈ 4400 ms.

### Bug #1 trace

```
HOME: FAB clicked -> showComposeSheet: false -> true
HOME: LaunchedEffect(showComposeSheet=true) -> sheet added to composition
   ... user presses Android Back ...
HOME: sheet onDismissRequest -> removed from composition; showComposeSheet is STILL true, SheetState = Hidden
HOME:   !! DESYNC: external state says VISIBLE, SheetState says HIDDEN
   ... user presses the FAB again ...
HOME: FAB clicked -> showComposeSheet: true -> true
HOME:   !! true -> true : no state change, Compose has nothing to react to
```

### What a fixed (concurrent) implementation would print

```
HOME: load started
HOME: posts request started
HOME: todos request started      <-- interleaved, not queued behind posts
HOME: posts request completed
HOME: todos request completed
HOME: UI state = Success
```

Two request-started lines before any request-completed line, one state publication, and
wall clock ≈ 2000 ms instead of 4400 ms. If your Logcat does not look like this, the
requests are still sequential.

---

## Exercises

1. Fix Bug #1 by deleting `sheetInComposition` and moving the dismissal to
   `viewModel.showComposeSheet = false`. Verify the sheet reopens after Back, repeatedly.
2. Fix Bug #2 with `coroutineScope { async { } }` and a single
   `StateFlow<HomeUiState>`. Verify Logcat interleaves the two requests.
3. Make the illegal states unrepresentable: delete `isLoading`, `postsLoaded`,
   `todosLoaded` and `errorMessage`, and make the code fail to compile until the UI reads
   only `uiState`.
4. Kill your Wi-Fi mid-load and observe how the buggy version renders posts next to an
   error card. Then confirm the fixed version cannot.
