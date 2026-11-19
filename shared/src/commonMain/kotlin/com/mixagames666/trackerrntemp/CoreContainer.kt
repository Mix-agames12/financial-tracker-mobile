package com.mixagames666.trackerrntemp

import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Backup
import com.mixagames666.trackerrntemp.data.JsonStore
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.domain.AnalyticsLoader
import com.mixagames666.trackerrntemp.domain.CardPurchases
import com.mixagames666.trackerrntemp.domain.CreditCardService
import com.mixagames666.trackerrntemp.domain.DashboardLoader
import com.mixagames666.trackerrntemp.domain.DataSync
import com.mixagames666.trackerrntemp.domain.ExpenseService
import com.mixagames666.trackerrntemp.domain.InvestmentService
import com.mixagames666.trackerrntemp.domain.LoanService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path

/**
 * Dependencias comunes a Android e iOS (inyección manual): almacén, repositorios y servicios de dominio.
 * Cada plataforma la extiende con lo propio (notificaciones, archivos, tema…) y la crea una vez por proceso.
 */
open class CoreContainer(val storeDirectory: Path) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val store = JsonStore(storeDirectory)

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

    /** Recordatorios, alerta de presupuesto y widget tras cambios en los datos (cada plataforma registra sus tareas). */
    val dataSync = DataSync(appScope)
}
