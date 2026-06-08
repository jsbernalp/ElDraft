package com.eldraft.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.draft.MyMatchesViewModel
import com.eldraft.data.models.Convocatory
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onCreateDraft: () -> Unit,
    onOpenApplicants: (String) -> Unit,
    onOpenPlayerCromo: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Mis Partidos") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("Buscar Cupo") }
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> MyMatchesTab(
                    onCreateDraft = onCreateDraft,
                    onOpenApplicants = onOpenApplicants
                )
                1 -> MapTab(
                    onOpenPlayerCromo = onOpenPlayerCromo
                )
            }
        }
    }
}

@Composable
private fun MyMatchesTab(
    onCreateDraft: () -> Unit,
    onOpenApplicants: (String) -> Unit,
    viewModel: MyMatchesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarga cada vez que el tab vuelve a mostrarse.
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Mis convocatorias",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading && state.matches.isEmpty() -> LoadingState()
                state.matches.isEmpty() -> EmptyState(
                    icon = "⚽",
                    title = "Aún no has creado convocatorias",
                    message = "Toca el botón + para crear tu primera convocatoria.",
                )
                else ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(state.matches, key = { it.id }) { match ->
                            MyMatchCard(match = match, onClick = { onOpenApplicants(match.id) })
                        }
                    }
            }
        }

        FloatingActionButton(
            onClick = onCreateDraft,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Text("+")
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MyMatchCard(match: Convocatory, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                match.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            match.addressText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${match.slotsNeeded} cupos · ${match.positionRequired} · ver postulantes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MapTab(onOpenPlayerCromo: (String) -> Unit) {
    com.eldraft.android.ui.map.MapTabContent(onOpenPlayerCromo = onOpenPlayerCromo)
}
