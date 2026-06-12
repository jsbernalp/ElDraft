package com.eldraft.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eldraft.android.ui.screens.*

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object OnboardingProfile : Screen("onboarding_profile")
    object Home : Screen("home")
    object ProfileEdit : Screen("profile_edit")
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

/**
 * Rutas que se dibujan a pantalla completa, sin aplicar insets de sistema.
 * - Splash: fondo a sangre completa.
 * - Home: el MainScaffold (NavigationBar) gestiona sus propios insets; aplicar
 *   safeDrawingPadding aquí dejaría el bottom bar flotando sobre la barra de
 *   navegación del sistema.
 */
private val FULLSCREEN_ROUTES = setOf(Screen.Splash.route, Screen.Home.route)

@Composable
fun ElDraftApp() {
    val navController = rememberNavController()

    // Edge-to-edge centralizado: aplica safeDrawing a TODAS las pantallas
    // automáticamente (actuales y futuras), excepto las fullscreen (Splash).
    // Las nuevas pantallas no necesitan manejar insets por su cuenta.
    val currentRoute by navController.currentBackStackEntryAsState()
    val isFullscreen = currentRoute?.destination?.route in FULLSCREEN_ROUTES

    val containerModifier = if (isFullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxSize().safeDrawingPadding()
    }

    Box(modifier = containerModifier) {
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
            MainScaffold(
                onCreateDraft = { navController.navigate(Screen.CreateDraft.route) },
                onOpenApplicants = { id -> navController.navigate(Screen.Applicants.route(id)) },
                onOpenPlayerCromo = { id -> navController.navigate(Screen.PlayerCromo.route(id)) },
                onOpenQrGenerator = { id -> navController.navigate(Screen.QRGenerator.route(id)) },
                onOpenQrScanner = { id -> navController.navigate(Screen.QRScanner.route(id)) },
                onOpenRating = { id -> navController.navigate(Screen.PostMatchRating.route(id)) },
                onEditProfile = { navController.navigate(Screen.ProfileEdit.route) },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.ProfileEdit.route) {
            ProfileEditScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.CreateDraft.route) {
            CreateDraftScreen(
                onDraftCreated = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Applicants.route) {
            ApplicantsScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onOpenPlayerCromo = { id -> navController.navigate(Screen.PlayerCromo.route(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.PlayerCromo.route) {
            PlayerCromoScreen(
                playerId = it.arguments?.getString("playerId") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.QRGenerator.route) {
            QRGeneratorScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onScanComplete = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.PostMatchRating.route) {
            PostMatchRatingScreen(
                convocatoryId = it.arguments?.getString("convocatoryId") ?: "",
                onRatingComplete = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        }
    }
}
