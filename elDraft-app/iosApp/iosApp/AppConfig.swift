import Foundation
import shared

/// Lee los endpoints y la key de Maps desde el Info.plist de la configuración
/// activa (Debug/Release), inyectados ahí por el `.xcconfig` correspondiente.
/// Mismo patrón que `local.properties` → `BuildConfig` en Android; ver
/// `Config/Debug.xcconfig.example` y `Config/Release.xcconfig.example`.
enum AppConfig {
    static let apiBaseURL: String = infoPlistString("API_BASE_URL")
    static let wsBaseURL: String = infoPlistString("WS_BASE_URL")
    static let mapsApiKey: String = infoPlistString("MAPS_API_KEY_IOS")
    /// Sirve siempre el backend de producción, sea cual sea `apiBaseURL`
    /// (ver `Config/Debug.xcconfig`). Usado por `ProfileEditView` para el
    /// enlace a la política de privacidad.
    static let legalBaseURL: String = infoPlistString("LEGAL_BASE_URL")

    static var apiConfig: ApiConfig {
        ApiConfig(baseUrl: apiBaseURL, wsBaseUrl: wsBaseURL)
    }

    private static func infoPlistString(_ key: String) -> String {
        Bundle.main.object(forInfoDictionaryKey: key) as? String ?? ""
    }
}
