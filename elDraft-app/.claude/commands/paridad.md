---
description: Factory de paridad iOS de elDraft — audita qué le falta a iOS frente a Android, pide aprobación del alcance, lo porta, vuelve a correr el control y propone el commit con aprobación.
argument-hint: "[feature, pantalla o rango de commits — vacío = todo el repo]"
---

Alcance pedido: $ARGUMENTS

Orquesta la factory de paridad iOS. Tú coordinas; el trabajo lo hacen los
subagentes `auditor-paridad` y `portador-ios`. iOS va siempre detrás de
Android por decisión (skill `paridad-ios`): esta factory cierra esa distancia
sin inventar comportamiento.

Hay dos puntos donde **te detienes y esperas un sí explícito** del humano: el
alcance (paso 2) y el commit (paso 5). No los saltes aunque el cambio parezca
trivial.

## 1. Auditar

Lanza `auditor-paridad` con el alcance pedido. Si `$ARGUMENTS` está vacío,
el alcance es el repo completo; si nombra una feature o commits, pásaselos
para que compare también el comportamiento, no solo lo mecánico.

Si el informe dice que no falta nada (check-paridad en verde y sin
hallazgos de comportamiento), dilo en una línea y termina.

## 2. ✋ Aprobar el alcance

Muestra al humano el informe priorizado **tal cual** y pregunta qué se porta
en esta pasada. Recomienda empezar por la prioridad 1 (lo que crashea en
runtime). Si el auditor propuso diferencias legítimas para la allowlist,
pregunta también por ellas: la allowlist la decide el humano.

Sin respuesta, no sigas.

## 3. Portar

Lanza `portador-ios` con **solo** los huecos aprobados, y el informe del
auditor como contexto. Si el alcance aprobado toca verticales independientes,
puedes portarlas una tras otra con el mismo agente; no lances portadores en
paralelo sobre el mismo árbol, porque los dos tocarían `project.pbxproj`,
`Localizable.xcstrings` y `KoinAccessors.kt` y se pisarían.

## 4. Verificar tú

No te quedes con el informe del portador; repite el control:

- `./scripts/check-paridad.sh` — los huecos aprobados ya no aparecen.
- `git status` — si hay `.swift` nuevos, el `project.pbxproj` regenerado
  tiene que estar entre los cambios.
- Si el portador dejó algo "pendiente de verificar en simulador", díselo al
  humano explícitamente.

Si algo falla, devuélveselo al portador con el error concreto. Dos intentos
fallidos sobre lo mismo: para y reporta.

## 5. ✋ Commit

Carga la skill `commit` y síguela: redacta el mensaje, muéstralo entero y
espera aprobación. Si el port introdujo una diferencia de plataforma, va
explicada en el cuerpo. Nunca `git push`.

## Al terminar

Una línea con lo que quedó portado, lo que sigue pendiente (otras
prioridades, verificación en simulador) y la salida resumida de
`./scripts/check-paridad.sh --resumen`.
