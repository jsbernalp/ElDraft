import SwiftUI

/// Roles tipográficos de Material 3 que la app usa. Espejo de
/// `ui/theme/Typography.kt`. El `letterSpacing` de Compose no tiene
/// equivalente en `Font`; se aplica con `.tracking()` en el call site donde
/// haga falta (ver `ElDraftTextStyles`).
enum ElDraftTypography {
    static let headlineLarge = Font.system(size: 32, weight: .heavy)
    static let headlineMedium = Font.system(size: 24, weight: .bold)
    static let titleLarge = Font.system(size: 20, weight: .bold)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    static let labelMedium = Font.system(size: 12, weight: .semibold)
}

/// Estilos tipográficos de marca que no encajan en un rol de
/// `ElDraftTypography` (se usan en un único sitio muy específico), con el
/// tracking que en Android lleva el propio `TextStyle`.
enum ElDraftTextStyles {
    /// Wordmark "elDraft" del splash.
    static let wordmark = Font.system(size: 44, weight: .black)
    static let wordmarkTracking: CGFloat = -0.5

    /// Tagline del splash: mayúsculas con tracking ancho (overline de marca).
    static let tagline = Font.system(size: 12, weight: .medium)
    static let taglineTracking: CGFloat = 4
}
