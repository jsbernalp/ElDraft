package com.eldraft.android.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eldraft.android.R
import com.eldraft.android.ui.components.FloatingTabBar
import com.eldraft.android.ui.components.FloatingTabBarItem
import com.eldraft.android.ui.screens.BuscarCupoScreen
import com.eldraft.android.ui.screens.JuegoScreen
import com.eldraft.android.ui.screens.OrganizoScreen
import com.eldraft.android.ui.screens.ProfileTabScreen
import com.eldraft.android.ui.theme.ElDraftTheme

/** Destinos de la barra de pestañas. */
private sealed class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    data object Organizo : Tab("tab_organizo", R.string.tab_organizo, Icons.Filled.SportsSoccer)
    data object Juego : Tab("tab_juego", R.string.tab_juego, Icons.AutoMirrored.Filled.DirectionsRun)
    data object BuscarCupo : Tab("tab_buscar_cupo", R.string.tab_buscar_cupo, Icons.Filled.Map)
    data object Perfil : Tab("tab_perfil", R.string.tab_perfil, Icons.Filled.Person)
}

private val TABS = listOf(Tab.Organizo, Tab.Juego, Tab.BuscarCupo, Tab.Perfil)

/**
 * Contenedor principal post-login: barra de pestañas flotante con 4 secciones y un
 * NavHost anidado. Cada tab mantiene su propio backstack (saveState/restoreState)
 * según el patrón estándar de Compose Navigation. Las pantallas de detalle
 * (postulantes, cromo ajeno, QR, calificación, editar perfil) se abren en el
 * NavHost raíz — encima de esta barra — vía los callbacks recibidos.
 *
 * La barra NO va en el slot `bottomBar` del `Scaffold`: flota sobre el contenido,
 * que sigue dibujándose hasta el borde inferior de la pantalla. A cambio, cada
 * pantalla tiene que reservarse el hueco con `floatingTabBarInset()`. El `Scaffold`
 * se queda solo por el inset superior — la app es edge-to-edge, y sin él las cuatro
 * pestañas se meterían debajo de la barra de estado.
 */
@Composable
fun MainScaffold(
    initialTab: String? = null,
    onCreateDraft: () -> Unit,
    onOpenApplicants: (String) -> Unit,
    onOpenPlayerCromo: (String) -> Unit,
    onOpenQrGenerator: (String) -> Unit,
    onOpenQrScanner: (String, Boolean) -> Unit,
    onOpenRating: (String) -> Unit,
    onOpenAttendance: (String) -> Unit,
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            navController.navigate(initialTab) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val items = TABS.map { FloatingTabBarItem(it.route, stringResource(it.label), it.icon) }

    Scaffold(
        // Solo el inset de arriba: el de abajo lo aplica la barra flotante por su
        // cuenta, y dejárselo también al Scaffold lo contaría dos veces.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Tab.Organizo.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Tab.Organizo.route) {
                    OrganizoScreen(
                        onCreateDraft = onCreateDraft,
                        onOpenApplicants = onOpenApplicants,
                        onOpenQrGenerator = onOpenQrGenerator,
                        // El organizador también escanea para marcar su propia presencia.
                        onOpenQrScanner = { id -> onOpenQrScanner(id, true) },
                        onOpenRating = onOpenRating,
                        onOpenAttendance = onOpenAttendance,
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
                    BuscarCupoScreen()
                }
                composable(Tab.Perfil.route) {
                    ProfileTabScreen(
                        onEditProfile = onEditProfile,
                        onLoggedOut = onLoggedOut,
                    )
                }
            }

            FloatingTabBar(
                items = items,
                selectedKey = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        // Volver al inicio del grafo guardando el estado del tab
                        // actual, evitar duplicados y restaurar el estado previo
                        // del tab destino.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = ElDraftTheme.spacing.md),
            )
        }
    }
}
