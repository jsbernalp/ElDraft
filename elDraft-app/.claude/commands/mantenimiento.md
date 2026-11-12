---
description: Factory de mantenimiento de elDraft — tests faltantes, barrido de UI, una dependencia o salud de CI/backups
argument-hint: "[tests|barrido|deps|salud] [detalle opcional]"
---

# /mantenimiento

Argumentos recibidos: `$ARGUMENTS`

Toma la primera palabra como modo y el resto como detalle (una suite, una
dependencia, un archivo). Si no hay modo, pregunta cuál de los cuatro, con una
línea por opción.

| Modo | Qué hace | Quién lo hace |
|---|---|---|
| `tests` | Cubre lógica sin tests en **una** suite (detalle opcional: `android`, `shared` o `backend`) | subagente `cazador-tests` |
| `barrido` | Tokens y strings quemados en toda la UI de Compose | subagente `barrendero` |
| `deps` | Sube y prueba **una** dependencia (detalle opcional: su nombre en `libs.versions.toml`) | subagente `actualizador-deps` |
| `salud` | Estado de los últimos runs de CI y del backup | tú mismo, solo lectura (ver abajo) |

## Reglas comunes a todos los modos

- **Antes de lanzar un subagente**, revisa que el árbol esté limpio
  (`git status --short`). Si hay cambios ajenos, dilo y pregunta antes de
  seguir: el mantenimiento no se mezcla con trabajo a medias.
- Ningún modo hace commit ni push. Al terminar, si hubo cambios, ofrece
  commitear con la skill `commit`, que muestra el mensaje y espera un sí.
- Si hay que crear una rama, sugiere el prefijo `chore/mantenimiento-<modo>-<fecha>`
  y espera confirmación. No la crees por tu cuenta.
- Relaya al usuario el reporte del subagente resumido. No lo pegues entero.

## Modo `salud` (solo lectura)

Desde la raíz del repo, con la CLI `gh` autenticada:

```bash
gh run list --workflow "Backend CI" --branch main --limit 5
gh run list --workflow "Backup de la base de datos" --limit 7
gh run list --workflow "iOS CI" --limit 5
```

Los nombres son el campo `name:` de cada archivo en `.github/workflows/`. Si
`gh` no está instalado o no tiene sesión, dilo y sugiere `! gh auth login`.

Reporta en pocas líneas:

- **Backend CI en `main`**: el último commit, ¿pasó? Un rojo aquí significa que
  Railway tiene el deploy en SKIPPED y que producción no tiene ese commit.
- **Backup**: ¿hay un run exitoso en las últimas ~26 h? El cron es diario a las
  08:00 UTC. Dos fallos seguidos son una alarma: no hay otro respaldo.
- **iOS CI**: estado del último run, si existe. No bloquea nada; solo informa.

Para cualquier run fallido, muestra el job y el paso que falló
(`gh run view <id> --log-failed`, solo las primeras líneas). No relances runs
ni toques nada: `salud` solo mira.

## Programarlo después

Esto **no** está programado todavía, a propósito: primero conviene correr cada
modo a mano y ver qué reporta. Cuando ya sea fiable, se convierte en rutina con
la skill `/schedule`, por ejemplo:

- `salud` cada día laborable por la mañana, que avise solo si algo está en rojo;
- `tests` una vez por semana, de modo que cada semana deje una rama revisable;
- `deps` una vez al mes.

Al programarlo, ten en cuenta:

- Las rutinas corren en la nube, sin tu máquina. No tienen Android SDK ni
  macOS a menos que el entorno los provea, así que en la práctica `tests` y
  `deps` solo pueden correr `-PbackendOnly :backend:test`. Para lo demás,
  conviene que abran un PR y dejen que el CI de GitHub haga la verificación.
- Una rutina **sí** puede empujar ramas y abrir PRs. Decide explícitamente si
  quieres eso, porque es lo contrario de la regla "commit solo con aprobación".
  La alternativa segura es que la rutina deje solo el reporte.
- `salud` necesita acceso de lectura a GitHub Actions en el entorno de la
  rutina.
