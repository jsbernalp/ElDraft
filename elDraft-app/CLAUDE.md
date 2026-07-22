# Convenciones de elDraft (Android)

## Design Tokens — OBLIGATORIO en la UI de Compose

Toda la UI (`androidApp/.../ui/screens`, `ui/map`, `ui/components`) debe usar los
Design Tokens definidos en `ui/theme/`. **Nunca** quemes valores literales de
diseño en estos archivos.

Accede a los tokens vía el accessor `ElDraftTheme`:

| Aspecto | Usa | NO uses |
|---|---|---|
| Color de marca/semántico | `ElDraftTheme.colors.*` o `MaterialTheme.colorScheme.*` | `Color(0xFF…)` |
| Opacidad | `ElDraftTheme.alpha.*` (textSecondary, divider, container…) | `.copy(alpha = 0.6f)` |
| Radio de esquina | `ElDraftTheme.shape.*` (pill, sm, md, lg, field) | `RoundedCornerShape(12.dp)` |
| Spacing / padding / gaps | `ElDraftTheme.spacing.*` (xs…xxl) | `.padding(16.dp)`, `spacedBy(8.dp)` |
| Tamaño de ícono / avatar / stroke | `ElDraftTheme.size.*` (iconSm/Md/Lg, avatar, stroke) | `.size(18.dp)`, `strokeWidth = 2.dp` |
| Elevación | `ElDraftTheme.elevation.*` (card, cardRaised, overlay) | `defaultElevation = 2.dp` |
| Tipografía | `MaterialTheme.typography.*` o `ElDraftTextStyles.*` | `fontSize = 14.sp` |

La **definición** de los tokens vive en `ui/theme/` (Color, Spacing, Alpha,
Shapes, Sizes, Elevation, Typography). Solo ahí se escriben los literales.

### Excepciones (one-off legítimos)

Algunos valores son genuinamente únicos y no corresponden a un token: dimensiones
de dibujo en `Canvas` (pines del mapa), tamaños fijos de layout (logo del splash,
QR), o un alpha muy específico de un componente. En esos casos:

- Para **spacing/size** one-off: déjalo literal con un comentario que explique por
  qué (p. ej. `// diámetro del disco del pin`).
- Para **color/alpha/shape** one-off (lo que vigila el linter): añade el marcador
  `// design-tokens-ignore: <razón>` al final de la línea.

### Enforcement

- `scripts/check-design-tokens.sh` revisa la UI y falla si hay color/opacidad/radio
  quemados. Córrelo cuando quieras: `./scripts/check-design-tokens.sh`.
- Un hook pre-commit lo ejecuta sobre los `.kt` de UI staged. Instálalo una vez
  tras clonar: `./scripts/install-hooks.sh`.

### Al añadir un token nuevo

Si necesitas un valor que se repite y no existe como token, **añádelo a la data
class correspondiente en `ui/theme/`** (no lo quemes). Mantén la escala
consistente; si un valor cae entre dos pasos, redondea al más cercano salvo que la
diferencia sea visualmente significativa.

## Strings — OBLIGATORIO usar recursos en la UI de Compose

Todo texto **visible al usuario** en la capa UI (`ui/screens`, `ui/map`,
`ui/components`) debe venir de `res/values/strings.xml`, nunca quemado en el `.kt`.

| Caso | Usa | NO uses |
|---|---|---|
| Texto en un `@Composable` | `stringResource(R.string.x)` | `Text("Hola")` |
| Con argumentos | `stringResource(R.string.x, arg)` | `Text("Hola $nombre")` |
| Cantidades (1 cupo / N cupos) | `pluralStringResource(R.plurals.x, n, n)` | `if (n==1) "cupo" else "cupos"` |
| Fuera de un `@Composable` (callback, `LaunchedEffect`, `Toast`) | captura `val s = stringResource(...)` arriba, o `context.getString(R.string.x)` | literal inline |

Import: `com.eldraft.android.R` (el namespace es `com.eldraft.android`).

### Qué NO se migra

- **Comentarios** y mensajes de `Log.*` (no son UI).
- **Valores de dominio** que viajan al backend, aunque se muestren: `FORMATS`
  (`"Fútbol 5"`…), `POSITIONS`, `BUILDS`, `FEET`, `ambiente` (`"Recocha"`/
  `"Competitivo"`), `CANCELLATION_REASONS`. Son datos, no etiquetas de presentación.
- **Símbolos/glyphs** sin idioma: `"+"`, `"–"`, `"✕"`, emojis de `EmptyState`.
- **Errores de ViewModels / clients** (`e.userMessage("…")` en `ui/*ViewModel.kt`,
  `EmailAuthClient`, `GoogleAuthClient`): son estado y `userMessage` vive en
  `shared` (KMP, sin acceso a recursos Android). Se dejan en español por ahora; si
  algún día se traducen, hay que exponer una clave/`@StringRes` desde el VM y
  resolver en la pantalla.

### Nombres de claves

Prefijo por pantalla/feature en `snake_case`: `login_*`, `profile_*`, `create_*`,
`applicants_*`, `attendance_*`, `qr_*`, etc. Comunes reutilizables sin prefijo de
pantalla: `action_*` (botones), diálogos compartidos (`logout_dialog_*`). Reutiliza
claves existentes antes de crear duplicados (p. ej. `cromo_metric_skill` se usa en
Cromo, rating y postulantes).

### Verificación

- El texto visible sale de `strings.xml`; corre la app y revisa que nada muestre
  una clave cruda ni un placeholder sin resolver.
- Barrido de literales con tilde/ñ que se hayan colado (deben quedar solo
  ViewModels y constantes de dominio):
  `grep -rnE '"[^"]*[áéíóúñ¿¡]' androidApp/.../ui --include=*.kt | grep -v ViewModel`
