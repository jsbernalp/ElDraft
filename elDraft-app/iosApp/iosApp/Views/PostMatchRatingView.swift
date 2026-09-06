import SwiftUI
import shared

/// Calificación post-partido en 3 criterios por compañero de convocatoria.
/// Espejo de `PostMatchRatingScreen.kt`.
struct PostMatchRatingView: View {
    let convocatoryId: String
    let onRatingComplete: () -> Void

    @State private var viewModel = RatingViewModel()
    @State private var toastMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            content
            if !viewModel.teammates.isEmpty {
                submitButton
            }
        }
        .background(ElDraftColors.background)
        .toast(message: $toastMessage)
        .task { viewModel.load(convocatoryId: convocatoryId) }
        .onChange(of: viewModel.done) { _, done in
            if done { onRatingComplete() }
        }
        .onChange(of: viewModel.error) { _, newValue in
            if let newValue {
                toastMessage = newValue
                viewModel.clearError()
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.sm)
                ScreenHeader(
                    title: String(localized: "rating_header_title"),
                    subtitle: String(localized: "rating_header_subtitle")
                )
                Spacer().frame(height: ElDraftSpacing.lg)

                if viewModel.isLoading {
                    LoadingState().frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.notAttended {
                    EmptyState(
                        title: String(localized: "rating_not_attended_title"),
                        message: String(localized: "rating_not_attended_message"),
                        icon: "📷"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.teammates.isEmpty {
                    EmptyState(
                        title: String(localized: "rating_empty_title"),
                        message: String(localized: "rating_empty_message"),
                        icon: "🤝"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    LazyVStack(spacing: ElDraftSpacing.md) {
                        ForEach(viewModel.teammates, id: \.userId) { teammate in
                            TeammateRatingCard(
                                teammate: teammate,
                                scores: viewModel.scores[teammate.userId] ?? TeammateScores(),
                                onScore: { criterion, value in
                                    viewModel.setCriterion(userId: teammate.userId, criterion: criterion, value: value)
                                }
                            )
                        }
                    }
                    .padding(.vertical, ElDraftSpacing.sm)
                }
            }
            .padding(.horizontal, ElDraftSpacing.xl)
        }
    }

    private var submitButton: some View {
        Button {
            viewModel.submitAll(convocatoryId: convocatoryId)
        } label: {
            Group {
                if viewModel.isSubmitting {
                    ProgressView().tint(ElDraftColors.onPrimary)
                } else {
                    Text(String(localized: "rating_submit"))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.borderedProminent)
        .tint(ElDraftColors.primary)
        .disabled(viewModel.isSubmitting)
        .padding(.horizontal, ElDraftSpacing.xl)
        .padding(.vertical, ElDraftSpacing.lg)
        .background(ElDraftColors.background)
    }
}

private struct TeammateRatingCard: View {
    let teammate: Teammate
    let scores: TeammateScores
    let onScore: (RatingCriterion, Int) -> Void

    private let criteria: [(RatingCriterion, String, String)] = [
        (.skill, MetricIcons.skill, String(localized: "cromo_metric_skill")),
        (.sportsmanship, MetricIcons.sportsmanship, String(localized: "cromo_metric_sportsmanship")),
        (.responsibility, MetricIcons.responsibility, String(localized: "cromo_metric_responsibility")),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: ElDraftSpacing.md) {
                AvatarCircle(name: teammate.name, avatarUrl: teammate.avatarUrl)
                Text(teammate.name)
                    .font(ElDraftTypography.bodyLarge)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onSurface)
                Spacer()
            }

            if teammate.alreadyRated {
                Spacer().frame(height: ElDraftSpacing.sm)
                Text(String(localized: "rating_already_rated"))
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.primary)
            } else {
                Spacer().frame(height: ElDraftSpacing.sm)
                ForEach(criteria, id: \.1) { criterion, icon, label in
                    CriterionRow(
                        icon: icon,
                        label: label,
                        score: scores.value(for: criterion),
                        onScore: { onScore(criterion, $0) }
                    )
                }
            }
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
    }
}

private struct CriterionRow: View {
    let icon: String
    let label: String
    let score: Int
    let onScore: (Int) -> Void

    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.system(size: ElDraftSizes.iconMd))
                .foregroundStyle(ElDraftColors.primary)
            Text(label)
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onSurface.opacity(0.8)) // design-tokens-ignore: instrucción de calificación, espejo de Android
                .frame(maxWidth: .infinity, alignment: .leading)
            StarRow(score: score, labelPrefix: label, onScore: onScore)
        }
        .padding(.vertical, ElDraftSpacing.xs)
    }
}

private struct StarRow: View {
    let score: Int
    let labelPrefix: String
    let onScore: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(1...5, id: \.self) { star in
                Button {
                    onScore(star)
                } label: {
                    Image(systemName: "star.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(star <= score ? ElDraftColors.primary : ElDraftColors.onSurface.opacity(0.25)) // design-tokens-ignore: estrella vacía del rating, espejo de Android
                        .padding(ElDraftSpacing.xxs)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(format: String(localized: "rating_star_content_description"), labelPrefix, star))
            }
        }
    }
}

private struct AvatarCircle: View {
    let name: String
    let avatarUrl: String?

    private var initial: String {
        name.trimmingCharacters(in: .whitespaces).first.map(String.init)?.uppercased()
            ?? String(localized: "rating_initial_fallback")
    }

    var body: some View {
        ZStack {
            Circle().fill(ElDraftColors.primary.opacity(ElDraftAlpha.containerStrong))
            if let avatarUrl, !avatarUrl.isEmpty, let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
                .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
                .clipShape(Circle())
                .accessibilityLabel(String(format: String(localized: "rating_avatar_content_description"), name))
            } else {
                Text(initial)
                    .font(ElDraftTypography.titleMedium)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.primary)
            }
        }
        .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
    }
}
