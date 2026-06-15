# Casos borde de asistencia: el organizador o un convocado no llegan al partido

> ✅ IMPLEMENTADO (backend + shared + Android). Tests del backend en verde.
> Commits en la rama `feat/organizador-no-show`.
>
> Este documento cubre **dos casos espejo** y cómo conviven sin contradecirse:
>
> 1. **El organizador no llega** → reporte **por consenso** de los convocados
>    (nadie tiene autoridad por encima del organizador).
> 2. **Un convocado no llega** → el organizador lo **declara unilateralmente**
>    al cierre (es la autoridad del partido).
>
> Ambos son **mutuamente excluyentes**: el primero en ocurrir define la verdad
> del partido y bloquea al otro (ver §6).

# Parte A — El organizador no llega (reporte por consenso)

## Problema

Hoy el sistema **asume que el organizador siempre está presente**:

- `RatingRepository.attended()` devuelve `true` si el usuario es el organizador,
  sin exigirle escanear QR (él lo genera, no lo escanea).
  Ver `RatingRepository.kt:36`.
- `RatingRepository.teammates()` inyecta al organizador a mano en la lista de
  calificables (`RatingRepository.kt:81-84`).

Si el organizador convoca pero **no llega**:

1. Un asistente no tiene forma de marcar que el organizador no apareció.
2. El organizador ausente puede calificar (y ser calificado) como si hubiera ido.
3. Su `attendance_pct` nunca refleja la ausencia (no entra en ese cálculo).

El no-show queda **impune**.

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| Presencia del organizador | El organizador **también escanea** (se elimina el atajo "organizador = presente"). |
| Quién genera el QR | **Cualquier jugador aprobado** puede generar el QR; el organizador escanea como uno más. |
| Organizador ausente y calificaciones | **Puede ser calificado, pero no puede calificar** (si no escaneó). |
| Reporte de no-show | **Por consenso** de los convocados (no basta un solo reporte). |
| Base del consenso | **Jugadores aprobados** (no los que escanearon): si el organizador no llegó, **nadie pudo escanear su QR**, así que la base son los aprobados. |
| Umbral de consenso | **Mayoría estricta**: `reportes > aprobados / 2`. |
| Quién puede reportar | Cualquier **jugador aprobado** (1 voto c/u), no el organizador. |
| Efectos del consenso | Los **tres**: baja `attendance_pct`, baja `responsibility_score`, y marca `organizer_no_show` visible en la convocatoria. |
| Apertura de la ventana | `scheduled_at + 15 min` (margen de tolerancia por retrasos). |
| Cierre de la ventana | `scheduled_at + 48 h`. |

## Comportamiento de calificación resultante

| Actor | ¿Puede calificar? | ¿Puede ser calificado? |
|---|---|---|
| Organizador que **no** llegó (no escaneó) | ❌ | ✅ (los asistentes le bajan responsabilidad) |
| Organizador que **sí** llegó (escaneó) | ✅ | ✅ |
| Postulado que asistió | ✅ | ✅ |

## Ventana de reporte de no-show

```
scheduled_at + 15 min   ←  apertura (botón habilitado)
scheduled_at + 48 h     ←  cierre (ya no se aceptan reportes)
```

- **Antes** de `scheduled_at + 15min`: botón deshabilitado
  ("disponible tras el inicio del partido"). No se espera a que termine el
  partido: la ausencia se sabe al inicio, no al final.
- **Dentro** de la ventana: asistentes validados pueden reportar.
- **Después** de `scheduled_at + 48h`: ventana cerrada, conteo congelado.

## Consenso

- Reportan los **jugadores aprobados** (1 voto c/u, sin duplicar). **No** se exige
  haber escaneado: ante un no-show del organizador no hay QR que escanear, así que
  exigir escaneo dejaría la feature lógicamente muerta.
- Umbral: `reportes > aprobados / 2` (mayoría estricta).
- Al alcanzarse → 3 efectos sobre el organizador:
  - baja `attendance_pct` (cuenta como inasistencia suya),
  - baja `responsibility_score` (visible en su cromo),
  - `organizer_no_show = true` en la convocatoria (visible para futuros postulantes).
- **Antiabuso**: un solo reporte no hace nada; hace falta mayoría → neutraliza
  el reporte falso por despecho.
- **Efecto colateral** (ver §6): al alcanzar consenso se **revierten** las marcas
  de ausencia que el organizador hubiera puesto sobre sus convocados (si él no
  estuvo, no puede dar fe de quién faltó).

---

# Parte B — Un convocado no llega (asistencia declarada por el organizador)

## Problema

La asistencia del convocado era **implícita**: si no escaneaba el QR, su
`attendance_pct` bajaba solo. Eso castiga injustamente a quien no escaneó por un
fallo técnico (sin batería, QR caducado) y no permite penalizar la
*responsabilidad* del ausente real.

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| Quién declara | **El organizador**, de forma **unilateral** (es la autoridad del partido; sin consenso, a diferencia de la Parte A). |
| Modelo de asistencia | **Explícito**: la asistencia ya no se infiere de "escaneó", sino de lo que el organizador confirma. Lo **no marcado** como ausente cuenta como **presente**. |
| El QR como prueba | Quien escaneó queda **"Presente" firme**: el organizador **no** puede marcarlo ausente. Solo edita a quienes no escanearon. |
| Mecánica | **Lista al cierre**: tras el partido (`scheduled_at + 45 min`) el organizador ve la lista de aprobados y marca a los ausentes en una sola pasada. |
| Cuándo | Solo tras `scheduled_at + 45 min` (partido terminado). |
| Efecto de marcar "no llegó" | No cuenta como asistido en `attendance_pct` + baja `responsibility_score` + flag visible. |
| Reversibilidad | La lista es **re-declarable**: quitar a alguien revierte su penalización (la responsabilidad se reconstruye desde el promedio limpio de ratings, no se acumula). |

## Modelo de asistencia resultante

```
attendance_pct = (aprobados − marcas de no-show) / aprobados * 100
presente       = escaneó QR  OR  (aprobado y NO marcado ausente)
```

> El escaneo deja de ser la fuente de verdad del porcentaje (sigue siendo prueba
> firme que impide que te marquen ausente). **Default = presente**: si el
> organizador no marca a nadie, todos cuentan como presentes.

## Flujo

1. El organizador abre **"Asistencia"** en su card (acceso visible tras el cierre).
2. Ve la lista de aprobados: los que escanearon salen **"Presente"** fijo; los
   demás con un toggle **"Llegó / No llegó"**.
3. Marca a los ausentes y **guarda** (aunque sea lista vacía).
4. Por cada marcado: baja su `attendance_pct` y `responsibility_score`.

---

# Parte C — Coherencia entre los dos casos (§6)

Los dos eventos pueden ocurrir en cualquier orden, pero **no pueden coexistir**:
el primero que sucede define la verdad del partido y bloquea al otro.

| Si ocurre primero… | Flag | …bloquea |
|---|---|---|
| Consenso de no-show del organizador | `organizer_no_show` | Que el organizador **declare** asistencia (403) **y revierte** las marcas que hubiera puesto sobre sus convocados. |
| El organizador **declara** asistencia (aunque sea lista vacía) | `organizer_confirmed` | Que los convocados **reporten** no-show contra él (botón oculto + `409`). Declarar = "yo estuve aquí". |

**Por qué:** si el organizador no llegó, no puede dar fe de quién faltó; y si
declaró la asistencia, eso prueba que estuvo presente, así que nadie debería
poder reportar que no llegó.

## Avisos en la UI (ambos casos)

- **Card del convocado** marcado ausente: banner rojo *"El organizador marcó que
  no llegaste"* + el impacto. Se alimenta de un campo `markedNoShow` añadido al
  `NoShowStatus` que la card ya consultaba (sin endpoint extra).
- **Card del organizador** marcado no-show: banner rojo *"No llegaste a este
  partido"* + el botón **"Asistencia" deshabilitado**. Usa `organizer_no_show`
  propagado al modelo `Convocatory`.
- **Chip "Ver postulantes"**: badge con el número de postulaciones **pendientes**
  por gestionar (`pendingCount` en `/convocatories/mine`); desaparece al
  gestionarlas todas.

---

## Plan técnico

### 1. Datos
- **Tabla** `organizer_no_show_reports` (Parte A):
  `reporter_id`, `convocatory_id`, `created_at`; índice único `(reporter_id, convocatory_id)`.
- **Tabla** `player_no_show_marks` (Parte B):
  `player_id`, `convocatory_id`, `created_at`; índice único `(convocatory_id, player_id)`.
  Una fila = ausencia confirmada por el organizador.
- **Columnas en `ConvocatoriesTable`** (default `false`):
  - `organizer_no_show` — consenso marcó al organizador como ausente.
  - `organizer_confirmed` — el organizador declaró la asistencia (prueba de presencia).

### 2. Backend
- **`AttendanceService` / `AttendanceRepository`**
  - `generateQr`: permitir a **organizador O jugador aprobado** (hoy solo organizador, `AttendanceService.kt:36`).
  - `scan`: `canAttend = isApprovedPlayer(...) || isOrganizer(...)` (hoy solo aprobado, `AttendanceService.kt:59`).
  - Añadir `isOrganizer` al `AttendanceRepository` (hoy existe privado solo en `RatingRepository`).
- **`RatingRepository`**
  - `attended()`: **quitar** el atajo `if (isOrganizer) return true` (`RatingRepository.kt:37`).
  - `teammates()`: **mantener** la inyección del organizador como calificable (`RatingRepository.kt:81-84`).
  - `RatingService.submit`: relajar la regla "solo puedes calificar a quienes asistieron"
    (`RatingService.kt:46`) para aceptar al organizador como `ratedPlayerId` aunque no haya escaneado
    (`isRateable(conv, userId) = attended(...) || isOrganizer(...)`).
- **Nuevo `NoShowService` + ruta** `POST /convocatories/{id}/report-no-show`:
  - valida que el solicitante asistió (escaneó),
  - valida ventana `[scheduled_at + 15min, scheduled_at + 48h]`,
  - inserta el reporte (idempotente; índice único evita duplicados),
  - recalcula consenso: si `reportes > asistentes/2` → aplica los 3 efectos.
- **`GET /convocatories/{id}/no-show-status`** para la UI:
  ¿puedo reportar?, ¿ya reporté?, votos actuales, ¿ventana abierta?, ¿consenso
  alcanzado?, `markedNoShow` (¿me marcó ausente el organizador?).
- **`AttendanceRepository.recomputeAttendancePct`** (Parte B): pasa al modelo
  explícito — `presente = aprobados − marcas de no-show`; el escaneo ya no entra
  en el numerador.
- **Nuevo `AttendanceDeclarationService` + `AttendanceDeclarationRepository`** (Parte B):
  - `GET /convocatories/{id}/attendance-list` — aprobados con `scanned`/`markedNoShow`.
  - `POST /convocatories/{id}/declare-attendance` body `{ absentPlayerIds }`.
  - Validaciones: solo el organizador; solo tras `+45min`; no marcar a quien
    escaneó ni a no-aprobados; **bloqueado si `organizer_no_show`** (§6).
  - Al guardar: `replaceMarks` + recompute reversible + `organizer_confirmed = true`.
- **Blindajes de coherencia (§6)**:
  - `NoShowService.report/status`: si `organizer_confirmed` → `canReport = false` y
    `409` al forzar.
  - `NoShowService`: al alcanzar consenso, invoca
    `AttendanceDeclarationRepository.clearMarksAndRecompute` (revierte marcas).
- **Badge de pendientes**: `ConvocatoryRepository.findByOrganizer` calcula
  `pendingCount` (postulaciones `pending`) y lo expone en `/convocatories/mine`.

### 3. Shared
- Modelo `NoShowStatus` (canReport, alreadyReported, windowOpen, reports,
  attendees, consensusReached, **markedNoShow**).
- Modelo `PlayerAttendanceRow` (playerId, name, avatarUrl, positionPrimary,
  scanned, markedNoShow) y `Convocatory` ahora con `organizerNoShow` y `pendingCount`.
- `AttendanceApi`: `reportNoShow`, `noShowStatus`, **`attendanceList`**,
  **`declareAttendance`**.
- `AttendanceRepository` (dominio + impl): los cuatro métodos.
- Use cases `ReportOrganizerNoShowUseCase`, `GetNoShowStatusUseCase`,
  **`GetAttendanceListUseCase`**, **`DeclareAttendanceUseCase`**; registrados en
  `SharedModule`.

### 4. Android
- **Escaneo cruzado** (`TabScreens.kt`): el organizador puede escanear (marca su
  presencia) y un aprobado puede generar el QR. La navegación al escáner lleva un
  flag `isOrganizer` para adaptar el copy.
- **`NoShowViewModel`**: carga el estado y emite el voto por convocatoria.
- **Botón "El organizador no llegó"** (`NoShowSection` en `MyGameCard`) con
  diálogo de confirmación y estados: disponible / reportado (con conteo
  `votos/asistentes`) / consenso confirmado. Solo visible cuando aplica.

### 4.1. Retoques de UX (iteración posterior)
- **Card "Mis convocatorias"**: "Ver postulantes" se movió al header como chip
  (la card entera ya abría Postulantes). La fila inferior quedó solo con acciones
  del día del partido.
- **Accesos rápidos** (`QuickAction`): "Ya llegué" / "Mostrar QR" / "Calificar"
  como ícono en pastilla circular + etiqueta. "Ya llegué" es primaria (pastilla
  sólida); las demás tonales. Mismo componente en ambas cards (organizador y
  convocado) para un lenguaje visual consistente.
  - Copy: "Asistí" → **"Ya llegué"** (vale para ambos roles); "QR" → **"Mostrar QR"**.
- **Escáner de QR** (`QRScannerScreen`): instrucciones paso a paso en una tarjeta
  semitransparente, con texto **según el rol** ("Pídele a el organizador / a un
  jugador convocado que toque «Mostrar QR»"). Recibe `isOrganizer` vía navegación.

### 4.2. Asistencia declarada + avisos (Parte B/C)
- **`AttendanceDeclarationScreen` + `AttendanceDeclarationViewModel`**: lista de
  aprobados; "Presente" firme para los que escanearon, toggle "Llegó / No llegó"
  para los demás, botón "Guardar asistencia". Si el backend responde `403`
  (organizador marcado no-show), muestra un estado bloqueado explicativo.
- **QuickAction "Asistencia"** en la card del organizador (gateado por
  `isMatchOver`), **deshabilitado** si `organizer_no_show`. Navegación
  `attendance/{convocatoryId}`.
- **Banners** (`MarkedNoShowBanner` / `OrganizerNoShowBanner`) en las cards de
  convocado y organizador respectivamente.
- **Badge** de postulaciones pendientes (`BadgedBox` sobre "Ver postulantes").

### 5. Tests
- `AttendanceServiceTest`: organizador escanea; aprobado genera QR; no-participante rechazado.
- `RatingServiceTest`: organizador ausente no califica; organizador ausente sí es calificable; organizador presente (escaneó) puede ambas.
- `NoShowServiceTest`:
  - reporte antes de `+15min` rechazado; después de `+48h` rechazado;
  - 1 voto no aplica efectos; mayoría aplica los 3 + revierte marcas de convocados;
  - no-aprobado no puede reportar; reporte duplicado no cuenta doble;
  - `organizer_confirmed` bloquea el reporte (`canReport = false` + conflict);
  - `status` refleja `markedNoShow`.
- `AttendanceDeclarationServiceTest`:
  - solo el organizador declara; solo tras el cierre;
  - no marcar a quien escaneó ni a no-aprobados;
  - declarar marca + recalcula a los afectados + `organizer_confirmed`;
  - lista vacía confirma igual; re-declarar revierte penalizaciones;
  - `organizer_no_show` bloquea declarar y ver la lista.

---

## Estado de implementación
- ✅ Backend (datos, repos, servicios, rutas) — compila, tests en verde.
- ✅ Shared (modelos, API, repo, use cases) — compila.
- ✅ Android (escaneo cruzado, no-show, asistencia declarada, banners, badge,
  accesos rápidos, escáner) — compila.
- ✅ Valores de **producción** restaurados: apertura `+15min`, cierre/`calificar`
  `+45min`, margen de 1 h al crear convocatoria (se usaron valores reducidos solo
  durante el desarrollo).
- ⏳ **No verificado en runtime**: los flujos end-to-end (escanear → reportar →
  consenso; declarar asistencia; exclusión mutua) están cubiertos por tests con
  fakes, no contra BD real ni en dispositivo. La UI no se ha revisado renderizada.
- Rama: `feat/organizador-no-show`.

## Archivos clave (referencia)

Backend:
- `backend/.../service/AttendanceService.kt`, `RatingService.kt`, `NoShowService.kt`,
  `AttendanceDeclarationService.kt`
- `backend/.../repository/AttendanceRepository.kt`, `RatingRepository.kt`,
  `NoShowRepository.kt`, `AttendanceDeclarationRepository.kt`, `ConvocatoryRepository.kt`
- `backend/.../routes/NoShowRoutes.kt`, `AttendanceDeclarationRoutes.kt`, `ConvocatoryRoutes.kt`
- `backend/.../db/tables/Tables.kt`
- `backend/.../plugins/Databases.kt` (registro de tablas), `Routing.kt`, `StatusPages.kt` (errores)

Shared:
- `shared/.../data/models/Models.kt` (NoShowStatus, PlayerAttendanceRow, Convocatory)
- `shared/.../data/remote/AttendanceApi.kt`
- `shared/.../domain/usecase/attendance/AttendanceUseCases.kt`

Android:
- `androidApp/.../ui/screens/TabScreens.kt` (cards + QuickAction + NoShowSection + banners + badge)
- `androidApp/.../ui/screens/AttendanceDeclarationScreen.kt` (lista de asistencia)
- `androidApp/.../ui/screens/QRScannerScreen.kt` (instrucciones por rol)
- `androidApp/.../ui/attendance/NoShowViewModel.kt`, `AttendanceDeclarationViewModel.kt`
- `androidApp/.../ui/components/ConvocatoryCardParts.kt` (fases por tiempo)
- `androidApp/.../ui/ElDraftApp.kt`, `MainScaffold.kt` (navegación)
