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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.eldraft.android.R
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.TeamPriorityInvite
import com.eldraft.domain.team.TeamWindow
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val TICK_MS = 15_000L

/**
 * Minutos que faltan para [endsAt] (ISO local), refrescados cada 15 s. Null si la
 * fecha no se puede leer: mejor omitir la cuenta regresiva que inventarla.
 */
@Composable
fun rememberMinutesLeft(endsAt: String?): Int? {
    val target = remember(endsAt) { endsAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    val minutes by produceState(initialValue = target?.let { TeamWindow.minutesLeft(it, nowLocal()) }, target) {
        while (target != null) {
            value = TeamWindow.minutesLeft(target, nowLocal())
            delay(TICK_MS)
        }
    }
    return minutes
}

private fun nowLocal(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/**
 * Card de "Juego" para un miembro del equipo cuyo organizador le dio prioridad:
 * "Voy" (abre el sheet de posición, solo con las que tienen cupo libre) o
 * "No puedo" (con confirmación, porque no se puede deshacer).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamPriorityCard(
    invite: TeamPriorityInvite,
    isResponding: Boolean,
    onGo: (position: String) -> Unit,
    onCant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPositions by remember { mutableStateOf(false) }
    var showCantConfirm by remember { mutableStateOf(false) }
    val minutesLeft = rememberMinutesLeft(invite.windowEndsAt)
    val convocatory = invite.convocatory

    if (showPositions) {
        PositionPickerSheet(
            invite = invite,
            onConfirm = { position ->
                showPositions = false
                onGo(position)
            },
            onDismiss = { showPositions = false },
        )
    }
    if (showCantConfirm) {
        AlertDialog(
            onDismissRequest = { showCantConfirm = false },
            title = { Text(stringResource(R.string.team_priority_cant_title)) },
            text = { Text(stringResource(R.string.team_priority_cant_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCantConfirm = false
                    onCant()
                }) { Text(stringResource(R.string.team_priority_cant_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCantConfirm = false }) {
                    Text(stringResource(R.string.action_back_short))
                }
            },
        )
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(ElDraftTheme.spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleBanner(convocatory.scheduledAt)
                minutesLeft?.let {
                    Text(
                        pluralStringResource(R.plurals.team_priority_closes_in, it, it),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(ElDraftTheme.spacing.md2))
            Text(
                stringResource(R.string.team_priority_card_title, invite.teamName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                convocatory.format,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm)) {
                Button(
                    onClick = { showPositions = true },
                    // Sin cupos libres no hay nada que apartar: solo queda "No puedo".
                    enabled = !isResponding && invite.freePositions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isResponding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ElDraftTheme.size.iconMd),
                            strokeWidth = ElDraftTheme.size.stroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.team_priority_go))
                    }
                }
                OutlinedButton(
                    onClick = { showCantConfirm = true },
                    enabled = !isResponding,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.team_priority_cant)) }
            }
        }
    }
}

/** Sheet de posición: solo las que aún tienen cupo libre, con cuántos quedan. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PositionPickerSheet(
    invite: TeamPriorityInvite,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ElDraftTheme.spacing.xl)
                .padding(bottom = ElDraftTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
        ) {
            Text(
                stringResource(R.string.team_priority_position_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
                verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
            ) {
                invite.freePositions.forEach { slot ->
                    FilterChip(
                        selected = selected == slot.position,
                        onClick = { selected = slot.position },
                        label = {
                            Text(
                                slot.position + " · " +
                                    pluralStringResource(R.plurals.team_priority_free_slots, slot.slots, slot.slots),
                            )
                        },
                    )
                }
            }
            Button(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.team_priority_position_confirm)) }
        }
    }
}
