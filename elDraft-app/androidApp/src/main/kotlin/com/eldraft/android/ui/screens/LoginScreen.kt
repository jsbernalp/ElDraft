package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.auth.AuthUiState
import com.eldraft.android.ui.auth.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: (needsOnboarding: Boolean) -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Modo: false = iniciar sesión, true = registrarse
    var isRegisterMode by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Estado propio, no el de arriba: confirmar existe para cazar un error de
    // tipeo, así que hay que poder destaparla sola y compararla con la otra.
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        (state as? AuthUiState.Success)?.let { onLoginSuccess(it.needsOnboarding) }
    }
    LaunchedEffect(state) {
        (state as? AuthUiState.Error)?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.resetError()
        }
    }

    val isLoading = state is AuthUiState.Loading

    // Validaciones
    val emailValid = email.contains("@") && email.contains(".")
    val passwordValid = password.length >= 6
    val canSubmitEmail = if (isRegisterMode) {
        name.isNotBlank() && emailValid && passwordValid && password == confirmPassword && !isLoading
    } else {
        email.isNotBlank() && password.isNotBlank() && !isLoading
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = ElDraftTheme.spacing.xxl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(ElDraftTheme.spacing.xxl))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.xs))
            Text(
                text = stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textSecondary),
            )

            Spacer(Modifier.height(ElDraftTheme.spacing.xxl))

            // Toggle Login / Registro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                FilterChip(
                    selected = !isRegisterMode,
                    onClick = { isRegisterMode = false },
                    label = { Text(stringResource(R.string.login_tab_signin)) },
                    modifier = Modifier.padding(end = ElDraftTheme.spacing.sm),
                )
                FilterChip(
                    selected = isRegisterMode,
                    onClick = { isRegisterMode = true },
                    label = { Text(stringResource(R.string.login_create_account)) },
                )
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.xl))

            // Campo nombre (solo en registro)
            AnimatedVisibility(visible = isRegisterMode) {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.login_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(ElDraftTheme.spacing.md))
                }
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(ElDraftTheme.spacing.md))

            // Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(
                        onClick = { passwordVisible = !passwordVisible },
                        contentPadding = PaddingValues(horizontal = ElDraftTheme.spacing.sm),
                    ) {
                        Text(
                            text = if (passwordVisible) stringResource(R.string.login_password_hide)
                                else stringResource(R.string.login_password_show),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                supportingText = if (isRegisterMode && password.isNotEmpty() && !passwordValid) {
                    { Text(stringResource(R.string.login_password_hint_min)) }
                } else null,
            )

            // Confirmar contraseña (solo en registro)
            AnimatedVisibility(visible = isRegisterMode) {
                Column {
                    Spacer(Modifier.height(ElDraftTheme.spacing.md))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.login_confirm_password_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(
                                onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                contentPadding = PaddingValues(horizontal = ElDraftTheme.spacing.sm),
                            ) {
                                Text(
                                    text = if (confirmPasswordVisible) stringResource(R.string.login_password_hide)
                                        else stringResource(R.string.login_password_show),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                        supportingText = if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                            { Text(stringResource(R.string.login_passwords_mismatch)) }
                        } else null,
                    )
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.xl))

            // Botón principal email/password
            Button(
                onClick = {
                    if (isRegisterMode) viewModel.registerWithEmail(name, email, password)
                    else viewModel.signInWithEmail(email, password)
                },
                enabled = canSubmitEmail,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconLg),
                        strokeWidth = ElDraftTheme.size.stroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (isRegisterMode) stringResource(R.string.login_create_account)
                        else stringResource(R.string.login_tab_signin))
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.login_divider_or),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textMuted),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            // Google Sign-In
            OutlinedButton(
                onClick = { viewModel.signInWithGoogle() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_google))
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.xxl))
        }
    }
}
