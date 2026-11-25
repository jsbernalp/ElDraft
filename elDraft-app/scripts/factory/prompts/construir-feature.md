# Factory de features — implementación (issue #{{ISSUE}})

El humano aprobó el plan (etiqueta `factory:aprobado`). El plan aprobado es el
**último** comentario "Factory (plan)" de la conversación del issue; lo que el
humano escribió **después** de ese comentario manda sobre él: responde sus
preguntas abiertas o lo corrige.

Ejecuta los **pasos 2 y 3** de `.claude/commands/feature.md` tal cual
—implementación por capa con su control en verde, y revisión con `revisor`—,
con las reglas del modo CI:

- **Preguntas sin responder.** Si una pregunta abierta del plan quedó sin
  respuesta, puedes seguir la recomendación del planificador solo si es una
  decisión de implementación. Si es una regla de negocio —quién puede, cuándo
  deja de poder, qué se notifica, si toca el esquema—, termina con
  `estado: "bloqueado"` antes de escribir código.
- **Bloqueos y controles que no pasan.** Lo que el comando manda mostrarme o
  preguntarme, aquí termina en `estado: "bloqueado"`, con el detalle en
  `pregunta` y lo que sí quedó hecho en `informe`. El workflow abre igual un PR
  en borrador para no perder el trabajo.
- **Pasos 4 y 5.** No commitees: el mensaje va en `commit_asunto` y
  `commit_cuerpo`. El cierre del paso 5 va en `informe`, y tiene que incluir una
  sección **Pendiente para iOS** con lo que devolvió `impl-android`: de ahí sale
  el issue de `factory:paridad` siguiente.

`estado: "listo"` solo cuando los controles de las tres etapas quedaron en verde
(con su salida vista, no supuesta) y el revisor no dejó hallazgos confirmados
sin resolver.
