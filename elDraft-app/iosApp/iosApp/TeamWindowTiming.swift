import Foundation
import SwiftUI
import shared

/// Envoltura de `TeamWindow` (shared) para Swift. Las reglas viven en Kotlin
/// (la ventana debe cerrar ≥ 1 h antes del partido); aquí solo se arman los
/// `LocalDateTime` de reloj de pared que esa API espera, igual que
/// `MatchTiming` arma `Instant`/`TimeZone`.
enum TeamWindowTiming {
    /// `Date` → reloj de pared local (como lo manda el formulario).
    static func localDateTime(from date: Date) -> Kotlinx_datetimeLocalDateTime {
        let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        return Kotlinx_datetimeLocalDateTime(
            year: Int32(c.year ?? 1970), monthNumber: Int32(c.month ?? 1), dayOfMonth: Int32(c.day ?? 1),
            hour: Int32(c.hour ?? 0), minute: Int32(c.minute ?? 0), second: Int32(c.second ?? 0), nanosecond: 0
        )
    }

    /// ISO local del backend (`2026-11-27T19:30:00`, con o sin fracción). Nil si
    /// no se puede leer: mejor omitir la cuenta regresiva que inventarla.
    static func localDateTime(fromISO iso: String) -> Kotlinx_datetimeLocalDateTime? {
        let nums = iso.prefix(19).split(whereSeparator: { !$0.isNumber }).compactMap { Int32($0) }
        guard nums.count >= 5 else { return nil }
        return Kotlinx_datetimeLocalDateTime(
            year: nums[0], monthNumber: nums[1], dayOfMonth: nums[2],
            hour: nums[3], minute: nums[4], second: nums.count > 5 ? nums[5] : 0, nanosecond: 0
        )
    }

    static func options(scheduledAt: Date) -> [TeamWindowOption] {
        TeamWindow.shared.options(scheduledAt: localDateTime(from: scheduledAt), now: localDateTime(from: Date()))
    }

    static func isValid(minutes: Int?, scheduledAt: Date) -> Bool {
        TeamWindow.shared.isValid(
            minutes: minutes.map { KotlinInt(int: Int32($0)) },
            scheduledAt: localDateTime(from: scheduledAt),
            now: localDateTime(from: Date())
        )
    }

    /// Minutos que faltan para `endsAt` (nil si la fecha no se puede leer).
    static func minutesLeft(endsAt: String?) -> Int? {
        guard let endsAt, let target = localDateTime(fromISO: endsAt) else { return nil }
        return Int(TeamWindow.shared.minutesLeft(endsAt: target, now: localDateTime(from: Date())))
    }

    /// Etiqueta corta de una ventana: 30 min / 1 h / 2 h / 6 h.
    static func label(minutes: Int) -> String {
        switch minutes {
        case 60: return String(localized: "team_window_60")
        case 120: return String(localized: "team_window_120")
        case 360: return String(localized: "team_window_360")
        default: return String(localized: "team_window_30")
        }
    }
}

/// Minutos restantes hasta `endsAt`, refrescados cada 15 s. Espejo de
/// `rememberMinutesLeft` en `TeamPriorityCard.kt`.
struct MinutesLeftView<Content: View>: View {
    let endsAt: String?
    @ViewBuilder let content: (Int?) -> Content

    var body: some View {
        TimelineView(.periodic(from: .now, by: 15)) { _ in
            content(TeamWindowTiming.minutesLeft(endsAt: endsAt))
        }
    }
}
