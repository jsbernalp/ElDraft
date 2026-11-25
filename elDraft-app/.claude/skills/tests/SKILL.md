---
name: tests
description: Escribir, ejecutar y diagnosticar los tests de elDraft — qué se prueba en cada suite (backend, shared, ViewModels de Android), los dos patrones de fake que usa el proyecto, los comandos exactos de Gradle incluido el flag -PbackendOnly, cómo leer un reporte fallido, y cuándo toca el smoke test end-to-end antes de un release. Úsala siempre que se vaya a añadir o arreglar un test, cuando falle el CI, cuando haya que decidir si un cambio necesita cobertura, y al terminar cualquier feature con lógica de negocio — un test roto frena el despliegue del backend, así que no es opcional.
---

# Tests de elDraft

## Las tres suites

| Suite | Qué cubre | Comando |
|---|---|---|
| `:backend:test` | Servicios: autorización, conflictos, reglas de negocio del servidor | `./gradlew -PbackendOnly :backend:test` |
| `:shared:testDebugUnitTest` | Casos de uso, lógica de dominio (`MatchTiming`, `ScheduleDefaults`), repositorios | `./gradlew :shared:testDebugUnitTest` |
| `:androidApp:testDebugUnitTest` | ViewModels: transiciones de estado y mensajes de error | `./gradlew :androidApp:testDebugUnitTest` |

Las tres corren en CI (`.github/workflows/backend.yml`) y **las tres pueden
frenar un despliegue**: Railway tiene "Wait for CI" y espera el check suite
completo del commit. Un test de ViewModel roto deja el backend sin desplegar.
Es deliberado —no se despliega desde un commit roto— pero conviene saberlo antes
de empujar a `main`.

### Por qué `-PbackendOnly`

Ese flag excluye `:shared` y `:androidApp` del `settings.gradle.kts`. Sin él,
Gradle configura los plugins de Android —que exigen un SDK instalado— solo para
poder correr los tests del servidor. En una máquina o un runner sin SDK, el build
del backend falla sin haber compilado nada suyo.

No lo uses para las otras dos suites: `:shared` y `:androidApp` **necesitan** el
SDK, y con el flag puesto ni siquiera existen como proyectos.

## Los dos patrones de fake

El proyecto no usa librerías de mocking. Se escriben fakes a mano, y el patrón
cambia según la capa porque las dos capas exponen cosas distintas.

### Backend: heredar del repositorio real

Los repositorios del backend son `open class` con métodos `open` justamente para
esto. El test hereda y sobreescribe **solo lo que el servicio consulta**:

```kotlin
private class FakeConvocatoryRepo(val record: ConvocatoryRecord?) : ConvocatoryRepository() {
    override fun findById(id: UUID): ConvocatoryRecord? = record
}
```

Así se prueba el servicio sin base de datos, sin Docker y sin PostGIS. Si al
añadir un repositorio lo declaras `final`, dejas su servicio sin poder testear.

### shared y Android: implementar la interfaz de dominio

En el cliente los repositorios **son interfaces**, así que el fake las implementa
entera. Los métodos que el test no ejercita se dejan explícitos:

```kotlin
private class FakePostulationRepository(...) : PostulationRepository {
    override suspend fun getApplicants(convocatoryId: String) = applicants
    override suspend fun withdraw(postulationId: String) = error("no usado")
}
```

Ese `error("no usado")` es intencional: documenta que el método no participa en
la prueba y hace ruido si algún día empieza a llamarse sin querer.

**Los casos de uso son clases concretas, no interfaces**: no los simules,
constrúyelos de verdad sobre el fake del repositorio
(`GetApplicantsUseCase(repo)`). Así el test cubre también la validación que vive
en el caso de uso.

## Tests de ViewModel

Los ViewModels se prueban en la JVM —sin emulador ni Robolectric— porque no tocan
el framework de Android: solo exponen `StateFlow` sobre casos de uso de `shared`.
Lo único que falta es sustituir el `Dispatchers.Main` que usa `viewModelScope`, y
de eso se encarga `MainDispatcherRule` (`androidApp/src/test/kotlin/com/eldraft/android/`):

```kotlin
class XViewModelTest {
    private val main = MainDispatcherRule()
    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    @Test
    fun load_publica_los_datos() = runTest {
        val vm = XViewModel(GetXUseCase(FakeRepo()))
        vm.load("c1")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
    }
}
```

El dispatcher es `StandardTestDispatcher`, así que las corrutinas **no avanzan
solas**: hay que llamar a `advanceUntilIdle()`. Eso no es un estorbo, es la
función principal — permite comprobar los estados intermedios que la pantalla sí
muestra y que con un dispatcher inmediato serían invisibles:

```kotlin
vm.load("c1")
assertTrue(vm.state.value.isLoading)   // sin advanceUntilIdle: lo que ve la UI mientras espera
```

Lo que vale la pena cubrir en un ViewModel: el estado de carga, que el error
publicado sea el **mensaje del backend** (no el respaldo), y los flags que
deshabilitan botones (`decidingIds` y similares). Lo que no: que un `copy()`
copie.

## Nombres

Español, `snake_case`, describiendo el comportamiento esperado y no el método:

```kotlin
fun apply_con_id_vacio_falla_sin_llamar_repo()
fun load_con_error_del_backend_muestra_el_mensaje_del_servidor()
fun mientras_decide_bloquea_el_boton_de_ese_postulante()
```

El nombre debe leerse como la frase que dirías al explicar por qué existe el
test. Cuando falle en el CI, es lo único que vas a ver.

## Qué no testear

- **UI de Compose y SwiftUI.** No hay tests de instrumentación y no se van a
  añadir por ahora.
- **Mapas, cámara y QR contra hardware.** El simulador de iOS no tiene cámara;
  `QRScannerView` trae un atajo `#if DEBUG` para pegar el payload a mano.
- **Wrappers sin decisiones.** Un `RepositoryImpl` que solo delega en la API no
  necesita test propio; lo que se prueba es el caso de uso que decide algo.

La pregunta útil no es "¿esto tiene cobertura?" sino "**¿esto decide algo?**". Si
hay un `if`, un `require`, una comparación de fechas o una regla de autorización,
merece test. Si solo pasa datos, no.

## Cuando el CI falla

El workflow sube el reporte HTML como artifact (`backend-test-report`,
`shared-test-report`, `android-test-report`) solo cuando el job falla. En local,
los reportes quedan en:

```
<módulo>/build/reports/tests/…       # HTML navegable
<módulo>/build/test-results/…/*.xml  # XML, más fácil de leer desde terminal
```

El XML da el resultado sin abrir el navegador:

```bash
python3 -c "
import xml.etree.ElementTree as ET,glob
for f in glob.glob('*/build/test-results/**/TEST-*.xml', recursive=True):
    r=ET.parse(f).getroot()
    if r.get('failures')!='0' or r.get('errors')!='0':
        print(r.get('name'))
        for tc in r.iter('testcase'):
            for fail in tc.iter('failure'): print('  ✗', tc.get('name'), '→', fail.get('message'))"
```

## Smoke test — la última puerta antes de un release

`./scripts/smoke-test.sh` recorre el camino crítico contra el **backend
desplegado**: Firebase → JWT propio → PostGIS → WebSocket. No es un test unitario
ni corre en CI; se lanza a mano antes de generar un build de release.

```bash
export BASE_URL=https://tu-servicio.up.railway.app
export FIREBASE_API_KEY=...  SMOKE_EMAIL=...  SMOKE_PASSWORD=...
./scripts/smoke-test.sh
```

Lo más valioso que comprueba: que `/auth/login` **rechace** un token de Firebase
inventado. Si lo acepta, el backend está en modo mock y cualquiera puede
autenticarse como quien quiera — ahí se para todo y no se genera ningún release.

Lo que **no** cubre, y hay que probar a mano en un dispositivo real:

- Que llegue un push FCM de verdad (necesita un token de dispositivo).
- El render del mapa y el flujo de UI.
- La persistencia tras un reinicio: eso es el modo
  `./scripts/smoke-test.sh --verificar <id>`, que se corre después de reiniciar
  el servicio en Railway.

## iOS

- `iosAppUITests/` corre con `xcodebuild test -scheme iosApp` y prueba dentro de
  una app real instalada en el simulador. Es la única forma de verificar cosas
  como que la sesión persista en el Keychain.
- `:shared:iosSimulatorArm64Test` ejecuta los tests de `shared` como binario
  nativo suelto, sin acceso a Keychain.
- **Nada de esto corre en CI**: los runners son Ubuntu y compilar iOS exige
  macOS. Es verificación local, y conviene decirlo explícitamente cuando se
  reporta que "los tests pasan".
