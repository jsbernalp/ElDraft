package com.eldraft.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eldraft.android.R
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.TeamSuggestion

/** Pestaña activa del sheet "Agregar jugador". */
enum class AddTeamPlayerTab { SUGGESTED, EMAIL }

/**
 * Sheet "Agregar jugador" del detalle de un equipo: pestañas Sugeridos (jugadores
 * con los que ya jugó) y Por correo. Es presentación pura: el estado (lista,
 * bloqueos de reenvío, error del backend) lo decide `TeamDetailViewModel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTeamPlayerSheet(
    suggestions: List<TeamSuggestion>,
    suggestionsLoading: Boolean,
    suggestionsError: String?,
    invitingIds: Set<String>,
    canAddMember: Boolean,
    email: String,
    emailError: String?,
    emailInvitedName: String?,
    isInvitingByEmail: Boolean,
    canSendEmail: Boolean,
    onInviteSuggested: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onInviteByEmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AddTeamPlayerTab.SUGGESTED) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ElDraftTheme.spacing.xl, end = ElDraftTheme.spacing.xl, bottom = ElDraftTheme.spacing.xxl),
        ) {
            Text(
                stringResource(R.string.team_add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))
            AddTeamPlayerTabs(selected = tab, onSelect = { tab = it })
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            when (tab) {
                AddTeamPlayerTab.SUGGESTED -> SuggestedTab(
                    suggestions = suggestions,
                    isLoading = suggestionsLoading,
                    error = suggestionsError,
                    invitingIds = invitingIds,
                    canAddMember = canAddMember,
                    onInvite = onInviteSuggested,
                )
                AddTeamPlayerTab.EMAIL -> EmailTab(
                    email = email,
                    error = emailError,
                    invitedName = emailInvitedName,
                    isInviting = isInvitingByEmail,
                    canSend = canSendEmail,
                    onEmailChange = onEmailChange,
                    onInvite = onInviteByEmail,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTeamPlayerTabs(selected: AddTeamPlayerTab, onSelect: (AddTeamPlayerTab) -> Unit) {
    val tabs = listOf(
        AddTeamPlayerTab.SUGGESTED to R.string.team_add_tab_suggested,
        AddTeamPlayerTab.EMAIL to R.string.team_add_tab_email,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                icon = {},
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun SuggestedTab(
    suggestions: List<TeamSuggestion>,
    isLoading: Boolean,
    error: String?,
    invitingIds: Set<String>,
    canAddMember: Boolean,
    onInvite: (String) -> Unit,
) {
    when {
        isLoading && suggestions.isEmpty() -> Box(
            Modifier.fillMaxWidth().padding(vertical = ElDraftTheme.spacing.xxl),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        error != null && suggestions.isEmpty() -> Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        suggestions.isEmpty() -> EmptyState(
            icon = "🤝",
            title = stringResource(R.string.team_suggestions_empty_title),
            message = stringResource(R.string.team_suggestions_empty_message),
            modifier = Modifier.heightIn(max = SHEET_LIST_MAX_HEIGHT),
        )
        else -> LazyColumn(
            modifier = Modifier.heightIn(max = SHEET_LIST_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
        ) {
            items(suggestions, key = { it.playerId }) { s ->
                SuggestionRow(
                    suggestion = s,
                    isInviting = s.playerId in invitingIds,
                    enabled = canAddMember,
                    onInvite = { onInvite(s.playerId) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: TeamSuggestion,
    isInviting: Boolean,
    enabled: Boolean,
    onInvite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamPlayerAvatar(name = suggestion.name, avatarUrl = suggestion.avatarUrl)
        Spacer(Modifier.width(ElDraftTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = listOfNotNull(
                suggestion.positionPrimary?.takeIf { it.isNotBlank() },
                pluralStringResource(
                    R.plurals.team_matches_together,
                    suggestion.matchesTogether,
                    suggestion.matchesTogether,
                ),
            ).joinToString(" · ")
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
            )
        }
        Spacer(Modifier.width(ElDraftTheme.spacing.sm))
        OutlinedButton(onClick = onInvite, enabled = enabled && !isInviting) {
            if (isInviting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ElDraftTheme.size.iconMd),
                    strokeWidth = ElDraftTheme.size.stroke,
                )
            } else {
                Text(stringResource(R.string.team_invite_button))
            }
        }
    }
}

@Composable
private fun EmailTab(
    email: String,
    error: String?,
    invitedName: String?,
    isInviting: Boolean,
    canSend: Boolean,
    onEmailChange: (String) -> Unit,
    onInvite: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md)) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.team_email_label)) },
            singleLine = true,
            isError = error != null,
            enabled = !isInviting,
            supportingText = {
                Text(error ?: stringResource(R.string.team_email_hint))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onInvite() }),
            shape = ElDraftTheme.shape.field,
            modifier = Modifier.fillMaxWidth(),
        )
        invitedName?.let {
            Text(
                stringResource(R.string.team_email_invited, it),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Button(
            onClick = onInvite,
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isInviting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ElDraftTheme.size.iconLg),
                    strokeWidth = ElDraftTheme.size.stroke,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.team_invite_button))
            }
        }
    }
}

/** Avatar redondo con foto o inicial, compartido por las pantallas de equipos. */
@Composable
fun TeamPlayerAvatar(name: String, avatarUrl: String?, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: stringResource(R.string.rating_initial_fallback)
    Box(
        modifier = modifier
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
            Text(
                initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// Alto máximo de la lista dentro del sheet: deja ver la cabecera y las pestañas
// sin que el sheet ocupe toda la pantalla (layout one-off de este componente).
private val SHEET_LIST_MAX_HEIGHT = 420.dp
