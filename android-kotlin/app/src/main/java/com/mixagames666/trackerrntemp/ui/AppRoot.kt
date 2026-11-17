package com.mixagames666.trackerrntemp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.R
import com.mixagames666.trackerrntemp.ui.components.ActionFeedback
import com.mixagames666.trackerrntemp.ui.components.AlertHost
import com.mixagames666.trackerrntemp.ui.navigation.AppNavigation
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.initCurrency
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Raíz de la app (App.tsx): tema, splash animado, navegación y avisos globales. */
@Composable
fun AppRoot(container: AppContainer) {
    val theme = container.theme
    val configuration = LocalConfiguration.current
    val systemDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    LaunchedEffect(systemDark) { theme.onSystemSchemeChanged(systemDark) }

    AppTheme(isDark = theme.isDark) {
        SystemBarsEffect(isDark = theme.isDark)
        AnimatedSplashScreen(container) { ThemedApp(container) }
        AlertHost()
    }
}

/** `StatusBar style={isDark ? 'light' : 'dark'}`: iconos claros sobre el tema oscuro y viceversa. */
@Composable
private fun SystemBarsEffect(isDark: Boolean) {
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

/**
 * Port de AnimatedSplashScreen: la moneda y las categorías quedan listas antes de pintar la app (mínimo
 * 1,5 s para conservar la animación) y el overlay índigo se desvanece durante 2 s.
 */
@Composable
private fun AnimatedSplashScreen(container: AppContainer, content: @Composable () -> Unit) {
    var isAppReady by remember { mutableStateOf(false) }
    var isAnimationComplete by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        try {
            // Datos heredados de la versión React Native (si los hay) antes de leer o escribir nada.
            container.legacyMigration.await()
            // Moneda (detectada en el primer inicio) y categorías listas antes de pintar la app;
            // la espera mínima conserva la animación del splash.
            coroutineScope {
                listOf(
                    async { initCurrency(container.repos) },
                    async { container.repos.categories.seedDefaults() },
                    // Préstamos de compras con tarjeta anteriores a cardId: se vinculan a su tarjeta.
                    async { container.cardPurchases.linkLegacyCardLoans() },
                    async { delay(1500) },
                ).awaitAll()
            }
        } catch (e: Exception) {
            Log.w("AppRoot", e)
        } finally {
            isAppReady = true
        }
    }

    LaunchedEffect(isAppReady) {
        if (isAppReady) {
            // Animated.timing por defecto: Easing.inOut(Easing.ease)
            alpha.animateTo(0f, tween(2000, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)))
            isAnimationComplete = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (isAppReady) content()
        if (!isAnimationComplete) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value }
                    .background(Color(0xFF312E81)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_icon),
                    contentDescription = null,
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/** ThemedApp de App.tsx: sincroniza recordatorios/alerta/widget al abrir y al volver a primer plano. */
@Composable
private fun ThemedApp(container: AppContainer) {
    // useNotifications(): configura el canal y pide el permiso al montar la app; con permiso, programa los avisos.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.dataSync.syncAfterDataChange()
    }
    LaunchedEffect(Unit) {
        container.notifications.ensureChannel()
        if (container.notifications.hasPermission()) {
            container.dataSync.syncAfterDataChange()
        } else if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Recordatorios, alerta de presupuesto y widget se recalculan al abrir y al volver a la app
        // (cambio de día o de mes): ON_START cubre ambos casos.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) container.dataSync.syncAfterDataChange()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        AppNavigation(container)
        ActionFeedback()
    }
}
