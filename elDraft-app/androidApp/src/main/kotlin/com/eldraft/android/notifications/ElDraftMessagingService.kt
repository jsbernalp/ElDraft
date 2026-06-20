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
    private val refreshBus: NotificationRefreshBus by inject()
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
        // El `type` (new_convocatory, convocatory_reminder, new_postulation…)
        // elige el canal, el emoji y el grupo de la notificación.
        val type = message.data["type"]
        val extraId = message.data["convocatoryId"]
        NotificationHelper.show(applicationContext, title, body, type, extraId)
        emitRefreshEvents(type)
    }

    private fun emitRefreshEvents(type: String?) {
        when (type) {
            "new_postulation", "postulation_withdrawn" -> {
                refreshBus.emit(RefreshEvent.MY_MATCHES)
                refreshBus.emit(RefreshEvent.APPLICANTS)
            }
            "postulation_approved", "postulation_rejected", "convocatory_cancelled" -> {
                refreshBus.emit(RefreshEvent.MY_POSTULATIONS)
            }
            "new_convocatory" -> {
                refreshBus.emit(RefreshEvent.MAP)
            }
            "convocatory_reminder" -> {
                refreshBus.emit(RefreshEvent.MY_POSTULATIONS)
                refreshBus.emit(RefreshEvent.MAP)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
