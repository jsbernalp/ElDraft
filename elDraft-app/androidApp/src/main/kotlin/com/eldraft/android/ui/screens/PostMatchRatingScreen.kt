package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.rating.RatingCriterion
import com.eldraft.android.ui.rating.RatingViewModel
import com.eldraft.android.ui.rating.TeammateScores
import com.eldraft.data.models.Teammate
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostMatchRatingScreen(
    convocatoryId: String,
    onRatingComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: RatingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(convocatoryId) { viewModel.load(convocatoryId) }
    LaunchedEffect(state.done) { if (state.done) onRatingComplete() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("¿Cómo estuvo el partido?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Califica a quienes jugaron en 3 aspectos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading -> LoadingState()
                state.notAttended -> EmptyState(
                    icon = "📷",
                    title = "Primero marca tu asistencia",
                    message = "Escanea el QR del organizador en el partido. Solo quienes asistieron pueden calificar.",
                )
                state.teammates.isEmpty() -> EmptyState(
                    icon = "🤝",
                    title = "Nadie más registró asistencia",
                    message = "Cuando otros jugadores marquen asistencia, podrás calificarlos.",
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.teammates, key = { it.userId }) { teammate ->
                            TeammateRatingCard(
                                teammate = teammate,
                                scores = state.scores[teammate.userId] ?: TeammateScores(),
                                onScore = { criterion, value ->
                                    viewModel.setCriterion(teammate.userId, criterion, value)
                                },
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.submitAll(convocatoryId) },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Enviar calificaciones")
                        }
                    }
                }
            }
        }
    }
}

/** Los 3 criterios con su etiqueta visible, en el orden en que se muestran. */
private val criteria = listOf(
    RatingCriterion.SKILL to "⚽ Habilidad",
    RatingCriterion.SPORTSMANSHIP to "🤝 Deportividad",
    RatingCriterion.RESPONSIBILITY to "📋 Responsabilidad",
)

private fun TeammateScores.valueOf(criterion: RatingCriterion) = when (criterion) {
    RatingCriterion.SKILL -> skill
    RatingCriterion.SPORTSMANSHIP -> sportsmanship
    RatingCriterion.RESPONSIBILITY -> responsibility
}

@Composable
private fun TeammateRatingCard(
    teammate: Teammate,
    scores: TeammateScores,
    onScore: (RatingCriterion, Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(teammate.name, teammate.avatarUrl)
                Spacer(Modifier.width(12.dp))
                Text(
                    teammate.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            if (teammate.alreadyRated) {
                Spacer(Modifier.height(8.dp))
                Text("Ya calificado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.height(8.dp))
                criteria.forEach { (criterion, label) ->
                    CriterionRow(
                        label = label,
                        score = scores.valueOf(criterion),
                        onScore = { onScore(criterion, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CriterionRow(label: String, score: Int, onScore: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
        )
        StarRow(score = score, contentDescriptionPrefix = label, onScore = onScore)
    }
}

@Composable
private fun StarRow(score: Int, contentDescriptionPrefix: String, onScore: (Int) -> Unit) {
    Row {
        (1..5).forEach { star ->
            val filled = star <= score
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "$contentDescriptionPrefix: $star",
                tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onScore(star) }
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun Avatar(name: String, avatarUrl: String?) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Foto de $name",
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
