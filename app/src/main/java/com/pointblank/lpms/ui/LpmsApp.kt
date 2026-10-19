package com.pointblank.lpms.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pointblank.lpms.ui.auth.AuthViewModel
import com.pointblank.lpms.ui.auth.LoginScreen
import com.pointblank.lpms.ui.auth.RegisterScreen
import com.pointblank.lpms.ui.help.HelpScreen
import com.pointblank.lpms.ui.home.HomeScreen
import com.pointblank.lpms.ui.navigation.Routes
import com.pointblank.lpms.ui.settings.SettingsScreen

@Composable
fun LpmsApp(authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Move between the auth screens and the signed-in area when the session changes.
    LaunchedEffect(user) {
        val current = navController.currentBackStackEntry?.destination?.route
        val onAuthScreen = current == Routes.LOGIN || current == Routes.REGISTER
        if (user == null) {
            if (current != null && !onAuthScreen) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        } else if (onAuthScreen) {
            navController.navigate(Routes.HOME) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(onRegister = { navController.navigate(Routes.REGISTER) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            user?.let {
                HomeScreen(
                    user = it,
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onHelp = { navController.navigate(Routes.HELP) }
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
    }
}
