# Caso borde: el organizador convoca pero no llega al partido

> ✅ IMPLEMENTADO (backend + shared + Android). Tests del backend en verde.
>
> Diseño acordado para resolver el caso en que quien convoca no se presenta:
> que un convocado pueda reportarlo y que esa ausencia tenga consecuencias,
> sin abrir la puerta a reportes falsos.

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
| Reporte de no-show | **Por consenso** de asistentes (no basta un solo reporte). |
| Umbral de consenso | **Mayoría estricta**: `reportes > asistentes_validados / 2`. |
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

- Solo reportan **asistentes con QR validado** (1 voto c/u, sin duplicar).
- Umbral: `reportes > asistentes_validados / 2` (mayoría estricta).
- Al alcanzarse → 3 efectos sobre el organizador:
  - baja `attendance_pct` (cuenta como inasistencia suya),
  - baja `responsibility_score` (visible en su cromo),
  - `organizer_no_show = true` en la convocatoria (visible para futuros postulantes).
- **Antiabuso**: un solo reporte no hace nada; hace falta mayoría → neutraliza
  el reporte falso por despecho.

---

## Plan técnico

### 1. Datos
- **Nueva tabla** `organizer_no_show_reports`:
  `reporter_id`, `convocatory_id`, `created_at`; índice único `(reporter_id, convocatory_id)`.
- **Nueva columna** `organizer_no_show: Boolean` (default `false`) en `ConvocatoriesTable`.

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
  ¿puedo reportar?, ¿ya reporté?, votos actuales, ¿ventana abierta?, ¿consenso alcanzado?

### 3. Shared / Android
- `GenerateAttendanceQrUseCase`: actualizar comentario (ya no es "solo organizador").
- **UI asistencia** (`TabScreens.kt`):
  - exponer **escanear** también en la tarjeta de "Mis convocatorias" (para que el organizador marque su presencia, hoy `TabScreens.kt:79` solo genera),
  - exponer **generar** también en "Mis postulaciones" aprobadas (hoy `TabScreens.kt:214` solo escanea).
- **Botón "El organizador no se presentó"** en la pantalla post-partido del asistente,
  con confirmación y estados: deshabilitado / disponible / reportado (esperando consenso) / consenso alcanzado.
- **Marca visible** de no-show en la tarjeta de convocatoria.

### 4. Tests
- `AttendanceServiceTest`: organizador puede escanear; aprobado puede generar QR.
- `RatingServiceTest`: organizador ausente no califica; organizador ausente sí es calificable; organizador presente (escaneó) puede ambas.
- `NoShowServiceTest` (nuevo):
  - reporte antes de `+15min` rechazado; después de `+48h` rechazado;
  - 1 voto no aplica efectos; mayoría aplica los 3;
  - no-asistente no puede reportar; reporte duplicado no cuenta doble.

---

## Orden de implementación sugerido
1. Datos (tabla + columna).
2. Backend: repos → servicios → rutas.
3. Shared (use cases / repos / modelos).
4. Android (UI asistencia + botón no-show + marca visible).
5. Tests en cada capa.

## Archivos clave (referencia)
- `backend/.../service/AttendanceService.kt`
- `backend/.../service/RatingService.kt`
- `backend/.../repository/AttendanceRepository.kt`
- `backend/.../repository/RatingRepository.kt`
- `backend/.../routes/AttendanceRoutes.kt`
- `backend/.../routes/RatingRoutes.kt`
- `backend/.../db/tables/Tables.kt`
- `shared/.../domain/usecase/attendance/AttendanceUseCases.kt`
- `androidApp/.../ui/screens/TabScreens.kt`
- `androidApp/.../ui/attendance/AttendanceViewModel.kt`
