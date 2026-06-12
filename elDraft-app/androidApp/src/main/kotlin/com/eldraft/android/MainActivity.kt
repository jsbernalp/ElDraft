package com.eldraft.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.eldraft.android.ui.ElDraftApp
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.android.ui.theme.LightBackground
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        // La app es siempre clara: forzamos barras claras (íconos oscuros) sin
        // importar el modo del sistema. El scrim usa el gris del fondo
        // (LightBackground) para que status/navigation bar combinen con la app.
        val barScrim = LightBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(barScrim, barScrim),
            navigationBarStyle = SystemBarStyle.light(barScrim, barScrim),
        )
        setContent {
            // Expone el contexto de Koin a Compose (koinViewModel/koinInject)
            KoinAndroidContext {
                ElDraftTheme {
                    ElDraftApp()
                }
            }
        }
    }

    /** Pide el permiso de notificaciones en Android 13+ (no existe antes). */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
