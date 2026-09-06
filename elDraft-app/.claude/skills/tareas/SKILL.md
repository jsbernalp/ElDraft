---
name: tareas
description: Convierte una feature descrita en lenguaje de producto ("quiero que el organizador pueda X") en un plan de trabajo ordenado por capas para el monorepo de elDraft — qué archivo se toca en cada paso, con qué criterio se da por terminado, qué test lo fija, y las tareas transversales que siempre se olvidan (registros de DI, strings, tokens, paridad con iOS). Úsala al arrancar cualquier feature nueva o cambio que cruce más de un módulo, cuando pidan estimar o descomponer trabajo, cuando digan "por dónde empiezo", y antes de escribir código para algo que no sea un arreglo puntual.
---

# Planear una feature de elDraft

Produce un plan **en la sesión**, no un documento en el repo. Los documentos de
la raíz de git (`Plan Técnico MVP`) registran las fases ya cerradas; el plan de
trabajo del día vive en la conversación y en la lista de tareas.

## Antes de planear: mirar

Un plan escrito sin abrir el código es una lista de deseos. Antes de proponer
nada:

1. **Busca lo que ya existe.** La mitad de las features nuevas son una variación
   de algo hecho: hay seis verticales completas (auth, perfil, convocatorias,
   postulaciones, asistencia, calificación) que ya resolvieron el problema de
   forma parecida. Encuentra la más cercana y sigue su forma.
2. **Consulta la skill `arquitectura`** para el recorrido de capas y los puntos
   de DI. No repitas aquí ese conocimiento: aplícalo.
3. **Comprueba si la lógica ya está en `shared`.** `MatchTiming`,
   `ScheduleDefaults` y `DomainCatalogs` ya resuelven fases del partido, agenda y
   catálogos de dominio. Duplicarlos en una pantalla es el error más caro de
   deshacer, porque para cuando se nota ya hay dos versiones divergentes.

## Preguntar antes, no a mitad

Hay ambigüedades que cambian el plan entero. Si alguna está sin resolver,
plantéala **antes** de proponer las tareas, con una recomendación propia:

- **¿Quién puede hacerlo?** Organizador, jugador postulado, cualquiera. Define la
  autorización del servicio y suele ser la mitad de los tests del backend.
- **¿Cuándo deja de poder hacerse?** Casi todas las acciones de esta app tienen
  una ventana temporal (antes del partido, tras escanear, hasta el cierre). Si no
  se define, sale un `…WindowClosed` improvisado más tarde.
- **¿Qué se le notifica a quién?** Push, refresco de una pantalla abierta vía
  `NotificationRefreshBus`, o nada.
- **¿Toca el esquema?** Si sí, la columna tiene que ser nullable o con default —
  no hay migraciones.

## La forma del plan

Tareas ordenadas por capa, **en el orden en que se ejecutan**, agrupadas en dos
fases separadas: Android primero, iOS después. Cada tarea lleva tres cosas:

```
[capa] Qué se hace — archivo(s)
   Listo cuando: <criterio observable>
   Test: <qué lo fija, o "no aplica: no decide nada">
```

Ejemplo de la forma (no del contenido):

```
FASE ANDROID

[backend] Añadir columna `closed_at` a ConvocatoriesTable — db/tables/Tables.kt
   Listo cuando: arranca contra la BD local sin recrear tablas
   Test: no aplica (declaración)

[backend] Regla de cierre en ConvocatoryService — service/ConvocatoryService.kt
   Listo cuando: cerrar dos veces lanza ConvocatoryConflict
   Test: ConvocatoryServiceTest — cierre doble, cierre por quien no organiza

[backend] Endpoint POST /convocatories/{id}/close — routes/ConvocatoryRoutes.kt
   Listo cuando: responde 200 con el DTO y 403 a un no-organizador
   Test: cubierto por el del servicio

[shared] Caso de uso CloseConvocatoryUseCase + registro en SharedModule e IosKoin
   Listo cuando: la pantalla lo resuelve por Koin sin excepción
   Test: CloseConvocatoryUseCaseTest — validación de id vacío

[android] Estado y acción en MyMatchesViewModel
   Listo cuando: el botón se deshabilita mientras cierra y el error viene del backend
   Test: MyMatchesViewModelTest — carga, error, flag de bloqueo

[android] Botón en la card + strings + registro en AndroidModule
   Listo cuando: se ve y funciona en el emulador

FASE iOS  (skill `paridad-ios`)
[…]
```

**El criterio de "listo" tiene que ser observable.** "Implementar el servicio" no
es criterio; "cerrar dos veces lanza `ConvocatoryConflict`" sí. Sin eso el plan
no sirve para saber cuándo parar.

## Las transversales que siempre se olvidan

No son tareas separadas al final: van pegadas a la tarea que las provoca. Repasa
esta lista al cerrar el plan y mete lo que falte donde corresponda.

- Registro en `SharedModule` **y** accessor en `IosKoin` — por cada caso de uso.
- `viewModelOf(::XViewModel)` en `AndroidModule` — por cada ViewModel.
- `@Throws(Exception::class)` en interfaz de repositorio y caso de uso.
- Excepción tipada nueva → entrada en `StatusPages`.
- Texto visible → `strings.xml` (y su clave gemela en `Localizable.xcstrings`
  cuando llegue iOS).
- Diseño → tokens de `ui/theme/`, nunca literales.
- Ruta nueva → objeto en `sealed class Screen` + `composable` en el `NavHost`.

## Dimensionar

- **Cambio puntual** (un botón, una validación, un texto): no necesita plan
  formal. Di qué archivos tocas y hazlo.
- **Feature vertical** (cruza backend → shared → UI): el plan de arriba, con sus
  dos fases.
- **Fase del Plan Técnico** (un bloque grande, tipo "Asistencia + Reputación"):
  además de las tareas, di explícitamente **qué queda fuera**. Las fases de este
  repo se han cerrado con paréntesis honestos —"Fase 8 (parte sin cuenta paga)"—
  y esa costumbre es la que evita dar por terminado algo que no lo está.

## Al terminar

Cuando el trabajo esté hecho, dos cosas que no son opcionales:

1. **Anota qué queda para iOS.** Android va primero por decisión, no por olvido;
   si no queda registrado qué falta portar, la deuda se pierde hasta que alguien
   compara las dos apps.
2. **El commit lo redacta la skill `commit`**, que pide aprobación antes de
   ejecutarse.
