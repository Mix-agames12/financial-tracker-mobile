package com.mixagames666.trackerrntemp.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mixagames666.trackerrntemp.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/**
 * Port de ThemeContext.tsx. El modo vive en los ajustes ('light' | 'dark' | 'system'); sin ajustes
 * guardados el valor de fábrica es 'dark'. Cargar un ajuste 'system' deja el tema claro hasta que cambie
 * el esquema del sistema, igual que el original (la UI sólo ofrece Claro/Oscuro).
 */
class ThemeController(
    private val settings: SettingsRepo,
    private val scope: CoroutineScope,
    initialSystemDark: Boolean,
) {
    private var systemDark: Boolean = initialSystemDark

    var themeMode: String by mutableStateOf("system")
        private set

    var isDark: Boolean by mutableStateOf(initialSystemDark)
        private set

    /** Carga la preferencia guardada (useEffect de ThemeProvider), una vez lista la migración de datos [after]. */
    fun load(after: Deferred<*>? = null) {
        scope.launch {
            after?.await()
            val s = settings.get()
            if (s.theme.isNotEmpty()) {
                themeMode = s.theme
                isDark = s.theme == "dark"
            }
        }
    }

    /** `Appearance.addChangeListener`: sólo aplica en modo 'system'. */
    fun onSystemSchemeChanged(isSystemDark: Boolean) {
        systemDark = isSystemDark
        if (themeMode == "system") isDark = isSystemDark
    }

    fun setTheme(mode: String) {
        themeMode = mode
        if (mode == "system") {
            isDark = systemDark
            val resolved = if (systemDark) "dark" else "light"
            scope.launch { settings.save { it.copy(theme = resolved) } }
        } else {
            isDark = mode == "dark"
            scope.launch { settings.save { it.copy(theme = mode) } }
        }
    }
}
