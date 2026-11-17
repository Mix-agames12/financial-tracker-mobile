package com.mixagames666.trackerrntemp

import android.app.Application
import com.mixagames666.trackerrntemp.widget.SummaryWidget

/** Punto de entrada de proceso (equivale al registro de la app en `index.ts`). */
class TrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.theme.load(after = container.legacyMigration)

        // syncAfterDataChange(): recordatorios, alerta de presupuesto y widget tras cada cambio en los datos.
        container.dataSync.register("recordatorios") { container.notifications.schedulePaymentReminders() }
        container.dataSync.register("alerta de presupuesto") { container.notifications.checkBudgetAlert() }
        container.dataSync.register("widget") { SummaryWidget.updateIfPresent(this@TrackerApp) }
    }
}
