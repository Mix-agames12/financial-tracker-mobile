package com.mixagames666.trackerrntemp.domain

import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Category
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.data.MonthlySummary
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.data.TotalBalance
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.daysInMonthOf
import java.time.LocalDateTime
import kotlin.math.min

// Lógica de carga de DashboardScreen (loadData) sin la UI.

/** Aviso de "Día de sueldo" (la hoja que pregunta si ya se acreditó el sueldo). */
data class SalaryPrompt(val amount: Double, val account: String, val configId: String)

/** Una fila de "Últimas transacciones": un gasto o un ingreso. */
data class RecentTransaction(
    val expense: Expense?,
    val income: Income?,
    val sortKey: String,
) {
    val isExpense: Boolean get() = expense != null
    val id: String get() = expense?.id ?: income?.id.orEmpty()
}

data class DashboardData(
    val appName: String,
    val balance: TotalBalance,
    val monthly: MonthlySummary,
    val totalLoanDebt: Double,
    val monthlyGoal: Double,
    val recentTx: List<RecentTransaction>,
    val categories: List<Category>,
    val upcoming: List<UpcomingPayment>,
    val needsWelcome: Boolean,
    val salaryPrompt: SalaryPrompt?,
)

class DashboardLoader(private val repos: Repos, private val aggregates: Aggregates) {

    suspend fun load(now: LocalDateTime = Time.now()): DashboardData {
        val year = now.year
        val month = now.monthValue

        val balance = aggregates.getTotalBalance()
        val monthly = aggregates.getMonthlySummary(year, month)
        val settings = repos.settings.get()
        val activeLoans = repos.loans.getActive()

        val totalLoanDebt = activeLoans.fold(0.0) { acc, l -> acc + ((l.installments - l.paidInstallments) * l.monthlyQuota) }

        val allExpenses = repos.expenses.getAll()
        val allIncomes = repos.income.getAll()

        val allTx = (
            allExpenses.map { RecentTransaction(it, null, "${it.date}T${it.time.takeUnless { t -> t.isNullOrEmpty() } ?: "00:00:00"}") } +
                allIncomes.map { RecentTransaction(null, it, "${it.date}T23:59:59") } // incomes al final del día si no hay hora
            )
            .sortedWith { a, b -> b.sortKey.compareTo(a.sortKey) }
            .take(8)

        val categories = repos.categories.getAll()
        val upcoming = getUpcomingPayments(repos, 30, now.toLocalDate())

        // Primer inicio: la hoja de bienvenida (moneda) va antes que cualquier otro aviso.
        val needsWelcome = settings.onboardingCompleted != true

        // Salary Check Logic
        var salaryPrompt: SalaryPrompt? = null
        val salaryConfig = repos.salary.getAll().firstOrNull()
        if (!needsWelcome && salaryConfig != null && salaryConfig.amount > 0) {
            var payDay = 31
            if (salaryConfig.payDateType == "specific") {
                payDay = JsCompat.parseIntOrZero(salaryConfig.payDate).let { if (it == 0) 31 else it }
            } else if (salaryConfig.payDateType == "last" || salaryConfig.payDateType == "lastBusiness") {
                payDay = daysInMonthOf(year, month)
            }

            val lastDayOfMonth = daysInMonthOf(year, month)
            val actualPayDay = min(payDay, lastDayOfMonth)

            if (now.dayOfMonth >= actualPayDay) {
                val mm = month.toString().padStart(2, '0')
                val start = "$year-$mm-01"
                val end = "$year-$mm-${lastDayOfMonth.toString().padStart(2, '0')}"
                val currentIncomes = repos.income.getByDateRange(start, end)

                val alreadyPaid = currentIncomes.any { it.isSalary == true }

                if (!alreadyPaid) {
                    val postponeUntil = settings.salaryPromptPostponedUntil ?: 0L
                    if (Time.nowMillis() > postponeUntil) {
                        salaryPrompt = SalaryPrompt(
                            amount = salaryConfig.amount,
                            account = salaryConfig.bankAccount?.takeIf { it.isNotEmpty() } ?: "Efectivo",
                            configId = salaryConfig.id,
                        )
                    }
                }
            }
        }

        return DashboardData(
            appName = settings.appName.ifEmpty { "Mi Dinero" },
            balance = balance,
            monthly = monthly,
            totalLoanDebt = totalLoanDebt,
            monthlyGoal = settings.monthlyGoal,
            recentTx = allTx,
            categories = categories,
            upcoming = upcoming,
            needsWelcome = needsWelcome,
            salaryPrompt = salaryPrompt,
        )
    }
}
