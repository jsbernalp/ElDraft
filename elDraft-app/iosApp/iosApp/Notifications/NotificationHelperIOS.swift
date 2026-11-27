import Foundation
import UserNotifications

/// Categoría de notificación. Android tiene un `NotificationChannel` propio
/// por categoría, silenciable por separado desde Ajustes; iOS no ofrece ese
/// control granular (solo el toggle general de la app), así que aquí la
/// categoría solo aporta `threadIdentifier` (agrupa en Notification Center,
/// el equivalente más cercano al `groupKey` de Android).
enum PushCategory: String, CaseIterable {
    case convocatories = "eldraft_convocatories"
    case postulations = "eldraft_postulations"
    case teams = "eldraft_teams"
    case general = "eldraft_general"

    var categoryObject: UNNotificationCategory {
        UNNotificationCategory(identifier: rawValue, actions: [], intentIdentifiers: [], options: [])
    }
}

/// Espejo de `NotificationHelper.kt`: decide categoría/emoji/deep-link según
/// el `type` del payload y muestra la notificación local. Hoy no hay push
/// remoto en iOS (Fase 8 del plan de port sigue diferida hasta tener cuenta
/// Apple Developer de pago para el APNs Auth Key), así que `show` todavía no
/// lo dispara ningún backend real — queda listo para el `MessagingDelegate`
/// que se conecte cuando exista push, y se puede ejercitar hoy mismo desde
/// `DebugHealthCheckView` para verificar el camino completo (categoría,
/// sonido, deep link, refresco) sin depender de la cuenta.
enum NotificationHelperIOS {

    /** Registra las categorías (idempotente). Llamar al iniciar la app. */
    static func ensureCategories() {
        UNUserNotificationCenter.current().setNotificationCategories(Set(PushCategory.allCases.map(\.categoryObject)))
    }

    static func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// Mapea el `type` del backend a su categoría y a un emoji que precede el
    /// título. Un `type` desconocido cae en `.general` sin emoji.
    private static func decorate(type: String?) -> (category: PushCategory, emoji: String) {
        switch type {
        case "new_convocatory": return (.convocatories, "⚽ ")
        case "convocatory_reminder": return (.convocatories, "⏰ ")
        case "convocatory_cancelled": return (.convocatories, "❌ ")
        case "new_postulation", "postulation_withdrawn": return (.postulations, "🙋 ")
        case "postulation_approved": return (.postulations, "✅ ")
        case "postulation_rejected": return (.postulations, "📋 ")
        case "team_invite", "team_priority", "team_window_opened": return (.teams, "👥 ")
        default: return (.general, "")
        }
    }

    /// Deep link al que navega el tap de la notificación. Espejo de
    /// `NotificationHelper.deepLinkUri` — a diferencia de la versión Android
    /// (que tiene un bug de índice para "applicants", ver `ElDraftApp.kt`),
    /// usa el primer y único segmento de path.
    static func deepLinkURL(type: String?, extraId: String?) -> URL? {
        switch type {
        case "new_postulation":
            if let extraId, !extraId.isEmpty {
                return URL(string: "eldraft://applicants/\(extraId)")
            }
            return URL(string: "eldraft://tab/organizo")
        case "postulation_approved", "postulation_rejected", "convocatory_cancelled":
            return URL(string: "eldraft://tab/juego")
        case "new_convocatory", "convocatory_reminder":
            return URL(string: "eldraft://tab/buscar_cupo")
        case "team_invite":
            return URL(string: "eldraft://teams/invitations")
        case "team_priority":
            return URL(string: "eldraft://tab/juego")
        case "team_window_opened":
            return URL(string: "eldraft://tab/organizo")
        default:
            return nil
        }
    }

    /// Muestra una notificación local, análoga a `NotificationHelper.show` de
    /// Android, y emite los eventos de refresco correspondientes — Android lo
    /// hace en el mismo punto (`onMessageReceived`), sin esperar a que el
    /// usuario toque la notificación. `@MainActor` porque `emitRefreshEvents`
    /// toca `NotificationRefreshBus`, que los ViewModels leen en ese actor.
    @MainActor
    static func show(title: String, body: String, type: String? = nil, extraId: String? = nil) {
        let (category, emoji) = decorate(type: type)

        let content = UNMutableNotificationContent()
        content.title = emoji + title
        content.body = body
        content.categoryIdentifier = category.rawValue
        content.threadIdentifier = category.rawValue
        content.sound = UNNotificationSound(named: UNNotificationSoundName("notification_eldraft.caf"))
        if let deepLink = deepLinkURL(type: type, extraId: extraId) {
            content.userInfo = ["deepLink": deepLink.absoluteString]
        }

        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)

        emitRefreshEvents(type: type)
    }

    @MainActor
    private static func emitRefreshEvents(type: String?) {
        switch type {
        case "new_postulation", "postulation_withdrawn":
            NotificationRefreshBus.shared.emit(.myMatches)
            NotificationRefreshBus.shared.emit(.applicants)
        case "postulation_approved", "postulation_rejected":
            NotificationRefreshBus.shared.emit(.myPostulations)
        case "convocatory_cancelled":
            NotificationRefreshBus.shared.emit(.myPostulations)
            NotificationRefreshBus.shared.emit(.myMatches)
            NotificationRefreshBus.shared.emit(.map)
        case "new_convocatory":
            NotificationRefreshBus.shared.emit(.map)
        case "convocatory_reminder":
            NotificationRefreshBus.shared.emit(.myPostulations)
            NotificationRefreshBus.shared.emit(.map)
        case "team_invite":
            NotificationRefreshBus.shared.emit(.teams)
        // Un miembro del equipo: aparece la card de prioridad en Juego.
        case "team_priority":
            NotificationRefreshBus.shared.emit(.myPostulations)
        // El organizador: su convocatoria pasó a abierta (quizá publicada).
        case "team_window_opened":
            NotificationRefreshBus.shared.emit(.myMatches)
            NotificationRefreshBus.shared.emit(.applicants)
        default:
            break
        }
    }
}
