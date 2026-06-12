package com.eldraft.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eldraft.android.ui.screens.BuscarCupoScreen
import com.eldraft.android.ui.screens.JuegoScreen
import com.eldraft.android.ui.screens.OrganizoScreen
import com.eldraft.android.ui.screens.ProfileTabScreen

/** Destinos del NavigationBar (bottom bar). */
private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Organizo : Tab("tab_organizo", "Organizo", Icons.Filled.Home)
    data object Juego : Tab("tab_juego", "Juego", Icons.Filled.PlayArrow)
    data object BuscarCupo : Tab("tab_buscar_cupo", "Buscar Cupo", Icons.Filled.Search)
    data object Perfil : Tab("tab_perfil", "Perfil", Icons.Filled.Person)
}

private val TABS = listOf(Tab.Organizo, Tab.Juego, Tab.BuscarCupo, Tab.Perfil)

/**
 * Contenedor principal post-login: NavigationBar con 4 secciones y un NavHost
 * anidado. Cada tab mantiene su propio backstack (saveState/restoreState) según
 * el patrón estándar de Compose Navigation. Las pantallas de detalle (postulantes,
 * cromo ajeno, QR, calificación, editar perfil) se abren en el NavHost raíz —
 * encima de esta barra — vía los callbacks recibidos.
 */
@Composable
fun MainScaffold(
    onCreateDraft: () -> Unit,
    onOpenApplicants: (String) -> Unit,
    onOpenPlayerCromo: (String) -> Unit,
    onOpenQrGenerator: (String) -> Unit,
    onOpenQrScanner: (String) -> Unit,
    onOpenRating: (String) -> Unit,
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Volver al inicio del grafo guardando el estado del
                                // tab actual, evitar duplicados y restaurar el estado
                                // previo del tab destino.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Organizo.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Tab.Organizo.route) {
                OrganizoScreen(
                    onCreateDraft = onCreateDraft,
                    onOpenApplicants = onOpenApplicants,
                    onOpenQrGenerator = onOpenQrGenerator,
                    onOpenRating = onOpenRating,
                )
            }
            composable(Tab.Juego.route) {
                JuegoScreen(
                    onOpenQrScanner = onOpenQrScanner,
                    onOpenRating = onOpenRating,
                )
            }
            composable(Tab.BuscarCupo.route) {
                BuscarCupoScreen(onOpenPlayerCromo = onOpenPlayerCromo)
            }
            composable(Tab.Perfil.route) {
                ProfileTabScreen(
                    onEditProfile = onEditProfile,
                    onLoggedOut = onLoggedOut,
                )
            }
        }
    }
}
