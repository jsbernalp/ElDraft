package com.eldraft.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eldraft.android.MainActivity
import com.eldraft.android.R

/**
 * Centraliza la creación de canales y el despliegue de notificaciones locales.
 *
 * Hay un canal por categoría para que el usuario pueda silenciar unas y no
 * otras (Ajustes de Android → Notificaciones). Cada notificación elige su canal,
 * emoji y grupo según el `type` que envía el backend en el payload de datos.
 */
object NotificationHelper {

    /** Color de acento de marca (tiñe ícono y título). Definido en colors.xml. */
    private fun brandColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.orange_vibrant)

    /**
     * Categorías de notificación. Cada una es un canal de Android propio y un
     * grupo para que varias del mismo tipo se apilen en vez de saturar la barra.
     */
    enum class Category(
        val channelId: String,
        val channelName: String,
        val channelDesc: String,
        val groupKey: String,
    ) {
        CONVOCATORIES(
            channelId = "eldraft_convocatories",
            channelName = "Convocatorias",
            channelDesc = "Nuevos partidos y recordatorios cerca de ti",
            groupKey = "com.eldraft.group.CONVOCATORIES",
        ),
        POSTULATIONS(
            channelId = "eldraft_postulations",
            channelName = "Postulaciones",
            channelDesc = "Postulaciones a tus partidos y respuestas a las tuyas",
            groupKey = "com.eldraft.group.POSTULATIONS",
        ),
        GENERAL(
            channelId = "eldraft_general",
            channelName = "General",
            channelDesc = "Otras novedades de elDraft",
            groupKey = "com.eldraft.group.GENERAL",
        ),
    }

    /**
     * Mapea el `type` del backend a su categoría y a un emoji que precede el
     * título. Un `type` desconocido cae en GENERAL sin emoji.
     */
    private fun decorate(type: String?): Pair<Category, String> = when (type) {
        "new_convocatory" -> Category.CONVOCATORIES to "⚽ "
        "convocatory_reminder" -> Category.CONVOCATORIES to "⏰ "
        "new_postulation" -> Category.POSTULATIONS to "🙋 "
        "postulation_approved" -> Category.POSTULATIONS to "✅ "
        "postulation_rejected" -> Category.POSTULATIONS to "📋 "
        else -> Category.GENERAL to ""
    }

    /** Crea todos los canales (idempotente). Llamar al iniciar la app. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        Category.entries.forEach { cat ->
            val channel = NotificationChannel(
                cat.channelId,
                cat.channelName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = cat.channelDesc }
            manager.createNotificationChannel(channel)
        }
    }

    /** Compat: algunos llamadores antiguos usaban ensureChannel(). */
    fun ensureChannel(context: Context) = ensureChannels(context)

    /**
     * Muestra una notificación local, eligiendo canal/emoji/grupo según [type].
     * Comprueba el permiso POST_NOTIFICATIONS (Android 13+) para no lanzar
     * SecurityException si no está concedido.
     */
    fun show(context: Context, title: String, body: String, type: String? = null) {
        ensureChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val (category, emoji) = decorate(type)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_eldraft)
            .setColor(brandColor(context))
            .setContentTitle(emoji + title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(category.groupKey)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(context)
        manager.notify(System.currentTimeMillis().toInt(), notification)

        // Resumen del grupo: agrupa varias notificaciones de la misma categoría
        // bajo un solo encabezado (Android 7+).
        val summary = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_eldraft)
            .setColor(brandColor(context))
            .setGroup(category.groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(category.groupKey.hashCode(), summary)
    }
}
