# Equipos y convocatoria prioritaria

> 📝 SPEC APROBADO — pendiente de implementar (backend → shared → Android → iOS).
> Se implementa en dos issues de la factory: **Parte 1 (Equipos)** y **Parte 2
> (Convocatoria con prioridad)**; luego paridad iOS.
>
> Un usuario arma un **equipo** con jugadores de confianza. Al crear una
> convocatoria puede elegir ese equipo: sus miembros reciben el aviso **primero**
> y tienen una ventana exclusiva para responder. Si al cerrar la ventana (o
> cuando todos respondieron) siguen faltando cupos, la convocatoria **se abre al
> público** como cualquier otra, solo con los cupos que faltan.

## Problema

Hoy toda convocatoria nace pública: se notifica a los jugadores cercanos
(`ConvocatoryService.notifyNearbyPlayers`) y aparece en el mapa (`new_pin`) en
el mismo instante. Quien ya tiene un grupo con el que juega seguido no puede
darle prioridad: compite por los cupos con desconocidos, o tiene que coordinar
por fuera de la app y crear la convocatoria solo para los cupos que le faltan.

---

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| Qué es un equipo | Una lista **del dueño** (quien lo crea) con nombre y miembros. Los miembros no la administran: solo aceptan la invitación o se salen. |
| Cómo se agrega un miembro | **Dos vías**: (a) sugerencias de **jugadores con los que ya jugó**, o (b) **escribiendo su correo**. |
| Correo no registrado | **Error**: *"No encontramos un jugador con ese correo"*. No hay invitaciones a personas sin cuenta. |
| Consentimiento | El agregado recibe una **invitación** y queda como `invited` hasta aceptarla. Solo los miembros `active` reciben avisos del equipo. |
| Ventana exclusiva | **La elige el organizador** al crear la convocatoria: 30 min / 1 h / 2 h / 6 h. |
| Respuesta "Voy" | Crea una **postulación `pending`** normal: el organizador la aprueba o rechaza como siempre. |
| Respuesta "No puedo" | Se registra explícitamente (hace falta para saber si **todos respondieron**). |
| Cuándo se abre al público | En lo **primero** que ocurra: **vence la ventana**, o **todos los miembros respondieron**. Solo se publica si quedan cupos libres. |
| Qué cuenta como cupo ocupado | **Aprobados + pendientes del equipo**. Si el organizador rechaza a uno del equipo, ese cupo se libera para el público. |
| "Ya jugaron juntos" | Compartieron una convocatoria **terminada** en la que ambos fueron **aprobados** (u organizador) y ninguno quedó marcado no-show (`player_no_show_marks` / `organizer_no_show`). Reutiliza la verdad de asistencia que ya existe; no exige escaneo de QR. |
| Equipos por usuario | Máximo **5**. |
| Miembros por equipo | Máximo **30** (contando `invited` + `active`). Limita la cantidad de pushes y el abuso. |
| Tope de la ventana | La ventana debe cerrar **al menos 1 h antes** del partido. Las opciones que no lo cumplen se muestran deshabilitadas, y el backend responde `400` si llegan igual. |
| Miembro que no respondió | Al abrirse al público su invitación queda `expired`, pero puede **postularse normalmente** desde Buscar Cupo: solo pierde la prioridad. |
| Perfil requerido | Solo se puede agregar a usuarios con `player_profiles` (onboarding completo): sin perfil no hay posición y no se puede postular. |
| Agregarse a sí mismo / duplicados | No se permite (`409`). |
| Abrir antes de tiempo | El organizador puede **"Abrir ya al público"** mientras está en `team_only`. |
| Búsqueda por correo | Rate-limit de **20 búsquedas/h por usuario**; la respuesta trae solo nombre y avatar, nunca el correo. |

---

## Flujos

### A. Armar el equipo

1. Perfil → **"Mis equipos"** → "Crear equipo" (nombre, obligatorio, ≤ 40 caracteres).
2. En el detalle del equipo, "Agregar jugador" abre un sheet con:
   - **Sugeridos**: los jugadores con los que ya jugó (avatar, nombre, posición,
     partidos juntos), excluyendo a quienes ya están en el equipo.
   - **Por correo**: campo de texto + "Invitar". Si el correo no existe →
     *"No encontramos un jugador con ese correo"*.
3. El invitado recibe el push `team_invite` → acepta o rechaza desde
   "Mis equipos → Invitaciones" (o desde el push).
4. El dueño ve a cada miembro con su estado: **Invitado** / **Miembro**. Puede
   quitar a cualquiera y borrar el equipo. Un miembro puede **salirse**.

### B. Convocar con prioridad para el equipo

```
Crear convocatoria ── ¿equipo? ── no ──▶ flujo actual (pública al instante)
                         │
                         sí: elige equipo + ventana
                         ▼
               status = team_only
               push "team_priority" solo a miembros active
               (sin new_pin, sin aviso a cercanos)
                         │
     miembros responden: "Voy" (elige posición → postulación pending)
                         "No puedo"
                         │
   ¿venció la ventana? ──┴── ¿respondieron todos?
                         │  (lo primero que ocurra)
                         ▼
             cupos libres = cupos − aprobados − pendientes del equipo
                         │
            ┌────────────┴────────────┐
       libres > 0                 libres = 0
  status = active                 status = active (sin publicar)
  new_pin + aviso a cercanos      el organizador gestiona sus pendientes;
  (solo los cupos libres)         si rechaza a uno → se publica en ese momento
```

1. En `CreateDraftScreen`, sección opcional **"Avisar primero a mi equipo"**:
   selector de equipo (solo si tiene alguno con ≥ 1 miembro `active`) + chips de
   ventana. El resumen dice: *"Tu equipo tendrá 1 h para responder; luego se abre
   al público si faltan cupos"*.
2. La convocatoria nace en estado **`team_only`**: no sale en `/nearby`, no se
   difunde por WebSocket y no dispara `notifyNearbyPlayers`.
3. Cada miembro `active` recibe el push **`team_priority`** (*"Tu equipo
   «Los del martes» juega Fútbol 5 el jue 8:00 pm. Tienes hasta las 6:00 pm para
   apartar tu cupo"*). En la pestaña **Juego** ve una card con "Voy" / "No puedo".
4. "Voy" pide la posición (solo las que tienen cupo libre) y crea la postulación
   `pending`. Se notifica al organizador con el `new_postulation` de siempre.
5. Al **abrirse** (por ventana o porque respondieron todos): pasa a `active`,
   las invitaciones sin respuesta quedan `expired` y, si hay cupos libres, se
   publica (pin + aviso a cercanos) con la cantidad de cupos libres.
6. El organizador ve en su card la fase actual: *"Prioridad del equipo · cierra en
   42 min · 3/5 respondieron"* → *"Abierta al público · faltan 2"*.
7. Mientras está en `team_only`, el organizador puede **abrirla antes de tiempo**
   ("Abrir ya al público"). Es barato de implementar (es la misma transición) y
   cubre el caso de "ya sé que no vienen".

### C. Casos borde

| Caso | Comportamiento |
|---|---|
| El organizador cancela durante `team_only` | Igual que hoy: se cancelan las postulaciones y se notifica a los aprobados. Las invitaciones pendientes quedan `expired`; no hubo pin, así que no hay `pin_closed`. |
| Un miembro dice "Voy" y luego se retira | Usa el `withdraw` actual. Si aún está en `team_only`, su invitación vuelve a `declined` (cuenta como respondida). Si ya está abierta, se libera el cupo. |
| Rechazan a un miembro **después** de abierta | Se recalculan los cupos libres; si pasa de 0 a > 0 y no se había publicado, **se publica en ese momento**. |
| Un miembro "Voy" tiene un partido aprobado a esa hora | Las mismas reglas de `PostulationService.apply` (`409 Ya tienes un partido aprobado a esa hora`). |
| Quitan a un miembro del equipo con una invitación viva | Su invitación queda `expired`; su postulación (si existe) **se mantiene**: ya es una postulación normal. |
| Se borra el equipo con convocatorias en `team_only` | Esas convocatorias **se abren al público de inmediato**. |
| El equipo no tiene miembros `active` al crear | No se puede elegir (`400`): la convocatoria sería privada para nadie. |
| La ventana venció y el scheduler se atrasó | La transición es idempotente y se evalúa también al leer (ver Plan técnico §2). |
| El organizador es miembro de otro equipo | Irrelevante: solo se usa el equipo elegido, y el organizador se excluye de los avisos. |

---

## Cupos libres (concepto nuevo)

Hoy el backend **no lleva la cuenta de ocupación**: el estado `full` aparece en
filtros (`status IN ('active','full')`) pero ningún código lo asigna, y aprobar
una postulación no revisa la capacidad. Esta feature necesita el número, así que
se introduce:

```
libres(posición) = slots(posición)
                 − aprobados(posición)
                 − pendientes_de_miembros_del_equipo(posición)
libres_total     = Σ max(0, libres(posición))
```

- Solo lo usa esta feature: para decidir si se publica, para el número de cupos
  del pin y del push a cercanos, y para limitar las posiciones que se ofrecen en
  "Voy".
- **Fuera de alcance**: bloquear aprobaciones por encima de la capacidad o
  asignar el estado `full`. Queda anotado como deuda (ver "Fuera de alcance").

---

## Plan técnico

### 1. Datos (`db/tables/Tables.kt` + registro en `plugins/Databases.kt`)

- **`TeamsTable`** (`teams`): `owner_id → users`, `name` (varchar 40),
  `created_at`.
- **`TeamMembersTable`** (`team_members`): `team_id → teams`,
  `player_id → users`, `status` (`invited` | `active`), `invited_at`,
  `joined_at` (nullable). Índice único `(team_id, player_id)`. Rechazar la
  invitación, salirse o ser quitado **borra la fila** (así se puede volver a
  invitar).
- **`TeamPriorityInvitesTable`** (`team_priority_invites`): `convocatory_id`,
  `player_id`, `response` (`pending` | `accepted` | `declined` | `expired`),
  `responded_at` (nullable), `postulation_id` (nullable). Índice único
  `(convocatory_id, player_id)`. Es una **foto** de los miembros `active` al
  momento de crear: quien se una después al equipo no entra en esa convocatoria.
- **Columnas nuevas en `ConvocatoriesTable`** (todas nullable):
  - `team_id` → equipo priorizado.
  - `team_window_ends_at` → cierre de la ventana exclusiva.
  - `opened_at` → cuándo pasó de `team_only` a `active`.
  - `published_at` → cuándo se publicó (pin + cercanos); `null` si aún no.
- **Nuevo valor de `status`**: `team_only`.
- Todo es aditivo y entra con `createMissingTablesAndColumns`. Sin migración de datos.

### 2. Backend

- **`TeamRepository`** (`open`): CRUD de equipos y miembros,
  `findSharedMatchPlayers(userId)` (sugerencias: consulta sobre `postulations`
  aprobadas + organizador de convocatorias terminadas, menos no-shows,
  agrupando por jugador con `count` de partidos juntos).
- **`UserRepository.findByEmail(email)`**: búsqueda **case-insensitive** y con
  `trim`. Hoy no existe.
- **`TeamService`**: reglas de "Decisiones" (topes, perfil requerido,
  sin duplicados, consentimiento). Envía el push `team_invite`.
- **`TeamPriorityService`** (el centro de la feature):
  - `startPriority(convocatory, teamId, window)`: valida el tope de la ventana,
    crea las invitaciones `pending` y envía el push `team_priority`.
  - `respond(convocatoryId, playerId, accept, position?)`: valida que la
    invitación esté `pending` y la convocatoria en `team_only`; "Voy" →
    crea la postulación por una **vía interna** de `PostulationService` que
    acepte `team_only` (hoy `apply` exige `status == "active"`,
    `PostulationService.kt:41`); luego llama a `maybeOpen`.
  - `maybeOpen(convocatoryId, now)`: **idempotente**. Si está en `team_only` y
    (`now >= team_window_ends_at` **o** no quedan invitaciones `pending`) →
    `status = active`, `opened_at = now`, `expired` a las pendientes y
    `maybePublish`.
  - `maybePublish(convocatoryId)`: si `published_at == null` y
    `libres_total > 0` → `notifyNearbyPlayers` + broadcast `new_pin` con
    `slots = libres_total`, y `published_at = now`.
  - `openNow(convocatoryId, organizerId)`: el atajo "Abrir ya al público".
- **`ConvocatoryService.create`**: si llega `teamId`, crea en `team_only` y
  delega en `TeamPriorityService.startPriority` **en lugar de**
  `notifyNearbyPlayers`. El broadcast `new_pin`, que hoy vive en la **ruta**
  (`ConvocatoryRoutes.kt:109`), se mueve a `maybePublish` para las convocatorias
  con equipo (o se condiciona a `status == active`); si no, el pin se filtraría
  durante la fase privada.
- **`PostulationService.decide`/`withdraw`**: tras rechazar o retirar, llamar a
  `maybePublish` (libera un cupo después de abierta) y actualizar la invitación
  (`withdraw` en `team_only` → `declined`).
- **Scheduler** (`plugins/Scheduler.kt`): un segundo bucle, `teams.windowCheckSeconds`
  (default 60), que llama a `maybeOpen` para las `team_only` con la ventana
  vencida. El bucle de recordatorios actual (`reminderIntervalMinutes`) es
  demasiado grueso para ventanas de 30 min.
- **Lectura defensiva**: `GET /convocatories/{id}` y `/mine` llaman a `maybeOpen`
  antes de responder, así un scheduler atrasado no deja la card desfasada.
- **Recordatorios**: `findActiveWithoutPostulations` filtra `status = 'active'`,
  así que ignora `team_only` sin cambios. Al abrirse, si el equipo no llenó nada,
  el recordatorio normal la toma sola.
- **Rutas** (`routes/TeamRoutes.kt`, registro en `Routing.kt`, errores en `StatusPages.kt`):

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/teams` | Mis equipos (dueño), con conteo de `active`/`invited`. |
| `POST` | `/teams` | Crear `{ name }`. |
| `PATCH` | `/teams/{id}` | Renombrar. |
| `DELETE` | `/teams/{id}` | Borrar (abre las `team_only` vivas). |
| `GET` | `/teams/{id}` | Detalle con miembros y estado. |
| `GET` | `/teams/suggestions` | Jugadores con los que ya jugué. |
| `POST` | `/teams/{id}/members` | `{ playerId }` o `{ email }` → invita. `404` si el correo no existe. |
| `DELETE` | `/teams/{id}/members/{playerId}` | Quitar (dueño) o salirse (el propio miembro). |
| `GET` | `/teams/invitations` | Invitaciones a equipos que recibí. |
| `POST` | `/teams/{id}/invitation` | `{ accept }` → acepta o rechaza. |
| `GET` | `/teams/priority-invites` | Convocatorias de mis equipos esperando mi respuesta. |
| `POST` | `/convocatories/{id}/team-response` | `{ accept, position? }`. |
| `POST` | `/convocatories/{id}/open` | Abrir ya al público (organizador). |

- `POST /convocatories` acepta `teamId?` y `teamWindowMinutes?` (∈ {30, 60, 120, 360}).
- El DTO de convocatoria expone `teamId`, `teamName`, `teamWindowEndsAt`,
  `teamResponded`/`teamInvited` (solo para el organizador), `freeSlots` y el
  nuevo `status`.
- **Push nuevos** (`data.type`): `team_invite`, `team_priority`,
  `team_window_opened` (al organizador: *"Se abrió al público: faltan 2"*, o
  *"Tu equipo llenó los cupos"*).

### 3. Shared

- Modelos (`Models.kt`): `Team`, `TeamMember` (+ `TeamMemberStatus`),
  `TeamSuggestion` (jugador + `matchesTogether`), `TeamInvitation`,
  `TeamPriorityInvite`; `Convocatory` con los campos nuevos.
- `TeamApi` (`data/remote/`) + `TeamRepository` (dominio + impl).
- `ConvocatoryApi`: `respondTeamPriority`, `openToPublic`; `create` con
  `teamId`/`teamWindowMinutes`.
- Casos de uso en `domain/usecase/team/`: `GetMyTeams`, `CreateTeam`,
  `RenameTeam`, `DeleteTeam`, `GetTeamDetail`, `GetTeamSuggestions`,
  `InviteToTeam` (id o correo), `RemoveTeamMember`, `GetTeamInvitations`,
  `RespondTeamInvitation`, `GetTeamPriorityInvites`, `RespondTeamPriority`,
  `OpenConvocatoryToPublic`. Registrados en `SharedModule`.
- Lógica pura `domain/team/TeamWindow`: las opciones válidas de ventana dado
  `scheduledAt` (la usan Android e iOS para deshabilitar chips).

### 4. Android

- **Perfil → "Mis equipos"** (`TeamsScreen`, `TeamDetailScreen` +
  `TeamsViewModel`, `TeamDetailViewModel` en `ui/team/`): lista, crear, detalle
  con miembros y estados, quitar, borrar, salirse. Sección "Invitaciones" con
  aceptar y rechazar.
- **Sheet "Agregar jugador"**: pestañas "Sugeridos" / "Por correo".
- **`CreateDraftScreen`**: sección opcional "Avisar primero a mi equipo"
  (selector + chips de ventana, deshabilitados según `TeamWindow`).
- **Pestaña Juego**: card `TeamPriorityCard` con cuenta regresiva y "Voy" (sheet
  de posición con cupos libres) / "No puedo".
- **Pestaña Organizo**: la card muestra la fase (prioridad con el conteo de
  respuestas y la cuenta regresiva → abierta al público con cupos libres) y la
  acción "Abrir ya al público" durante `team_only`.
- **Notificaciones** (`ElDraftMessagingService` / `NotificationHelper`): los 3
  `type` nuevos con deep link (`team_invite` → invitaciones;
  `team_priority` → Juego; `team_window_opened` → Organizo).
- Navegación en `ElDraftApp.kt` (`teams`, `team/{id}`).
- Strings con prefijo `team_*`, plurales para "faltan N cupos" y "N/M respondieron";
  design tokens según `CLAUDE.md`.

### 5. iOS

Paridad completa con el punto 4 (`/paridad` al cerrar Android). Mismas claves en
`Localizable.xcstrings`.

### 6. Tests (backend; los de shared/VM según la skill `tests`)

- `TeamServiceTest`: crear con topes; invitar por id y por correo
  (case-insensitive); correo inexistente → not found; sin perfil → rechazo; a sí
  mismo / duplicado → conflict; aceptar/rechazar invitación; quitar y salirse;
  solo el dueño administra.
- `TeamRepository` sugerencias: incluye a los co-aprobados de partidos
  terminados y al organizador; excluye a los no-show, los partidos futuros y los
  cancelados.
- `TeamPriorityServiceTest`:
  - crear con equipo → `team_only`, sin aviso a cercanos, invitaciones solo a `active`;
  - ventana que no cierra 1 h antes → `400`; equipo sin `active` → `400`;
  - "Voy" crea una postulación `pending` en `team_only`; "Voy" sin cupo libre en
    esa posición → conflict;
  - todos responden → se abre antes de la ventana; vence la ventana → abre y
    pone `expired` a las pendientes;
  - al abrir con libres > 0 → publica con `slots = libres`; con libres = 0 → no publica;
  - rechazar a un miembro después de abierta → publica; `maybeOpen` y
    `maybePublish` son idempotentes (2 llamadas = 1 push);
  - `openNow` solo para el organizador y solo en `team_only`;
  - borrar el equipo abre sus `team_only`; cancelar en `team_only` expira las invitaciones.
- `PostulationServiceTest`: `apply` público sigue rechazando `team_only`.

---

## Fuera de alcance

- Invitar por correo a personas **sin cuenta** (requiere envío de correos, que hoy
  no existe).
- Equipos compartidos/co-administrados, chat de equipo, estadísticas de equipo.
- **Control general de capacidad** (bloquear aprobaciones por encima de los
  cupos, asignar el estado `full`). Es deuda que este spec deja a la vista; se
  recomienda tratarla aparte.
- Aprobación automática de miembros del equipo (se decidió `pending`).

## Riesgos y notas

- **Enumeración de correos**: "Agregar por correo" revela si un correo tiene
  cuenta. Mitigación: exigir sesión, aplicar rate-limit por usuario
  (20/h) y devolver solo nombre y avatar del encontrado, nunca el correo de vuelta.
- **Volumen de push**: el tope de 30 miembros y la foto de invitaciones acotan
  el envío 1-a-1 actual de FCM.
- **El `new_pin` vive en la ruta**: si no se mueve o se condiciona, la fase
  privada se filtra en el mapa. Es el error más fácil de cometer.
