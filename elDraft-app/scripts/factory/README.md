# La factory de elDraft en GitHub

Las mismas tres factories que corren en local (`/feature`, `/paridad`,
`/mantenimiento`), pero disparadas desde GitHub y sin la terminal abierta: los
agentes trabajan en Actions, abren un PR y **tú apruebas** — el plan con una
etiqueta, el código mergeando.

No hay una segunda copia de la lógica. Los workflows corren `claude -p` desde
`elDraft-app/`, así que cargan los mismos agentes, skills, comandos, CLAUDE.md
y `settings.json` que una sesión local. Lo único propio del CI es
`prompts/_sistema-ci.md`: qué cambia cuando no hay nadie a quien preguntar.

## Cómo se usa

### Una feature

```
issue  ──factory:feature──▶  planificador comenta el plan     (factory:plan-listo)
                               │  respondes las preguntas en un comentario
                               │  ¿rehacerlo?  ──factory:replanear──▶ (vuelve arriba)
                               ▼
       ──factory:aprobado──▶  backend → shared → android → revisor   (Ubuntu)
                               puertas: las 3 suites + tokens, corridas por el workflow
                               ▼
                             PR "Closes #N"  ──tú revisas y mergeas──▶ Railway despliega
```

La plantilla **"Feature para la factory"** (`New issue`) ya trae las preguntas
que el planificador te haría: quién puede, cuándo deja de poder, qué se
notifica, si toca el esquema. Contestadas ahí, el plan sale sin preguntas
abiertas.

### Paridad iOS

Igual, con `factory:paridad`: el auditor comenta qué le falta a iOS, tú dices
en un comentario qué se porta en esta pasada y pones `factory:aprobado`. El port
corre en **macOS** y compila contra el simulador.

### Mantenimiento

| Cómo | Qué pasa |
|---|---|
| Cada lunes, solo | `cazador-tests` cubre una suite → PR |
| El día 1 de cada mes, solo | `actualizador-deps` sube una dependencia → PR |
| Actions → *Factory (mantenimiento)* → *Run workflow* | el modo que elijas (`tests`, `barrido`, `deps`) |
| Etiqueta `factory:tests`, `factory:barrido` o `factory:deps` en un issue | ese modo, con el detalle que pongas en el issue |

Si ya hay un PR abierto del mismo modo, la corrida programada no abre otro.

### @claude

En cualquier issue o PR, `@claude …` para lo que no es una feature entera:
"por qué falla este test", "aplica lo que dijo el revisor", o seguir un PR en
borrador que la factory dejó bloqueado. Solo responde a quien tiene permisos de
escritura.

### Revisión automática

Cada PR que se abre (o sale de borrador) recibe una revisión con el guion de
`revisor`: comentarios en línea solo para hallazgos confirmados y un resumen.
No aprueba ni bloquea. Para otra pasada: `@claude revisa de nuevo`.

## Qué significa cada resultado

- **PR listo**: el agente terminó con `estado: listo` y las puertas que corrió
  el workflow quedaron en verde.
- **PR en borrador + `factory:bloqueado`**: hubo cambios pero algo no cerró — una
  regla de negocio sin decidir, un control en rojo, o la corrida se quedó sin
  turnos o sin presupuesto. El trabajo no se pierde: sigue en el PR con
  `@claude …`, o responde en el issue y vuelve a poner `factory:aprobado` para
  empezar de cero desde `main`.
- **Comentario sin PR**: no hubo cambios (nada que portar, la dependencia no
  subía, o se detuvo antes de escribir código para preguntarte algo).

Cada corrida guarda como artifact el prompt exacto, la salida del agente, los
logs de las puertas y el `.patch` de los cambios.

## Setup (una vez)

1. **Mergear esta rama a `main`.** Los eventos de issue y los crons solo corren
   con los workflows de la rama por defecto.

2. **Credencial de Claude** — en *Settings → Secrets and variables → Actions*,
   una de las dos:
   - `CLAUDE_CODE_OAUTH_TOKEN`: el que imprime `claude setup-token` en tu Mac.
     Usa tu suscripción de Claude.
   - `ANTHROPIC_API_KEY`: una key de la Console. Se factura por uso.

   Y **`GOOGLE_SERVICES_JSON`**, que ya existe porque lo usa el workflow de la
   tienda: Backend CI, la factory y `@claude` lo restauran para que `:androidApp`
   compile (`scripts/restaurar-google-services.sh`).

3. **App de GitHub de Claude** — `@claude` y la revisión de PRs usan
   `anthropics/claude-code-action`, que necesita la app instalada en el repo:
   desde Claude Code, `/install-github-app`, o en
   <https://github.com/apps/claude>.

4. **`FACTORY_GH_TOKEN`** (recomendado) — un PAT *fine-grained* solo para este
   repo, con *Contents*, *Pull requests* e *Issues* en *Read and write*. Con él
   se empuja la rama y se abre el PR. **Sin él** se usa `GITHUB_TOKEN`, y GitHub
   no corre workflows sobre lo que abre `GITHUB_TOKEN`: el PR llega sin Backend
   CI ni iOS CI (las puertas de la factory sí corren igual) y sin revisión
   automática. En ese caso activa también *Settings → Actions → General →
   "Allow GitHub Actions to create and approve pull requests"*.

5. **Proteger `main`** — *Settings → Branches*: exigir PR para mergear. Es lo
   que garantiza que nada llega a producción sin que lo mires, también si un
   agente se equivoca de rama. Si exiges aprobaciones, ten en cuenta que no
   puedes aprobar un PR abierto con tu propio PAT.

6. **Etiquetas**: `./scripts/factory/crear-etiquetas.sh` (con `gh` autenticado).

## Costos y límites

Cada etapa tiene tope de turnos y de gasto estimado (`run.sh`). Se pueden
cambiar sin tocar código, con *variables* del repo:

| Variable | Por defecto | Etapa |
|---|---|---|
| `FACTORY_MAX_BUDGET_USD_PLAN` | 10 | plan / auditoría |
| `FACTORY_MAX_BUDGET_USD_BUILD` | 60 | implementación de feature o port |
| `FACTORY_MAX_BUDGET_USD_MANTENIMIENTO` | 25 | mantenimiento |

El gasto es una estimación de Claude Code, no tu factura; con
`CLAUDE_CODE_OAUTH_TOKEN` lo que se consume es el uso de tu suscripción.

Minutos de Actions: la implementación de una feature son ~30–90 min de Ubuntu
(los agentes corren Gradle varias veces y luego las puertas otra vez). El port a
iOS corre en **macOS**, que en repos privados cuenta ~10 veces más.

**Para frenar una corrida**: cancélala desde Actions. Quitar la etiqueta no la
detiene; solo evita la siguiente.

## Seguridad

- Solo dispara quien puede poner etiquetas o tiene permisos de escritura.
- Al agente le llega el issue y los comentarios de colaboradores; los de
  cualquier otra persona se cuentan pero no se copian al prompt.
- El agente no tiene el token de GitHub: el checkout no lo guarda en
  `.git/config`, el paso del agente no lo recibe, y el commit, el push y el PR
  los hace `publicar.sh` después. Además se le niegan `git commit/push`, `gh`,
  `curl` y cambiar de rama — reglas que ayudan pero no son una frontera (un
  `sh -c` las esquiva); la frontera es lo anterior.
- `settings.json` del proyecto sigue negando la lectura de keystores y archivos
  de Firebase, igual que en local.

## Archivos

| Archivo | Qué hace |
|---|---|
| `.github/workflows/factory.yml` | etiquetas de issue → plan / construcción |
| `.github/workflows/factory-mantenimiento.yml` | crons y *Run workflow* de mantenimiento |
| `.github/workflows/claude.yml` | `@claude` en issues y PRs |
| `.github/workflows/claude-review.yml` | revisión automática de PRs |
| `.github/actions/factory-setup/` | JDK, Node y Claude Code en el runner |
| `scripts/factory/run.sh` | corre una etapa con `claude -p` y sus permisos |
| `scripts/factory/contexto-issue.sh` | issue + comentarios confiables → markdown |
| `scripts/factory/puertas.sh` | vuelve a correr las suites, sin creerle al agente |
| `scripts/factory/publicar.sh` | comentarios, etiquetas, commit, push y PR |
| `scripts/factory/prompts/` | qué hace cada etapa en modo CI |
| `scripts/factory/schemas/` | la forma de lo que devuelve el agente |

Para probar una etapa en local sin GitHub (desde `elDraft-app/`, en una rama
limpia, con tu sesión de Claude Code):

```bash
FACTORY_MODO=tests FACTORY_OUT=/tmp/factory ./scripts/factory/run.sh mantenimiento
jq .structured_output /tmp/factory/resultado.json
./scripts/factory/puertas.sh android
```
