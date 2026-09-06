import Foundation
import shared

/// Envoltura de `MatchTimingKt` (shared) con los valores por defecto que
/// Kotlin le da a `now`/`zone` (`Clock.System.now()` /
/// `TimeZone.currentSystemDefault()`): los parámetros con valor por defecto no
/// cruzan el puente Objective-C, así que cada llamada real desde Swift los
/// exige explícitos. Se reconstruyen aquí una sola vez para que las cards no
/// tengan que armar `Instant`/`TimeZone` cada vez. Espejo de
/// `domain/match/MatchTiming.kt`.
enum MatchTiming {
    static var matchEndMinutes: Int64 { MatchTimingKt.MATCH_END_MINUTES }

    private static var now: Kotlinx_datetimeInstant {
        Kotlinx_datetimeInstant.Companion.shared.fromEpochMilliseconds(
            epochMilliseconds: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }

    private static var zone: Kotlinx_datetimeTimeZone {
        Kotlinx_datetimeTimeZone.Companion.shared.currentSystemDefault()
    }

    static func minutesSinceStart(_ scheduledAt: String) -> Int64? {
        MatchTimingKt.minutesSinceStart(scheduledAt: scheduledAt, now: now, zone: zone)?.int64Value
    }

    static func isMatchOver(_ scheduledAt: String) -> Bool {
        MatchTimingKt.isMatchOver(scheduledAt: scheduledAt, now: now, zone: zone)
    }

    static func hasMatchStarted(_ scheduledAt: String) -> Bool {
        MatchTimingKt.hasMatchStarted(scheduledAt: scheduledAt, now: now, zone: zone)
    }

    static func canReportNoShowByTime(_ scheduledAt: String) -> Bool {
        MatchTimingKt.canReportNoShowByTime(scheduledAt: scheduledAt, now: now, zone: zone)
    }
}
