package com.example.catchmestreaming.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.catchmestreaming.ui.screens.MainScreen
import com.example.catchmestreaming.ui.screens.SettingsScreen
import com.example.catchmestreaming.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    // Share the same ViewModel instance across screens
    val mainViewModel: MainViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                viewModel = mainViewModel
            )
        }
        
        composable(Screen.Settings.route) {
            val uiState by mainViewModel.uiState.collectAsState()
            SettingsScreen(
                currentConfig = uiState.streamConfig,
                currentRecordingConfig = uiState.recordingConfig,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveConfig = { config ->
                    mainViewModel.updateStreamConfig(config)
                },
                onSaveRecordingConfig = { config ->
                    mainViewModel.updateRecordingConfig(config)
                }
            )
        }
    }
}