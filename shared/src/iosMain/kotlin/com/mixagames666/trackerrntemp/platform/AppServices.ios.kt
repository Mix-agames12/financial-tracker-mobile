package com.mixagames666.trackerrntemp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UNUserNotifications.UNAuthorizationOptionAlert
import platform.UNUserNotifications.UNAuthorizationOptionBadge
import platform.UNUserNotifications.UNAuthorizationOptionSound
import platform.UNUserNotifications.UNUserNotificationCenter
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Documento elegido con `UIDocumentPickerViewController` (copiado a la caché de la app, se lee como UTF-8). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class UrlDocument(private val url: NSURL) : PickedDocument {
    override suspend fun readText(): String {
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            return NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)
                ?: throw IllegalStateException("No se pudo abrir el archivo")
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberBackupImportPicker(onPicked: (PickedDocument?) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // El delegado es débil en UIKit: se conserva en la composición mientras se muestra el selector.
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                currentOnPicked(url?.let { UrlDocument(it) })
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                currentOnPicked(null)
            }
        }
    }
    return {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText, UTTypeData),
            asCopy = true,
        )
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (granted: Boolean) -> Unit): (() -> Unit)? {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        {
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { granted, _ ->
                IosNotificationState.authorized = granted
                dispatch_async(dispatch_get_main_queue()) { currentOnResult(granted) }
            }
        }
    }
}

@Composable
actual fun SystemBarsEffect(isDark: Boolean) {
    // TODO iOS: el estilo de la barra de estado lo decide el UIViewController raíz (preferredStatusBarStyle) o
    // Info.plist; sin implementar aquí. Pendiente de verificar en Mac (claro sobre tema oscuro y viceversa).
}
