# elDraft

**Nunca más te quedes sin partido.**

elDraft resuelve el problema de las cancelaciones de última hora en el fútbol
aficionado: conecta a organizadores con partidos incompletos con jugadores
disponibles en su zona, en tiempo real y sobre un mapa.

Un organizador publica una *convocatoria* ("me faltan 2 arqueros para hoy a las
8"), aparece como un pin en el mapa de los jugadores cercanos, ellos se postulan
y el organizador aprueba manualmente según la ficha técnica de cada uno.

---

## Funcionalidades

| | |
|---|---|
| **Mapa de convocatorias** | Pines de partidos activos en un radio de ~5 km, actualizados en vivo por WebSocket |
| **El Draft** | Creación rápida de convocatoria: cupos, posición, cuota, formato (Fútbol 5/7/8/9/11) y ambiente (Recocha vs. Competitivo) |
| **Postulación y aprobación** | El jugador se postula desde el pin; el organizador recibe push y aprueba/rechaza manualmente |
| **El Cromo** | Ficha técnica del jugador: posiciones, pierna hábil, físico, y reputación (% de asistencia + compañerismo) |
| **Cancelación** | Solo el organizador, antes del inicio, con motivo obligatorio. Cancelar con < 20 min penaliza el perfil |
| **Asistencia por QR** | El jugador genera un QR al llegar a la cancha; el organizador lo escanea y se actualizan sus stats |
| **Calificación post-partido** | Valoración de compañerismo que alimenta el sistema de reputación |

Extras: notificaciones push con deep links, prevención de partidos cruzados
(no puedes postularte a dos convocatorias que se solapan) y sonido de silbato
personalizado.

---

## Stack

Monorepo **Kotlin Multiplatform** con backend propio.

```
┌──────────────────────────────────┐
│   App móvil (Android + iOS)      │
│   Compose Multiplatform          │
│   shared/ → lógica de negocio    │
└────────────┬─────────────────────┘
             │ REST + WebSocket
┌────────────▼─────────────────────┐
│   Backend Ktor (Netty)           │
└────────────┬─────────────────────┘
             │
┌────────────▼─────────────────────┐   ┌──────────────────────┐
│   PostgreSQL 16 + PostGIS 3.4    │   │  Firebase Auth + FCM │
└──────────────────────────────────┘   └──────────────────────┘
```

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.1.21 (KMP) |
| UI | Compose Multiplatform 1.8.2 + Material3 |
| Backend | Ktor Server 3.0.1 (Netty) |
| Base de datos | PostgreSQL 16 + PostGIS 3.4 + Exposed ORM 0.55.0 |
| Tiempo real | WebSockets (Ktor) |
| Auth | Firebase Auth + Credential Manager (Google Sign-In) |
| Push | Firebase Cloud Messaging |
| Sesión | Jetpack DataStore |
| Cámara / QR | CameraX + ML Kit Barcode |
| Ubicación | FusedLocationProviderClient |
| DI | Koin 4.0.0 |
| Build | Gradle 8.11.1 · AGP 8.7.3 · JDK 17 (Corretto) |

### Módulos

```
elDraft-app/
├── shared/       Modelos + cliente HTTP/WebSocket (KMP)
├── androidApp/   App Android (Compose)
├── iosApp/       Wrapper iOS
└── backend/      API Ktor + PostGIS
```

---

## Puesta en marcha

### Requisitos

- JDK 17 (Amazon Corretto)
- Docker (para la base de datos)
- Android Studio Ladybug o superior
- Xcode 16+ (solo si vas a compilar iOS)

### 1. Base de datos

Desde `elDraft-app/`:

```bash
docker compose up -d
```

Levanta PostgreSQL 16 + PostGIS en `localhost:5432` (db/usuario/password:
`eldraft`). El backend crea la extensión PostGIS y sincroniza el esquema solo al
arrancar.

```bash
docker compose stop     # pausar (conserva datos)
docker compose down -v  # borrar todo y resetear la DB
```

> La DB debe estar arriba **antes** de correr el backend, o HikariCP falla con
> `Connection to localhost:5432 refused`.

### 2. Configuración local

Crea `elDraft-app/local.properties` (gitignored):

```properties
sdk.dir=/ruta/al/Android/sdk
MAPS_API_KEY=tu_api_key_de_google_maps
# Opcional: IP de tu máquina en la WiFi para probar en dispositivo físico.
# El emulador usa 10.0.2.2 por defecto.
DEV_HOST=192.168.1.x
# Opcional: service account de Firebase para enviar push desde el backend.
FIREBASE_SERVICE_ACCOUNT_PATH=/ruta/a/service-account.json
```

También necesitas `androidApp/google-services.json` de tu proyecto Firebase
(tampoco se versiona).

### 3. Backend

```bash
./gradlew :backend:run
```

Responde en `http://0.0.0.0:8080`. El modo de autenticación se controla con
`firebase.authMode` (`mock` para desarrollo | `firebase`) en `application.conf`.

### 4. App Android

Abre `elDraft-app/` en Android Studio (Gradle JVM → corretto-17) y ejecuta la
configuración `androidApp`, o:

```bash
./gradlew :androidApp:assembleDebug
```

---

## API

Base: `/api/v1`

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/auth/login` | Verifica token Firebase, crea/retorna usuario, emite JWT |
| `PUT` | `/auth/phone` | Guarda número de teléfono |
| `GET` | `/players/:id/profile` | Ficha técnica (El Cromo) |
| `PUT` | `/players/:id/profile` | Actualiza datos técnicos |
| `POST` | `/convocatories` | Crea convocatoria |
| `GET` | `/convocatories/nearby?lat=&lng=&radius=5000` | Pines cercanos (PostGIS `ST_DWithin`) |
| `GET` | `/convocatories/:id` | Detalle |
| `GET` | `/convocatories/mine` | Mis convocatorias (organizador) |
| `DELETE` | `/convocatories/:id` | Cancela convocatoria |
| `POST` | `/convocatories/:id/apply` | Postularse |
| `GET` | `/convocatories/:id/applicants` | Lista de postulantes |
| `PUT` | `/postulations/:id/approve` | Aprobar postulante |
| `PUT` | `/postulations/:id/reject` | Rechazar postulante |
| `POST` | `/attendance/generate-qr` | Genera QR (expira en 10 min) |
| `POST` | `/attendance/scan` | Valida asistencia |
| `POST` | `/ratings` | Calificación post-partido |

### WebSocket

```
ws://host/ws/map?lat=X&lng=Y&radius=5000
```

Emite eventos geofiltrados cuando cambia el estado del mapa:

```json
{ "event": "new_pin",    "data": { "id": "...", "lat": 4.6, "lng": -74.1, "slots": 2, "format": "Fútbol 5" } }
{ "event": "pin_closed", "data": { "id": "..." } }
```

`pin_closed` se emite tanto si la convocatoria se llena como si se cancela.

---

## Convenciones de código

La UI de Compose tiene dos reglas obligatorias, documentadas en
[`elDraft-app/CLAUDE.md`](elDraft-app/CLAUDE.md):

1. **Design tokens** — nada de colores, spacing, radios o elevaciones quemados.
   Todo sale de `ElDraftTheme.*` (paleta "Fuego y Asfalto").
2. **Strings en recursos** — todo texto visible viene de `strings.xml`, nunca
   inline en el `.kt`.

Un linter y un hook pre-commit lo verifican. Instálalo una vez tras clonar:

```bash
./scripts/install-hooks.sh          # instala el hook
./scripts/check-design-tokens.sh    # ejecución manual
```

---

## Documentación

| Documento | Contenido |
|---|---|
| [Documento de Requerimientos (MVP)](Documento%20de%20Requerimientos_%20elDraft%20(MVP).md) | Alcance funcional, identidad visual, modelo de negocio |
| [Plan Técnico MVP](Plan%20T%C3%A9cnico%20MVP%20-%20elDraft.md) | Stack, esquema de BD, endpoints, fases de implementación |
| [Plan de Mejora Arquitectónica](Plan%20de%20Mejora%20Arquitect%C3%B3nica%20-%20elDraft.md) | Deuda técnica y refactors planeados |
| [Buscar Cupo — Ajustes UX](Buscar%20Cupo%20-%20Ajustes%20UX.md) | Iteraciones sobre la pantalla de búsqueda |
| [Caso Borde — Organizador No Llega](Caso%20Borde%20-%20Organizador%20No%20Llega.md) | Diseño del flujo de no-show del organizador |

---

## Estado

MVP funcional. Fases 0 a 3.5 completadas: autenticación, perfil, mapa con
PostGIS, creación de convocatorias, postulaciones con push, cancelación,
asistencia por QR y calificación post-partido.

Pendiente: Apple Sign-In (requiere cuenta Apple Developer), date/time picker
completo en la creación de convocatoria y build de iOS.

## Monetización

Gratuita para el usuario final. El modelo B2B contempla "pines patrocinados" y
convocatorias globales para que las canchas sintéticas llenen horarios vacíos.
