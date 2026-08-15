package com.eldraft.domain.usecase

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.models.NoShowStatus
import com.eldraft.data.models.PlayerAttendanceRow
import com.eldraft.domain.repository.AttendanceRepository
import com.eldraft.domain.usecase.attendance.GenerateAttendanceQrUseCase
import com.eldraft.domain.usecase.attendance.ScanAttendanceUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeAttendanceRepository : AttendanceRepository {
    var generatedFor: String? = null
    var scannedCode: String? = null
    override suspend fun generateQr(convocatoryId: String): AttendanceQr {
        generatedFor = convocatoryId
        return AttendanceQr(qrCode = "token-$convocatoryId", expiresInSeconds = 600)
    }
    override suspend fun scan(qrCode: String): AttendanceScanResult {
        scannedCode = qrCode
        return AttendanceScanResult(validated = true, convocatoryId = "c1", playerId = "u1")
    }

    // Miembros que AttendanceRepository ganó después de escribirse estos tests. Sin
    // implementar aquí, TODO el source set de commonTest dejaba de compilar.
    // Los casos de uso de este archivo no los tocan.
    override suspend fun reportNoShow(convocatoryId: String): NoShowStatus = error("no usado")
    override suspend fun noShowStatus(convocatoryId: String): NoShowStatus = error("no usado")
    override suspend fun attendanceList(convocatoryId: String): List<PlayerAttendanceRow> = error("no usado")
    override suspend fun declareAttendance(
        convocatoryId: String,
        absentPlayerIds: List<String>,
    ): List<PlayerAttendanceRow> = error("no usado")
}

class AttendanceUseCasesTest {

    @Test
    fun generar_qr_delega_al_repo() = runTest {
        val repo = FakeAttendanceRepository()
        val qr = GenerateAttendanceQrUseCase(repo)("c1")
        assertEquals("c1", repo.generatedFor)
        assertEquals(600, qr.expiresInSeconds)
    }

    @Test
    fun generar_qr_convocatoria_vacia_falla() = runTest {
        val repo = FakeAttendanceRepository()
        assertFailsWith<IllegalArgumentException> { GenerateAttendanceQrUseCase(repo)("") }
    }

    @Test
    fun escanear_delega_al_repo() = runTest {
        val repo = FakeAttendanceRepository()
        val result = ScanAttendanceUseCase(repo)("token-x")
        assertEquals("token-x", repo.scannedCode)
        assertTrue(result.validated)
    }

    @Test
    fun escanear_codigo_vacio_falla() = runTest {
        val repo = FakeAttendanceRepository()
        assertFailsWith<IllegalArgumentException> { ScanAttendanceUseCase(repo)("") }
    }
}
