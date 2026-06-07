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
| Auth | Firebase Auth (Google + Apple Sign-In) | BOM 33.6.0 |
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
  fee, format, ambiente, status, created_at
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
{ "event": "new_pin", "data": { "id": "...", "lat": X, "lng": Y, "slots": 2, "format": "Fútbol 5" } }
{ "event": "pin_closed", "data": { "id": "..." } }
```

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
├── androidApp/                  ✅ App Android (BUILD SUCCESSFUL)
│   ├── build.gradle.kts         ✅ AGP 8.7.3, Compose, Firebase, Maps, CameraX, Koin
│   ├── AndroidManifest.xml      ✅ Permisos: INTERNET, LOCATION, CAMERA, NOTIFICATIONS
│   ├── src/main/res/
│   │   ├── mipmap-*/            ✅ ic_launcher.png en todos los densities
│   │   └── values/              ✅ styles.xml, colors.xml, strings.xml
│   └── src/main/kotlin/
│       ├── MainActivity.kt      ✅
│       ├── ui/ElDraftApp.kt     ✅ NavHost con todas las rutas
│       ├── ui/theme/            ✅ Paleta "Fuego y Asfalto" (naranja/rojo/asfalto)
│       └── ui/screens/          ✅ 9 screens (stubs con TODO)
│
└── backend/                     ✅ Ktor Server
    ├── build.gradle.kts         ✅ Ktor 3.0.1, Exposed, HikariCP, Koin
    ├── Application.kt           ✅ Entry point
    ├── plugins/                 ✅ Serialization, CORS, Auth JWT, DB, WebSockets, Routing, StatusPages
    ├── db/tables/Tables.kt      ✅ 6 tablas Exposed ORM
    ├── routes/                  ✅ 6 archivos (auth, players, convocatories, postulations, attendance, ratings)
    └── websocket/MapWebSocket.kt ✅ Broadcast geofiltrado en tiempo real
```

---

## 8. Pantallas / Screens del MVP

| Screen | Archivo | Estado |
|---|---|---|
| `SplashScreen` | SplashScreen.kt | ✅ Stub creado |
| `LoginScreen` | LoginScreen.kt | ✅ Stub creado |
| `OnboardingProfileScreen` | OnboardingProfileScreen.kt | ✅ Stub creado |
| `HomeScreen (HorizontalPager)` | HomeScreen.kt | ✅ Stub creado |
| `CreateDraftScreen` | CreateDraftScreen.kt | ✅ Stub creado |
| `ApplicantsScreen` | ApplicantsScreen.kt | ✅ Stub creado |
| `PlayerCromoScreen` | PlayerCromoScreen.kt | ✅ Stub creado |
| `QRGeneratorScreen` | QRGeneratorScreen.kt | ✅ Stub creado |
| `QRScannerScreen` | QRScannerScreen.kt | ✅ Stub creado |
| `PostMatchRatingScreen` | PostMatchRatingScreen.kt | ✅ Stub creado |
| `PinDetailSheet` | — | ⬜ Pendiente |
| `PlayerProfileSetupScreen` | — | ⬜ Pendiente (fusionar con Onboarding) |

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
- [ ] Firebase project creado (pendiente)

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
- [ ] Apple Sign-In (diferido; requiere cuenta Apple Developer)
- [ ] Prueba en emulador con backend + Postgres corriendo (pendiente de runtime)

### Fase 2 — El Draft + Mapa ⬜ PENDIENTE
- [x] PostgreSQL local con extensión PostGIS instalada (Docker) ✅
- [ ] `CreateDraftScreen` con selección de ubicación en mapa
- [ ] Endpoint `POST /convocatories` guardando en BD
- [ ] Query `GET /convocatories/nearby` con `ST_DWithin` de PostGIS
- [ ] `MapScreen` con Google Maps y pines reales
- [ ] Conexión WebSocket cliente (`ElDraftApi.observeMapEvents`)
- [ ] `PinDetailSheet` bottom sheet al tocar un pin

### Fase 3 — Postulaciones + Notificaciones ⬜ PENDIENTE
- [ ] `ApplicantsScreen` con datos reales y botones Aprobar/Rechazar
- [ ] Endpoints de postulación funcionales
- [ ] FCM: enviar push al organizador cuando alguien se postula
- [ ] `ElDraftMessagingService` para recibir notificaciones

### Fase 4 — Asistencia + Reputación ⬜ PENDIENTE
- [ ] `QRGeneratorScreen` con QR real (ZXing) + countdown 10 min
- [ ] `QRScannerScreen` con CameraX + ML Kit real
- [ ] Endpoint `POST /attendance/scan` + actualización de `attendance_pct`
- [ ] `PostMatchRatingScreen` con calificación por jugador
- [ ] Recálculo de `sportsmanship_score` tras cada partido

### Fase 5 — QA + Lanzamiento ⬜ PENDIENTE
- [ ] Testing E2E de flujos críticos
- [ ] Optimización de queries PostGIS
- [ ] Configuración de producción (servidor, dominio, SSL)
- [ ] Publicación App Store + Play Store

---

## 10. Riesgos Técnicos

| Riesgo | Mitigación |
|---|---|
| KMP en iOS: Camera2 y ML Kit no disponibles nativamente | Usar `expect/actual`; ML Kit tiene versión iOS oficial |
| WebSockets con muchos usuarios concurrentes | `MapSessionRegistry` en memoria → evaluar Redis pub/sub al escalar |
| Precisión de geolocalización en interiores (canchas cubiertas) | Aceptar imprecisión en MVP; campo `addressText` como fallback |
| Fraude QR (compartir screenshot) | QR con expiración de 10 min ya modelado en `attendance_records.qr_expires_at` |
| `play-services-location` jalando Kotlin más reciente | `resolutionStrategy.force` ya aplicado en `androidApp/build.gradle.kts` |
