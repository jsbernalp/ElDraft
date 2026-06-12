package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eldraft.android.ui.components.DropdownField
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.profile.ProfileEditViewModel
import com.eldraft.data.models.PlayerProfile
import org.koin.androidx.compose.koinViewModel

private val POSITIONS = listOf("Arquero", "Defensa", "Mediocampista", "Delantero", "Extremo")
private val FEET = listOf("Derecho", "Zurdo", "Ambidiestro")
private val BUILDS = listOf("Delgado", "Atlético", "Robusto")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Campos editables — se inicializan cuando llegan los datos
    var name by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var positionPrimary by remember { mutableStateOf("") }
    var positionSecondary by remember { mutableStateOf("") }
    var dominantFoot by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var build by remember { mutableStateOf("") }

    // Pre-rellenar campos cuando los datos lleguen (una sola vez)
    val user = state.user
    val profile = state.profile
    LaunchedEffect(user, profile) {
        user?.let { u ->
            if (name.isEmpty()) name = u.name
            if (avatarUrl.isEmpty()) avatarUrl = u.avatarUrl ?: ""
            if (phone.isEmpty()) phone = u.phone ?: ""
        }
        profile?.let { p ->
            if (positionPrimary.isEmpty()) positionPrimary = p.positionPrimary
            if (positionSecondary.isEmpty()) positionSecondary = p.positionSecondary ?: ""
            if (dominantFoot.isEmpty()) dominantFoot = p.dominantFoot
            if (height.isEmpty()) height = p.height?.toString() ?: ""
            if (build.isEmpty()) build = p.build ?: ""
        }
    }

    // Navegación post-guardado
    LaunchedEffect(state.saved) {
        if (state.saved) {
            onBack()
            viewModel.clearSaved()
        }
    }

    // Navegación post-logout
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    // Errores → snackbar
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Diálogo de confirmación de logout
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Se borrará tu sesión local. Tendrás que iniciar sesión de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            },
        )
    }

    val canSave = name.isNotBlank() && dominantFoot.isNotBlank() && positionPrimary.isNotBlank()
        && !state.isSaving && !state.isLoading

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Mi perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { LoadingState() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            // Avatar preview + campo URL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AvatarPreview(name = name, avatarUrl = avatarUrl.ifBlank { null })
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("URL del avatar (opcional)") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Nombre
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Teléfono
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("Ficha técnica")
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "Posición principal *",
                options = POSITIONS,
                selected = positionPrimary,
                onSelected = { positionPrimary = it },
            )
            Spacer(Modifier.height(16.dp))
            DropdownField(
                label = "Posición secundaria",
                options = POSITIONS,
                selected = positionSecondary,
                onSelected = { positionSecondary = it },
            )
            Spacer(Modifier.height(16.dp))
            DropdownField(
                label = "Pierna hábil *",
                options = FEET,
                selected = dominantFoot,
                onSelected = { dominantFoot = it },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = height,
                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) height = it },
                label = { Text("Altura (cm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(16.dp))
            DropdownField(
                label = "Contextura física",
                options = BUILDS,
                selected = build,
                onSelected = { build = it },
            )

            // Stats solo-lectura (si hay perfil cargado)
            profile?.let { StatsSection(it) }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.save(
                        name = name,
                        avatarUrl = avatarUrl.ifBlank { null },
                        phone = phone.ifBlank { null },
                        positionPrimary = positionPrimary,
                        positionSecondary = positionSecondary.ifBlank { null },
                        dominantFoot = dominantFoot,
                        height = height.toIntOrNull(),
                        build = build.ifBlank { null },
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Guardar cambios")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Cerrar sesión")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AvatarPreview(name: String, avatarUrl: String?) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Tu foto de perfil",
                modifier = Modifier.size(64.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatsSection(profile: PlayerProfile) {
    Spacer(Modifier.height(24.dp))
    SectionTitle("Mis estadísticas")
    Spacer(Modifier.height(12.dp))
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatRow("Partidos jugados", "${profile.totalMatches}")
            StatRow("Asistencia", "${"%.0f".format(profile.attendancePct)}%")
            // Reputación entre pares (calificación post-partido en 3 criterios).
            StatRow("⚽ Habilidad", "${"%.1f".format(profile.skillScore)} / 5.0")
            StatRow("🤝 Deportividad", "${"%.1f".format(profile.sportsmanshipScore)} / 5.0")
            StatRow("📋 Responsabilidad", "${"%.1f".format(profile.responsibilityScore)} / 5.0")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
