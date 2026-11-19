package com.mixagames666.trackerrntemp.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.platform.AppServices
import com.mixagames666.trackerrntemp.ui.components.Ionicon
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.screens.analytics.AnalyticsScreen
import com.mixagames666.trackerrntemp.ui.screens.dashboard.DashboardScreen
import com.mixagames666.trackerrntemp.ui.screens.expenses.ExpensesScreen
import com.mixagames666.trackerrntemp.ui.screens.income.IncomeScreen
import com.mixagames666.trackerrntemp.ui.screens.loans.LoansScreen
import com.mixagames666.trackerrntemp.ui.screens.settings.SettingsScreen
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.bottomInset
import com.mixagames666.trackerrntemp.ui.theme.topInset

// Port de navigation/AppNavigator.tsx y types.ts: pila con las pestañas (MainTabs) y Configuración.

private const val ROUTE_MAIN = "MainTabs"
private const val ROUTE_SETTINGS = "Settings"

/** Pestañas inferiores: ruta, etiqueta, ícono relleno (activa) y de contorno (inactiva). */
private enum class MainTab(val route: String, val label: String, val iconFocused: String, val icon: String) {
    Dashboard("Dashboard", "Inicio", "apps", "apps-outline"),
    Expenses("Expenses", "Gastos", "receipt", "receipt-outline"),
    Income("Income", "Ingresos", "wallet", "wallet-outline"),
    Loans("Loans", "Préstamos", "card", "card-outline"),
    Analytics("Analytics", "Análisis", "bar-chart", "bar-chart-outline"),
}

@Composable
fun AppNavigation(services: AppServices) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = ROUTE_MAIN,
        enterTransition = { fadeIn() + slideInVertically { it / 16 } },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { fadeOut() },
    ) {
        composable(ROUTE_MAIN) {
            MainTabs(services.core, onOpenSettings = { nav.navigate(ROUTE_SETTINGS) })
        }
        composable(ROUTE_SETTINGS) {
            SettingsRoute(services, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun MainTabs(container: CoreContainer, onOpenSettings: () -> Unit) {
    val colors = AppTheme.colors
    val tabNav = rememberNavController()
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { BottomTabBar(tabNav) },
    ) { padding ->
        NavHost(
            navController = tabNav,
            startDestination = MainTab.Dashboard.route,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(MainTab.Dashboard.route) { DashboardScreen(container, onOpenSettings) }
            composable(MainTab.Expenses.route) { ExpensesScreen(container) }
            composable(MainTab.Income.route) { IncomeScreen(container) }
            composable(MainTab.Loans.route) { LoansScreen(container) }
            composable(MainTab.Analytics.route) { AnalyticsScreen(container) }
        }
    }
}

/** tabBarStyle de AppNavigator: alto 64 + inset, relleno inferior max(8, inset), arriba 8, borde superior. */
@Composable
private fun BottomTabBar(nav: NavHostController) {
    val colors = AppTheme.colors
    val inset = bottomInset()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Column(Modifier.background(colors.surfaceContainerLowest)) {
        HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = colors.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp + inset - androidx.compose.ui.unit.Dp.Hairline)
                .padding(top = 8.dp, bottom = max(8.dp, inset)),
        ) {
            MainTab.entries.forEach { tab ->
                val focused = current == tab.route
                // Texto/ícono de la pestaña activa con contraste ≥ 4,5:1 y, además del color y del ícono relleno, un
                // indicador en píldora (no depender sólo del color para mostrar el estado).
                val tint = if (focused) colors.primaryText else colors.onSurfaceVariant
                val pill by animateColorAsState(
                    targetValue = if (focused) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tabIndicator",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .touchableOpacity(role = Role.Tab, activeOpacity = 0.6f) {
                            nav.navigate(tab.route) {
                                popUpTo(MainTab.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .semantics {
                            selected = focused
                            contentDescription = tab.label
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(pill, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Ionicon(if (focused) tab.iconFocused else tab.icon, 24, tint) }
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Pantalla "Configuración" de la pila nativa: barra con flecha atrás y título. */
@Composable
private fun SettingsRoute(services: AppServices, onBack: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerLowest)
                .padding(top = topInset())
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(56.dp)
                    .fillMaxSize()
                    .touchableOpacity(onClickLabel = "Atrás", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Ionicon("arrow-back", 24, colors.onSurface)
            }
            Text(
                text = "Configuración",
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        HorizontalDivider(thickness = Dp.Hairline, color = colors.outlineVariant)
        Box(Modifier.weight(1f)) { SettingsScreen(services) }
    }
}
