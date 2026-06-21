package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

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
import com.eldraft.android.ui.components.MetricIcons
import com.eldraft.android.ui.components.ScreenHeader
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
                .padding(horizontal = ElDraftTheme.spacing.xl),
        ) {
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            ScreenHeader(title = "¿Cómo estuvo el partido?", subtitle = "Califica a tus compañeros")
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

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
                        verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
                        contentPadding = PaddingValues(vertical = ElDraftTheme.spacing.sm),
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = ElDraftTheme.spacing.lg),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(ElDraftTheme.size.iconLg), strokeWidth = ElDraftTheme.size.stroke, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Enviar calificaciones")
                        }
                    }
                }
            }
        }
    }
}

/** Los 3 criterios con su ícono y etiqueta, en el orden en que se muestran. */
private val criteria = listOf(
    Triple(RatingCriterion.SKILL, MetricIcons.Skill, "Habilidad"),
    Triple(RatingCriterion.SPORTSMANSHIP, MetricIcons.Sportsmanship, "Deportividad"),
    Triple(RatingCriterion.RESPONSIBILITY, MetricIcons.Responsibility, "Responsabilidad"),
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
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(ElDraftTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(teammate.name, teammate.avatarUrl)
                Spacer(Modifier.width(ElDraftTheme.spacing.md))
                Text(
                    teammate.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            if (teammate.alreadyRated) {
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                Text("Ya calificado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                criteria.forEach { (criterion, icon, label) ->
                    CriterionRow(
                        icon = icon,
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
private fun CriterionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    score: Int,
    onScore: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElDraftTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ElDraftTheme.size.iconMd), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(ElDraftTheme.spacing.sm))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), // design-tokens-ignore: instrucción de calificación
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
                tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), // design-tokens-ignore: estrella vacía del rating
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onScore(star) }
                    .padding(ElDraftTheme.spacing.xxs),
            )
        }
    }
}

@Composable
private fun Avatar(name: String, avatarUrl: String?) {
    Box(
        modifier = Modifier.size(ElDraftTheme.size.avatar).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.containerStrong)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Foto de $name",
                modifier = Modifier.size(ElDraftTheme.size.avatar).clip(CircleShape),
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
