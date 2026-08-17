package com.eldraft.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eldraft.android.R

/** Vista activa en las listas de partidos (Organizo y Juego). */
enum class MatchListTab { PROXIMOS, CERRADOS }

/**
 * Conmutador entre los partidos que siguen en juego y los cerrados: los que ya
 * se jugaron y todavía piden algo (declarar asistencia, calificar, reportar un
 * no-show) y, en Juego, las postulaciones que no prosperaron.
 *
 * Usa el mismo `SingleChoiceSegmentedButtonRow` que Lista/Mapa en Buscar Cupo:
 * un solo lenguaje para "cambiar de vista dentro de una pantalla".
 *
 * [pendingCount] cuenta SOLO lo accionable, nunca los rechazos: un badge que
 * avisa de cosas que no requieren nada se aprende a ignorar. Va en color
 * primario y no en el rojo de error que trae `Badge` por defecto, porque es una
 * llamada a la acción y no un fallo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListTabs(
    selected: MatchListTab,
    pendingCount: Int,
    onSelect: (MatchListTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == MatchListTab.PROXIMOS,
            onClick = { onSelect(MatchListTab.PROXIMOS) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {},
            label = { Text(stringResource(R.string.section_upcoming)) },
        )
        SegmentedButton(
            selected = selected == MatchListTab.CERRADOS,
            onClick = { onSelect(MatchListTab.CERRADOS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                if (pendingCount > 0) {
                    // Un número suelto no se lee bien en TalkBack: "3" no dice de qué.
                    val spoken = pluralStringResource(
                        R.plurals.section_closed_badge,
                        pendingCount,
                        pendingCount,
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics { contentDescription = spoken },
                    ) {
                        Text(pendingCount.toString())
                    }
                }
            },
            label = { Text(stringResource(R.string.section_closed)) },
        )
    }
}
