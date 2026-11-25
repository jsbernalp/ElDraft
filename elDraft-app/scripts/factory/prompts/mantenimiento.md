# Factory de mantenimiento — modo `{{MODO}}`

Ejecuta `.claude/commands/mantenimiento.md` en modo `{{MODO}}`, con detalle
`{{DETALLE}}` (vacío: el subagente elige según su prioridad). {{ORIGEN}}

Si hay issue, el detalle también puede venir en su descripción.

Reglas del modo CI:

- El árbol está limpio: es un checkout recién hecho de `main`, no hay trabajo a
  medias que revisar.
- No crees rama ni commitees: el workflow abre el PR. El mensaje va en
  `commit_asunto` y `commit_cuerpo`.
- Aquí sí hay Android SDK y JDK 17: las tres suites corren
  (`-PbackendOnly :backend:test`, `:shared:testDebugUnitTest`,
  `:androidApp:testDebugUnitTest`). Lo que el comando dice sobre rutinas en la
  nube sin SDK no aplica a esta corrida.
- Si no hubo nada que hacer —o la dependencia no se pudo subir y el subagente la
  revirtió—, `estado: "sin_cambios"`, el árbol como estaba y el porqué en
  `informe`.
- `salud` no se corre en CI; si te piden ese modo, `estado: "sin_cambios"` y
  dilo.
