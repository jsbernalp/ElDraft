---
name: commit
description: Redacta el mensaje de commit en el estilo de elDraft — infinitivo en español, describiendo el cambio visible y no los archivos, con un cuerpo que explica síntoma, causa y decisión — y lo somete a aprobación antes de ejecutar `git commit`. Úsala siempre que haya que commitear, guardar o "subir" cambios, cuando pidan un mensaje de commit, y también antes de proponer cualquier commit propio al terminar un trabajo. No hace `git push` nunca.
---

# Commits de elDraft

## Lo primero: nunca commitees sin aprobación

El flujo es siempre el mismo, y el paso 3 no se salta aunque el cambio parezca
trivial o el mensaje evidente:

1. Mira qué hay realmente en el commit: `git status` y `git diff --staged`. Si
   hay cambios sin preparar que claramente pertenecen a este commit, dilo — no
   los añadas por tu cuenta.
2. Redacta el mensaje completo siguiendo este documento.
3. **Muéstralo entero y espera un sí explícito.** Sin eso no se ejecuta
   `git commit`.
4. Después del commit, para. `git push` es una decisión aparte que se pide
   aparte.

Esto no es burocracia: el mensaje es la única documentación que sobrevive al
código, y quien lo firma es el autor. Un commit que aparece sin haberse leído
antes es un texto que nadie escribió.

## El asunto

Verbo en **infinitivo, en español**, describiendo el cambio tal como lo notaría
alguien que usa la app. Sin prefijos de tipo, sin nombres de archivo, sin punto
final. Apunta a unos 60-72 caracteres.

```
Quitar el cero de arranque al escribir la cuota
Dejar de ofrecer "Ya llegué" a quien ya llegó
Poner el backend en la hora local de las convocatorias
Sacar de "mis postulaciones" las que ya no llevan a ningún sitio
Declarar los targets de iOS solo en macOS
```

La prueba de si el asunto está bien: **se entiende sin abrir el diff**. "Corregir
bug en MapViewModel" no dice nada; "Retirar de las listas los partidos que ya no
piden nada" sí.

Verbos que ya usa el repo, por si ayudan a arrancar: Quitar, Dejar (de), Sacar,
Retirar, Mover, Poner, Añadir, Separar, Proponer, Fijar, Cambiar, Centralizar,
Abstraer, Declarar, Publicar, Borrar, Cerrar, Corregir.

### Commits de fase

Cuando el commit cierra una fase del `Plan Técnico MVP`, el asunto lleva el
prefijo de la fase y describe el alcance:

```
Fase 6: Asistencia, QR y no-show en iOS
Fase 8 (parte sin cuenta paga): notificaciones locales y deep links en iOS
Fase 2 (parcial): agregar Firebase Auth y GoogleSignIn por SPM
```

El paréntesis se usa para ser honesto sobre lo que queda fuera. Es un prefijo
para hitos del plan, no para trabajo normal.

### La historia tiene dos épocas — no copies la vieja

Hasta julio de 2026 el repo usó conventional commits (`feat(ui):`, `fix(ux):`,
`docs:`). Ese estilo **se abandonó**: desde agosto de 2026 todos los commits son
infinitivo en español. Si miras la historia para calibrar el estilo, mira lo
reciente; un `feat(android):` de junio no es el estándar, es un fósil.

## El cuerpo

Casi todos los commits llevan cuerpo (134 de 139). Va a unos 72-76 caracteres por
línea y sigue este orden, que es el que hace que el commit siga sirviendo dos
años después:

1. **El síntoma**, en términos de quien lo sufre. Qué se veía mal, qué no
   funcionaba, qué era confuso.
2. **La causa**. Por qué pasaba de verdad. Esta es la parte que más valor
   conserva con el tiempo y la que nadie puede reconstruir después leyendo el
   diff.
3. **La decisión**. Qué se hizo y por qué así y no de otra forma. Si hubo un
   detalle no obvio —un orden de operaciones que importa, una alternativa
   descartada— va aquí.
4. **Lo que queda**, cuando aplica: deuda asumida, lo que no se migró, si esto es
   un parche y cuál sería la solución de fondo.

Un ejemplo real del repo, condensado:

> **Poner el backend en la hora local de las convocatorias**
>
> Las convocatorias de las próximas horas eran invisibles para todo el mundo
> menos para su organizador, y cancelarlas fallaba con "no se puede cancelar un
> partido que ya comenzó" estando el partido todavía por jugarse.  ← síntoma
>
> Las dos cosas son el mismo error. La app manda `scheduled_at` sin offset […]
> En Railway el proceso corre en UTC, así que ese "ahora" va cinco horas por
> delante […]  ← causa
>
> Se fija la zona en los dos lados de la comparación […] TimeZone.setDefault
> antes de abrir el pool —el driver manda la zona de la JVM en el handshake, así
> que el orden importa—  ← decisión
>
> Es un parche. Mientras `scheduled_at` siga siendo una hora sin offset, la app
> solo funciona en una zona horaria; la solución de fondo es que el cliente
> mande el instante con offset.  ← lo que queda

### Dos fórmulas del repo que vale la pena mantener

- **"De paso, corrige un bug real: …"** — cuando al hacer el trabajo principal
  apareció y se arregló otra cosa. Hace visible un arreglo que si no quedaría
  enterrado en un diff que nadie relaciona con él.
- **Mencionar los tests que fijan el comportamiento**: "Dos tests nuevos fijan la
  asimetría: la asistencia del organizador bloquea el reporte, la del jugador
  no."

### Cierre

El mensaje termina con el trailer de coautoría en el formato que indiquen tus
instrucciones de sesión, nombrando el modelo que escribió el cambio.

## Qué NO hacer

- **No uses `--no-verify`.** El hook pre-commit verifica los design tokens de la
  UI de Compose. Si bloquea, hay un valor de diseño quemado: se corrige con los
  tokens de `ui/theme/`, o se marca el one-off legítimo con
  `// design-tokens-ignore: <razón>`. Saltarse el hook mete en `main` justo lo
  que el hook existe para impedir, y nadie se entera hasta la siguiente revisión.
- **No mezcles asuntos.** Un commit, una idea. Si el mensaje necesita un "y
  además" que no sea un "de paso", probablemente son dos commits.
- **No describas archivos.** "Actualizar MapViewModel.kt y MapTabContent.kt" es
  el diff, no el cambio.
- **No inventes el porqué.** Si no sabes por qué se hizo algo así, pregúntalo en
  vez de rellenar con una justificación plausible. Un cuerpo que explica mal es
  peor que uno que no explica.
