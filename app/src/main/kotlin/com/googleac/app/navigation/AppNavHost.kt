package com.googleac.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.googleac.feature.auth.ui.AuthScreen
import com.googleac.feature.auth.ui.FeatureEnablementScreen
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
                onAuthSuccess = { accountId, email ->
                    navController.navigate(
                        Screen.FeatureEnablement.createRoute(accountId, email)
                    )
                }
            )
        }
        composable(
            route = Screen.FeatureEnablement.route,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("email") { type = NavType.StringType }
            )
        ) {
            FeatureEnablementScreen(
                onDone = {
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
    data object FeatureEnablement : Screen("feature_enablement/{accountId}/{email}") {
        fun createRoute(accountId: String, email: String): String =
            "feature_enablement/${Uri.encode(accountId)}/${Uri.encode(email)}"
    }
    data object Drive : Screen("drive")
}
