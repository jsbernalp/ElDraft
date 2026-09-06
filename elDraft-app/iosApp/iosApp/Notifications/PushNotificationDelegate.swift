import UIKit
import UserNotifications

/// Delegate de `UNUserNotificationCenter`. Combina lo que en Android son dos
/// caminos separados:
///  - Con la app en foreground, Android igual muestra el banner con sonido
///    (`NotificationHelper.show`, no el default silencioso del SO); acá lo
///    replica `willPresent`.
///  - Al tocar la notificación, Android navega vía el `PendingIntent` con el
///    deep link (`NotificationHelper.deepLinkUri`); acá `didReceive` reabre
///    la propia app por su esquema `eldraft://`, reusando el mismo
///    `.onOpenURL` que ya resuelve esos links en `RootView` — sin duplicar la
///    lógica de navegación en dos sitios.
final class PushNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = PushNotificationDelegate()
    private override init() {}

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let raw = response.notification.request.content.userInfo["deepLink"] as? String,
           let url = URL(string: raw) {
            Task { @MainActor in
                UIApplication.shared.open(url)
            }
        }
        completionHandler()
    }
}
