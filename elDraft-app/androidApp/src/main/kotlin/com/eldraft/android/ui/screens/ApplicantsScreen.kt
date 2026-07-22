package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eldraft.android.R
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.MetricIcons
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.postulation.ApplicantsViewModel
import com.eldraft.data.models.Postulation
import com.eldraft.data.models.PostulantSummary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantsScreen(
    convocatoryId: String,
    onOpenPlayerCromo: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ApplicantsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(convocatoryId) { viewModel.load(convocatoryId) }
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
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.load(convocatoryId) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = ElDraftTheme.spacing.xl),
        ) {
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            ScreenHeader(
                title = stringResource(R.string.applicants_header_title),
                subtitle = stringResource(R.string.applicants_header_subtitle),
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            if (state.isLoading && state.applicants.isEmpty()) {
                LoadingState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
                    contentPadding = PaddingValues(vertical = ElDraftTheme.spacing.md),
                ) {
                    if (state.applicants.isEmpty()) {
                        item {
                            EmptyState(
                                icon = "📋",
                                title = stringResource(R.string.applicants_empty_title),
                                message = stringResource(R.string.applicants_empty_message),
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                    } else {
                        items(state.applicants, key = { it.id }) { postulation ->
                            ApplicantCard(
                                postulation = postulation,
                                isDeciding = postulation.id in state.decidingIds,
                                onApprove = { viewModel.approve(postulation.id) },
                                onReject = { viewModel.reject(postulation.id) },
                                onOpenCromo = { onOpenPlayerCromo(postulation.playerId) },
                            )
                        }
                    }
                }
            }
        }
        } // PullToRefreshBox
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplicantCard(
    postulation: Postulation,
    isDeciding: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenCromo: () -> Unit,
) {
    val player = postulation.player
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCromo),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(ElDraftTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.Top) {
                AvatarCircle(name = player?.name ?: stringResource(R.string.rating_initial_fallback), avatarUrl = player?.avatarUrl)
                Spacer(Modifier.width(ElDraftTheme.spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        player?.name ?: stringResource(R.string.applicants_player_fallback),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = listOfNotNull(
                        player?.positionPrimary,
                        player?.dominantFoot?.let { stringResource(R.string.applicants_dominant_foot, it) },
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                        )
                    }
                    // Posición a la que se postuló en ESTE partido (distinta de la
                    // posición habitual de su ficha, que va en el subtítulo).
                    postulation.position?.takeIf { it.isNotBlank() }?.let { pos ->
                        Spacer(Modifier.height(ElDraftTheme.spacing.xs))
                        Text(
                            stringResource(R.string.applicants_applied_as, pos),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                StatusChip(postulation.status)
            }

            if (player != null) {
                Spacer(Modifier.height(ElDraftTheme.spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.divider))
                Spacer(Modifier.height(ElDraftTheme.spacing.md))
                StatsRow(player)
            }

            // Botones solo cuando la postulación sigue pendiente.
            if (postulation.status == "pending") {
                Spacer(Modifier.height(ElDraftTheme.spacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md)) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isDeciding,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.applicants_reject)) }
                    Button(
                        onClick = onApprove,
                        enabled = !isDeciding,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isDeciding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ElDraftTheme.size.iconMd),
                                strokeWidth = ElDraftTheme.size.stroke,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.applicants_approve))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Métricas del jugador en FlowRow: se acomodan en varias líneas si no caben,
 * evitando que la última ("Asistencia") se desborde verticalmente. Cada métrica
 * lleva un ícono de dominio junto al valor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsRow(player: PostulantSummary) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.lg2),
        verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
    ) {
        player.skillScore?.let { Stat(MetricIcons.Skill, stringResource(R.string.cromo_metric_skill), "%.1f".format(it)) }
        player.sportsmanshipScore?.let { Stat(MetricIcons.Sportsmanship, stringResource(R.string.cromo_metric_sportsmanship), "%.1f".format(it)) }
        player.responsibilityScore?.let { Stat(MetricIcons.Responsibility, stringResource(R.string.cromo_metric_responsibility), "%.1f".format(it)) }
        player.attendancePct?.let { Stat(MetricIcons.Attendance, stringResource(R.string.cromo_tile_attendance), stringResource(R.string.applicants_attendance_value, it.toInt())) }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(ElDraftTheme.size.iconSm),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary))
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "approved" -> stringResource(R.string.applicants_status_approved) to MaterialTheme.colorScheme.primary
        "rejected" -> stringResource(R.string.applicants_status_rejected) to MaterialTheme.colorScheme.error
        "cancelled" -> stringResource(R.string.applicants_status_cancelled) to MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textMuted)
        else -> stringResource(R.string.applicants_status_pending) to MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textMuted)
    }
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun AvatarCircle(name: String, avatarUrl: String? = null) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: stringResource(R.string.rating_initial_fallback)
    Box(
        modifier = Modifier
            .size(ElDraftTheme.size.avatar)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.containerStrong)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.rating_avatar_content_description, name),
                modifier = Modifier.size(ElDraftTheme.size.avatar).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
