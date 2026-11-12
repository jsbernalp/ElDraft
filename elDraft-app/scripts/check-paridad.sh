#!/usr/bin/env bash
#
# Compara Android con iOS y lista lo que iOS todavía no espeja.
#
# iOS va siempre detrás de Android (ver la skill `paridad-ios`). Este script
# convierte "¿quedó algo sin portar?" en una pregunta con respuesta
# determinista, para que la deuda de iOS no dependa de la memoria de nadie ni
# del juicio de un agente. Revisa:
#
#   vm        ViewModels de androidApp sin espejo Swift (archivo con el mismo
#             nombre o un comentario `///` que cite `XViewModel.kt`).
#   string    Claves de strings.xml que faltan en Localizable.xcstrings, plurals
#             de Android que en iOS no tienen variación plural, y placeholders
#             que no cuadran en número (un %1$s en Android que en iOS no existe
#             revienta o muestra basura en runtime, no al compilar).
#   accessor  Casos de uso / repositorios de dominio que usa Android y no tienen
#             accessor en IosKoin (KoinAccessors.kt). Sin él, Swift no puede
#             obtenerlos: los genéricos reified no se exportan a Objective-C.
#   throws    `suspend fun` de domain/repository y domain/usecase sin
#             `@Throws(Exception::class)`. Sin la anotación, una excepción real
#             mata la app de iOS en vez de llegar a Swift como `throw`.
#   ruta      `Screen` de ElDraftApp.kt sin `case` en AppRoute.swift.
#
# Y como advertencias (no hacen fallar salvo con --estricto):
#
#   literal-ios  Claves que solo existen en iOS y se usan en Swift: casi siempre
#                un `Text("…")` quemado que Xcode extrajo solo. Android lo tiene
#                en strings.xml; iOS debería usar la misma clave.
#   huerfana-ios Claves solo de iOS que ya no aparecen en ningún .swift (restos
#                de código borrado). Limpieza, no paridad.
#
# Excepciones legítimas: scripts/paridad-allowlist.txt, una por línea, con el
# formato `categoria:elemento  # razón`. La razón es obligatoria: una excepción
# sin porqué es una deuda escondida.
#
# Uso:
#   ./scripts/check-paridad.sh             # informe legible
#   ./scripts/check-paridad.sh --resumen   # solo conteos por categoría
#   ./scripts/check-paridad.sh --json      # salida para agentes
#   ./scripts/check-paridad.sh --estricto  # las advertencias también fallan
#
# Salida: 0 sin huecos; 1 si hay huecos (o advertencias con --estricto);
#         2 si el propio script no pudo leer algún archivo.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

exec python3 -I - "$ROOT" "$@" <<'PY'
import json
import os
import re
import sys

root = sys.argv[1]
args = sys.argv[2:]
MODE = "texto"
STRICT = False
for a in args:
    if a == "--json":
        MODE = "json"
    elif a == "--resumen":
        MODE = "resumen"
    elif a == "--estricto":
        STRICT = True
    elif a in ("-h", "--help"):
        print("Uso: check-paridad.sh [--json|--resumen] [--estricto]")
        sys.exit(0)
    else:
        print(f"Argumento desconocido: {a}", file=sys.stderr)
        sys.exit(2)

P = lambda *p: os.path.join(root, *p)
ANDROID = P("androidApp/src/main/kotlin/com/eldraft/android")
STRINGS_XML = P("androidApp/src/main/res/values/strings.xml")
SHARED_DOMAIN = P("shared/src/commonMain/kotlin/com/eldraft/domain")
KOIN_ACCESSORS = P("shared/src/iosMain/kotlin/com/eldraft/core/di/KoinAccessors.kt")
IOS = P("iosApp/iosApp")
XCSTRINGS = P("iosApp/iosApp/Resources/Localizable.xcstrings")
APP_ROUTE = P("iosApp/iosApp/Navigation/AppRoute.swift")
ELDRAFT_APP = P("androidApp/src/main/kotlin/com/eldraft/android/ui/ElDraftApp.kt")
ALLOWLIST = P("scripts/paridad-allowlist.txt")


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError as e:
        print(f"check-paridad: no pude leer {os.path.relpath(path, root)}: {e}", file=sys.stderr)
        sys.exit(2)


def walk(base, ext):
    out = []
    for d, dirs, files in os.walk(base):
        dirs[:] = [x for x in dirs if x not in ("build", ".gradle")]
        out += [os.path.join(d, f) for f in files if f.endswith(ext)]
    return sorted(out)


rel = lambda p: os.path.relpath(p, root)

# ── allowlist ───────────────────────────────────────────────────────────────
allow = {}
if os.path.exists(ALLOWLIST):
    for n, line in enumerate(read(ALLOWLIST).splitlines(), 1):
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        entry, _, reason = s.partition("#")
        entry = entry.strip()
        if ":" not in entry or not reason.strip():
            print(f"check-paridad: allowlist línea {n} inválida (falta `categoria:elemento  # razón`): {s}",
                  file=sys.stderr)
            sys.exit(2)
        allow[entry] = reason.strip()
used_allow = set()

findings = []  # dicts: categoria, nivel, elemento, detalle, donde


def add(cat, level, item, detail, where=""):
    key = f"{cat}:{item}"
    if key in allow:
        used_allow.add(key)
        return
    findings.append({"categoria": cat, "nivel": level, "elemento": item,
                     "detalle": detail, "donde": where})


swift_files = walk(IOS, ".swift")
swift_src = {p: read(p) for p in swift_files}
swift_all = "\n".join(swift_src.values())
swift_doc = "\n".join(l for l in swift_all.splitlines() if l.lstrip().startswith("//"))
swift_names = {os.path.basename(p)[:-6] for p in swift_files}

# ── vm ──────────────────────────────────────────────────────────────────────
for p in walk(ANDROID, "ViewModel.kt"):
    name = os.path.basename(p)[:-3]
    if name in swift_names or f"{name}.kt" in swift_doc:
        continue
    add("vm", "error", name, "sin espejo en iosApp (ni XViewModel.swift ni `/// Espejo de " + name + ".kt`)",
        rel(p))

# ── string ──────────────────────────────────────────────────────────────────
xml = read(STRINGS_XML)
android_strings = {}
for m in re.finditer(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', xml, re.S):
    android_strings[m.group(1)] = ("string", m.group(2))
for m in re.finditer(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', xml, re.S):
    items = re.findall(r'<item\s+quantity="other"[^>]*>(.*?)</item>', m.group(2), re.S)
    android_strings[m.group(1)] = ("plurals", items[0] if items else "")
for m in re.finditer(r'<string\s+name="([^"]+)"[^>]*/>', xml):
    android_strings.setdefault(m.group(1), ("string", ""))

try:
    xc = json.loads(read(XCSTRINGS))
except json.JSONDecodeError as e:
    print(f"check-paridad: Localizable.xcstrings no es JSON válido: {e}", file=sys.stderr)
    sys.exit(2)
ios_strings = xc.get("strings", {})
src_lang = xc.get("sourceLanguage", "es")

ANDROID_PH = re.compile(r"%(?:\d+\$)?[sdf]")
IOS_PH = re.compile(r"%(?:\d+\$)?(?:@|l?l?[dfu]|ld)")


def ios_value(entry):
    loc = entry.get("localizations", {}).get(src_lang, {})
    if "stringUnit" in loc:
        return loc["stringUnit"].get("value", ""), False
    plural = loc.get("variations", {}).get("plural")
    if plural:
        other = plural.get("other", {}).get("stringUnit", {}).get("value", "")
        return other, True
    return None, False


for key, (kind, aval) in sorted(android_strings.items()):
    if key not in ios_strings:
        add("string", "error", key, f"{kind} de strings.xml sin clave en Localizable.xcstrings", rel(STRINGS_XML))
        continue
    ival, is_plural = ios_value(ios_strings[key])
    if kind == "plurals" and not is_plural:
        add("string", "error", key, "en Android es <plurals>; en iOS no tiene variación plural", rel(XCSTRINGS))
    if ival is None:
        # Sin localización explícita: Xcode usa la clave como texto. Solo es
        # válido si el valor de Android no tiene placeholders y es la clave.
        if ANDROID_PH.search(aval or ""):
            add("string", "error", key, "clave sin valor en iOS y el texto de Android lleva argumentos",
                rel(XCSTRINGS))
        else:
            add("string", "error", key, "clave sin valor (stringUnit) en iOS: se mostraría la clave cruda",
                rel(XCSTRINGS))
        continue
    na, ni = len(ANDROID_PH.findall(aval or "")), len(IOS_PH.findall(ival))
    if na != ni:
        add("string", "error", key, f"placeholders distintos: Android {na}, iOS {ni}", rel(XCSTRINGS))

KEY_RE = re.compile(r"^[a-z][a-z0-9]*(_[a-z0-9]+)+$")
# Glyphs sin idioma ("+", "–", "⚽") y claves que son solo un placeholder
# ("%@", que Xcode extrae de `Text("\(n)")`): CLAUDE.md los excluye de la
# migración a strings, así que tampoco cuentan aquí.
NO_LANG = re.compile(r"^(?:%(?:\d+\$)?[@dlfu]+|[^A-Za-zÁÉÍÓÚÑáéíóúñ]*)$")
for key, entry in sorted(ios_strings.items()):
    if key in android_strings or NO_LANG.match(key):
        continue
    if entry.get("extractionState") == "stale":
        add("huerfana-ios", "aviso", key, "marcada stale por Xcode", rel(XCSTRINGS))
        continue
    literal = '"' + key.replace("\\", "\\\\") + '"'
    used_in = [rel(p) for p, s in swift_src.items() if literal in s]
    if not used_in:
        add("huerfana-ios", "aviso", key, "solo en iOS y no aparece en ningún .swift", rel(XCSTRINGS))
    elif KEY_RE.match(key):
        add("literal-ios", "aviso", key, "clave con forma de Android que Android no tiene", ", ".join(used_in))
    else:
        add("literal-ios", "aviso", key, "texto quemado en Swift (Xcode lo extrajo solo)", ", ".join(used_in))

# ── accessor ────────────────────────────────────────────────────────────────
android_src = "\n".join(read(p) for p in walk(ANDROID, ".kt"))
# Solo tipos que se piden a Koin: los inputs/DTOs del paquete usecase (p. ej.
# SaveProfileInput) se construyen a mano y no necesitan accessor.
needed = {t for t in re.findall(r"import com\.eldraft\.domain\.(?:usecase\.\w+|repository)\.(\w+)", android_src)
          if t.endswith(("UseCase", "Repository"))}
koin = read(KOIN_ACCESSORS)
present = set(re.findall(r"val\s+\w+\s*:\s*(\w+)\s+get\(\)", koin))
for t in sorted(needed - present):
    add("accessor", "error", t, "Android lo usa y IosKoin no tiene accessor", rel(KOIN_ACCESSORS))

# ── throws ──────────────────────────────────────────────────────────────────
for sub in ("repository", "usecase"):
    for p in walk(os.path.join(SHARED_DOMAIN, sub), ".kt"):
        lines = read(p).splitlines()
        owner = None
        for i, line in enumerate(lines):
            m = re.match(r"\s*(?:(?:abstract|open|data|fun)\s+)*(?:class|interface|object)\s+(\w+)", line)
            if m:
                owner = m.group(1)
            fm = re.match(r"\s*(?:override\s+|public\s+|open\s+)*suspend\s+(?:operator\s+)?fun\s+(\w+)", line)
            if not fm:
                continue
            j, annotated = i - 1, False
            while j >= 0 and lines[j].strip().startswith("@"):
                if "@Throws" in lines[j]:
                    annotated = True
                j -= 1
            if "@Throws" in line:
                annotated = True
            if not annotated:
                item = f"{owner}.{fm.group(1)}" if owner else fm.group(1)
                add("throws", "error", item, "suspend fun sin @Throws(Exception::class)", f"{rel(p)}:{i + 1}")

# ── ruta ────────────────────────────────────────────────────────────────────
app = read(ELDRAFT_APP)
sm = re.search(r"sealed class Screen\b.*?\n}\n", app, re.S)
screens = re.findall(r"^\s*object\s+(\w+)\s*:\s*Screen\(", sm.group(0) if sm else "", re.M)
route_src = read(APP_ROUTE)
cases = set(re.findall(r"^\s*case\s+(\w+)", route_src, re.M))
for s in screens:
    camel = s[0].lower() + s[1:]
    # QRGenerator → qrGenerator: baja el prefijo en mayúsculas completo.
    m = re.match(r"^([A-Z]+)([A-Z][a-z].*)$", s)
    if m:
        camel = m.group(1).lower() + m.group(2)
    if camel not in cases:
        add("ruta", "error", s, f"Screen.{s} sin `case {camel}` en AppRoute", rel(APP_ROUTE))

# ── allowlist obsoleta ──────────────────────────────────────────────────────
stale_allow = sorted(set(allow) - used_allow)

# ── salida ──────────────────────────────────────────────────────────────────
CATS = ["vm", "string", "accessor", "throws", "ruta", "literal-ios", "huerfana-ios"]
errors = [f for f in findings if f["nivel"] == "error"]
warnings = [f for f in findings if f["nivel"] == "aviso"]
failed = bool(errors) or (STRICT and bool(warnings))

if MODE == "json":
    print(json.dumps({
        "ok": not failed,
        "errores": len(errors),
        "avisos": len(warnings),
        "por_categoria": {c: sum(1 for f in findings if f["categoria"] == c) for c in CATS},
        "hallazgos": findings,
        "excepciones_aplicadas": sorted(used_allow),
        "excepciones_sin_uso": stale_allow,
    }, ensure_ascii=False, indent=2))
else:
    print("Paridad Android → iOS")
    print("─" * 60)
    for c in CATS:
        items = [f for f in findings if f["categoria"] == c]
        mark = "✓" if not items else ("✗" if items[0]["nivel"] == "error" else "!")
        print(f"{mark} {c:<13} {len(items)}")
        if MODE == "texto":
            for f in items:
                where = f"  ({f['donde']})" if f["donde"] else ""
                print(f"    - {f['elemento']}: {f['detalle']}{where}")
    if used_allow:
        print(f"\n{len(used_allow)} excepción(es) aplicada(s) desde scripts/paridad-allowlist.txt")
    if stale_allow:
        print("\nExcepciones que ya no aplican (bórralas de la allowlist):")
        for k in stale_allow:
            print(f"    - {k}")
    print("─" * 60)
    print(f"{len(errors)} hueco(s), {len(warnings)} aviso(s)" + (" — modo estricto" if STRICT else ""))

sys.exit(1 if failed else 0)
PY
