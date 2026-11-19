package com.mixagames666.trackerrntemp

import android.app.Application
import android.content.res.Configuration
import com.mixagames666.trackerrntemp.data.BackupFiles
import com.mixagames666.trackerrntemp.data.LegacyAsyncStorageImporter
import com.mixagames666.trackerrntemp.notifications.PaymentNotifications
import com.mixagames666.trackerrntemp.platform.AppServices
import com.mixagames666.trackerrntemp.reports.ReportGenerator
import com.mixagames666.trackerrntemp.ui.theme.ThemeController
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import okio.Path.Companion.toOkioPath
import java.io.File

/** Contenedor de dependencias de la app Android: lo común ([CoreContainer]) más lo que necesita Context. */
class AppContainer(val application: Application) : CoreContainer(File(application.filesDir, "stores").toOkioPath()), AppServices {
    override val core: CoreContainer get() = this

    private val storeFolder = File(application.filesDir, "stores")

    /**
     * Copia los datos de la versión React Native (si esta app se instaló encima) antes de que nada lea o
     * escriba el almacén; quien necesite los datos debe esperar este trabajo.
     */
    override val legacyMigration: Deferred<Int> = appScope.async {
        LegacyAsyncStorageImporter(application.getDatabasePath("RKStorage"), storeFolder, store).importIfNeeded()
    }

    override val backupFiles = BackupFiles(application, backup)
    override val reports = ReportGenerator(application, repos, aggregates)
    override val notifications = PaymentNotifications(application, repos)

    override val theme = ThemeController(
        settings = repos.settings,
        scope = appScope,
        initialSystemDark = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES,
    )
}
