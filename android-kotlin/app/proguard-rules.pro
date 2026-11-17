# Reglas de R8/ProGuard para el build release (isMinifyEnabled está desactivado por defecto).
# kotlinx.serialization, WorkManager y Glance publican sus propias reglas de consumidor.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.mixagames666.trackerrntemp.data.** {
    *** Companion;
}
