# Backend — patrones de Ktor + Exposed + PostGIS

Detalle de cada capa del servidor. El orden general de una feature está en el
`SKILL.md`.

## 1. Tabla (`db/tables/Tables.kt`)

Todas las tablas viven en ese archivo, como `object X : UUIDTable("nombre_snake")`.

```kotlin
object PostulationsTable : UUIDTable("postulations") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val status = varchar("status", 20).default("pending")
    val createdAt = datetime("created_at")
}
```

**Columna nueva: `.nullable()` o `.default(...)`, sin excepciones.** No hay
migraciones; el arranque llama a `createMissingTablesAndColumns`, que no puede
añadir un `NOT NULL` sin default a una tabla con filas. El síntoma no es un error
claro al arrancar sino un fallo posterior en runtime, y la salida de emergencia
(`DATABASE_RESET_ON_START=true`) borra datos de producción.

### Columnas geográficas

Las columnas `geography` de PostGIS **no** se declaran en Exposed. Se guardan
`location_lat` / `location_lng` como `double` y la columna `location` se crea y
se mantiene con SQL crudo en `plugins/Databases.kt`:

```sql
ALTER TABLE convocatories ADD COLUMN IF NOT EXISTS location geography(Point, 4326);
UPDATE convocatories SET location = ST_SetSRID(ST_MakePoint(location_lng, location_lat), 4326)::geography ...
CREATE INDEX IF NOT EXISTS convocatories_location_idx ON convocatories USING GIST (location);
```

Ojo al orden de los argumentos: `ST_MakePoint(lng, lat)`, longitud primero. Un
punto con las coordenadas invertidas no falla, simplemente aparece en otro
continente.

## 2. Repositorio (`repository/`)

Dos piezas: un `data class XRecord` (el modelo interno del servidor, distinto del
DTO que sale por HTTP) y una **`open class XRepository`** con métodos `open`.

```kotlin
data class PostulationRecord(val id: UUID, val status: String, /* … */)

open class PostulationRepository {
    open fun findById(id: UUID): PostulationRecord? = transaction { /* … */ }
}
```

Que la clase sea `open` es lo que permite testear los servicios sin base de
datos: el test hereda y sobreescribe solo los métodos que el servicio consulta.
Si declaras el repositorio `final`, dejas su servicio sin poder testear.

Cada método abre su propia `transaction { }`.

### SQL crudo

Se usa donde Exposed no llega (consultas PostGIS). Interpola solo valores ya
tipados como número —`Double`, `Int`, `UUID`—, nunca texto que venga del usuario:
un `String` interpolado en un `exec()` es una inyección SQL esperando a ocurrir.

## 3. Servicio (`service/`)

Reglas de negocio, autorización y notificaciones. **No sabe que existe HTTP**:
no toca `call`, no responde códigos de estado. Cuando algo no procede, lanza una
excepción tipada declarada en el mismo archivo:

```kotlin
class PostulationForbidden(message: String) : RuntimeException(message)
class PostulationConflict(message: String) : RuntimeException(message)
```

La convención de nombres ya está establecida y conviene respetarla, porque es lo
que hace legible el mapeo de `StatusPages`:

| Sufijo | Status HTTP | Cuándo |
|---|---|---|
| `…Forbidden` | 403 | El usuario no es quien puede hacer esto |
| `…Conflict` | 409 | El estado actual no lo permite (ya ocurrió, choca con otra cosa) |
| `…NotFound` | 404 | No existe |
| `…Invalid` / `…WindowClosed` | 400 | Datos inválidos o fuera de plazo |
| `IllegalArgumentException` | 400 | Validación simple; ya está mapeada |

El mensaje de la excepción **es el texto que verá el usuario final** en la app:
`ApiException.userMessage` lo saca del cuerpo de la respuesta. Escríbelo en
español y en términos de producto ("Ya te postulaste a este partido"), no de
implementación ("duplicate key violation").

## 4. Rutas (`routes/`)

DTOs `@Serializable` arriba del archivo, luego una función de extensión
`fun Route.xRoutes()`. La ruta parsea, delega y responde:

```kotlin
fun Route.postulationRoutes() {
    val service = application.get<PostulationService>()

    authenticate("firebase-auth") {
        post("/convocatories/{convocatoryId}/apply") {
            val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
            val playerId = call.currentUserId()
            val body = call.receive<ApplyRequest>()
            call.respond(HttpStatusCode.Created, service.apply(convocatoryId, playerId, body.position).toDto())
        }
    }
}
```

- `parseUuid(raw, campo)` es una función **privada de cada archivo de rutas**,
  repetida en los seis. Un archivo de rutas nuevo lleva su propia copia; no la
  busques en `plugins/`.
- `call.currentUserId()` solo funciona dentro de `authenticate("firebase-auth")`.
- Para una ruta pública que quiera *saber* quién pregunta sin exigirlo, existe
  `call.optionalUserId()`: devuelve `null` ante un token ausente o inválido y
  nunca concede acceso extra. Se usa, por ejemplo, para ocultarle al organizador
  sus propias convocatorias en `/nearby`.
- La conversión `Record → Dto` va en una función `toDto()` privada del archivo de
  rutas. El `Record` no sale nunca por el cable: es el que puede llevar campos
  internos.

## 5. Registro

- `di/BackendModule.kt`: `singleOf(::XService)` (o un `single { }` explícito si el
  constructor necesita valores de configuración).
- `plugins/Routing.kt`: llamar a `xRoutes()` dentro de `route("/api/v1")`.
- `plugins/StatusPages.kt`: una entrada `exception<X> { }` por excepción tipada
  nueva. Sin ella el cliente recibe un 500 y muestra "El servidor tuvo un
  problema" en lugar del motivo.

## Fechas y zona horaria

`scheduled_at` se guarda como `LocalDateTime` **sin zona**, en hora local de las
convocatorias. La sesión de Postgres se fija con `SET TIME ZONE` al abrir la
conexión (`app.timezone`, por defecto la de Colombia) para que el `NOW()` de las
consultas crudas esté en el mismo reloj que esa columna.

Consecuencia práctica: un `scheduledAt` que llegue con sufijo `Z` o con offset
**no** lo parsea `LocalDateTime.parse` y el fallo no es obvio. El cliente manda
`"2027-01-15T20:00:00"` a secas.

## Tests

`backend/src/test/kotlin/...`, con `kotlin.test`. El patrón es sustituir los
repositorios `open` por fakes y probar el servicio sin base de datos:

```kotlin
private class FakeConvocatoryRepo(val record: ConvocatoryRecord?) : ConvocatoryRepository() {
    override fun findById(id: UUID): ConvocatoryRecord? = record
}
```

Se ejecutan con `./gradlew -PbackendOnly :backend:test`. El flag `-PbackendOnly`
excluye `:shared` y `:androidApp` del `settings.gradle.kts`; sin él, Gradle exige
un SDK de Android solo para correr los tests del servidor.
