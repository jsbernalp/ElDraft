package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.CromoContent
import com.eldraft.android.ui.profile.CromoUiState
import com.eldraft.android.ui.profile.ProfileViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerCromoScreen(
    playerId: String,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.cromo.collectAsStateWithLifecycle()

    LaunchedEffect(playerId) {
        if (playerId.isNotBlank()) viewModel.loadCromo(playerId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is CromoUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                is CromoUiState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(ElDraftTheme.spacing.xl)
                )
                is CromoUiState.Loaded -> CromoContent(s.profile)
            }
        }
    }
}
