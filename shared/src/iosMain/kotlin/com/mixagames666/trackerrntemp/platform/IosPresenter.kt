package com.mixagames666.trackerrntemp.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * El controlador visible más arriba (para presentar selectores y hojas de compartir encima de la app).
 * Parte de la ventana clave de la escena en primer plano (`UIApplication.keyWindow` está obsoleto).
 */
internal fun topViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
    val window: UIWindow? = scene?.keyWindow ?: scene?.windows?.firstOrNull() as? UIWindow
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** Muestra la hoja de compartir del sistema con el archivo en [path]. Devuelve false si no hay dónde presentarla. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun shareFile(path: String): Boolean = withContext(Dispatchers.Main) {
    val presenter = topViewController() ?: return@withContext false
    val controller = UIActivityViewController(
        activityItems = listOf(NSURL.fileURLWithPath(path)),
        applicationActivities = null,
    )
    // En iPad la hoja es un popover y exige un ancla: el centro de la pantalla.
    controller.popoverPresentationController?.let { popover ->
        popover.sourceView = presenter.view
        presenter.view.bounds.useContents {
            popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
        }
    }
    presenter.presentViewController(controller, animated = true, completion = null)
    true
}
