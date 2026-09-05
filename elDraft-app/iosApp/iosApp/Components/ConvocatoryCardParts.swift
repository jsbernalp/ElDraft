import SwiftUI

// Formatea un `scheduledAt` "naive" (sin zona, como lo manda el backend) tal
// cual llega, sin convertir zona horaria — por eso los formatters de entrada
// y salida comparten `TimeZone(identifier: "UTC")`: solo sirve para que
// Foundation no corra los componentes, no representa la hora real en UTC.
private let scheduleParser: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = TimeZone(identifier: "UTC")
    f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    return f
}()

private let scheduleParserNoSeconds: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = TimeZone(identifier: "UTC")
    f.dateFormat = "yyyy-MM-dd'T'HH:mm"
    return f
}()

private let scheduleDateFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "es")
    f.timeZone = TimeZone(identifier: "UTC")
    f.dateFormat = "EEE d MMM"
    return f
}()

private let scheduleTimeFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "es")
    f.timeZone = TimeZone(identifier: "UTC")
    f.dateFormat = "h:mm a"
    return f
}()

/// Formatea un `scheduledAt` en `LocalDateTime` ISO a algo legible (p. ej.
/// "vie 13 jun · 8:30 p. m."). Devuelve `nil` si no se puede parsear. Espejo
/// de `formatSchedule` en `ConvocatoryCardParts.kt` — igual que
/// `LocalDateTime.parse` (ISO_LOCAL_DATE_TIME), acepta fracción de segundos
/// de cualquier longitud (el backend manda microsegundos); `DateFormatter`
/// solo entiende un número fijo de dígitos, así que se trunca antes.
func formatSchedule(_ scheduledAt: String) -> String? {
    let withoutFraction = scheduledAt.split(separator: ".", maxSplits: 1).first.map(String.init) ?? scheduledAt
    guard let date = scheduleParser.date(from: withoutFraction) ?? scheduleParserNoSeconds.date(from: withoutFraction) else {
        return nil
    }
    return "\(scheduleDateFormatter.string(from: date)) · \(scheduleTimeFormatter.string(from: date))"
}

/// Formatea el costo: "Gratis" si es 0, si no "$X". Espejo de `formatFee` en
/// `ConvocatoryCardParts.kt`.
func formatFee(_ fee: Double) -> String {
    guard fee > 0 else { return "Gratis" }
    if fee.truncatingRemainder(dividingBy: 1) == 0 {
        return "$\(Int(fee))"
    }
    return "$\(fee)"
}

/// Nombres SF Symbols reexportados para no repetirlos en cada card. Espejo de
/// `IconPlace`/`IconGroups`/`IconFee` en `ConvocatoryCardParts.kt`.
enum ConvocatoryIcons {
    static let place = "mappin.and.ellipse"
    static let groups = "person.3.fill"
    static let fee = "tag.fill"
}

/// Una entrada de la fila de metadatos: ícono + texto. Espejo de `MetaItem`
/// en `ConvocatoryCardParts.kt`.
struct MetaItem: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: ElDraftSpacing.xs) {
            Image(systemName: icon)
                .font(.system(size: ElDraftSizes.iconSm))
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
            Text(text)
                .font(ElDraftTypography.bodySmall)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
        }
    }
}

/// Etiqueta legible del estado de mi postulación. Cualquier estado bloquea
/// volver a postularse (el backend solo admite una por convocatoria), por eso
/// todas se muestran. Espejo de `postulationLabel` en
/// `ConvocatoryListContent.kt`.
func postulationLabel(_ status: String) -> String {
    switch status {
    case "approved": return "Aprobada"
    case "rejected": return "Rechazada"
    case "cancelled": return "Cancelada"
    default: return "Ya te postulaste"
    }
}

/// Chip con el estado de mi postulación. `compact` mirroa la versión chica
/// que usa `PinGroupSheet` (`RowPostulationBadge`, labelSmall/padding sm)
/// frente a la de la lista (`PostulationBadge`, labelMedium/padding md2).
///
/// El texto y el fondo se calculan por separado desde el MISMO color base
/// (nunca uno a partir del otro): Compose usa `color.copy(alpha=X)`, que
/// REEMPLAZA el alfa en vez de multiplicarlo como haría anidar
/// `.opacity()` en SwiftUI.
struct PostulationBadge: View {
    let status: String
    var compact: Bool = false

    private var baseColor: Color {
        switch status {
        case "approved": return ElDraftColors.primary
        case "rejected": return ElDraftColors.error
        case "cancelled": return ElDraftColors.onSurface
        default: return ElDraftColors.tertiary
        }
    }

    var body: some View {
        Text(postulationLabel(status))
            .font(compact ? ElDraftTypography.labelSmall : ElDraftTypography.labelMedium)
            .fontWeight(.semibold)
            .foregroundStyle(status == "cancelled" ? baseColor.opacity(ElDraftAlpha.textTertiary) : baseColor)
            .padding(.horizontal, compact ? ElDraftSpacing.sm : ElDraftSpacing.md2)
            .padding(.vertical, ElDraftSpacing.xs)
            .background(baseColor.opacity(ElDraftAlpha.container), in: ElDraftShapes.pill)
    }
}
