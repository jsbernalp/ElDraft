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

    static var apiConfig: ApiConfig {
        ApiConfig(baseUrl: apiBaseURL, wsBaseUrl: wsBaseURL)
    }

    private static func infoPlistString(_ key: String) -> String {
        Bundle.main.object(forInfoDictionaryKey: key) as? String ?? ""
    }
}
