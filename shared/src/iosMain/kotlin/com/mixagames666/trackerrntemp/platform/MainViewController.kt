package com.mixagames666.trackerrntemp.platform

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.mixagames666.trackerrntemp.ui.AppRoot
import platform.UIKit.UIViewController

/** Punto de entrada para iOS: el `UIViewController` con toda la app en Compose (lo aloja SwiftUI en `iosApp`). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    AppRoot(remember { IosAppServices.create() })
}
