package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.CromoContent
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.profile.ProfileEditViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Tab "Perfil" del NavigationBar: muestra el Cromo del usuario actual (solo
 * lectura) con acciones para editar el perfil y cerrar sesión. La edición vive
 * en [ProfileEditScreen], abierta encima de este tab. Reutiliza
 * [ProfileEditViewModel], que ya carga la cuenta + ficha técnica del usuario.
 */
@Composable
fun ProfileTabScreen(
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Recargar al volver al tab (p. ej. tras editar el perfil).
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Se borrará tu sesión local. Tendrás que iniciar sesión de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            val profile = state.profile
            when {
                state.isLoading -> LoadingState()
                profile == null -> ProfileEmptyState(
                    onEditProfile = onEditProfile,
                    onLogout = { showLogoutDialog = true },
                )
                else -> CromoContent(
                    profile = profile,
                    name = state.user?.name,
                    avatarUrl = state.user?.avatarUrl,
                ) {
                    Spacer(Modifier.height(ElDraftTheme.spacing.xxl))
                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Editar perfil")
                    }
                    Spacer(Modifier.height(ElDraftTheme.spacing.md))
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Cerrar sesión")
                    }
                    Spacer(Modifier.height(ElDraftTheme.spacing.lg))
                }
            }
        }
    }
}

/** Usuario sin ficha técnica todavía: invita a completarla. */
@Composable
private fun ProfileEmptyState(
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ElDraftTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚽", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(ElDraftTheme.spacing.md))
        Text(
            "Completa tu ficha técnica",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(ElDraftTheme.spacing.sm))
        Text(
            "Añade tu posición y datos para mostrar tu Cromo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textSecondary),
        )
        Spacer(Modifier.height(ElDraftTheme.spacing.xl))
        Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Editar perfil")
        }
        Spacer(Modifier.height(ElDraftTheme.spacing.md))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Cerrar sesión")
        }
    }
}
