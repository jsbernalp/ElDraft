package com.eldraft.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eldraft.android.R
import com.eldraft.android.ui.theme.ElDraftTheme

/**
 * Sección plegable de un formulario por pasos. Muestra un check verde cuando la
 * sección es válida ([isComplete]); al colapsar, deja a la vista un [summary] del
 * dato ya ingresado. El plegado lo coordina la pantalla (solo una abierta a la
 * vez) vía [expanded] + [onHeaderClick].
 */
@Composable
fun CollapsibleFormSection(
    title: String,
    expanded: Boolean,
    isComplete: Boolean,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = ElDraftTheme.elevation.cardRaised),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(ElDraftTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(isComplete = isComplete)
                Spacer(Modifier.width(ElDraftTheme.spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // El resumen solo se ve cuando la sección está colapsada.
                    if (!expanded && !summary.isNullOrBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textMuted),
                    modifier = Modifier.rotate(chevronRotation),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = ElDraftTheme.spacing.lg, end = ElDraftTheme.spacing.lg, bottom = ElDraftTheme.spacing.lg)) {
                    content()
                }
            }
        }
    }
}

/** Círculo de estado: check verde si está completa, punto neutro si no. */
@Composable
private fun StatusBadge(isComplete: Boolean) {
    val bg = if (isComplete) ElDraftTheme.colors.success else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (isComplete) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.section_completed_content_description),
                tint = ElDraftTheme.colors.onSuccess,
                modifier = Modifier.size(ElDraftTheme.spacing.lg),
            )
        }
    }
}
