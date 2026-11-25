import CoreGraphics

/// Tamaños de ícono/avatar de la app. Unifican los `.frame(width:height:)`
/// que se aplicarían a íconos por toda la UI. Las dimensiones one-off de
/// componente NO viven aquí. Espejo de `ui/theme/ElDraftSizes.kt`.
enum ElDraftSizes {
    /// Ícono diminuto (chips de metadatos compactos).
    static let iconXs: CGFloat = 12
    /// Ícono pequeño (metadatos de card).
    static let iconSm: CGFloat = 16
    /// Ícono mediano (acciones, banners). El más común.
    static let iconMd: CGFloat = 18
    /// Ícono grande (botones destacados, headers).
    static let iconLg: CGFloat = 20
    /// Avatar circular estándar (cards de personas).
    static let avatar: CGFloat = 44
    /// Avatar mediano (edición de perfil).
    static let avatarMd: CGFloat = 64
    /// Avatar grande (cabecera del Cromo).
    static let avatarLg: CGFloat = 88
    /// Grosor de línea de spinners e indicadores de progreso.
    static let stroke: CGFloat = 2
    /// Borde fino (outlines de campos en estado de error).
    static let borderHairline: CGFloat = 1
}
