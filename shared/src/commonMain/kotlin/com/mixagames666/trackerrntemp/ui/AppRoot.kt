package com.mixagames666.trackerrntemp.ui

import com.mixagames666.trackerrntemp.util.AppLog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.isSystemInDarkTheme
import com.mixagames666.trackerrntemp.platform.AppServices
import com.mixagames666.trackerrntemp.platform.SystemBarsEffect
import com.mixagames666.trackerrntemp.platform.rememberNotificationPermissionRequester
import com.mixagames666.trackerrntemp.shared.resources.Res
import com.mixagames666.trackerrntemp.shared.resources.splash_icon
import org.jetbrains.compose.resources.painterResource
import com.mixagames666.trackerrntemp.ui.components.ActionFeedback
import com.mixagames666.trackerrntemp.ui.components.AlertHost
import com.mixagames666.trackerrntemp.ui.components.Ionicons
import com.mixagames666.trackerrntemp.ui.navigation.AppNavigation
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.initCurrency
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Raíz de la app (App.tsx): tema, splash animado, navegación y avisos globales. */
@Composable
fun AppRoot(services: AppServices) {
    val theme = services.theme
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) { theme.onSystemSchemeChanged(systemDark) }

    AppTheme(isDark = theme.isDark) {
        SystemBarsEffect(isDark = theme.isDark)
        AnimatedSplashScreen(services) { ThemedApp(services) }
        AlertHost()
    }
}

/**
 * Port de AnimatedSplashScreen: la moneda y las categorías quedan listas antes de pintar la app (mínimo
 * 1,5 s para conservar la animación) y el overlay índigo se desvanece durante 2 s.
 */
@Composable
private fun AnimatedSplashScreen(services: AppServices, content: @Composable () -> Unit) {
    var isAppReady by remember { mutableStateOf(false) }
    var isAnimationComplete by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        try {
            // Datos heredados de la versión React Native (si los hay) antes de leer o escribir nada.
            services.legacyMigration.await()
            // Moneda (detectada en el primer inicio) y categorías listas antes de pintar la app;
            // la espera mínima conserva la animación del splash.
            coroutineScope {
                listOf(
                    async { initCurrency(services.core.repos) },
                    async { services.core.repos.categories.seedDefaults() },
                    // Préstamos de compras con tarjeta anteriores a cardId: se vinculan a su tarjeta.
                    async { services.core.cardPurchases.linkLegacyCardLoans() },
                    // Mapa de glifos de los iconos listo antes de pintar la app (sin parpadeos al aparecer los iconos).
                    async { Ionicons.preload() },
                    async { delay(1500) },
                ).awaitAll()
            }
        } catch (e: Exception) {
            AppLog.w("AppRoot", "No se pudo preparar el inicio", e)
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
                    painter = painterResource(Res.drawable.splash_icon),
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
private fun ThemedApp(services: AppServices) {
    val container = services.core
    // useNotifications(): configura el canal y pide el permiso al montar la app; con permiso, programa los avisos.
    val requestPermission = rememberNotificationPermissionRequester { granted ->
        if (granted) container.dataSync.syncAfterDataChange()
    }
    LaunchedEffect(Unit) {
        services.notifications.ensureChannel()
        if (services.notifications.hasPermission()) {
            container.dataSync.syncAfterDataChange()
        } else {
            requestPermission?.invoke()
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
        AppNavigation(services)
        ActionFeedback()
    }
}
