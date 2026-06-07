package com.eldraft.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eldraft.android.ui.screens.*

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object OnboardingProfile : Screen("onboarding_profile")
    object Home : Screen("home")
    object CreateDraft : Screen("create_draft")
    object Applicants : Screen("applicants/{convocatoryId}") {
        fun route(id: String) = "applicants/$id"
    }
    object PlayerCromo : Screen("player_cromo/{playerId}") {
        fun route(id: String) = "player_cromo/$id"
    }
    object QRGenerator : Screen("qr_generator/{convocatoryId}") {
        fun route(id: String) = "qr_generator/$id"
    }
    object QRScanner : Screen("qr_scanner/{convocatoryId}") {
        fun route(id: String) = "qr_scanner/$id"
    }
    object PostMatchRating : Screen("post_match_rating/{convocatoryId}") {
        fun route(id: String) = "post_match_rating/$id"
    }
}

@Composable
fun ElDraftApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { needsOnboarding ->
                    val target = if (needsOnboarding) Screen.OnboardingProfile.route else Screen.Home.route
                    navController.navigate(target) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.OnboardingProfile.route) {
            OnboardingProfileScreen(
                onProfileComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.OnboardingProfile.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onCreateDraft = { navController.navigate(Screen.CreateDraft.route) },
                onOpenApplicants = { id -> navController.navigate(Screen.Applicants.route(id)) },
                onOpenPlayerCromo = { id -> navController.navigate(Screen.PlayerCromo.route(id)) }
            )
        }
        composable(Screen.CreateDraft.route) {
            CreateDraftScreen(
                onDraftCreated = { navController.popBackStack() }
            )
        }
        composable(Screen.Applicants.route) {
            ApplicantsScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onOpenPlayerCromo = { id -> navController.navigate(Screen.PlayerCromo.route(id)) }
            )
        }
        composable(Screen.PlayerCromo.route) {
            PlayerCromoScreen(
                playerId = it.arguments?.getString("playerId") ?: ""
            )
        }
        composable(Screen.QRGenerator.route) {
            QRGeneratorScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: ""
            )
        }
        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onScanComplete = { navController.popBackStack() }
            )
        }
        composable(Screen.PostMatchRating.route) {
            PostMatchRatingScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onRatingComplete = { navController.popBackStack() }
            )
        }
    }
}
