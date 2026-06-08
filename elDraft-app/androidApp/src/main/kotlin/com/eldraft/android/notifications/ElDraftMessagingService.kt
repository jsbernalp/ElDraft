package com.eldraft.android.notifications

import com.eldraft.data.local.SessionStore
import com.eldraft.domain.usecase.auth.RegisterFcmTokenUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Recibe los mensajes FCM y el rotado de token del dispositivo.
 *  - onNewToken: registra el token en el backend (solo si hay sesión).
 *  - onMessageReceived: muestra una notificación local cuando la app está en
 *    primer plano (los mensajes "notification" en background los muestra el SO).
 */
class ElDraftMessagingService : FirebaseMessagingService() {

    private val registerFcmToken: RegisterFcmTokenUseCase by inject()
    private val sessionStore: SessionStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            // Solo tiene sentido registrar si el usuario ya inició sesión.
            if (!sessionStore.currentToken().isNullOrBlank()) {
                registerFcmToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "elDraft"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        NotificationHelper.show(applicationContext, title, body)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
