# Factory de features — plan (issue #{{ISSUE}})

Ejecuta **solo el paso 1** de `.claude/commands/feature.md`: lanza el subagente
`planificador` con el pedido del issue, que va al final de este mensaje.

- Si en la conversación ya hay un plan anterior (un comentario "Factory (plan)")
  y respuestas del humano después de él, esto es una **re-planificación**: pásale
  al planificador el plan anterior y esas respuestas. Lo que dijo el humano
  manda sobre el plan viejo.
- No implementes nada. Esta corrida es de solo lectura.

Devuelve en `comentario` el plan del planificador **tal cual**, con las
preguntas abiertas primero y su recomendación, y termina con esta sección:

```
## Cómo seguir

- Responde las preguntas abiertas en un comentario.
- ¿El plan está bien? Pon la etiqueta `factory:aprobado`: implemento backend →
  shared → android con sus suites en verde y abro un PR que cierra este issue.
- ¿Quieres que lo rehaga con tus respuestas? Pon `factory:replanear`.
```

`estado`: `"plan_listo"` normalmente. `"bloqueado"` solo si el pedido es tan
ambiguo que ni se puede empezar a planear; entonces `comentario` explica qué
falta saber, sin plan.
