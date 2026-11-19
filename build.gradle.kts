// Proyecto Android nativo (Kotlin + Jetpack Compose) de Mi Dinero / Tracker de gastos.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// Windows con rutas largas: si el repositorio vive en una ruta profunda (p. ej. un worktree de git) y
// LongPathsEnabled=0, AGP/aapt2 fallan por superar los 260 caracteres. Definiendo
// ANDROID_KOTLIN_BUILD_ROOT (p. ej. C:\tgb) la carpeta build/ de cada módulo se redirige a esa ruta corta.
System.getenv("ANDROID_KOTLIN_BUILD_ROOT")?.takeIf { it.isNotBlank() }?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
