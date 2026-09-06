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

/// Banda con fecha/hora del partido en color de marca. Espejo de
/// `ScheduleBanner` en `ConvocatoryCardParts.kt`.
struct ScheduleBanner: View {
    let scheduledAt: String

    var body: some View {
        if let text = formatSchedule(scheduledAt) {
            HStack(spacing: ElDraftSpacing.xs2) {
                Image(systemName: "calendar")
                    .font(.system(size: ElDraftSpacing.lg))
                    .foregroundStyle(ElDraftColors.primary)
                Text(text)
                    .font(ElDraftTypography.labelLarge)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.primary)
            }
        }
    }
}

/// Chip con el estado del partido según el reloj: sin empezar, en curso o
/// terminado. Se recalcula cada vez que la vista se redibuja (al volver a la
/// pestaña, al refrescar), no en vivo mientras se mira la pantalla. Espejo de
/// `MatchStateBadge` en `ConvocatoryCardParts.kt`.
struct MatchStateBadge: View {
    let scheduledAt: String

    var body: some View {
        if let mins = MatchTiming.minutesSinceStart(scheduledAt) {
            let (label, container, content): (String, Color, Color) = {
                if mins >= MatchTiming.matchEndMinutes {
                    return (String(localized: "match_state_finished"), ElDraftColors.onSurface.opacity(ElDraftAlpha.hairline), ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
                } else if mins >= 0 {
                    return (String(localized: "match_state_in_progress"), ElDraftColors.primary.opacity(ElDraftAlpha.container), ElDraftColors.primary)
                } else {
                    return (String(localized: "match_state_not_started"), ElDraftColors.tertiary.opacity(ElDraftAlpha.container), ElDraftColors.tertiary)
                }
            }()
            Text(label)
                .font(ElDraftTypography.labelMedium)
                .fontWeight(.semibold)
                .foregroundStyle(content)
                .padding(.horizontal, ElDraftSpacing.md2)
                .padding(.vertical, ElDraftSpacing.xs)
                .background(container, in: ElDraftShapes.pill)
        }
    }
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
/// Chip de metadato pequeño (ambiente, dirección, cupos, cuota). `primary`
/// resalta el ambiente. Espejo de `MetaChip` en `TabScreens.kt`, compartido
/// por Organizo (`MyMatchCard`) y Juego (`MyGameCard`).
struct MetaChip: View {
    let text: String
    var icon: String? = nil
    var primary: Bool = false

    var body: some View {
        HStack(spacing: ElDraftSpacing.xs) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: ElDraftSizes.iconXs))
            }
            Text(text)
                .font(ElDraftTypography.labelSmall)
        }
        .foregroundStyle(primary ? ElDraftColors.primary : ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
        .padding(.horizontal, ElDraftSpacing.sm)
        .padding(.vertical, ElDraftSpacing.xs)
        .background(
            primary ? ElDraftColors.primary.opacity(ElDraftAlpha.containerSoft) : ElDraftColors.onSurface.opacity(ElDraftAlpha.divider),
            in: ElDraftShapes.pill
        )
    }
}

/// Confirmación de que la asistencia quedó registrada. Sustituye al botón "Ya
/// llegué" en cuanto el escaneo se valida. Espejo de `AttendanceRegistered`
/// en `TabScreens.kt`, compartido por Organizo y Juego.
struct AttendanceRegisteredView: View {
    var body: some View {
        HStack(spacing: ElDraftSpacing.xs2) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(ElDraftColors.primary)
            Text(String(localized: "attendance_registered"))
                .font(ElDraftTypography.labelMedium)
                .fontWeight(.semibold)
                .foregroundStyle(ElDraftColors.primary)
        }
    }
}

/// Acceso rápido: ícono en pastilla circular con etiqueta debajo. `primary`
/// resalta la acción principal con pastilla sólida. Espejo de `QuickAction`
/// en `TabScreens.kt`, compartido por Organizo y Juego.
struct QuickActionButton: View {
    let icon: String
    let label: String
    var primary: Bool = false
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: ElDraftSpacing.xs2) {
                ZStack {
                    Circle()
                        .fill(containerColor)
                        .frame(width: 46, height: 46) // diámetro de la pastilla, espejo del 46.dp de Android
                    Image(systemName: icon)
                        .foregroundStyle(tintColor)
                }
                Text(label)
                    .font(ElDraftTypography.labelMedium)
                    .foregroundStyle(ElDraftColors.onSurface.opacity(enabled ? 1 : ElDraftAlpha.disabled))
            }
            .padding(.horizontal, ElDraftSpacing.sm)
            .padding(.vertical, ElDraftSpacing.xs)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private var containerColor: Color {
        guard enabled else { return ElDraftColors.onSurface.opacity(ElDraftAlpha.containerSoft) }
        return primary ? ElDraftColors.primary : ElDraftColors.primaryContainer
    }

    private var tintColor: Color {
        guard enabled else { return ElDraftColors.onSurface.opacity(ElDraftAlpha.disabled) }
        return primary ? ElDraftColors.onPrimary : ElDraftColors.primary
    }
}

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
