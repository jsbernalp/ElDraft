import CoreGraphics

/// Elevaciones de la app, expresadas como radio de sombra (no hay
/// `tonalElevation` de Material en SwiftUI). Espejo de
/// `ui/theme/ElDraftElevation.kt`.
enum ElDraftElevation {
    /// Card estándar (lista de convocatorias).
    static let card: CGFloat = 2
    /// Card destacada (secciones del formulario).
    static let cardRaised: CGFloat = 3
    /// Superficie flotante sobre contenido (overlays, pills sobre cámara).
    static let overlay: CGFloat = 4
}
