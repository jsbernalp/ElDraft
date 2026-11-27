package com.eldraft.domain.team

import com.eldraft.core.network.ApiException
import com.eldraft.core.network.eldraftJson
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamMemberStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamRulesTest {

    @Test
    fun nombre_de_40_es_valido_y_de_41_no() {
        assertTrue(TeamRules.isValidName("a".repeat(40)))
        assertFalse(TeamRules.isValidName("a".repeat(41)))
    }

    @Test
    fun nombre_con_espacios_alrededor_cuenta_sin_ellos() {
        assertEquals("Los Pibes", TeamRules.normalizeName("  Los Pibes  "))
        assertTrue(TeamRules.isValidName("  " + "a".repeat(40) + "  "))
    }

    @Test
    fun nombre_vacio_o_solo_espacios_es_invalido() {
        assertFalse(TeamRules.isValidName(""))
        assertFalse(TeamRules.isValidName("    "))
    }

    @Test
    fun correo_con_espacios_y_mayusculas_se_normaliza() {
        assertEquals("juan@correo.com", TeamRules.normalizeEmail("  Juan@Correo.COM "))
        assertTrue(TeamRules.isPlausibleEmail("  Juan@Correo.COM "))
    }

    @Test
    fun correo_sin_forma_de_correo_no_es_plausible() {
        assertFalse(TeamRules.isPlausibleEmail(""))
        assertFalse(TeamRules.isPlausibleEmail("juan"))
        assertFalse(TeamRules.isPlausibleEmail("juan@correo"))
        assertFalse(TeamRules.isPlausibleEmail("juan perez@correo.com"))
        assertFalse(TeamRules.isPlausibleEmail("@correo.com"))
    }

    @Test
    fun limites_de_equipos_y_miembros() {
        assertTrue(TeamRules.canCreateAnotherTeam(4))
        assertFalse(TeamRules.canCreateAnotherTeam(5))
        assertTrue(TeamRules.canAddMember(29))
        assertFalse(TeamRules.canAddMember(30))
    }

    @Test
    fun estado_de_miembro_desconocido_no_revienta_la_deserializacion() {
        val json = """
            {"id":"t1","name":"Los Pibes","ownerId":"u1","ownerName":"Ana","isOwner":true,
             "createdAt":"2026-06-10T12:00","members":[
               {"playerId":"p1","name":"Beto","status":"active","invitedAt":"2026-06-10T12:00","joinedAt":"2026-06-11T09:30"},
               {"playerId":"p2","name":"Caro","status":"invited","invitedAt":"2026-06-10T12:00"},
               {"playerId":"p3","name":"Dani","status":"suspended","invitedAt":"2026-06-10T12:00","extra":1}
             ]}
        """.trimIndent()
        val detail = eldraftJson.decodeFromString(TeamDetail.serializer(), json)
        assertEquals(
            listOf(TeamMemberStatus.ACTIVE, TeamMemberStatus.INVITED, TeamMemberStatus.UNKNOWN),
            detail.members.map { it.memberStatus },
        )
        assertTrue(detail.members[0].isActive)
        assertTrue(detail.members[1].isInvited)
    }

    @Test
    fun error_429_sin_cuerpo_usa_el_respaldo_y_con_cuerpo_el_del_backend() {
        assertEquals("Hiciste demasiados intentos. Prueba en un rato.", ApiException(429, "").userMessage)
        val withBody = ApiException(429, """{"code":"RATE_LIMITED","message":"Hiciste demasiadas búsquedas por correo"}""")
        assertEquals("Hiciste demasiadas búsquedas por correo", withBody.userMessage)
        assertEquals("RATE_LIMITED", withBody.code)
    }

    @Test
    fun error_422_sin_cuerpo_tiene_respaldo_propio() {
        val generic = ApiException(500, "").userMessage
        val msg = ApiException(422, "").userMessage
        assertTrue(msg.isNotBlank())
        assertFalse(msg == ApiException(418, "").userMessage)
        assertFalse(msg == generic)
    }
}
