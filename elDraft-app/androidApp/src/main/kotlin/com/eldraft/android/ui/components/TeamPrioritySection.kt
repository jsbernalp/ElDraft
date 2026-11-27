package com.eldraft.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.eldraft.android.R
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.Team
import com.eldraft.domain.team.TeamWindowOption

/** Etiqueta corta de una ventana: 30 min / 1 h / 2 h / 6 h. */
@Composable
fun teamWindowLabel(minutes: Int): String = when (minutes) {
    30 -> stringResource(R.string.team_window_30)
    60 -> stringResource(R.string.team_window_60)
    120 -> stringResource(R.string.team_window_120)
    360 -> stringResource(R.string.team_window_360)
    else -> stringResource(R.string.team_window_30)
}

/**
 * Sección opcional "Avisar primero a mi equipo" del formulario de convocatoria:
 * interruptor, selector de equipo y chips de ventana. Es de presentación pura: el
 * estado y la validez de cada ventana ([windowOptions], de `TeamWindow`) los
 * decide quien la usa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamPrioritySection(
    teams: List<Team>,
    enabled: Boolean,
    selectedTeamId: String?,
    selectedWindow: Int?,
    windowOptions: List<TeamWindowOption>,
    onEnabledChange: (Boolean) -> Unit,
    onSelectTeam: (String) -> Unit,
    onSelectWindow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).padding(end = ElDraftTheme.spacing.md)) {
                Text(
                    stringResource(R.string.team_priority_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.team_priority_section_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (!enabled) return@Column

        Spacer(Modifier.height(ElDraftTheme.spacing.md))
        Text(
            stringResource(R.string.team_priority_team_label),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
            verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
        ) {
            teams.forEach { team ->
                FilterChip(
                    selected = team.id == selectedTeamId,
                    onClick = { onSelectTeam(team.id) },
                    label = { Text(team.name) },
                )
            }
        }

        Spacer(Modifier.height(ElDraftTheme.spacing.md))
        Text(
            stringResource(R.string.team_priority_window_label),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
            verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
        ) {
            windowOptions.forEach { option ->
                FilterChip(
                    selected = option.minutes == selectedWindow,
                    onClick = { onSelectWindow(option.minutes) },
                    // Deshabilitada: la ventana no cerraría 1 h antes del partido.
                    enabled = option.enabled,
                    label = { Text(teamWindowLabel(option.minutes)) },
                )
            }
        }

        if (windowOptions.none { it.enabled }) {
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            Text(
                stringResource(R.string.team_priority_too_close),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (selectedWindow != null) {
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            Text(
                stringResource(R.string.team_priority_summary, teamWindowLabel(selectedWindow)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
            )
        }
    }
}
