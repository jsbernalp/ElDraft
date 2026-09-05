import CoreGraphics

/// Escala de spacing de la app. Cubre los valores de padding/gap recurrentes
/// en la UI. Las dimensiones one-off de un componente concreto (alturas
/// fijas, tamaños de avatar, etc.) NO viven aquí. Espejo de
/// `ui/theme/ElDraftSpacing.kt`.
enum ElDraftSpacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let xs2: CGFloat = 6
    static let sm: CGFloat = 8
    static let md2: CGFloat = 10
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let lg2: CGFloat = 20
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
}
