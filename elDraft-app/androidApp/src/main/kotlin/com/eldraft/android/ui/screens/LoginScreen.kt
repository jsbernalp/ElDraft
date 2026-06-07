package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

        // TODO: implementar Google Sign-In con Firebase Auth
        Button(
            onClick = { /* Google Sign-In */ onLoginSuccess() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Continuar con Google")
        }

        Spacer(Modifier.height(16.dp))

        // TODO: implementar Apple Sign-In con Firebase Auth
        OutlinedButton(
            onClick = { /* Apple Sign-In */ onLoginSuccess() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continuar con Apple", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
