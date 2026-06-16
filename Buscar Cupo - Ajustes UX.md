# Buscar Cupo — ajustes de UX y flujo

> ✅ IMPLEMENTADO (Android). Build en verde, validado en emulador.
> Commits en la rama `feat/buscar-cupo` (`84e9e93..8f9e541`).
>
> Este documento recoge los ajustes hechos sobre la pestaña **Buscar Cupo**
> (descubrir y postularse a convocatorias abiertas) y el acceso "Cómo llegar"
> de la pestaña **Juego**. El hilo común es reducir fricción: que el usuario
> vea lo que le sirve sin trabajo extra y sin clics muertos.

## Resumen de cambios

| # | Ajuste | Commit |
|---|---|---|
| 1 | Ocultar convocatorias propias + agrupar pines por ubicación | `84e9e93` |
| 2 | Toggle Lista/Mapa con la **lista** como vista por defecto | `c14e90c` |
| 3 | Indicar en la card/sheet que **ya te postulaste** | `39b81f9` |
| 4 | Botón **"Cómo llegar"** en postulaciones aprobadas | `33a605b` |
| 5 | Cargar al entrar + loading inicial + pantalla de permiso | `8f9e541` |

---

## 1. Ocultar convocatorias propias y agrupar pines

### Problema

- El organizador veía **su propia convocatoria** como un pin más en el mapa de
  Buscar Cupo (no tiene sentido postularse a lo que uno organiza).
- Varias convocatorias en **la misma cancha** se dibujaban como pines
  superpuestos, imposibles de distinguir o tocar.

### Decisiones

| Tema | Decisión |
|---|---|
| Ocultar las propias | **En el servidor**, no en el cliente (no enviar `organizerId` en el pin). |
| `/nearby` (snapshot REST) | Autenticación **opcional**: si llega el token, se excluye al organizador; anónimo recibe todo (pero no tiene convocatorias propias). |
| WebSocket (`new_pin`) | Se excluye la **sesión del creador** vía `userId` en el query param; el broadcast no le reenvía su propio pin. |
| Agrupar pines | **Opción A**: un marcador por ubicación con **badge numérico** si hay más de una. |
| Clave de agrupación | **Coordenada exacta** (`lat,lng`). |
| Interacción del grupo | Tap → **sheet con lista** de las convocatorias de esa ubicación → elegir → detalle. |

### Cómo

- Backend: `optionalUserId()` (decodifica el JWT **sin verificar firma**, solo
  como pista para filtrar — nunca otorga acceso), `findNearby(..., excludeOrganizerId)`,
  y `broadcast(..., excludeUserId)`.
- Android: los pines se agrupan con `groupBy { it.lat to it.lng }`; un grupo de
  uno usa `MapPin()` (gota de marca con balón), un grupo de varios usa
  `MapPin(count)` (la misma gota con el número) y abre `PinGroupSheet`.

---

## 2. Toggle Lista/Mapa (lista por defecto)

### Problema

Buscar Cupo era **solo un mapa**. Pero quien busca cupo decide por **datos**
(hora, formato, cupos, cuota, ambiente), que se escanean mejor en una lista
vertical que tocando pines uno por uno. El mapa responde otra pregunta ("¿qué
tan lejos / en qué zona queda?"), valiosa pero secundaria.

### Decisiones

| Tema | Decisión |
|---|---|
| Control | **Toggle de segmentos** (`Lista \| Mapa`) fijo arriba, patrón Airbnb/Plei. |
| Vista por defecto | **Lista**. |
| Orden de la lista | **Por hora del partido** (los más próximos a empezar primero). |
| Contenido de la tarjeta | Completa: fecha/hora, formato, dirección, cupos, cuota, ambiente y chips de posiciones. |
| Fuente de datos | Lista y mapa **comparten el mismo `MapViewModel`** (un solo snapshot REST + WebSocket) y abren el **mismo** `PinDetailSheet`. |

### Cómo

- `BuscarCupoScreen` posee el estado de selección y los sheets; `MapTabContent`
  dejó de gestionarlos y solo notifica `onPinClick`/`onGroupClick`.
- Nuevo `ConvocatoryListContent` con la lista ordenada y los estados
  loading/empty.

---

## 3. Indicar que ya te postulaste

### Problema

El usuario podía abrir el detalle de una convocatoria a la que **ya se había
postulado**, tocar "Postularme" y recibir el error "Ya te postulaste". Un clic
desperdiciado y un error evitable.

### Regla del backend (la UI la refleja, no la cambia)

`PostulationService.apply` falla si ya existe **cualquier** postulación de ese
jugador a esa convocatoria (índice único `convocatory_id + player_id`), **sin
importar el estado**. Es decir: **una postulación por convocatoria, para
siempre** — pendiente, aprobada **o rechazada**, todas bloquean re-postularse.

### Decisiones

| Tema | Decisión |
|---|---|
| Postulación rechazada | **Bloquea igual** (refleja la regla real; no se toca el backend). |
| Visibilidad | **Badge** en la card/fila + **botón deshabilitado** en el sheet. |
| Estados mostrados | "Ya te postulaste" (pending) / "Aprobada" / "Rechazada". |

### Cómo

- `MapViewModel` expone `myPostulations: Map<convocatoryId, status>`, cargado en
  `loadArea` y refrescado tras postularse (`refreshMyPostulations`).
- El badge aparece en `ConvocatoryListContent`, en cada fila del
  `PinGroupSheet`, y el botón de `PinDetailSheet` queda deshabilitado con el
  texto del estado (y oculta el selector de posición).

---

## 4. "Cómo llegar" en postulaciones aprobadas

### Problema

Una vez aprobada la postulación, el usuario tenía la dirección como texto pero
ningún atajo para navegar hasta la cancha.

### Decisiones

| Tema | Decisión |
|---|---|
| Dónde | En la card de **Mis postulaciones** (pestaña Juego), solo si la postulación está **aprobada** y la convocatoria tiene ubicación. |
| Destino | Abre **Google Maps en modo navegación** (`google.navigation:`), con la ruta lista para pulsar "Iniciar". |
| Fallback | Si Google Maps no está, `geo:` genérico (cualquier app de mapas). Si no hay ninguna, Toast. |
| Detección de app | **try/catch** sobre `startActivity`, no `resolveActivity` (la visibilidad de paquetes de Android 11+ daría un falso negativo con `setPackage`). |

### Cómo

- `util/MapNavigation.kt → openDirections(context, lat, lng, label)`.
- `QuickAction` "Cómo llegar" como primera acción del bloque de aprobados en
  `MyGameCard`.

---

## 5. Cargar al entrar, loading inicial y pantalla de permiso

### Problema

1. La carga (`loadArea`) vivía **dentro del mapa**. Como la lista es la vista
   por defecto, al entrar nadie la disparaba → "No hay partidos cerca". Había
   que ir a Mapa, esperar y volver a Lista para ver datos.
2. Mientras se obtenía la ubicación GPS, la lista mostraba el **estado vacío**
   en vez de un loading (la carga aún no había empezado, `isLoading` era false).
3. Si el usuario **negaba** el permiso de ubicación, toda la funcionalidad
   quedaba bloqueada sin explicación. Android además deja de mostrar el diálogo
   tras varias negativas.

### Decisiones

| Tema | Decisión |
|---|---|
| Dónde vive la carga | **En `BuscarCupoScreen`** (no en el mapa): la ubicación + `loadArea` se disparan al entrar a la pestaña, sea cual sea la vista activa. |
| Loading inicial | `MapUiState.hasLoadedOnce` (false hasta la primera carga, incluida la espera de GPS); la lista muestra `LoadingState` en vez del estado vacío mientras prepara. |
| Sin permiso | **Pantalla explicativa** (`LocationPermissionRequired`), **sin** fallback a Medellín: la ubicación es central para "cerca de ti". |
| Botón de la pantalla | Abre **Ajustes del sistema** de la app (`ACTION_APPLICATION_DETAILS_SETTINGS`) — más fiable que reintentar el diálogo. |
| Re-chequeo | Al volver de Ajustes (`ON_RESUME`) se re-verifica el permiso; si se concedió, dispara la carga. |

### Cómo

- `BuscarCupoScreen` posee permiso, `cameraPositionState` y los `LaunchedEffect`
  de ubicación/carga; `MapTabContent` los recibe como parámetros.
- `util/MapNavigation.kt → openAppSettings(context)`.

---

## Notas

- El permiso de **cámara** (manifest) es de otra funcionalidad: el escáner de QR
  de asistencia (`QRScannerScreen`), que lo pide on-demand al abrirse. **No** se
  solicita en Buscar Cupo.
- Valores de prueba (umbrales de partido reducidos a 10 min, etc.) y la falta de
  remoto git son del entorno de desarrollo, no de estos cambios.
