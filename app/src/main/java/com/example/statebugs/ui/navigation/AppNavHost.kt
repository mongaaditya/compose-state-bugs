package com.example.statebugs.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.statebugs.ui.home.HomeScreen
import com.example.statebugs.ui.home.HomeViewModel

object Routes {
    const val HOME = "home"
    const val WHATS_WRONG = "whats-wrong"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { entry ->
            val viewModel: HomeViewModel = viewModel(viewModelStoreOwner = entry)
            HomeScreen(
                viewModel = viewModel,
                onOpenWhatsWrong = { navController.navigate(Routes.WHATS_WRONG) }
            )
        }
    }
}
