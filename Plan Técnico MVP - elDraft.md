# Plan Técnico MVP — elDraft

## 1. IDEs y Herramientas de Desarrollo

| Parte del proyecto | IDE / Herramienta | Por qué |
|---|---|---|
| **Android (KMP + Compose)** | Android Studio (Ladybug o superior) | Soporte oficial para KMP, Compose preview, emuladores Android, profiler integrado |
| **iOS (iosApp wrapper)** | Xcode 16+ | Obligatorio para compilar y firmar la app en iOS; también para el simulador |
| **Backend (Ktor + Kotlin)** | IntelliJ IDEA — abrir desde la raíz `elDraft-app/` | Mejor soporte para Ktor, Exposed ORM; abrir la raíz del monorepo para que detecte `:backend` como submódulo |
| **Base de datos (PostgreSQL + PostGIS)** | Docker (contenedor `postgis/postgis`) para correr la DB + TablePlus o DBeaver como GUI | Docker levanta Postgres con PostGIS sin instalar nada en el Mac; la GUI sirve para diseñar esquemas, ejecutar queries geoespaciales y revisar datos |
| **Diseño UI / Prototipos** | Figma | Diseño de pantallas, sistema de colores "Fuego y Asfalto", handoff a Compose |
| **Control de versiones** | Git + GitHub / GitLab | Monorepo con módulos KMP + backend |
| **CI/CD** | GitHub Actions | Builds automáticos para Android, iOS y backend |
| **API Testing** | Postman o Bruno | Prueba manual de endpoints REST y WebSocket |
| **Gestión de tareas** | Linear o Notion | Seguimiento de fases e historias de usuario |

### Notas importantes sobre los IDEs

- **Android Studio** abre la raíz `elDraft-app/` para ver los módulos `:shared` y `:androidApp`.
- **IntelliJ IDEA** también abre la raíz `elDraft-app/` (no la subcarpeta `backend/`). Así detecta el `settings.gradle.kts` y carga los 3 módulos correctamente.
- En ambos IDEs: **Gradle JVM → corretto-17** (Java 17). El `~/.gradle/gradle.properties` global también lo fuerza como respaldo.
- **Xcode** solo para compilar/probar en iOS/simulador.

---

## 2. Stack Tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Kotlin | Kotlin Multiplatform | 2.1.21 |
| Android Gradle Plugin | AGP | 8.7.3 |
| Gradle Wrapper | Gradle | 8.11.1 |
| Compose Multiplatform | CMP | 1.8.2 |
| Compose Material3 | androidx.compose.material3 | 1.3.2 |
| Backend | Ktor Server (Netty) | 3.0.1 |
| Base de datos | PostgreSQL + PostGIS + Exposed ORM | 0.55.0 |
| Tiempo real | WebSockets (Ktor) | — |
| Auth | Firebase Auth + Credential Manager (Google Sign-In) | BOM 33.6.0 / credentials 1.3.0 / googleid 1.1.1 |
| Sesión (cliente) | Jetpack DataStore (Preferences) | 1.1.7 |
| Notificaciones | Firebase Cloud Messaging (FCM) | — |
| Cámara / QR | CameraX 1.3.4 + ML Kit Barcode 17.3.0 | — |
| Ubicación | FusedLocationProviderClient (play-services-location 21.3.0) | — |
| DI | Koin | 4.0.0 |
| Imágenes | Coil3 | 3.4.0 |
| JDK | Amazon Corretto 17 | 17.0.19 |

---

## 3. Arquitectura General

```
┌─────────────────────────────────┐
│       KMP Mobile App            │
│  (Android + iOS - Compose MP)   │
│                                 │
│  shared/  ← lógica de negocio   │
│  android/ ← entry point         │
│  ios/     ← entry point         │
└──────────────┬──────────────────┘
               │ REST + WebSocket
┌──────────────▼──────────────────┐
│         Ktor Backend            │
│  ┌─────────┐  ┌──────────────┐  │
│  │  REST   │  │  WebSocket   │  │
│  │  API    │  │   Handler    │  │
│  └────┬────┘  └──────┬───────┘  │
└───────┼──────────────┼──────────┘
        │              │
┌───────▼──────────────▼──────────┐
│   PostgreSQL + PostGIS          │
└─────────────────────────────────┘
        │
┌───────▼──────────────────────────┐
│   Firebase (Auth + FCM)          │
└──────────────────────────────────┘
```

---

## 4. Módulos y Entidades de Base de Datos

### Tablas principales

```sql
-- Usuarios
users (id, firebase_uid, name, phone, email, avatar_url, created_at)

-- Ficha técnica del jugador (El Cromo)
player_profiles (
  user_id, position_primary, position_secondary,
  dominant_foot, height, build,
  speed_rating, precision_rating,
  attendance_pct, sportsmanship_score, total_matches
)

-- Convocatorias (El Draft)
convocatories (
  id, organizer_id, location GEOGRAPHY(POINT),
  address_text, slots_needed, position_required,
  fee, format, ambiente, status, created_at,
  cancellation_reason VARCHAR(100),   -- motivo si fue cancelada
  cancelled_at TIMESTAMP              -- momento de cancelación
)

-- Postulaciones
postulations (
  id, convocatory_id, player_id, status (pending/approved/rejected), created_at
)

-- Validación de asistencia
attendance_records (
  id, convocatory_id, player_id, qr_code, qr_expires_at, scanned_at, validated BOOL
)

-- Calificaciones post-partido
ratings (
  id, convocatory_id, rater_id, rated_player_id, sportsmanship_score, created_at
)

-- Ficha técnica del jugador (campos adicionales)
player_profiles (
  ...,
  cancel_penalty_count INT DEFAULT 0  -- penalizaciones por cancelar con < 20 min
)
```

### Índice geoespacial crítico

```sql
CREATE INDEX convocatories_location_idx
  ON convocatories USING GIST(location);
```

---

## 5. Endpoints REST API

### Auth
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Verifica token Firebase, crea/retorna usuario |
| `PUT` | `/api/v1/auth/phone` | Guarda número de teléfono |

### Perfil
| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/players/:id/profile` | Retorna Ficha Técnica (El Cromo) |
| `PUT` | `/api/v1/players/:id/profile` | Actualiza datos técnicos |

### Convocatorias
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/convocatories` | Crea convocatoria (El Draft) |
| `GET` | `/api/v1/convocatories/nearby?lat=&lng=&radius=5000` | Pines en radio (PostGIS) |
| `GET` | `/api/v1/convocatories/:id` | Detalle de convocatoria |
| `GET` | `/api/v1/convocatories/mine` | Mis convocatorias (organizador) |
| `DELETE` | `/api/v1/convocatories/:id` | Cancela convocatoria (solo organizador, antes del inicio) |

### Postulaciones
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/convocatories/:id/apply` | Jugador se postula |
| `GET` | `/api/v1/convocatories/:id/applicants` | Lista postulantes (organizador) |
| `PUT` | `/api/v1/postulations/:id/approve` | Aprobar postulante |
| `PUT` | `/api/v1/postulations/:id/reject` | Rechazar postulante |

### Asistencia
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/attendance/generate-qr` | Genera QR con expiración 10 min |
| `POST` | `/api/v1/attendance/scan` | Escanea QR y valida asistencia |

### Valoraciones
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/ratings` | Envía calificación post-partido |

---

## 6. WebSocket — Actualización de Pines

```
ws://host/ws/map?lat=X&lng=Y&radius=5000
```

El servidor emite eventos cuando se publican nuevas convocatorias dentro del radio del cliente:

```json
{ "event": "new_pin",    "data": { "id": "...", "lat": X, "lng": Y, "slots": 2, "format": "Fútbol 5" } }
{ "event": "pin_closed", "data": { "id": "..." } }
```

> `pin_closed` se emite tanto cuando una convocatoria se llena como cuando se cancela.

---

## 6.1. Base de Datos Local (Docker)

La base de datos de desarrollo corre en un contenedor Docker con **PostgreSQL 16 + PostGIS 3.4** ya integrados (imagen `postgis/postgis:16-3.4`). No requiere instalar PostgreSQL en el Mac. La configuración vive en [`docker-compose.yml`](elDraft-app/docker-compose.yml) y usa exactamente los valores que el backend espera en `application.conf`:

| Parámetro | Valor |
|---|---|
| Host : Puerto | `localhost:5432` |
| Base de datos | `eldraft` |
| Usuario | `eldraft` |
| Password | `eldraft` |
| Datos persistentes | Volumen Docker `eldraft-pgdata` |

### Flujo de trabajo

Desde la carpeta `elDraft-app/`:

```bash
docker compose up -d      # Levantar la DB (hacer ANTES de correr el backend)
docker compose stop       # Pausar el contenedor (conserva los datos)
docker compose down       # Apagar y quitar el contenedor (datos se conservan en el volumen)
docker compose down -v    # Apagar y BORRAR los datos (reset total de la DB)
```

> **Importante:** la DB debe estar levantada (`docker compose up -d`) antes de ejecutar el backend desde IntelliJ / Android Studio. Si no, HikariCP falla con `Connection to localhost:5432 refused`.

Al arrancar, el backend ejecuta automáticamente `CREATE EXTENSION IF NOT EXISTS postgis;` y sincroniza las 6 tablas vía `SchemaUtils` ([Databases.kt](elDraft-app/backend/src/main/kotlin/com/eldraft/backend/plugins/Databases.kt)). Arranque verificado exitoso:

```
HikariPool-1 - Start completed.
Database connected and schema synchronized
Responding at http://0.0.0.0:8080
```

---

## 7. Estructura del Proyecto (estado actual)

```
elDraft-app/
├── gradle/
│   ├── libs.versions.toml       ✅ Version catalog completo
│   └── wrapper/
│       └── gradle-wrapper.properties  ✅ Gradle 8.11.1
├── gradle.properties            ✅ Java 17, config cache, Android flags
├── settings.gradle.kts          ✅ 3 módulos: shared, androidApp, backend
├── build.gradle.kts             ✅ Plugins raíz declarados
├── gradlew                      ✅ Wrapper funcional
├── docker-compose.yml           ✅ PostgreSQL 16 + PostGIS 3.4 (DB local)
│
├── shared/                      ✅ Módulo KMP
│   └── src/commonMain/kotlin/com/eldraft/
│       ├── data/models/Models.kt      ✅ Modelos: User, Convocatory, PlayerProfile, MapPin...
│       └── data/api/ElDraftApi.kt     ✅ Cliente HTTP + WebSocket (Ktor client)
│
├── androidApp/                  ✅ App Android (probada en emulador)
│   ├── build.gradle.kts         ✅ + Credential Manager, DataStore, Lifecycle
│   ├── google-services.json     ✅ Firebase (fuera de git)
│   ├── AndroidManifest.xml      ✅ Permisos + ElDraftApplication
│   ├── src/debug/               ✅ network_security_config (HTTP local solo debug)
│   └── src/main/kotlin/com/eldraft/android/
│       ├── ElDraftApplication.kt ✅ Service locator (api, session, googleAuth)
│       ├── MainActivity.kt      ✅
│       ├── data/
│       │   ├── SessionManager.kt    ✅ DataStore (JWT + userId)
│       │   └── GoogleAuthClient.kt  ✅ Google Sign-In (Credential Manager)
│       └── ui/
│           ├── ElDraftApp.kt         ✅ NavHost (needsOnboarding + back stack)
│           ├── ViewModelFactories.kt ✅ Inyección de ViewModels
│           ├── auth/AuthViewModel.kt ✅ Login (Google + dev mock)
│           ├── profile/ProfileViewModel.kt ✅ Onboarding + Cromo
│           ├── theme/                ✅ Paleta "Fuego y Asfalto"
│           └── screens/              ✅ Login/Onboarding/Cromo funcionales; resto stubs
│
└── backend/                     ✅ Ktor Server (auth + perfil funcionando)
    ├── build.gradle.kts         ✅ Ktor 3.0.1, Exposed, HikariCP, Koin
    ├── Application.kt           ✅ Entry point
    ├── application.conf         ✅ DB + JWT + firebase.authMode (mock|firebase)
    ├── auth/                    ✅ TokenVerifier, MockTokenVerifier (decodifica JWT), JwtService
    ├── repository/UserRepository.kt ✅ findOrCreate, perfil, teléfono
    ├── plugins/                 ✅ Serialization, CORS, Auth JWT, DB, WS, Routing, StatusPages
    ├── db/tables/Tables.kt      ✅ 6 tablas Exposed ORM (firebase_uid varchar 512)
    ├── routes/                  ✅ 6 archivos (auth + players con lógica real)
    └── websocket/MapWebSocket.kt ✅ Broadcast geofiltrado en tiempo real
```

---

## 8. Pantallas / Screens del MVP

| Screen | Archivo | Estado |
|---|---|---|
| `SplashScreen` | SplashScreen.kt | ✅ Funcional (enruta según sesión) |
| `LoginScreen` | LoginScreen.kt | ✅ Funcional (Google Sign-In real) |
| `OnboardingProfileScreen` | OnboardingProfileScreen.kt | ✅ Funcional (formulario → backend) |
| `OrganizoScreen` | TabScreens.kt | ✅ Funcional (mis convocatorias + cancelación + swipe-to-refresh) |
| `JuegoScreen` | TabScreens.kt | ✅ Funcional (mis postulaciones + swipe-to-refresh) |
| `BuscarCupoScreen` | TabScreens.kt | ✅ Funcional (lista + mapa + swipe-to-refresh) |
| `CreateDraftScreen` | CreateDraftScreen.kt | ✅ Funcional (Fútbol 5/7/8/9/11) |
| `ApplicantsScreen` | ApplicantsScreen.kt | ✅ Funcional (aprobar/rechazar + swipe-to-refresh) |
| `PlayerCromoScreen` | PlayerCromoScreen.kt | ✅ Funcional (ficha real del backend) |
| `QRGeneratorScreen` | QRGeneratorScreen.kt | ✅ Funcional |
| `QRScannerScreen` | QRScannerScreen.kt | ✅ Funcional |
| `PostMatchRatingScreen` | PostMatchRatingScreen.kt | ✅ Funcional |
| `PinDetailSheet` | PinDetailSheet.kt | ✅ Funcional (postulación desde mapa) |
| `CancelConvocatorySheet` | TabScreens.kt | ✅ Funcional (motivo obligatorio + confirmación) |

---

## 9. Fases de Implementación

### Fase 0 — Setup ✅ COMPLETADA
- [x] Monorepo KMP + backend scaffoldeado
- [x] Versiones de dependencias verificadas y funcionales
- [x] Gradle Wrapper 8.11.1 + AGP 8.7.3 + Kotlin 2.1.21
- [x] Build `androidApp` exitoso en Android Studio
- [x] Estructura de directorios y archivos base creada
- [x] PostgreSQL local + PostGIS levantado (Docker) y backend conectado ✅
- [x] Repositorio Git inicializado (rama `main`, `.gitignore` con secrets/artifacts) ✅
- [x] Firebase project creado (`eldraft-a6d42`, Auth + Google Sign-In habilitado) ✅

### Fase 1 — Auth + Perfil ✅ COMPLETADA (backend ✅ / Android ✅)

**Backend ✅ COMPLETADO y verificado end-to-end (10/10 pruebas curl):**
- [x] `TokenVerifier` (interfaz) + `MockTokenVerifier` (dev) + stub Firebase Admin
- [x] `JwtService` que emite JWT propio del backend con claim `userId`
- [x] Modo de auth configurable (`firebase.authMode` = `mock` | `firebase`)
- [x] Endpoint `POST /auth/login`: verifica token, crea/recupera usuario, emite JWT, `needsOnboarding`
- [x] Guardar usuario en PostgreSQL (`UserRepository.findOrCreateByIdentity`)
- [x] Endpoint `PUT /auth/phone` (autenticado)
- [x] Endpoint `GET /players/:id/profile` (público, 404 si no existe)
- [x] Endpoint `PUT /players/:id/profile` (solo dueño; 403 si es ajeno; validaciones 400)
- [x] Persistencia verificada en Postgres (acentos OK)

**Android ✅ COMPLETADO (build assembleDebug OK):**
- [x] Firebase project en consola (Auth habilitado, SHA-1 registrado)
- [x] `google-services.json` (con oauth_client) en `androidApp/`
- [x] Plugin `google-services` aplicado
- [x] Google Sign-In real con Credential Manager (`GoogleAuthClient`)
- [x] `SessionManager` con DataStore (persiste JWT + userId)
- [x] `SplashScreen` enruta según sesión guardada
- [x] Formulario completo de `OnboardingProfileScreen` (dropdowns posición/pierna/físico)
- [x] `PlayerCromoScreen` con datos reales (stats + reputación)
- [x] Cliente `ElDraftApi` consumiendo `/auth/login`, `/players/:id/profile`, `/auth/phone`
- [x] NavHost con manejo de `needsOnboarding` y back stack
- [x] **Probado en emulador end-to-end: login con Google real → onboarding → home** ✅
- [ ] Apple Sign-In (diferido; requiere cuenta Apple Developer)

**Problemas de integración resueltos durante las pruebas en emulador:**
- `GetCredentialResponse error` → el emulador no tenía cuenta de Google añadida. Se mejoró el manejo de errores en `GoogleAuthClient` (mensajes claros por subtipo).
- `Cleartext HTTP traffic not permitted` → Android bloquea HTTP plano (API 28+). Se agregó `network_security_config.xml` que permite cleartext **solo en debug** hacia `10.0.2.2`/`localhost` (release mantiene HTTPS).
- `Illegal input: fields missing` en `LoginResponse` → el backend/Postgres estaban caídos y la app intentaba parsear una respuesta de error. Se añadió `expectSuccess = true` + `ApiException` en `ElDraftApi` para fallar con mensaje claro.
- `Value exceeds length (1113 > 128)` → el ID token de Google (JWT largo) se guardaba completo como `firebase_uid`. `MockTokenVerifier` ahora **decodifica el JWT y extrae el claim `sub`** (uid corto + nombre/email reales); columna ampliada a `varchar(512)`.

### Fase 2 — El Draft + Mapa ✅ COMPLETADA Y PROBADA
- [x] PostgreSQL local con extensión PostGIS instalada (Docker) ✅
- [x] Columna `location GEOGRAPHY(Point,4326)` + índice GIST (gestionados por SQL crudo en `Databases.kt`) ✅
- [x] `CreateDraftScreen` con selección de ubicación en mapa (`LocationPickerMap`) ✅
- [x] Endpoint `POST /convocatories` guardando en BD (con `ST_MakePoint`) ✅
- [x] Query `GET /convocatories/nearby` con `ST_DWithin` de PostGIS (verificado: discrimina por distancia real) ✅
- [x] `MapTabContent` con Google Maps y pines reales de `/nearby` ✅
- [x] Conexión WebSocket cliente (`ConvocatoryApi.observeMapEvents` → `ObserveMapEventsUseCase`) ✅
- [x] `PinDetailSheet` bottom sheet al tocar un pin ✅
- [x] Capa Clean: `ConvocatoryRepository` (domain) + impl + `CreateConvocatoryUseCase`/`ObserveMapEventsUseCase` + tests ✅
- [x] Backend con `ConvocatoryService` (validación) + `ConvocatoryRepository` vía Koin ✅
- [x] Google Maps API key configurada vía `local.properties` + `manifestPlaceholders` (no versionada) ✅

> Pendiente menor para una iteración futura: date/time picker completo (hoy la convocatoria
> se programa por defecto a mañana 19:00) y centrar el mapa en la ubicación real del usuario
> (FusedLocationProvider). El botón "Postularme" del PinDetailSheet se conecta en Fase 3.

### Fase 3 — Postulaciones + Notificaciones ✅ COMPLETADA
- [x] `ApplicantsScreen` con datos reales y botones Aprobar/Rechazar
- [x] Endpoints de postulación funcionales (apply, approve, reject)
- [x] FCM: push al organizador cuando alguien se postula (`new_postulation`)
- [x] FCM: push al jugador cuando es aprobado/rechazado (`postulation_approved`, `postulation_rejected`)
- [x] `ElDraftMessagingService` para recibir notificaciones en foreground
- [x] Sonido personalizado de silbato de árbitro (`notification_eldraft.wav`)
- [x] Canales de notificación por categoría (Convocatorias, Postulaciones, General)
- [x] Deep links `eldraft://` para navegar desde la notificación a la pantalla correcta
- [x] `NotificationRefreshBus` (SharedFlow): refresca automáticamente la pantalla activa al recibir una notificación
- [x] Prevención de partidos cruzados: un jugador no puede postularse a convocatorias que se solapan en horario

### Fase 3.5 — Cancelación de Convocatorias ✅ COMPLETADA
- [x] Endpoint `DELETE /convocatories/:id` con validaciones de política
- [x] Selección de motivo obligatoria en la UI (`CancelConvocatorySheet`)
- [x] Penalización automática si se cancela con < 20 min de anticipación y hay aprobados
- [x] Push a todos los jugadores aprobados (`convocatory_cancelled`)
- [x] Cancelación automática de postulaciones pendientes/aprobadas
- [x] Evento `pin_closed` vía WebSocket para eliminar el pin del mapa en tiempo real
- [x] Botón "Cancelar partido" visible solo en convocatorias activas/llenas no iniciadas

### Fase 4 — Asistencia + Reputación ✅ COMPLETADA
- [x] `QRGeneratorScreen` con QR real + countdown 10 min
- [x] `QRScannerScreen` con CameraX + ML Kit real
- [x] Endpoint `POST /attendance/scan` + actualización de `attendance_pct`
- [x] `PostMatchRatingScreen` con calificación por jugador
- [x] Recálculo de `sportsmanship_score` tras cada partido
- [x] Reporte de ausencia del organizador por consenso de jugadores

### Fase 4.5 — UX / Pulido ✅ COMPLETADA
- [x] Swipe-to-refresh (PullToRefreshBox Material3) en Organizo, Juego, Postulantes y Buscar Cupo
- [x] Swipe-to-refresh funcional también en estado vacío y tras error de red
- [x] Formatos Fútbol 8 y Fútbol 9 añadidos al formulario de creación

### Fase 5 — QA + Lanzamiento ⬜ PENDIENTE
- [ ] Testing E2E de flujos críticos
- [ ] Optimización de queries PostGIS
- [ ] Configuración de producción (servidor, dominio, SSL)
- [ ] Publicación App Store + Play Store

---

## 10. Arquitectura de Notificaciones Push

### Flujo general

```
Backend (ConvocatoryService / PostulationService)
  └─► FcmService.sendNotification(token, title, body, data)
        └─► Firebase Cloud Messaging
              └─► Dispositivo Android
                    ├─► Foreground: ElDraftMessagingService.onMessageReceived()
                    │     ├─► NotificationHelper.show()  ← canal + sonido + deep link
                    │     └─► NotificationRefreshBus.emit(RefreshEvent)
                    │           └─► ViewModel activo recarga sus datos
                    └─► Background: sistema operativo muestra la notificación
```

### Tipos de notificación (`data.type`)

| Tipo | Canal | Destinatario | Refresca |
|---|---|---|---|
| `new_postulation` | Postulaciones | Organizador | `MY_MATCHES`, `APPLICANTS` |
| `postulation_approved` | Postulaciones | Jugador | `MY_POSTULATIONS` |
| `postulation_rejected` | Postulaciones | Jugador | `MY_POSTULATIONS` |
| `convocatory_cancelled` | Convocatorias | Jugadores aprobados | `MY_POSTULATIONS` |
| `new_convocatory` | Convocatorias | Jugadores cercanos | `MAP` |
| `convocatory_reminder` | Convocatorias | Jugadores aprobados | `MY_POSTULATIONS`, `MAP` |

### Deep links desde notificación

| Tipo | URI | Destino |
|---|---|---|
| `new_postulation` | `eldraft://applicants/{convocatoryId}` | Pantalla de postulantes |
| `postulation_approved` / `postulation_rejected` / `convocatory_cancelled` | `eldraft://tab/juego` | Tab Juego |
| `new_convocatory` / `convocatory_reminder` | `eldraft://tab/buscar_cupo` | Tab Buscar Cupo |

### Sonido personalizado

Archivo `res/raw/notification_eldraft.wav` (70 KB): silbato de árbitro con trino real (modulación de frecuencia 30 Hz simulando pepa de corcho) y ruido de turbulencia de aire. Patrón: corto-corto-largo. Los canales usan IDs `_v2` para forzar la recreación con el nuevo sonido (Android cachea la configuración de canales).

---

## 11. Riesgos Técnicos

| Riesgo | Mitigación |
|---|---|
| KMP en iOS: Camera2 y ML Kit no disponibles nativamente | Usar `expect/actual`; ML Kit tiene versión iOS oficial |
| WebSockets con muchos usuarios concurrentes | `MapSessionRegistry` en memoria → evaluar Redis pub/sub al escalar |
| Precisión de geolocalización en interiores (canchas cubiertas) | Aceptar imprecisión en MVP; campo `addressText` como fallback |
| Fraude QR (compartir screenshot) | QR con expiración de 10 min ya modelado en `attendance_records.qr_expires_at` |
| `play-services-location` jalando Kotlin más reciente | `resolutionStrategy.force` ya aplicado en `androidApp/build.gradle.kts` |
