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
