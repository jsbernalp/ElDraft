/// Opacidades semánticas. Reemplazan los `.opacity(X)` con números mágicos.
/// Se aplican sobre un color base (normalmente `onSurface`/`onBackground`)
/// según el contexto. Espejo de `ui/theme/ElDraftAlpha.kt`.
enum ElDraftAlpha {
    /// Texto secundario (subtítulos, descripciones).
    static let textSecondary: Double = 0.7
    /// Texto terciario / metadatos / captions.
    static let textTertiary: Double = 0.6
    /// Texto atenuado: hints, placeholders, estados neutros.
    static let textMuted: Double = 0.5
    /// Tint de íconos de metadatos.
    static let icon: Double = 0.55
    /// Contenido deshabilitado (alpha estándar de M3).
    static let disabled: Double = 0.38
    /// Fondo de contenedor fuerte (chips destacados).
    static let containerStrong: Double = 0.2
    /// Fondo de contenedor (status badges).
    static let container: Double = 0.15
    /// Fondo de contenedor suave (chips sutiles).
    static let containerSoft: Double = 0.12
    /// Separadores / divisores.
    static let divider: Double = 0.08
    /// Bordes muy sutiles (hairline).
    static let hairline: Double = 0.1
    /// Overlays oscuros sobre contenido (scrim).
    static let scrim: Double = 0.55
}
