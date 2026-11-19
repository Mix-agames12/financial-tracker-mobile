package com.mixagames666.trackerrntemp.reports

import com.mixagames666.trackerrntemp.data.Account
import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.jsDate
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.roundMoney
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import com.mixagames666.trackerrntemp.util.JsCompat
import kotlinx.datetime.LocalDate

// Port de src/utils/reportGenerator.ts (modelo del reporte y CSV; PDF y compartir son de cada plataforma).

enum class ReportPeriod(val wireName: String, val label: String) {
    THIS_MONTH("thisMonth", "Este mes"),
    LAST_MONTH("lastMonth", "Mes anterior"),
    THIS_YEAR("thisYear", "Este año"),
    ALL("all", "Todo"),
}

enum class ReportType(val wireName: String) {
    ALL("all"),
    INGRESOS("ingresos"),
    GASTOS("gastos"),
}

enum class ReportFormat { PDF, CSV }

data class ReportRange(
    val startDate: String?, // 'YYYY-MM-DD'
    val endDate: String?,
    val label: String,
    val fileTag: String,
)

/** Datos de entrada de un reporte (los mismos que arma SettingsScreen). */
data class ReportData(
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val accounts: List<Account>,
    val accountBalances: Map<String, Double>,
    val type: ReportType,
    val period: ReportPeriod,
)

/** Rango de fechas (hora local) de cada período del reporte. */
fun getReportPeriod(period: ReportPeriod, now: LocalDate = Time.today()): ReportRange {
    val year = now.year
    val month = now.monthNumber - 1 // base 0
    fun monthRange(offset: Int): ReportRange {
        val start = toLocalDateStr(jsDate(year, month + offset, 1))
        val end = toLocalDateStr(jsDate(year, month + offset + 1, 0))
        return ReportRange(start, end, "${formatDate(start)} – ${formatDate(end)}", start.substring(0, 7))
    }
    return when (period) {
        ReportPeriod.THIS_MONTH -> monthRange(0)
        ReportPeriod.LAST_MONTH -> monthRange(-1)
        ReportPeriod.THIS_YEAR -> ReportRange("$year-01-01", "$year-12-31", "Año $year", year.toString())
        ReportPeriod.ALL -> ReportRange(null, null, "Todo el historial", "historial")
    }
}

private fun inRange(date: String, range: ReportRange): Boolean =
    (range.startDate == null || date >= range.startDate) && (range.endDate == null || date <= range.endDate)

fun expenseAmount(exp: Expense): Double = (exp.totalCharge ?: exp.amount).orZero()

/** Registros que entran en el reporte según período y tipo; CSV y PDF usan exactamente los mismos. */
class SelectedRecords(
    val range: ReportRange,
    val includeIncomes: Boolean,
    val includeExpenses: Boolean,
    val incomes: List<Income>,
    val expenses: List<Expense>,
)

fun selectRecords(data: ReportData, now: LocalDate = Time.today()): SelectedRecords {
    val range = getReportPeriod(data.period, now)
    val includeIncomes = data.type != ReportType.GASTOS
    val includeExpenses = data.type != ReportType.INGRESOS
    return SelectedRecords(
        range = range,
        includeIncomes = includeIncomes,
        includeExpenses = includeExpenses,
        incomes = if (includeIncomes) data.incomes.filter { inRange(it.date, range) } else emptyList(),
        expenses = if (includeExpenses) data.expenses.filter { inRange(it.date, range) } else emptyList(),
    )
}

private fun sanitizeCsv(text: String): String {
    if (text.isEmpty()) return ""
    return "\"${text.replace("\"", "\"\"")}\""
}

private class CsvRow(val date: String, val type: String, val category: String, val account: String, val amount: Double, val detail: String)

/** Contenido del CSV (con BOM para que Excel lea UTF-8) y nombre del archivo. */
fun buildCsv(data: ReportData): Pair<String, String> {
    val selected = selectRecords(data)

    val sb = StringBuilder("\uFEFFFecha,Tipo,Categoría/Fuente,Cuenta,Monto,Detalle\n")
    val rows = selected.incomes.map { CsvRow(it.date, "Ingreso", it.source, it.bankAccount, it.amount.orZero(), it.detail) } +
        selected.expenses.map {
            CsvRow(
                it.date, "Gasto", it.category,
                it.accountName?.takeIf { a -> a.isNotEmpty() } ?: it.cardName.orEmpty(),
                expenseAmount(it), it.detail,
            )
        }
    // Ordenar descendente (fecha más actual -> más antigua)
    rows.sortedWith { a, b -> b.date.compareTo(a.date) }.forEach { r ->
        sb.append("${r.date},${r.type},${sanitizeCsv(r.category)},${sanitizeCsv(r.account)},${JsCompat.toFixed(roundMoney(r.amount), 2)},${sanitizeCsv(r.detail)}\n")
    }
    return sb.toString() to "reporte_${selected.range.fileTag}_${getToday()}.csv"
}

/** Datos de entrada del reporte: todos los registros más los saldos actuales (el filtro por período lo hace [selectRecords]). */
suspend fun loadReportData(repos: Repos, aggregates: Aggregates, type: ReportType, period: ReportPeriod): ReportData = ReportData(
    expenses = repos.expenses.getAll(),
    incomes = repos.income.getAll(),
    accounts = repos.accounts.getAll(),
    accountBalances = aggregates.getAccountBalances(),
    type = type,
    period = period,
)
