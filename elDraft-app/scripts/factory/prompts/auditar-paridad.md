# Factory de paridad iOS — auditoría (issue #{{ISSUE}})

Ejecuta **solo el paso 1** de `.claude/commands/paridad.md`: lanza
`auditor-paridad`. El alcance es lo que pida el issue (una feature, una
pantalla, un rango de commits, o el issue de una feature ya mergeada); si no
pide nada concreto, el repo completo.

Si en la conversación ya hay una auditoría anterior y comentarios del humano
después, tenlos en cuenta: esto es una re-auditoría.

Devuelve en `comentario` el informe del auditor **tal cual** y termina con:

```
## Cómo seguir

- Dime en un comentario qué porto en esta pasada (recomendado: la prioridad 1,
  lo que crashea en runtime) y cuáles de las diferencias propuestas van a la
  allowlist.
- Luego pon `factory:aprobado`: porto solo lo que digas, en un runner macOS,
  compilo contra el simulador y abro un PR.
```

`estado`: `"sin_trabajo"` si el auditor no encontró nada que portar (y entonces
el comentario es una línea con la salida del script); `"plan_listo"` si sí.
