package com.mixagames666.trackerrntemp.platform

import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.domain.WidgetSummary
import com.mixagames666.trackerrntemp.domain.loadWidgetSummary
import com.mixagames666.trackerrntemp.ui.theme.ThemeController
import com.mixagames666.trackerrntemp.util.ioDispatcher
import com.mixagames666.trackerrntemp.util.platformFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyleDark

/** Grupo de apps compartido con la extensión del widget (mismo identificador en los `.entitlements` de `iosApp`). */
const val IOS_APP_GROUP = "group.com.mixagames666.trackerrntemp"

/** Nombre del archivo que escribe la app y lee el widget (en la raíz del contenedor del grupo de apps). */
const val WIDGET_SUMMARY_FILE = "widget-summary.json"

/** Lo escribe Swift al arrancar para que el código Kotlin pueda pedir a WidgetKit que recargue (WidgetCenter es sólo Swift). */
interface WidgetReloader {
    fun reload()
}

/** Puente Swift -> Kotlin (se asigna una vez, en `MiDineroApp.init`). */
object IosBridge {
    var widgetReloader: WidgetReloader? = null
}

/**
 * Contenedor de dependencias de iOS: equivale al `AppContainer` de Android. Los datos viven en el contenedor del
 * grupo de apps (para que el widget los vea) o, si no hay grupo (app sin firmar), en Documents.
 */
class IosAppServices private constructor(private val baseDirectory: Path) :
    CoreContainer(baseDirectory / "stores"), AppServices {

    override val core: CoreContainer get() = this

    /** No hay datos de la versión React Native en iOS: nada que migrar. */
    override val legacyMigration: Deferred<Int> = CompletableDeferred(0)

    private val iosNotifications = IosNotifications(repos)
    override val notifications: NotificationService = iosNotifications
    override val backupFiles: BackupService = IosBackupFiles(backup)
    override val reports: ReportService = IosReportService(repos, aggregates)

    override val theme = ThemeController(
        settings = repos.settings,
        scope = appScope,
        initialSystemDark = UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyleDark,
    )

    init {
        theme.load(after = legacyMigration)
        iosNotifications.start(appScope)

        // syncAfterDataChange(): recordatorios, alerta de presupuesto y widget tras cada cambio en los datos.
        dataSync.register("recordatorios") { iosNotifications.schedulePaymentReminders() }
        dataSync.register("alerta de presupuesto") { iosNotifications.checkBudgetAlert() }
        dataSync.register("widget") { writeWidgetSummary() }
    }

    /** Escribe `widget-summary.json` (escritura atómica) y avisa a Swift para que WidgetKit recargue la línea de tiempo. */
    private suspend fun writeWidgetSummary() {
        val summary = loadWidgetSummary(this, legacyMigration)
        val json = Json.encodeToString(WidgetSummary.serializer(), summary)
        withContext(ioDispatcher) {
            platformFileSystem.createDirectories(baseDirectory)
            val target = baseDirectory / WIDGET_SUMMARY_FILE
            val temp = baseDirectory / "$WIDGET_SUMMARY_FILE.tmp"
            platformFileSystem.write(temp) { writeUtf8(json) }
            try {
                platformFileSystem.atomicMove(temp, target)
            } catch (_: Exception) {
                platformFileSystem.write(target) { writeUtf8(json) }
                platformFileSystem.delete(temp)
            }
        }
        IosBridge.widgetReloader?.reload()
    }

    companion object {
        private var instance: IosAppServices? = null

        /**
         * Crea el contenedor una sola vez por proceso (devuelve el mismo si se vuelve a llamar, p. ej. si el
         * controlador se recrea); sólo se usa desde el hilo principal.
         */
        fun create(): IosAppServices = instance ?: IosAppServices(baseDirectory().toPath()).also { instance = it }

        /** Contenedor del grupo de apps; si no existe (sin firmar o sin capacidad), la carpeta Documents de la app. */
        private fun baseDirectory(): String {
            val fileManager = NSFileManager.defaultManager
            val group = fileManager.containerURLForSecurityApplicationGroupIdentifier(IOS_APP_GROUP)?.path
            if (group != null) return group
            val documents = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as? NSURL
            return documents?.path ?: NSHomeDirectory()
        }
    }
}
