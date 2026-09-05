import SwiftUI

/// Paleta "Fuego claro": la identidad naranja/rojo de la marca sobre fondo
/// claro. La app es SIEMPRE clara (no hay modo oscuro ni se sigue el del
/// sistema). Espejo de `ui/theme/Theme.kt` + `ElDraftColors.kt` de Android.
enum ElDraftColors {
    // Roles equivalentes al ColorScheme de Material 3.
    static let primary = Color(hex: 0xFF5722)
    static let onPrimary = Color.white
    static let primaryContainer = Color(hex: 0xFFEDE6)
    static let onPrimaryContainer = Color(hex: 0xC5340A)
    static let secondary = Color(hex: 0xE53935)
    static let onSecondary = Color.white
    static let secondaryContainer = Color(hex: 0xFCDAD9)
    static let onSecondaryContainer = Color(hex: 0x8E1B19)
    static let tertiary = Color(hex: 0x3A6EA5)
    static let onTertiary = Color.white
    static let tertiaryContainer = Color(hex: 0xD7E4F2)
    static let onTertiaryContainer = Color(hex: 0x1B3A5C)
    static let background = Color(hex: 0xF2F2F4)
    static let onBackground = Color(hex: 0x1A1A1A)
    static let surface = Color(hex: 0xFFFFFF)
    static let onSurface = Color(hex: 0x1A1A1A)
    static let surfaceVariant = Color(hex: 0xF2F1EC)
    static let onSurfaceVariant = Color(hex: 0x5F5F5F)
    static let outline = Color(hex: 0xD9D7D0)
    static let outlineVariant = Color(hex: 0xE6E4DD)
    static let error = Color(hex: 0xC5340A)
    static let onError = Color.white
    static let errorContainer = Color(hex: 0xFCDAD9)
    static let onErrorContainer = Color(hex: 0x8E1B19)

    // Roles semánticos propios (no cubiertos por Material).
    /// Verde de éxito: sección completada, asistencia registrada.
    static let success = Color(hex: 0x1E9E5A)
    static let onSuccess = Color.white
    static let successContainer = Color(hex: 0xE5F5EC)
    /// Fondo a sangre del splash (igual a surface, pero explícito).
    static let splashBackground = Color(hex: 0xFFFFFF)
    /// Gris acero del wordmark del splash.
    static let splashSteel = Color(hex: 0x1C1D20)
    /// Gris del tagline del splash.
    static let splashTagline = Color(hex: 0x8A8B90)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
