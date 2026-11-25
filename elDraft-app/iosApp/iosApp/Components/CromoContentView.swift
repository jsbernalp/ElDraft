import SwiftUI
import shared

/// Carta visual del Cromo de un jugador (ficha técnica + reputación), con
/// estilo de "carta coleccionable": cabecera de marca, posiciones en chips,
/// ficha en grid y reputación con barras de progreso. Espejo de
/// `CromoContent.kt` (Android).
///
/// Componente compartido entre `PlayerCromoView` (cromo ajeno) y
/// `ProfileTabView` (cromo propio, que inyecta `footer` con sus acciones
/// "Editar perfil" / "Cerrar sesión").
struct CromoContentView<Footer: View>: View {
    let profile: PlayerProfile
    var name: String? = nil
    var avatarUrl: String? = nil
    @ViewBuilder var footer: () -> Footer

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    CromoHeader(profile: profile, name: name, avatarUrl: avatarUrl)

                    VStack(alignment: .leading, spacing: 0) {
                        TechnicalGrid(profile: profile)

                        Spacer().frame(height: ElDraftSpacing.lg2)

                        HStack(spacing: ElDraftSpacing.xs2) {
                            Image(systemName: MetricIcons.skill)
                                .font(.system(size: ElDraftSpacing.lg))
                                .foregroundStyle(ElDraftColors.primary)
                            Text(String(localized: "cromo_reputation"))
                                .font(ElDraftTypography.labelLarge)
                                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
                        }
                        Spacer().frame(height: ElDraftSpacing.md)

                        ScoreBar(icon: MetricIcons.skill, label: String(localized: "cromo_metric_skill"), score: profile.skillScore)
                        Spacer().frame(height: ElDraftSpacing.md)
                        ScoreBar(icon: MetricIcons.sportsmanship, label: String(localized: "cromo_metric_sportsmanship"), score: profile.sportsmanshipScore)
                        Spacer().frame(height: ElDraftSpacing.md)
                        ScoreBar(icon: MetricIcons.responsibility, label: String(localized: "cromo_metric_responsibility"), score: profile.responsibilityScore)
                    }
                    .padding(ElDraftSpacing.lg)
                }
                .background(ElDraftColors.surface)
                .clipShape(ElDraftShapes.lg)
                .shadow(radius: ElDraftElevation.cardRaised)

                footer()
            }
            .padding(ElDraftSpacing.lg)
        }
    }
}

extension CromoContentView where Footer == EmptyView {
    init(profile: PlayerProfile, name: String? = nil, avatarUrl: String? = nil) {
        self.init(profile: profile, name: name, avatarUrl: avatarUrl, footer: { EmptyView() })
    }
}

/// Cabecera tipo carta: banda de marca con avatar, nombre, posiciones y
/// partidos. Espejo de `CromoHeader` en `CromoContent.kt`.
private struct CromoHeader: View {
    let profile: PlayerProfile
    let name: String?
    let avatarUrl: String?

    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                AvatarBadge(name: name, avatarUrl: avatarUrl, fallback: profile.positionPrimary)

                if let name, !name.trimmingCharacters(in: .whitespaces).isEmpty {
                    Spacer().frame(height: ElDraftSpacing.md2)
                    Text(name)
                        .font(ElDraftTypography.titleLarge)
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                }

                Spacer().frame(height: ElDraftSpacing.sm)
                HStack(spacing: ElDraftSpacing.xs2) {
                    PositionChip(text: profile.positionPrimary, primary: true)
                    if let secondary = profile.positionSecondary, !secondary.isEmpty {
                        PositionChip(text: secondary, primary: false)
                    }
                }
            }
            .frame(maxWidth: .infinity)

            MatchesPlayedBadge(count: Int(profile.totalMatches))
        }
        .padding(.horizontal, ElDraftSpacing.lg)
        .padding(.vertical, ElDraftSpacing.lg2)
        .background(ElDraftColors.primary)
    }
}

/// Badge de partidos jugados, esquina superior derecha de la cabecera.
private struct MatchesPlayedBadge: View {
    let count: Int

    var body: some View {
        Text(String(format: String(localized: "cromo_matches_played"), count))
            .font(ElDraftTypography.labelMedium)
            .fontWeight(.semibold)
            .foregroundStyle(.white)
            .padding(.horizontal, ElDraftSpacing.md2)
            .padding(.vertical, ElDraftSpacing.xs)
            .background(Color.white.opacity(ElDraftAlpha.containerStrong), in: ElDraftShapes.pill)
    }
}

/// Avatar circular con borde claro: foto si hay `avatarUrl`, si no la
/// inicial del nombre (o de la posición, o un `?`).
private struct AvatarBadge: View {
    let name: String?
    let avatarUrl: String?
    let fallback: String

    private var initial: String {
        if let first = name?.trimmingCharacters(in: .whitespaces).first {
            return String(first).uppercased()
        }
        if let first = fallback.first {
            return String(first).uppercased()
        }
        return String(localized: "cromo_initial_fallback")
    }

    var body: some View {
        ZStack {
            Circle().fill(.white)
            if let avatarUrl, !avatarUrl.isEmpty, let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
                .frame(width: 82, height: 82) // diámetro interno del avatar, deja un borde blanco — espejo del 82.dp de Android
                .clipShape(Circle())
                .accessibilityLabel(String(localized: "cromo_avatar_content_description"))
            } else {
                Text(initial)
                    .font(ElDraftTypography.headlineLarge)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.primary)
            }
        }
        .frame(width: ElDraftSizes.avatarLg, height: ElDraftSizes.avatarLg)
    }
}

private struct PositionChip: View {
    let text: String
    let primary: Bool

    var body: some View {
        Text(text)
            .font(ElDraftTypography.labelMedium)
            .fontWeight(primary ? .semibold : .regular)
            .foregroundStyle(.white)
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.xs)
            .background(
                Color.white.opacity(primary ? 0.28 : 0.16), // design-tokens-ignore: contraste de marca del chip de posición, espejo de Android
                in: ElDraftShapes.pill
            )
    }
}

/// Ficha técnica en grid 2×2 de mini-cards. Espejo de `TechnicalGrid` en
/// `CromoContent.kt`.
private struct TechnicalGrid: View {
    let profile: PlayerProfile

    private var none: String { String(localized: "cromo_value_none") }

    var body: some View {
        VStack(spacing: ElDraftSpacing.md2) {
            HStack(spacing: ElDraftSpacing.md2) {
                InfoTile(label: String(localized: "cromo_dominant_foot"), value: profile.dominantFoot)
                InfoTile(
                    label: String(localized: "cromo_tile_height"),
                    value: profile.height.map { String(format: String(localized: "cromo_tile_height_value"), Int($0.intValue)) } ?? none
                )
            }
            HStack(spacing: ElDraftSpacing.md2) {
                InfoTile(label: String(localized: "cromo_tile_build"), value: profile.build ?? none)
                InfoTile(
                    label: String(localized: "cromo_tile_attendance"),
                    value: String(format: String(localized: "cromo_attendance_value"), Int(profile.attendancePct)),
                    accent: true
                )
            }
        }
    }
}

private struct InfoTile: View {
    let label: String
    let value: String
    var accent: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.xxs) {
            Text(label)
                .font(ElDraftTypography.labelSmall)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
            Text(value)
                .font(ElDraftTypography.titleSmall)
                .fontWeight(.semibold)
                .foregroundStyle(accent ? ElDraftColors.primary : ElDraftColors.onSurface)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ElDraftSpacing.md)
        .padding(.vertical, ElDraftSpacing.md2)
        .background(ElDraftColors.surfaceVariant, in: ElDraftShapes.sm)
    }
}

/// Una métrica de reputación: ícono + label + nota + barra de progreso
/// (0..5). Espejo de `ScoreBar` en `CromoContent.kt`.
private struct ScoreBar: View {
    let icon: String
    let label: String
    let score: Double

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                HStack(spacing: ElDraftSpacing.xs2) {
                    Image(systemName: icon)
                        .font(.system(size: ElDraftSpacing.lg))
                        .foregroundStyle(ElDraftColors.primary)
                    Text(label)
                        .font(ElDraftTypography.bodyMedium)
                        .foregroundStyle(ElDraftColors.onSurface)
                }
                Spacer()
                Text(formatScore(score))
                    .font(ElDraftTypography.bodyMedium)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.onSurface)
            }
            Spacer().frame(height: ElDraftSpacing.xs)
            ScoreProgressBar(progress: min(max(score / 5.0, 0), 1))
        }
    }
}

/// Barra de progreso a mano: `ProgressView` no permite fijar el grosor
/// (7pt, espejo de Android), así que se dibuja con dos cápsulas.
private struct ScoreProgressBar: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(ElDraftColors.surfaceVariant)
                Capsule()
                    .fill(ElDraftColors.primary)
                    .frame(width: geo.size.width * progress)
            }
        }
        .frame(height: 7) // grosor de la barra, espejo del 7.dp de Android
    }
}

/// Formatea una nota 0..5 a un decimal (p. ej. 4.3), sin ceros sobrantes.
/// Espejo de `fmtScore` en `CromoContent.kt`.
private func formatScore(_ value: Double) -> String {
    let rounded = (value * 10).rounded() / 10
    if rounded.truncatingRemainder(dividingBy: 1) == 0 {
        return String(Int(rounded))
    }
    return String(rounded)
}
