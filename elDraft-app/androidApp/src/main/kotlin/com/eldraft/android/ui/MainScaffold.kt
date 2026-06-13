package com.eldraft.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
    data object Organizo : Tab("tab_organizo", "Organizo", Icons.Filled.SportsSoccer)
    data object Juego : Tab("tab_juego", "Juego", Icons.AutoMirrored.Filled.DirectionsRun)
    data object BuscarCupo : Tab("tab_buscar_cupo", "Buscar Cupo", Icons.Filled.Map)
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
    onOpenQrScanner: (String, Boolean) -> Unit,
    onOpenRating: (String) -> Unit,
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                // Barra blanca (surface) que se separa del fondo gris; el ítem
                // activo se marca con un pill naranja suave (primaryContainer).
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
                    // El organizador también escanea para marcar su propia presencia.
                    onOpenQrScanner = { id -> onOpenQrScanner(id, true) },
                    onOpenRating = onOpenRating,
                )
            }
            composable(Tab.Juego.route) {
                JuegoScreen(
                    onOpenQrScanner = { id -> onOpenQrScanner(id, false) },
                    // Un aprobado puede generar el QR para que el organizador lo escane.
                    onOpenQrGenerator = onOpenQrGenerator,
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
