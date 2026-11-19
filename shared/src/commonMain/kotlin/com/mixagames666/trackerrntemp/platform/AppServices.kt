package com.mixagames666.trackerrntemp.platform

import androidx.compose.runtime.Composable
import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.reports.ReportData
import com.mixagames666.trackerrntemp.reports.ReportPeriod
import com.mixagames666.trackerrntemp.reports.ReportType
import com.mixagames666.trackerrntemp.ui.theme.ThemeController
import kotlinx.coroutines.Deferred

/**
 * Lo que la UI común necesita además de [CoreContainer] y que cada plataforma implementa
 * (el `AppContainer` de Android; en iOS, el equivalente que crea `MainViewController`).
 */
interface AppServices {
    /** Almacén, repositorios y servicios de dominio. */
    val core: CoreContainer

    /** Preferencia de tema (claro/oscuro/sistema), persistida en los ajustes. */
    val theme: ThemeController

    /** Migración de datos de la versión anterior; la UI espera este trabajo antes de leer o escribir datos. */
    val legacyMigration: Deferred<Int>

    val notifications: NotificationService

    val backupFiles: BackupService

    val reports: ReportService
}

/** Notificaciones locales: canal, permiso y estado actual del permiso. */
interface NotificationService {
    /** Crea el canal/categoría de notificaciones (idempotente). */
    fun ensureChannel()

    /** `true` si la app puede mostrar notificaciones ahora mismo. */
    fun hasPermission(): Boolean
}

/** Exportar el backup JSON y compartirlo con el selector del sistema. */
interface BackupService {
    /** Devuelve false si el dispositivo no permite compartir archivos. */
    suspend fun exportAndShare(): Boolean
}

/** Genera y comparte los reportes del selector de Configuración. */
interface ReportService {
    suspend fun loadData(type: ReportType, period: ReportPeriod): ReportData

    /** Devuelve false si el dispositivo no permite compartir archivos. */
    suspend fun generateCsv(data: ReportData): Boolean

    /** Devuelve false si el dispositivo no permite compartir archivos. */
    suspend fun generatePdf(data: ReportData): Boolean
}

/** Documento elegido por el usuario en el selector de archivos del sistema. */
interface PickedDocument {
    /** Lee todo el documento como UTF-8; lanza si no se puede abrir. */
    suspend fun readText(): String
}

/**
 * Selector de archivos para importar un backup (JSON). Devuelve la función que lo abre; [onPicked] recibe el
 * documento elegido o `null` si el usuario cancela.
 */
@Composable
expect fun rememberBackupImportPicker(onPicked: (PickedDocument?) -> Unit): () -> Unit

/**
 * Solicitud del permiso de notificaciones. Devuelve la función que muestra el aviso del sistema, o `null` si la
 * plataforma no necesita pedirlo en tiempo de ejecución. [onResult] recibe si el permiso quedó concedido.
 */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (granted: Boolean) -> Unit): (() -> Unit)?

/** Iconos de las barras del sistema (estado y navegación) claros u oscuros según el tema. */
@Composable
expect fun SystemBarsEffect(isDark: Boolean)
