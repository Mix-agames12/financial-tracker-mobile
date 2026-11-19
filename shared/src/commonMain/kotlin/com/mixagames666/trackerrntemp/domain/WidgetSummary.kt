package com.mixagames666.trackerrntemp.domain

import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.applySavedCurrency
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDateShort
import com.mixagames666.trackerrntemp.util.getMonthName
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable

// Port de src/widgets/widgetData.ts: lo que muestra el widget "Resumen financiero" (Glance en Android,
// WidgetKit en iOS, que lo lee de widget-summary.json).

/** Datos del widget ya formateados. Es el contrato del JSON que lee el widget de iOS: no renombrar campos. */
@Serializable
data class WidgetSummary(
    val appName: String,
    val monthLabel: String,
    val balance: String,
    val isNegative: Boolean,
    val monthIncome: String,
    val monthExpenses: String,
    val nextPayment: String?,
    val updatedAt: String,
)

/** [legacyMigration]: migración de datos previa que debe terminar antes de leer el almacén. */
suspend fun loadWidgetSummary(container: CoreContainer, legacyMigration: Deferred<*>? = null): WidgetSummary {
    legacyMigration?.await() // el widget puede dibujarse antes que la app al arrancar el proceso
    applySavedCurrency(container.repos)
    val now = Time.now()
    val settings = container.repos.settings.get()
    val totals = container.aggregates.getTotalBalance()
    val monthly = container.aggregates.getMonthlySummary(now.year, now.monthNumber)
    val upcoming = getUpcomingPayments(container.repos, 30)
    val next = upcoming.firstOrNull { it.daysLeft >= 0 }

    fun two(n: Int) = n.toString().padStart(2, '0')
    return WidgetSummary(
        appName = settings.appName.ifEmpty { "Mi Dinero" },
        monthLabel = "${getMonthName(now.monthNumber)} ${now.year}",
        balance = formatCurrency(totals.balance),
        isNegative = totals.balance < 0,
        monthIncome = formatCurrency(monthly.totalIncome),
        monthExpenses = formatCurrency(monthly.totalExpenses),
        nextPayment = next?.let { "${it.title} · ${formatDateShort(it.dueDate)} · ${formatCurrency(it.amount)}" },
        updatedAt = "${two(now.hour)}:${two(now.minute)}",
    )
}
