package com.eldraft.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldraft.android.R
import com.eldraft.android.notifications.FcmTokenSync
import com.eldraft.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// Colores del splash sobre fondo claro (a tono con el tema claro de la app).
private val SplashBackground = Color(0xFFFFFFFF)
private val SplashSteel = Color(0xFF1C1D20)
private val SplashOrange = Color(0xFFFF5722)
private val SplashTagline = Color(0xFF8A8B90)

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
            .background(SplashBackground),
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

            Spacer(Modifier.height(20.dp))

            // Wordmark "elDraft": "el" en acero, "Draft" en naranja de marca.
            Row {
                Text(
                    text = "el",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = SplashSteel,
                )
                Text(
                    text = "Draft",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = SplashOrange,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "ENCUENTRA TU PARTIDO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                color = SplashTagline,
                textAlign = TextAlign.Center,
            )
        }
    }
}
