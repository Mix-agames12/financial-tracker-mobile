package com.mixagames666.trackerrntemp.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Documento elegido con el selector del sistema (SAF), leído con el ContentResolver de la app. */
private class UriDocument(private val context: Context, private val uri: Uri) : PickedDocument {
    override suspend fun readText(): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("No se pudo abrir el archivo")
    }
}

@Composable
actual fun rememberBackupImportPicker(onPicked: (PickedDocument?) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPicked(uri?.let { UriDocument(context, it) })
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (granted: Boolean) -> Unit): (() -> Unit)? {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onResult(granted) }
    // POST_NOTIFICATIONS sólo se pide en tiempo de ejecución desde Android 13.
    if (Build.VERSION.SDK_INT < 33) return null
    return { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/** `StatusBar style={isDark ? 'light' : 'dark'}`: iconos claros sobre el tema oscuro y viceversa. */
@Composable
actual fun SystemBarsEffect(isDark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
