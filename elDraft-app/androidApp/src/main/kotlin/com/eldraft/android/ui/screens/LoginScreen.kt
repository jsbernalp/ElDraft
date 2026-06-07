package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.auth.AuthUiState
import com.eldraft.android.ui.auth.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: (needsOnboarding: Boolean) -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navegar cuando el login fue exitoso
    LaunchedEffect(state) {
        (state as? AuthUiState.Success)?.let { onLoginSuccess(it.needsOnboarding) }
    }
    // Mostrar errores en snackbar
    LaunchedEffect(state) {
        (state as? AuthUiState.Error)?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.resetError()
        }
    }

    val isLoading = state is AuthUiState.Loading

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // El contenedor raíz (ElDraftApp) ya aplica safeDrawing; evitamos doble padding.
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "elDraft",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Encuentra tu partido",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(64.dp))

            Button(
                onClick = { viewModel.signInWithGoogle() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continuar con Google")
                }
            }

            Spacer(Modifier.height(16.dp))

            // TODO: Apple Sign-In (requiere cuenta Apple Developer)
            OutlinedButton(
                onClick = { /* Apple Sign-In — pendiente */ },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continuar con Apple (próximamente)")
            }

            Spacer(Modifier.height(32.dp))

            // Atajo de desarrollo: login sin Google (modo mock del backend)
            TextButton(
                onClick = { viewModel.signInDev() },
                enabled = !isLoading
            ) {
                Text(
                    "Entrar como invitado (dev)",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}
