package com.mixagames666.trackerrntemp

import android.app.Application
import android.content.res.Configuration
import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Backup
import com.mixagames666.trackerrntemp.data.BackupFiles
import com.mixagames666.trackerrntemp.data.JsonStore
import com.mixagames666.trackerrntemp.data.LegacyAsyncStorageImporter
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.domain.AnalyticsLoader
import com.mixagames666.trackerrntemp.domain.CardPurchases
import com.mixagames666.trackerrntemp.domain.CreditCardService
import com.mixagames666.trackerrntemp.domain.DashboardLoader
import com.mixagames666.trackerrntemp.domain.DataSync
import com.mixagames666.trackerrntemp.domain.ExpenseService
import com.mixagames666.trackerrntemp.domain.InvestmentService
import com.mixagames666.trackerrntemp.domain.LoanService
import com.mixagames666.trackerrntemp.notifications.PaymentNotifications
import com.mixagames666.trackerrntemp.reports.ReportGenerator
import com.mixagames666.trackerrntemp.ui.theme.ThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File

/** Contenedor de dependencias de la app (inyección manual): una instancia por proceso. */
class AppContainer(val application: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val storeDirectory = File(application.filesDir, "stores")
    val store = JsonStore(storeDirectory)

    /**
     * Copia los datos de la versión React Native (si esta app se instaló encima) antes de que nada lea o
     * escriba el almacén; quien necesite los datos debe esperar este trabajo.
     */
    val legacyMigration: Deferred<Int> = appScope.async {
        LegacyAsyncStorageImporter(application.getDatabasePath("RKStorage"), storeDirectory, store).importIfNeeded()
    }

    val repos = Repos(store)
    val aggregates = Aggregates(repos)
    val cardPurchases = CardPurchases(repos)

    val expenseService = ExpenseService(repos, aggregates, cardPurchases)
    val loanService = LoanService(repos, aggregates, cardPurchases)
    val creditCardService = CreditCardService(repos)
    val investmentService = InvestmentService(repos, aggregates)
    val dashboardLoader = DashboardLoader(repos, aggregates)
    val analyticsLoader = AnalyticsLoader(repos)
    val backup = Backup(store)
    val backupFiles = BackupFiles(application, backup)
    val reports = ReportGenerator(application, repos, aggregates)
    val notifications = PaymentNotifications(application, repos)

    /** Recordatorios, alerta de presupuesto y widget tras cambios en los datos (se registran en TrackerApp). */
    val dataSync = DataSync(appScope)

    val theme = ThemeController(
        settings = repos.settings,
        scope = appScope,
        initialSystemDark = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES,
    )
}
