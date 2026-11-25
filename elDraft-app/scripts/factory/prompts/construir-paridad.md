# Factory de paridad iOS — port (issue #{{ISSUE}})

El humano aprobó el alcance (etiqueta `factory:aprobado`). El informe del
auditor es el **último** comentario "Factory (plan)" de la conversación; **lo
que se porta** es lo que el humano pidió en sus comentarios posteriores. Si no
pidió nada concreto, porta solo la prioridad 1 y dilo en el informe. La
allowlist (`scripts/paridad-allowlist.txt`) solo se toca con lo que el humano
aprobó explícitamente.

Ejecuta los **pasos 3 y 4** de `.claude/commands/paridad.md`: portar con
`portador-ios` y verificar tú. Corres en un runner macOS con Xcode:

- XcodeGen se instala con `brew install xcodegen` si falta.
- `iosApp/Config/Debug.xcconfig` y `Release.xcconfig` ya están creados desde sus
  `.example` (XcodeGen exige los dos). No hacen falta secretos para compilar en
  Debug; Release no se compila aquí.
- No hay quien pruebe en el simulador: lo que el portador deje "pendiente de
  verificar en simulador" va en `informe`, en una sección propia.
- Dos intentos fallidos sobre lo mismo: `estado: "bloqueado"` con el error.

Paso 5: no commitees. El mensaje va en `commit_asunto` y `commit_cuerpo`; si el
port introdujo una diferencia de plataforma, va explicada en el cuerpo.

`estado: "listo"` cuando el build de iOS compila, los huecos aprobados ya no
salen en `./scripts/check-paridad.sh` y, si hubo `.swift` nuevos, el
`project.pbxproj` regenerado está entre los cambios.
