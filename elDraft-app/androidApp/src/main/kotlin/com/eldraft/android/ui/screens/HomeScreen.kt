package com.eldraft.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    onOpenApplicants: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // TODO: lista de convocatorias del organizador
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Mis convocatorias", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        FloatingActionButton(
            onClick = onCreateDraft,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text("+")
        }
    }
}

@Composable
private fun MapTab(onOpenPlayerCromo: (String) -> Unit) {
    com.eldraft.android.ui.map.MapTabContent(onOpenPlayerCromo = onOpenPlayerCromo)
}
