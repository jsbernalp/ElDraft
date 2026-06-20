package com.eldraft.android

import android.Manifest
import android.content.Intent
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

    // URI del deep link que llegó por intent (notificación tapada). Se pasa a
    // ElDraftApp para que navegue al destino correcto tras el login/splash.
    private var pendingDeepLinkUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkUri = intent?.data
        requestNotificationPermissionIfNeeded()
        val barScrim = LightBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(barScrim, barScrim),
            navigationBarStyle = SystemBarStyle.light(barScrim, barScrim),
        )
        setContent {
            KoinAndroidContext {
                ElDraftTheme {
                    ElDraftApp(deepLinkUri = pendingDeepLinkUri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // La actividad ya estaba abierta (FLAG_ACTIVITY_SINGLE_TOP): recibimos el
        // nuevo intent aquí. Actualizamos el campo para que Compose lo recoja.
        pendingDeepLinkUri = intent.data
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
