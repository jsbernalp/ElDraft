import Foundation
import shared

/// Puente `Date` (nativo) <-> `LocalDateTime` (kotlinx-datetime) para poder
/// reusar `defaultScheduledAt` de `shared` tal cual (Fase 0.2 del plan de
/// port). Solo entra/sale por acá la aritmética de fecha/hora sugerida; el
/// resto del formulario trabaja en `Date` nativo — el `DatePicker` de SwiftUI
/// no tiene el quirk de "medianoche UTC del día" del `DatePickerState` de
/// Material3, así que `toPickerMillis`/`pickerMillisToLocalDate` no hacen
/// falta acá.
extension Date {
    var toKotlinLocalDateTime: Kotlinx_datetimeLocalDateTime {
        let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: self)
        return Kotlinx_datetimeLocalDateTime(
            year: Int32(c.year ?? 1970),
            monthNumber: Int32(c.month ?? 1),
            dayOfMonth: Int32(c.day ?? 1),
            hour: Int32(c.hour ?? 0),
            minute: Int32(c.minute ?? 0),
            second: Int32(c.second ?? 0),
            nanosecond: 0
        )
    }
}

extension Kotlinx_datetimeLocalDateTime {
    var toDate: Date {
        var c = DateComponents()
        c.year = Int(year)
        c.month = Int(monthNumber)
        c.day = Int(dayOfMonth)
        c.hour = Int(hour)
        c.minute = Int(minute)
        c.second = Int(second)
        return Calendar.current.date(from: c) ?? Date()
    }
}

/// Fecha/hora sugerida al abrir el formulario de creación (hoy 7 p.m. / esta
/// noche a la siguiente hora en punto / mañana 7 p.m.). Espejo de
/// `domain/convocatory/ScheduleDefaults.kt`, reutilizado tal cual — la
/// implementación real y sus tests viven solo ahí.
enum ScheduleDefaults {
    static var minHoursAhead: Int64 { ScheduleDefaultsKt.MIN_HOURS_AHEAD }

    static func defaultScheduledAt(now: Date = Date()) -> Date {
        ScheduleDefaultsKt.defaultScheduledAt(now: now.toKotlinLocalDateTime).toDate
    }
}
