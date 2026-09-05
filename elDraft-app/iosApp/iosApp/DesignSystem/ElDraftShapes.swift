import SwiftUI

/// Radios de esquina de la app. `pill` es totalmente redondeado
/// (chips/badges); el resto escalan de menor a mayor. Espejo de
/// `ui/theme/ElDraftShapes.kt`. `AnyShape` (iOS 17+) permite que cada token
/// sea directamente un `Shape`, igual que en Compose.
enum ElDraftShapes {
    static let pill = AnyShape(Capsule())
    static let sm = AnyShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    static let field = AnyShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    static let md = AnyShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    static let lg = AnyShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
}
