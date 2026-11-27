package com.eldraft.core.di

import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.repository.ProfileRepository
import com.eldraft.domain.usecase.attendance.DeclareAttendanceUseCase
import com.eldraft.domain.usecase.attendance.GenerateAttendanceQrUseCase
import com.eldraft.domain.usecase.attendance.GetAttendanceListUseCase
import com.eldraft.domain.usecase.attendance.GetNoShowStatusUseCase
import com.eldraft.domain.usecase.attendance.ReportOrganizerNoShowUseCase
import com.eldraft.domain.usecase.attendance.ScanAttendanceUseCase
import com.eldraft.domain.usecase.auth.DeleteAccountUseCase
import com.eldraft.domain.usecase.auth.GetMyAccountUseCase
import com.eldraft.domain.usecase.auth.LogoutUseCase
import com.eldraft.domain.usecase.auth.ReportLocationUseCase
import com.eldraft.domain.usecase.auth.SignInWithEmailUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
import com.eldraft.domain.usecase.auth.UpdateAccountUseCase
import com.eldraft.domain.usecase.convocatory.CancelConvocatoryUseCase
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import com.eldraft.domain.usecase.postulation.ApplyToConvocatoryUseCase
import com.eldraft.domain.usecase.postulation.ApproveApplicantUseCase
import com.eldraft.domain.usecase.postulation.GetApplicantsUseCase
import com.eldraft.domain.usecase.postulation.GetMyPostulationsUseCase
import com.eldraft.domain.usecase.postulation.RejectApplicantUseCase
import com.eldraft.domain.usecase.postulation.WithdrawPostulationUseCase
import com.eldraft.domain.usecase.profile.SaveProfileUseCase
import com.eldraft.domain.usecase.rating.GetTeammatesToRateUseCase
import com.eldraft.domain.usecase.rating.SubmitRatingUseCase
import com.eldraft.domain.usecase.team.CreateTeamUseCase
import com.eldraft.domain.usecase.team.DeleteTeamUseCase
import com.eldraft.domain.usecase.team.GetMyTeamMembershipsUseCase
import com.eldraft.domain.usecase.team.GetMyTeamsUseCase
import com.eldraft.domain.usecase.team.GetTeamDetailUseCase
import com.eldraft.domain.usecase.team.GetTeamInvitationsUseCase
import com.eldraft.domain.usecase.team.GetTeamPriorityInvitesUseCase
import com.eldraft.domain.usecase.team.GetTeamSuggestionsUseCase
import com.eldraft.domain.usecase.team.InviteToTeamUseCase
import com.eldraft.domain.usecase.team.LeaveTeamUseCase
import com.eldraft.domain.usecase.team.OpenConvocatoryToPublicUseCase
import com.eldraft.domain.usecase.team.RemoveTeamMemberUseCase
import com.eldraft.domain.usecase.team.RenameTeamUseCase
import com.eldraft.domain.usecase.team.RespondTeamInvitationUseCase
import com.eldraft.domain.usecase.team.RespondTeamPriorityUseCase
import org.koin.mp.KoinPlatformTools

/**
 * Fachada mínima sobre Koin para que Swift no necesite conocer su API
 * (`getKoin<T>()` con reified generics no se exporta a Objective-C/Swift).
 * Se amplía a medida que cada fase agrega pantallas que necesitan un nuevo
 * repositorio o caso de uso — ver [startKoinIos].
 */
object IosKoin {
    val authRepository: AuthRepository get() = KoinPlatformTools.defaultContext().get().get()
    val signInWithEmail: SignInWithEmailUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val signInWithGoogle: SignInWithGoogleUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val saveProfile: SaveProfileUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val logout: LogoutUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val convocatoryRepository: ConvocatoryRepository get() = KoinPlatformTools.defaultContext().get().get()
    val observeMapEvents: ObserveMapEventsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val reportLocation: ReportLocationUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getMyPostulations: GetMyPostulationsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val applyToConvocatory: ApplyToConvocatoryUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val createConvocatory: CreateConvocatoryUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val cancelConvocatory: CancelConvocatoryUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val withdrawPostulation: WithdrawPostulationUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getApplicants: GetApplicantsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val approveApplicant: ApproveApplicantUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val rejectApplicant: RejectApplicantUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getNoShowStatus: GetNoShowStatusUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val reportOrganizerNoShow: ReportOrganizerNoShowUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val generateAttendanceQr: GenerateAttendanceQrUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val scanAttendance: ScanAttendanceUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getAttendanceList: GetAttendanceListUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val declareAttendance: DeclareAttendanceUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val profileRepository: ProfileRepository get() = KoinPlatformTools.defaultContext().get().get()
    val getMyAccount: GetMyAccountUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val updateAccount: UpdateAccountUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val deleteAccount: DeleteAccountUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getTeammatesToRate: GetTeammatesToRateUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val submitRating: SubmitRatingUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getMyTeams: GetMyTeamsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val createTeam: CreateTeamUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val renameTeam: RenameTeamUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val deleteTeam: DeleteTeamUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getTeamDetail: GetTeamDetailUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getTeamSuggestions: GetTeamSuggestionsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val inviteToTeam: InviteToTeamUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val removeTeamMember: RemoveTeamMemberUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val leaveTeam: LeaveTeamUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getTeamInvitations: GetTeamInvitationsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val respondTeamInvitation: RespondTeamInvitationUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getMyTeamMemberships: GetMyTeamMembershipsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getTeamPriorityInvites: GetTeamPriorityInvitesUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val respondTeamPriority: RespondTeamPriorityUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val openConvocatoryToPublic: OpenConvocatoryToPublicUseCase get() = KoinPlatformTools.defaultContext().get().get()
}
