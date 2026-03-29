package com.googleac.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.googleac.feature.auth.ui.AuthScreen
import com.googleac.feature.drive.ui.DriveScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Auth.route
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Drive.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Drive.route) {
            DriveScreen()
        }
    }
}

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Drive : Screen("drive")
}
