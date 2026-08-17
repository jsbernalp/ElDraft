# Imagen de producción del backend de elDraft.
#
# Multi-stage: la etapa `build` trae el JDK completo y todo el árbol de fuentes
# (~400 MB de imagen), la etapa final solo lleva el JRE y el artefacto ya
# compilado. Nada del código fuente, del wrapper de Gradle ni de la caché de
# dependencias llega a la imagen que corre en el servidor.
#
# Contexto de build: este directorio (elDraft-app/).
#   docker build -t eldraft-backend .

# ---------------------------------------------------------------------------
# Etapa 1 — compilar
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /src

# El JDK viene fijado por la imagen base, no por gradle.properties: ese archivo
# está versionado y no puede apuntar al JDK de ninguna máquina en concreto.
# El toolchain del módulo (jvmToolchain(17)) garantiza el bytecode.

# Primero solo lo necesario para resolver dependencias. Si cambia el código pero
# no las dependencias, Docker reutiliza esta capa y el build es mucho más rápido.
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY backend/build.gradle.kts backend/

# `backendOnly` excluye :shared y :androidApp del settings. Sin esto Gradle
# intentaría configurar los plugins de Android, que exigen un SDK que esta
# imagen no tiene (ni debe tener).
    # `sed`: en un checkout de Windows (core.autocrlf=true) gradlew llega con CRLF y
    # el shebang queda como "/bin/sh\r", que Linux no resuelve — el error es un
    # críptico "./gradlew: not found". El .gitattributes de la raíz lo evita en
    # checkouts nuevos; esto protege builds desde un working copy ya existente.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && \
    ./gradlew -PbackendOnly --no-daemon :backend:dependencies --configuration runtimeClasspath > /dev/null

# Ahora sí el código. Cambia en cada commit, así que va después.
COPY backend/src/ backend/src/

RUN ./gradlew -PbackendOnly --no-daemon :backend:installDist

# ---------------------------------------------------------------------------
# Etapa 2 — ejecutar
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# Usuario sin privilegios: si alguien logra ejecución remota, no la consigue como
# root. El proceso no necesita escribir en ningún sitio del filesystem.
RUN useradd --system --create-home --uid 10001 eldraft
USER eldraft
WORKDIR /app

COPY --from=build --chown=eldraft:eldraft /src/backend/build/install/backend/ ./

# El puerto real lo decide el proveedor vía $PORT (application.conf lo lee).
# Esto es solo el valor por defecto y documentación para quien lea la imagen.
ENV PORT=8080
EXPOSE 8080

# Zona horaria del contenedor. El backend fija además la de la JVM y la de la
# sesión de Postgres desde `app.timezone` (APP_TIMEZONE); esto alinea los logs y
# cualquier herramienta del sistema con esa misma hora.
ENV TZ=America/Bogota

# -XX:MaxRAMPercentage: sin esto la JVM asume que puede usar la RAM del host y no
# la del contenedor, y el OOM killer mata el proceso sin dejar rastro en los logs.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

ENTRYPOINT ["./bin/backend"]
