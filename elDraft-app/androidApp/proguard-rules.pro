# Reglas de R8 para el build de release.
#
# Contexto: `isMinifyEnabled = true` está activo desde antes de que existiera este
# archivo, y `build.gradle.kts` ya lo referenciaba aunque no existía. La mayoría de
# las librerías del proyecto (Firebase, Maps, Places, ML Kit, Coil, Koin) publican
# sus propias consumer rules dentro del .aar, así que R8 las respeta sin ayuda.
#
# Lo que SÍ necesita reglas explícitas es el código propio que se resuelve por
# nombre en tiempo de ejecución: los modelos serializados de `shared/`. R8 no ve
# quién los usa y los renombra, y el fallo aparece recién en el dispositivo como un
# SerializationException, no en el build.
#
# Estas reglas reducen el riesgo, no lo eliminan: la verificación real es correr el
# .aab firmado en un dispositivo (Fase 4 del plan de despliegue).

# ---------- Atributos que kotlinx.serialization y Ktor necesitan ----------
# Sin Signature, los tipos genéricos (List<Convocatory>, Response<T>) se pierden y
# la deserialización falla en runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ---------- kotlinx.serialization ----------
# El plugin genera un `$$serializer` por cada @Serializable y lo busca por nombre.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Modelos de dominio compartidos: viajan por red en ambos sentidos.
-keep,includedescriptorclasses class com.eldraft.data.models.** { *; }
-keep,includedescriptorclasses class com.eldraft.data.remote.** { *; }

# ---------- Ktor client ----------
# El motor y los plugins se resuelven por ServiceLoader / reflexión parcial.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# ---------- Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ---------- Ruido esperado ----------
# Referencias a APIs de JVM de escritorio que no existen en Android; las arrastran
# dependencias multiplataforma y nunca se ejecutan aquí.
-dontwarn java.lang.instrument.**
-dontwarn javax.naming.**
