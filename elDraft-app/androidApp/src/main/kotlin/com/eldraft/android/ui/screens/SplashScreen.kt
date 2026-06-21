package com.eldraft.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eldraft.android.R
import com.eldraft.android.notifications.FcmTokenSync
import com.eldraft.android.ui.theme.ElDraftTextStyles
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    authRepository: AuthRepository = koinInject(),
    fcmTokenSync: FcmTokenSync = koinInject(),
) {
    LaunchedEffect(Unit) {
        delay(1200)
        // El token (si existe) lo leen las APIs vía AuthTokenProvider; aquí
        // solo decidimos a qué pantalla entrar.
        if (authRepository.hasSession()) {
            // Refresca el token FCM en cada arranque con sesión: corrige tokens
            // muertos por reinstalación/rotación (UNREGISTERED) sin re-login.
            fcmTokenSync.syncIfLoggedIn()
            onNavigateToHome()
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElDraftTheme.colors.splashBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // El ícono circular de la app (avatar sobre fondo naranja). Usamos un
            // WebP raster (logo_app_round) en vez de @mipmap/ic_launcher_round
            // porque este último resuelve al <adaptive-icon> XML en API 26+, que
            // painterResource no sabe cargar (solo VectorDrawable o bitmaps).
            Image(
                painter = painterResource(R.drawable.logo_app_round),
                contentDescription = "elDraft",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.height(ElDraftTheme.spacing.lg2))

            // Wordmark "elDraft": "el" en acero, "Draft" en naranja de marca.
            Row {
                Text(
                    text = "el",
                    style = ElDraftTextStyles.Wordmark,
                    color = ElDraftTheme.colors.splashSteel,
                )
                Text(
                    text = "Draft",
                    style = ElDraftTextStyles.Wordmark,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.xs))

            Text(
                text = "ENCUENTRA TU PARTIDO",
                style = ElDraftTextStyles.Tagline,
                color = ElDraftTheme.colors.splashTagline,
                textAlign = TextAlign.Center,
            )
        }
    }
}
