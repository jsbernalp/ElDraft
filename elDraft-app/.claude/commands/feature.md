---
description: Factory de features de elDraft — planifica, implementa backend → shared → android con un control por capa, revisa y propone el commit, con aprobación humana del plan y del commit.
argument-hint: "<pedido en lenguaje de producto>"
---

Pedido: $ARGUMENTS

Orquesta la factory de features de elDraft. Tú coordinas; el trabajo lo hacen
los subagentes. Las etapas van **en secuencia**, porque cada capa consume lo que
dejó la anterior: paralelizarlas solo produce conflictos y controles que no
significan nada.

## 1. Plan — `planificador`

Lanza el subagente `planificador` con el pedido. Muéstrame su plan completo,
incluidas las preguntas abiertas.

**✋ Para aquí.** No sigas hasta que yo apruebe el plan y responda las
preguntas. Si cambio algo, vuelve a lanzar el planificador con mis respuestas
en lugar de parchear el plan tú.

## 2. Implementación por capa

Para cada fase del plan aprobado, en este orden, lanza su subagente con el plan
y el resumen de lo que devolvió la etapa anterior:

| Etapa | Subagente | Control que tiene que quedar en verde |
|---|---|---|
| Backend | `impl-backend` | `./gradlew -PbackendOnly :backend:test` |
| Shared | `impl-shared` | `./gradlew :shared:testDebugUnitTest` |
| Android | `impl-android` | `./gradlew :androidApp:testDebugUnitTest` + `./scripts/check-design-tokens.sh` |

Sáltate las fases que el plan no tenga. Entre etapas:

- **Comprueba el control tú mismo** si el subagente no muestra la salida. Un
  "ya pasa" sin evidencia no cuenta.
- Si un subagente devuelve un **bloqueo** (falta algo de la capa anterior, o una
  regla de negocio sin decidir), no lo resuelvas por tu cuenta: si es de la capa
  anterior, vuelve a esa etapa; si es una decisión, pregúntamela.
- Si el control no pasa tras un reintento razonable, para y muéstrame el fallo.

## 3. Revisión — `revisor`

Lanza el subagente `revisor` sobre el diff completo. Si hay hallazgos
confirmados, devuélvelos al subagente de la capa que corresponde, vuelve a
correr su control y pide una segunda revisión. Los dudosos me los muestras.

## 4. Commit

Carga la skill `commit` y síguela: mensaje completo, **✋ espera mi sí
explícito**, y nunca `git push`.

## 5. Cierre

Resúmeme en pocas líneas: qué se hizo por capa, qué tests se añadieron, y **qué
queda para iOS** según lo que devolvió `impl-android`. Termina sugiriendo
`/paridad` para portarlo.
