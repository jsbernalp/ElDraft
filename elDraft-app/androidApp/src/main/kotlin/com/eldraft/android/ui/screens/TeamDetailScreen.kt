package com.eldraft.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.components.AddTeamPlayerSheet
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.TeamPlayerAvatar
import com.eldraft.android.ui.team.TeamDetailViewModel
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamMember
import com.eldraft.domain.team.TeamRules
import org.koin.androidx.compose.koinViewModel

/**
 * Detalle de un equipo. El dueño ve el menú (renombrar, borrar), quita jugadores
 * y abre el sheet "Agregar jugador"; un miembro solo puede salirse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    teamId: String,
    onBack: () -> Unit,
    viewModel: TeamDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<TeamMember?>(null) }

    LaunchedEffect(teamId) { viewModel.load(teamId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LaunchedEffect(state.renamed) {
        if (state.renamed) {
            showRename = false
            viewModel.consumeRenamed()
        }
    }

    val team = state.team

    if (showAddSheet) {
        AddTeamPlayerSheet(
            suggestions = state.suggestions,
            suggestionsLoading = state.suggestionsLoading,
            suggestionsError = state.suggestionsError,
            invitingIds = state.invitingIds,
            canAddMember = state.canAddMember,
            email = state.email,
            emailError = state.emailError,
            emailInvitedName = state.emailInvitedName,
            isInvitingByEmail = state.isInvitingByEmail,
            canSendEmail = state.canSendEmail,
            onInviteSuggested = viewModel::inviteSuggested,
            onEmailChange = viewModel::onEmailChange,
            onInviteByEmail = viewModel::inviteByEmail,
            onDismiss = { showAddSheet = false },
        )
    }
    if (showRename && team != null) {
        RenameTeamDialog(
            initialName = team.name,
            isRenaming = state.isRenaming,
            onConfirm = viewModel::rename,
            onDismiss = { showRename = false },
        )
    }
    if (showDelete && team != null) {
        ConfirmDialog(
            title = stringResource(R.string.team_delete_dialog_title),
            message = stringResource(R.string.team_delete_dialog_message, team.name),
            confirmLabel = stringResource(R.string.team_delete_confirm),
            onConfirm = {
                showDelete = false
                viewModel.delete()
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showLeave && team != null) {
        ConfirmDialog(
            title = stringResource(R.string.team_leave_dialog_title, team.name),
            message = stringResource(R.string.team_leave_dialog_message),
            confirmLabel = stringResource(R.string.team_leave_confirm),
            onConfirm = {
                showLeave = false
                viewModel.leave()
            },
            onDismiss = { showLeave = false },
        )
    }
    memberToRemove?.let { member ->
        ConfirmDialog(
            title = stringResource(R.string.team_remove_dialog_title, member.name),
            message = stringResource(R.string.team_remove_dialog_message),
            confirmLabel = stringResource(R.string.team_remove_confirm),
            onConfirm = {
                memberToRemove = null
                viewModel.remove(member.playerId)
            },
            onDismiss = { memberToRemove = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.load(teamId) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                team == null && state.isLoading -> LoadingState()
                team == null -> Unit
                else -> TeamDetailContent(
                    team = team,
                    canAddMember = state.canAddMember,
                    removingIds = state.removingIds,
                    isBusy = state.isDeleting || state.isLeaving,
                    onRename = { showRename = true },
                    onDelete = { showDelete = true },
                    onAddPlayer = {
                        viewModel.openAddPlayer()
                        showAddSheet = true
                    },
                    onRemove = { memberToRemove = it },
                    onLeave = { showLeave = true },
                )
            }
        }
    }
}

@Composable
private fun TeamDetailContent(
    team: TeamDetail,
    canAddMember: Boolean,
    removingIds: Set<String>,
    isBusy: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddPlayer: () -> Unit,
    onRemove: (TeamMember) -> Unit,
    onLeave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = ElDraftTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
        contentPadding = PaddingValues(vertical = ElDraftTheme.spacing.md),
    ) {
        item(key = "header") {
            TeamHeader(team = team, isBusy = isBusy, onRename = onRename, onDelete = onDelete)
        }
        if (team.isOwner) {
            item(key = "add") {
                Column {
                    Button(
                        onClick = onAddPlayer,
                        enabled = canAddMember && !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(ElDraftTheme.size.iconMd))
                        Spacer(Modifier.width(ElDraftTheme.spacing.sm))
                        Text(stringResource(R.string.team_detail_add_player))
                    }
                    if (!canAddMember) {
                        Spacer(Modifier.height(ElDraftTheme.spacing.xs))
                        Text(
                            stringResource(R.string.team_detail_full, TeamRules.MAX_MEMBERS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textSecondary),
                        )
                    }
                }
            }
        }
        item(key = "members_title") {
            Text(
                stringResource(R.string.team_detail_members_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (team.members.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = "👥",
                    title = stringResource(R.string.team_detail_empty_title),
                    message = stringResource(R.string.team_detail_empty_message),
                )
            }
        } else {
            items(team.members, key = { it.playerId }) { member ->
                MemberRow(
                    member = member,
                    canRemove = team.isOwner,
                    isRemoving = member.playerId in removingIds,
                    onRemove = { onRemove(member) },
                )
            }
        }
        if (!team.isOwner) {
            item(key = "leave") {
                OutlinedButton(
                    onClick = onLeave,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = ElDraftTheme.spacing.lg),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.team_leave_button))
                }
            }
        }
    }
}

@Composable
private fun TeamHeader(team: TeamDetail, isBusy: Boolean, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                team.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.team_detail_owner, team.ownerName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textSecondary),
            )
        }
        if (team.isOwner) {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !isBusy) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.team_menu_content_description),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.team_menu_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.team_menu_delete), color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: TeamMember,
    canRemove: Boolean,
    isRemoving: Boolean,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(ElDraftTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamPlayerAvatar(name = member.name, avatarUrl = member.avatarUrl)
            Spacer(Modifier.width(ElDraftTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                member.positionPrimary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                    )
                }
            }
            MemberStatusChip(member)
            if (canRemove) {
                Spacer(Modifier.width(ElDraftTheme.spacing.xs))
                if (isRemoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconMd),
                        strokeWidth = ElDraftTheme.size.stroke,
                    )
                } else {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.team_remove_content_description, member.name),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.icon),
                        )
                    }
                }
            }
        }
    }
}

/** Chip Invitado / Miembro. Un estado desconocido no se pinta. */
@Composable
private fun MemberStatusChip(member: TeamMember) {
    val (label, color: Color) = when {
        member.isActive -> stringResource(R.string.team_member_status_active) to MaterialTheme.colorScheme.primary
        member.isInvited -> stringResource(R.string.team_member_status_invited) to
            MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary)
        else -> return
    }
    Surface(
        shape = ElDraftTheme.shape.pill,
        color = color.copy(alpha = ElDraftTheme.alpha.container),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.xs),
        )
    }
}

@Composable
private fun RenameTeamDialog(
    initialName: String,
    isRenaming: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val length = TeamRules.normalizeName(name).length
    AlertDialog(
        onDismissRequest = { if (!isRenaming) onDismiss() },
        title = { Text(stringResource(R.string.team_rename_dialog_title)) },
        text = {
            TeamNameField(
                value = name,
                length = length,
                isError = length > TeamRules.NAME_MAX_LENGTH,
                enabled = !isRenaming,
                onValueChange = { name = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = !isRenaming && TeamRules.isValidName(name),
            ) {
                if (isRenaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconMd),
                        strokeWidth = ElDraftTheme.size.stroke,
                    )
                } else {
                    Text(stringResource(R.string.team_rename_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRenaming) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Confirmación destructiva (borrar, quitar, salirse): botón de confirmar en rojo. */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
