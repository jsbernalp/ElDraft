package com.eldraft.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.floatingTabBarInset
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.TeamPlayerAvatar
import com.eldraft.android.ui.team.TeamsUiState
import com.eldraft.android.ui.team.TeamsViewModel
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamInvitation
import com.eldraft.domain.team.TeamRules
import org.koin.androidx.compose.koinViewModel

/** Pestañas de "Mis equipos". [key] es el valor del argumento `tab` de la ruta. */
enum class TeamsTab(val key: String) {
    OWNED("owned"),
    MEMBER("member"),
    INVITATIONS("invitations");

    companion object {
        fun fromKey(key: String?): TeamsTab = entries.firstOrNull { it.key == key } ?: OWNED
    }
}

/**
 * "Mis equipos" (Perfil → Mis equipos): los equipos propios, en los que soy
 * miembro y las invitaciones pendientes. [initialTab] llega por la ruta
 * (`teams?tab=invitations` desde el push `team_invite`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    initialTab: String?,
    onOpenTeam: (String) -> Unit,
    // Null cuando es la pestaña del home: no hay a dónde volver.
    onBack: (() -> Unit)?,
    viewModel: TeamsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable(initialTab) { mutableStateOf(TeamsTab.fromKey(initialTab)) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    // Se recarga cada vez que se vuelve a la pantalla (p. ej. tras borrar un equipo).
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.createdTeamId) {
        state.createdTeamId?.let { id ->
            showCreateDialog = false
            viewModel.consumeCreatedTeam()
            onOpenTeam(id)
        }
    }

    if (showCreateDialog) {
        CreateTeamDialog(
            state = state,
            onNameChange = viewModel::onNewTeamNameChange,
            onConfirm = viewModel::create,
            onDismiss = {
                showCreateDialog = false
                viewModel.resetNewTeam()
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { if (onBack != null) BackTopBar(onBack = onBack) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ElDraftTheme.spacing.xl),
            ) {
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                ScreenHeader(
                    title = stringResource(R.string.team_list_title),
                    subtitle = stringResource(R.string.team_list_subtitle),
                )
                Spacer(Modifier.height(ElDraftTheme.spacing.lg))
                TeamsTabs(
                    selected = tab,
                    invitationsCount = state.invitations.size,
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(ElDraftTheme.spacing.md))

                val isEmptyLoad = state.isLoading &&
                    state.myTeams.isEmpty() && state.memberships.isEmpty() && state.invitations.isEmpty()
                if (isEmptyLoad) {
                    LoadingState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
                        // Como pestaña, la barra flotante tapa el final de la lista.
                        contentPadding = PaddingValues(
                            top = ElDraftTheme.spacing.md,
                            bottom = ElDraftTheme.spacing.md +
                                if (onBack == null) floatingTabBarInset() else 0.dp,
                        ),
                    ) {
                        when (tab) {
                            TeamsTab.OWNED -> ownedSection(state, onCreate = { showCreateDialog = true }, onOpenTeam)
                            TeamsTab.MEMBER -> memberSection(state.memberships, onOpenTeam)
                            TeamsTab.INVITATIONS -> invitationsSection(
                                invitations = state.invitations,
                                respondingIds = state.respondingIds,
                                onAccept = viewModel::accept,
                                onDecline = viewModel::decline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamsTabs(selected: TeamsTab, invitationsCount: Int, onSelect: (TeamsTab) -> Unit) {
    val tabs = listOf(
        TeamsTab.OWNED to R.string.team_tab_owned,
        TeamsTab.MEMBER to R.string.team_tab_member,
        TeamsTab.INVITATIONS to R.string.team_tab_invitations,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                icon = {
                    if (value == TeamsTab.INVITATIONS && invitationsCount > 0) {
                        val spoken = pluralStringResource(
                            R.plurals.team_invitations_badge,
                            invitationsCount,
                            invitationsCount,
                        )
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.semantics { contentDescription = spoken },
                        ) { Text(invitationsCount.toString()) }
                    }
                },
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

private fun LazyListScope.ownedSection(
    state: TeamsUiState,
    onCreate: () -> Unit,
    onOpenTeam: (String) -> Unit,
) {
    item(key = "create") {
        Column {
            Button(
                onClick = onCreate,
                enabled = state.canCreateAnotherTeam,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ElDraftTheme.size.iconMd))
                Spacer(Modifier.width(ElDraftTheme.spacing.sm))
                Text(stringResource(R.string.team_create_button))
            }
            if (!state.canCreateAnotherTeam) {
                Spacer(Modifier.height(ElDraftTheme.spacing.xs))
                Text(
                    stringResource(R.string.team_create_limit, TeamRules.MAX_TEAMS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textSecondary),
                )
            }
        }
    }
    if (state.myTeams.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                icon = "👥",
                title = stringResource(R.string.team_owned_empty_title),
                message = stringResource(R.string.team_owned_empty_message),
            )
        }
    } else {
        items(state.myTeams, key = { it.id }) { team ->
            TeamCard(team = team, showOwner = false, onClick = { onOpenTeam(team.id) })
        }
    }
}

private fun LazyListScope.memberSection(teams: List<Team>, onOpenTeam: (String) -> Unit) {
    if (teams.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                icon = "🎽",
                title = stringResource(R.string.team_member_empty_title),
                message = stringResource(R.string.team_member_empty_message),
            )
        }
    } else {
        items(teams, key = { it.id }) { team ->
            TeamCard(team = team, showOwner = true, onClick = { onOpenTeam(team.id) })
        }
    }
}

private fun LazyListScope.invitationsSection(
    invitations: List<TeamInvitation>,
    respondingIds: Set<String>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    if (invitations.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                icon = "📨",
                title = stringResource(R.string.team_invitations_empty_title),
                message = stringResource(R.string.team_invitations_empty_message),
            )
        }
    } else {
        items(invitations, key = { it.teamId }) { invitation ->
            InvitationCard(
                invitation = invitation,
                isResponding = invitation.teamId in respondingIds,
                onAccept = { onAccept(invitation.teamId) },
                onDecline = { onDecline(invitation.teamId) },
            )
        }
    }
}

@Composable
private fun TeamCard(team: Team, showOwner: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(ElDraftTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Groups,
                contentDescription = null,
                modifier = Modifier.size(ElDraftTheme.size.iconLg),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(ElDraftTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    team.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val parts = buildList {
                    if (showOwner) add(stringResource(R.string.team_owner_label, team.ownerName))
                    add(pluralStringResource(R.plurals.team_members_count, team.activeCount, team.activeCount))
                    team.invitedCount?.takeIf { it > 0 }?.let {
                        add(pluralStringResource(R.plurals.team_invited_count, it, it))
                    }
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                )
            }
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: TeamInvitation,
    isResponding: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(ElDraftTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamPlayerAvatar(name = invitation.ownerName, avatarUrl = invitation.ownerAvatarUrl)
                Spacer(Modifier.width(ElDraftTheme.spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        invitation.teamName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.team_invitation_message, invitation.ownerName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                    )
                }
            }
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md)) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isResponding,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.team_invitation_decline)) }
                Button(
                    onClick = onAccept,
                    enabled = !isResponding,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isResponding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ElDraftTheme.size.iconMd),
                            strokeWidth = ElDraftTheme.size.stroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.action_accept))
                    }
                }
            }
        }
    }
}

/** Diálogo "Nuevo equipo" con contador x/40. Se cierra cuando el VM confirma la creación. */
@Composable
private fun CreateTeamDialog(
    state: TeamsUiState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tooLong = state.newTeamNameLength > TeamRules.NAME_MAX
    AlertDialog(
        onDismissRequest = { if (!state.isCreating) onDismiss() },
        title = { Text(stringResource(R.string.team_create_dialog_title)) },
        text = {
            TeamNameField(
                value = state.newTeamName,
                length = state.newTeamNameLength,
                isError = tooLong,
                enabled = !state.isCreating,
                onValueChange = onNameChange,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canSubmitNewTeam) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconMd),
                        strokeWidth = ElDraftTheme.size.stroke,
                    )
                } else {
                    Text(stringResource(R.string.team_create_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isCreating) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Campo del nombre del equipo con el contador `x/40`; lo comparten crear y renombrar. */
@Composable
internal fun TeamNameField(
    value: String,
    length: Int,
    isError: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.team_name_label)) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                if (isError) {
                    Text(
                        stringResource(R.string.team_name_invalid, TeamRules.NAME_MAX),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(stringResource(R.string.team_name_counter, length, TeamRules.NAME_MAX))
            }
        },
        shape = ElDraftTheme.shape.field,
        modifier = Modifier.fillMaxWidth(),
    )
}
