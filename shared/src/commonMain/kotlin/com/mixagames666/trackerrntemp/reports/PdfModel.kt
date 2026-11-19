package com.mixagames666.trackerrntemp.reports

import com.mixagames666.trackerrntemp.data.Account
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.orZero
import kotlin.math.max
import kotlin.math.min

// Modelo del PDF del reporte (port de reportGenerator.ts): las secciones y cifras que dibujan, cada una con
// su motor, Android (PdfDocument) e iOS (UIGraphics).

data class PdfStat(val label: String, val value: String, val color: String?)
data class PdfBar(val label: String, val widthPercent: String, val color: String, val value: String)
data class PdfBalance(val label: String, val value: String, val color: String)
data class PdfMovement(val date: String, val detail: String, val category: String, val method: String, val amount: String, val isIncome: Boolean)

class PdfModel(
    val periodLabel: String,
    val typeLabel: String,
    val generatedOn: String,
    val includeIncomes: Boolean,
    val includeExpenses: Boolean,
    val stats: List<PdfStat>,
    val expensePercentage: Int,
    val balancePercentage: Int,
    val categoryBars: List<PdfBar>,
    val sourceBars: List<PdfBar>,
    val balances: List<PdfBalance>,
    val noAccounts: Boolean,
    val movements: List<PdfMovement>,
)

/** "Crédito · Visa", "Débito · Ahorros" o sólo el método si no hay cuenta ni tarjeta. */
private fun paymentMethodLabel(exp: Expense): String {
    val source = if (exp.paymentMethod == "Crédito") exp.cardName else exp.accountName
    return if (!source.isNullOrEmpty()) "${exp.paymentMethod} · $source" else exp.paymentMethod.ifEmpty { "Otro" }
}

private fun <T> topEntries(items: List<T>, key: (T) -> String, amount: (T) -> Double): List<Pair<String, Double>> {
    val totals = LinkedHashMap<String, Double>()
    items.forEach { item ->
        val k = key(item)
        totals[k] = (totals[k] ?: 0.0) + amount(item)
    }
    return totals.entries.map { it.key to it.value }.sortedByDescending { it.second }
}

fun buildPdfModel(data: ReportData): PdfModel {
    val selected = selectRecords(data)
    val incomes = selected.incomes
    val expenses = selected.expenses
    val includeIncomes = selected.includeIncomes
    val includeExpenses = selected.includeExpenses

    val totalIncome = incomes.fold(0.0) { sum, inc -> sum + inc.amount.orZero() }
    val totalExpense = expenses.fold(0.0) { sum, exp -> sum + expenseAmount(exp) }
    val balance = totalIncome - totalExpense

    val expPercentage = if (totalIncome > 0) min(JsCompat.round(totalExpense / totalIncome * 100).toInt(), 100) else 0
    val balPercentage = if (totalIncome > 0) max(0, JsCompat.round(balance / totalIncome * 100).toInt()) else 0

    val typeLabel = if (includeIncomes && includeExpenses) "Ingresos y gastos" else if (includeIncomes) "Sólo ingresos" else "Sólo gastos"
    val balanceColor = if (balance < 0) "#E74C3C" else "#2E7D32"

    val stats = mutableListOf<PdfStat>()
    if (includeIncomes) stats.add(PdfStat("Ingresos", formatCurrency(totalIncome), "#2E7D32"))
    if (includeExpenses) stats.add(PdfStat("Gastos", formatCurrency(totalExpense), "#E74C3C"))
    if (includeIncomes && includeExpenses) stats.add(PdfStat("Balance del período", formatCurrency(balance), balanceColor))
    stats.add(PdfStat("Transacciones", (incomes.size + expenses.size).toString(), null))

    fun bars(entries: List<Pair<String, Double>>, total: Double, color: String) = entries
        .filter { it.second > 0 }
        .map { (label, amount) ->
            val perc = if (total > 0) (amount / total) * 100 else 0.0
            PdfBar(label, JsCompat.toFixed(perc, 1), color, formatCurrency(amount))
        }

    // Todas las categorías y fuentes con movimientos en el período, de mayor a menor.
    val categoryBars = bars(topEntries(expenses, { it.category.ifEmpty { "Sin categoría" } }, ::expenseAmount), totalExpense, "#E74C3C")
    val sourceBars = bars(topEntries(incomes, { it.source.ifEmpty { "Otros" } }, { it.amount.orZero() }), totalIncome, "#2E7D32")

    val balances = data.accounts.map { acc: Account ->
        val accBalance = (data.accountBalances[acc.id] ?: 0.0).orZero()
        PdfBalance("${acc.name} · ${acc.bankName}", formatCurrency(accBalance), if (accBalance < 0) "#E74C3C" else "#2196F3")
    }

    // Detalle de movimientos: del más reciente al más antiguo (la hora sólo desempata).
    class Movement(val date: String, val time: String, val detail: String, val category: String, val method: String, val amount: Double, val isIncome: Boolean)
    val movements = (
        incomes.map {
            Movement(
                it.date, "", it.detail.ifEmpty { it.source.ifEmpty { "Ingreso" } }, it.source.ifEmpty { "Ingreso" },
                if (it.bankAccount.isNotEmpty()) "Depósito · ${it.bankAccount}" else "Depósito", it.amount.orZero(), true,
            )
        } + expenses.map {
            Movement(
                it.date, it.time.orEmpty(), it.detail.ifEmpty { it.category.ifEmpty { "Gasto" } }, it.category.ifEmpty { "Sin categoría" },
                paymentMethodLabel(it), expenseAmount(it), false,
            )
        }
        ).sortedWith { a, b ->
        val byDate = b.date.compareTo(a.date)
        if (byDate != 0) byDate else b.time.compareTo(a.time)
    }.map {
        PdfMovement(formatDate(it.date), it.detail, it.category, it.method, "${if (it.isIncome) "+" else "-"}${formatCurrency(it.amount)}", it.isIncome)
    }

    return PdfModel(
        periodLabel = selected.range.label,
        typeLabel = typeLabel,
        generatedOn = formatDate(getToday()),
        includeIncomes = includeIncomes,
        includeExpenses = includeExpenses,
        stats = stats,
        expensePercentage = expPercentage,
        balancePercentage = balPercentage,
        categoryBars = categoryBars,
        sourceBars = sourceBars,
        balances = balances,
        noAccounts = data.accounts.isEmpty(),
        movements = movements,
    )
}
