package com.mixagames666.trackerrntemp.golden

import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.ALL_STORES
import com.mixagames666.trackerrntemp.data.AppJson
import com.mixagames666.trackerrntemp.data.Backup
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.data.JsonStore
import com.mixagames666.trackerrntemp.data.Loan
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.data.Salary
import com.mixagames666.trackerrntemp.data.Settings
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.domain.AnalyticsPeriod
import com.mixagames666.trackerrntemp.domain.CardPurchases
import com.mixagames666.trackerrntemp.domain.ComparisonOutcome
import com.mixagames666.trackerrntemp.domain.CreditCardForm
import com.mixagames666.trackerrntemp.domain.CreditCardService
import com.mixagames666.trackerrntemp.domain.DashboardLoader
import com.mixagames666.trackerrntemp.domain.ExpenseForm
import com.mixagames666.trackerrntemp.domain.ExpenseService
import com.mixagames666.trackerrntemp.domain.InvestmentService
import com.mixagames666.trackerrntemp.domain.LoanForm
import com.mixagames666.trackerrntemp.domain.LoanService
import com.mixagames666.trackerrntemp.domain.ServiceResult
import com.mixagames666.trackerrntemp.domain.CardCycleSummary
import com.mixagames666.trackerrntemp.domain.compareMonths
import com.mixagames666.trackerrntemp.domain.computeAnalytics
import com.mixagames666.trackerrntemp.domain.getCardCycleSummary
import com.mixagames666.trackerrntemp.domain.getUpcomingPayments
import com.mixagames666.trackerrntemp.domain.loanCardOutstanding
import com.mixagames666.trackerrntemp.domain.loanRemainingDebt
import com.mixagames666.trackerrntemp.reports.PdfModel
import com.mixagames666.trackerrntemp.reports.PdfReport
import com.mixagames666.trackerrntemp.reports.ReportData
import com.mixagames666.trackerrntemp.reports.ReportPeriod
import com.mixagames666.trackerrntemp.reports.ReportType
import com.mixagames666.trackerrntemp.reports.buildCsv
import com.mixagames666.trackerrntemp.reports.getReportPeriod
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.daysUntil
import com.mixagames666.trackerrntemp.util.formatMonths
import com.mixagames666.trackerrntemp.util.formatPercent
import com.mixagames666.trackerrntemp.util.getDaysInMonth
import com.mixagames666.trackerrntemp.util.getMonthName
import com.mixagames666.trackerrntemp.util.getNow
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.getWeekNumber
import com.mixagames666.trackerrntemp.util.lastDateForDay
import com.mixagames666.trackerrntemp.util.nextDateForDay
import com.mixagames666.trackerrntemp.util.nextDateForMonthDay
import com.mixagames666.trackerrntemp.util.roundMoney
import com.mixagames666.trackerrntemp.util.sanitizeDecimalInput
import com.mixagames666.trackerrntemp.util.sanitizeIntegerInput
import com.mixagames666.trackerrntemp.util.toAmountInput
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Clock

/**
 * Reproduce un escenario dorado (ver tools/golden) contra la versión Kotlin: siembra las colecciones,
 * repite las operaciones con el reloj fijo y devuelve resultados + estado final normalizados con la
 * misma regla que run.js (ids generados → gen-N, createdAt de los nuevos → "<createdAt>").
 */
class GoldenRunner(directory: File) {
    private val store = JsonStore(directory.toOkioPath())
    private val repos = Repos(store)
    private val aggregates = Aggregates(repos)
    private val cardPurchases = CardPurchases(repos)
    private val expenseService = ExpenseService(repos, aggregates, cardPurchases)
    private val loanService = LoanService(repos, aggregates, cardPurchases)
    private val creditCardService = CreditCardService(repos)
    private val investmentService = InvestmentService(repos, aggregates)
    private val backup = Backup(store)
    private val refs = HashMap<String, String>()

    private var seedIds: Set<String> = emptySet()
    private val generated = LinkedHashMap<String, String>()

    class Output(val results: List<JsonElement>, val finalState: JsonObject)

    suspend fun run(scenario: JsonObject): Output {
        val clock = scenario["clock"]?.jsonPrimitive?.content ?: "2026-11-17T12:00:00"
        setClock(clock)

        val seed = scenario["seed"] as? JsonObject ?: JsonObject(emptyMap())
        val ids = HashSet<String>()
        for (name in ALL_STORES) {
            val array = seed[name] as? JsonArray ?: continue
            store.writeText(name, AppJson.encodeToString(JsonArray.serializer(), array))
            array.forEach { rec -> (rec as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.let { ids.add(it) } }
        }
        seedIds = ids

        val results = mutableListOf<JsonElement>()
        for (step in scenario["steps"]!!.jsonArray) {
            val op = step.jsonObject["op"]!!.jsonPrimitive.content
            val args = resolveRefs(step.jsonObject["args"] ?: JsonObject(emptyMap())) as JsonObject
            if (op == "setClock") {
                setClock(args["at"]!!.jsonPrimitive.content)
                results.add(JsonNull)
                continue
            }
            val raw = execute(op, args)
            scan()
            results.add(normalize(raw))
        }
        return Output(results, snapshot())
    }

    // ---------------------------------------------------------------------------------------------
    // Reloj
    // ---------------------------------------------------------------------------------------------

    private fun setClock(text: String) {
        val now = LocalDateTime.parse(text)
        Time.nowProvider = { now }
        Time.zoneProvider = { TimeZone.of("America/Guayaquil") }
    }

    // ---------------------------------------------------------------------------------------------
    // Normalización de ids
    // ---------------------------------------------------------------------------------------------

    private suspend fun scan() {
        for (name in ALL_STORES) {
            for (rec in store.readRaw(name)) {
                val id = (rec as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull ?: continue
                if (id !in seedIds && id !in generated) generated[id] = "gen-${generated.size + 1}"
            }
        }
    }

    private fun normalize(value: JsonElement): JsonElement = when (value) {
        is JsonPrimitive -> if (value.isString && generated.containsKey(value.content)) JsonPrimitive(generated.getValue(value.content)) else value
        is JsonArray -> JsonArray(value.map { normalize(it) })
        is JsonObject -> JsonObject(value.mapValues { normalize(it.value) })
    }

    private suspend fun snapshot(): JsonObject {
        val map = LinkedHashMap<String, JsonElement>()
        for (name in ALL_STORES) {
            map[name] = JsonArray(
                store.readRaw(name).map { rec ->
                    val normalized = normalize(rec) as JsonObject
                    val id = (rec as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                    if (id != null && id !in seedIds && normalized.containsKey("createdAt")) {
                        JsonObject(normalized + ("createdAt" to JsonPrimitive("<createdAt>")))
                    } else {
                        normalized
                    }
                },
            )
        }
        return JsonObject(map)
    }

    private fun resolveRefs(value: JsonElement): JsonElement = when (value) {
        is JsonPrimitive -> if (value.isString && value.content.startsWith("$") && refs.containsKey(value.content.substring(1))) {
            JsonPrimitive(refs.getValue(value.content.substring(1)))
        } else {
            value
        }
        is JsonArray -> JsonArray(value.map { resolveRefs(it) })
        is JsonObject -> JsonObject(value.mapValues { resolveRefs(it.value) })
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers de conversión
    // ---------------------------------------------------------------------------------------------

    private inline fun <reified T> JsonElement.decode(): T = AppJson.decodeFromJsonElement(this)

    private fun <T> merge(value: T, serializer: KSerializer<T>, overrides: JsonObject): T {
        val base = AppJson.encodeToJsonElement(serializer, value).jsonObject
        return AppJson.decodeFromJsonElement(serializer, JsonObject(base + overrides))
    }

    private fun String?.orNullIfEmpty() = this?.takeIf { it.isNotEmpty() }

    private suspend fun loanFor(args: JsonObject): Loan {
        args["loanId"]?.jsonPrimitive?.contentOrNull?.let { return repos.loans.get(it)!! }
        val expenseId = args["loanOfExpense"]!!.jsonPrimitive.content
        return repos.loans.getAll().first { it.sourceExpenseId == expenseId }
    }

    private fun num(element: JsonElement?): Double {
        val p = element as? JsonPrimitive ?: return 0.0
        if (p is JsonNull) return 0.0
        return if (p.isString) JsCompat.number(p.content) else p.double
    }

    private fun dateArg(element: JsonElement?): LocalDate? {
        val obj = element as? JsonObject ?: return null
        return LocalDate.parse(obj["\$date"]!!.jsonPrimitive.content.substringBefore('T'))
    }

    private fun alertJson(title: String, message: String) = buildJsonObject {
        put("alert", buildJsonArray { add(JsonPrimitive(title)); add(JsonPrimitive(message)) })
    }

    private fun resultJson(result: ServiceResult): JsonElement = when (result) {
        is ServiceResult.Alert -> alertJson(result.title, result.message)
        is ServiceResult.Done -> result.successAlert?.let { alertJson("Éxito", it) } ?: buildJsonObject { put("saved", true) }
    }

    private fun summaryJson(summary: CardCycleSummary) = buildJsonObject {
        put("lastCutOff", summary.lastCutOff?.let { JsonPrimitive(it) } ?: JsonNull)
        put("nextDueDate", summary.nextDueDate?.let { JsonPrimitive(it) } ?: JsonNull)
        put("dueAmount", summary.dueAmount)
        put("activeDebt", summary.activeDebt)
        put("totalPending", summary.totalPending)
        put("available", summary.available?.let { JsonPrimitive(it) } ?: JsonNull)
        put("hasOverdue", summary.hasOverdue)
        put(
            "purchases",
            buildJsonArray {
                summary.purchases.forEach { p ->
                    add(
                        buildJsonObject {
                            put("loanId", p.loan.id)
                            put("expenseId", p.expense?.id?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("title", p.title)
                            put("pending", p.pending)
                            put("isPaid", p.isPaid)
                        },
                    )
                }
            },
        )
    }

    private fun mapJson(map: Map<String, Double>) = JsonObject(map.mapValues { JsonPrimitive(it.value) })

    private fun doubles(list: List<Double>) = JsonArray(list.map { JsonPrimitive(it) })

    private fun strings(list: List<String>) = JsonArray(list.map { JsonPrimitive(it) })

    // ---------------------------------------------------------------------------------------------
    // Operaciones
    // ---------------------------------------------------------------------------------------------

    private suspend fun execute(op: String, a: JsonObject): JsonElement = when (op) {
        "addExpense" -> {
            val e = repos.expenses.add(a["payload"]!!.decode<Expense>())
            a["as"]?.jsonPrimitive?.contentOrNull?.let { refs[it] = e.id }
            JsonNull
        }
        "addLoan" -> {
            val l = repos.loans.add(a["payload"]!!.decode<Loan>())
            a["as"]?.jsonPrimitive?.contentOrNull?.let { refs[it] = l.id }
            JsonNull
        }
        "addIncome" -> {
            val i = repos.income.add(a["payload"]!!.decode<Income>())
            a["as"]?.jsonPrimitive?.contentOrNull?.let { refs[it] = i.id }
            JsonNull
        }

        // ---- cardPurchases ----
        "registerCardPurchase" -> {
            val expense = repos.expenses.get(a["expenseId"]!!.jsonPrimitive.content)!!
            val card = repos.creditCards.get(a["cardId"]!!.jsonPrimitive.content)!!
            cardPurchases.registerCardPurchase(expense, card, a["totalCharge"]!!.jsonPrimitive.double, a["nextPaymentDate"]!!.jsonPrimitive.content)
            JsonNull
        }
        "updateCardPurchase" -> {
            val id = a["expenseId"]!!.jsonPrimitive.content
            val before = repos.expenses.get(id)!!
            val updated = repos.expenses.update(merge(before, Expense.serializer(), a["overrides"]!!.jsonObject))
            val card = a["cardId"]?.jsonPrimitive?.contentOrNull?.let { repos.creditCards.get(it) }
            cardPurchases.updateCardPurchase(before, updated, card, a["nextPaymentDate"]?.jsonPrimitive?.contentOrNull ?: "")
            JsonNull
        }
        "deleteExpenseWithCardSync" -> {
            cardPurchases.deleteExpenseWithCardSync(a["expenseId"]!!.jsonPrimitive.content)
            JsonNull
        }
        "applyLoanPaymentToCard" -> {
            val r = cardPurchases.applyLoanPaymentToCard(loanFor(a), a["quota"]!!.jsonPrimitive.double, a["isFinalPayment"]!!.jsonPrimitive.boolean)
            r?.let { JsonPrimitive(it) } ?: JsonNull
        }
        "releaseLoanFromCard" -> {
            cardPurchases.releaseLoanFromCard(loanFor(a))
            JsonNull
        }
        "syncLoanEditWithCard" -> {
            val before = loanFor(a)
            val after = merge(before, Loan.serializer(), a["overrides"]!!.jsonObject)
            val r = cardPurchases.syncLoanEditWithCard(before, after)
            r?.let { JsonPrimitive(it) } ?: JsonNull
        }
        "linkLegacyCardLoans" -> {
            cardPurchases.linkLegacyCardLoans()
            JsonNull
        }
        "getCardCycleSummary" -> {
            val card = repos.creditCards.get(a["cardId"]!!.jsonPrimitive.content)!!
            val summary = getCardCycleSummary(card, repos.loans.getAll(), repos.expenses.getAll().associateBy { it.id })
            summaryJson(summary)
        }
        "loanDerived" -> {
            val loan = loanFor(a)
            buildJsonObject {
                put("outstanding", loanCardOutstanding(loan))
                put("remainingDebt", loanRemainingDebt(loan))
            }
        }

        // ---- paymentReminders ----
        "getUpcomingPayments" -> {
            val list = getUpcomingPayments(repos, a["horizonDays"]!!.jsonPrimitive.int)
            buildJsonArray {
                list.forEach { p ->
                    add(
                        buildJsonObject {
                            put("id", p.id)
                            put("kind", p.kind.wireName)
                            put("title", p.title)
                            put("amount", p.amount)
                            put("dueDate", p.dueDate)
                            put("daysLeft", p.daysLeft)
                        },
                    )
                }
            }
        }

        // ---- storage ----
        "getTotalBalance" -> {
            val t = aggregates.getTotalBalance()
            buildJsonObject {
                put("totalIncome", t.totalIncome)
                put("totalExpenses", t.totalExpenses)
                put("balance", t.balance)
                put("salaryAmount", t.salaryAmount)
                put("extraIncome", t.extraIncome)
            }
        }
        "getAccountBalances" -> mapJson(aggregates.getAccountBalances())
        "getMonthlySummary" -> {
            val s = aggregates.getMonthlySummary(a["year"]!!.jsonPrimitive.int, a["month"]!!.jsonPrimitive.int)
            buildJsonObject {
                put("totalExpenses", s.totalExpenses)
                put("totalIncome", s.totalIncome)
                put("salaryAmount", s.salaryAmount)
                put("byCategory", mapJson(s.byCategory))
                put("byPaymentMethod", mapJson(s.byPaymentMethod))
                put("byAccount", mapJson(s.byAccount))
                put("incomeByAccount", mapJson(s.incomeByAccount))
                put("expenses", JsonArray(s.expenses.map { AppJson.encodeToJsonElement(Expense.serializer(), it) }))
                put("incomes", JsonArray(s.incomes.map { AppJson.encodeToJsonElement(Income.serializer(), it) }))
            }
        }
        "seedDefaults" -> {
            repos.categories.seedDefaults()
            JsonNull
        }
        "updateCategoryByName" -> {
            val cat = repos.categories.getAll().first { it.name == a["name"]!!.jsonPrimitive.content }
            repos.categories.update(merge(cat, com.mixagames666.trackerrntemp.data.Category.serializer(), a["patch"]!!.jsonObject))
            JsonNull
        }
        "saveSettings" -> {
            repos.settings.save { cur -> merge(cur, Settings.serializer(), a["patch"]!!.jsonObject) }
            JsonNull
        }
        "getSettings" -> AppJson.encodeToJsonElement(Settings.serializer(), repos.settings.get())
        "saveSalary" -> {
            val data = a["data"]!!.jsonObject
            repos.salary.save { existing -> merge(existing ?: Salary(), Salary.serializer(), data) }
            JsonNull
        }
        "clearAllData" -> {
            backup.clearAllData()
            JsonNull
        }
        "hasAnyData" -> JsonPrimitive(backup.hasAnyData())
        "exportAllData" -> {
            val exported = backup.exportAllData()
            JsonObject(exported + ("_exportDate" to JsonPrimitive("<exportDate>")))
        }
        "importAllData" -> {
            backup.importAllData(a["data"]!!.jsonObject)
            JsonNull
        }

        // ---- referencias ----
        "refExpense" -> {
            val match = a["match"]!!.jsonObject
            val found = repos.expenses.getAll().firstOrNull { e ->
                val json = AppJson.encodeToJsonElement(Expense.serializer(), e).jsonObject
                match.all { (k, v) -> json[k] == v }
            }
            refs[a["as"]!!.jsonPrimitive.content] = found?.id ?: "<no-encontrado>"
            JsonNull
        }
        "refLoan" -> {
            val match = a["match"]!!.jsonObject
            val found = repos.loans.getAll().firstOrNull { l ->
                val json = AppJson.encodeToJsonElement(Loan.serializer(), l).jsonObject
                match.all { (k, v) -> json[k] == v }
            }
            refs[a["as"]!!.jsonPrimitive.content] = found?.id ?: "<no-encontrado>"
            JsonNull
        }

        // ---- semántica JS y formatters ----
        "js" -> jsOp(a)
        "fn" -> fnOp(a)

        // ---- handlers de pantalla ----
        "saveExpense" -> {
            val editingId = a["editingId"]?.jsonPrimitive?.contentOrNull
            val taxes = (a["taxesConfig"] as? JsonObject)?.let { AppJson.decodeFromJsonElement(TaxesConfig.serializer(), it) }
            val form = a["form"]!!.decode<ExpenseForm>()
            val result = expenseService.save(form, editingId, taxes)
            if (editingId == null && result is ServiceResult.Done) {
                a["as"]?.jsonPrimitive?.contentOrNull?.let { name ->
                    // El original no devuelve el id; se toma el gasto principal recién creado (no el cargo de impuestos).
                    refs[name] = repos.expenses.getAll().last { it.parentExpenseId.isNullOrEmpty() }.id
                }
            }
            resultJson(result)
        }
        "payInstallment" -> {
            val taxes = (a["taxesConfig"] as? JsonObject)?.let { AppJson.decodeFromJsonElement(TaxesConfig.serializer(), it) }
            resultJson(loanService.payInstallment(loanFor(a), a["paymentAccount"]?.jsonPrimitive?.contentOrNull ?: "", taxes))
        }
        "saveLoan" -> {
            val editing = a["editingLoanId"]?.jsonPrimitive?.contentOrNull?.let { repos.loans.get(it) }
            val result = loanService.save(a["form"]!!.decode<LoanForm>(), editing)
            if (editing == null && result is ServiceResult.Done) {
                a["as"]?.jsonPrimitive?.contentOrNull?.let { refs[it] = repos.loans.getAll().last().id }
            }
            resultJson(result)
        }
        "deleteLoan" -> {
            loanService.delete(loanFor(a))
            JsonNull
        }
        "saveCreditCard" -> {
            val editing = a["editingCardId"]?.jsonPrimitive?.contentOrNull?.let { repos.creditCards.get(it) }
            val result = creditCardService.save(a["form"]!!.decode<CreditCardForm>(), editing)
            if (editing == null && result is ServiceResult.Done) {
                a["as"]?.jsonPrimitive?.contentOrNull?.let { refs[it] = repos.creditCards.getAll().last().id }
            }
            resultJson(result)
        }
        "investmentDeposit" -> {
            val inv = repos.investments.get(a["investmentId"]!!.jsonPrimitive.content)!!
            val taxes = (a["taxesConfig"] as? JsonObject)?.let { AppJson.decodeFromJsonElement(TaxesConfig.serializer(), it) }
            resultJson(investmentService.deposit(inv, a["sourceAccount"]?.jsonPrimitive?.contentOrNull ?: "", taxes))
        }
        "dashboard" -> {
            val data = DashboardLoader(repos, aggregates).load()
            buildJsonObject {
                put("totalLoanDebt", data.totalLoanDebt)
                put(
                    "recentTx",
                    buildJsonArray {
                        data.recentTx.forEach { tx ->
                            add(
                                buildJsonObject {
                                    put("id", tx.id)
                                    put("txType", if (tx.isExpense) "expense" else "income")
                                    put("sortKey", tx.sortKey)
                                },
                            )
                        }
                    },
                )
                put(
                    "salaryPrompt",
                    data.salaryPrompt?.let { p ->
                        buildJsonObject {
                            put("amount", p.amount)
                            put("account", p.account)
                            put("configId", p.configId)
                        }
                    } ?: JsonNull,
                )
            }
        }
        "analytics" -> {
            val period = when (a["period"]!!.jsonPrimitive.content) {
                "daily" -> AnalyticsPeriod.DAILY
                "weekly" -> AnalyticsPeriod.WEEKLY
                else -> AnalyticsPeriod.MONTHLY
            }
            val d = computeAnalytics(period, repos.expenses.getAll(), repos.income.getAll(), repos.accounts.getAll().map { it.name }.toSet(), repos.categories.getAll())
            fun slices(list: List<com.mixagames666.trackerrntemp.domain.PieSlice>) = JsonArray(
                list.map { s ->
                    buildJsonObject {
                        put("name", s.name)
                        put("population", s.population)
                        put("color", s.color)
                    }
                },
            )
            buildJsonObject {
                put("labels", strings(d.labels))
                put("expData", doubles(d.expData))
                put("incData", doubles(d.incData))
                put("tSpent", d.totalSpent)
                put("tIncome", d.totalIncome)
                put("pieCategory", slices(d.pieCategory))
                put("pieMethod", slices(d.pieMethod))
                put("byAccountExp", JsonArray(d.byAccountExpense.map { (k, v) -> buildJsonArray { add(JsonPrimitive(k)); add(JsonPrimitive(v)) } }))
                put("optCategories", strings(d.optCategories))
                put("optMethods", strings(d.optMethods))
                put("optIncomeSources", strings(d.optIncomeSources))
            }
        }
        "comparison" -> {
            val outcome = compareMonths(
                aggregates,
                a["compareP1"]!!.jsonPrimitive.content,
                a["compareP2"]!!.jsonPrimitive.content,
                a["compCategory"]?.jsonPrimitive?.contentOrNull ?: "",
                a["compMethod"]?.jsonPrimitive?.contentOrNull ?: "",
                a["compIncomeSource"]?.jsonPrimitive?.contentOrNull ?: "",
            )
            when (outcome) {
                null -> JsonNull
                is ComparisonOutcome.Invalid -> alertJson("Error", outcome.message)
                is ComparisonOutcome.Result -> outcome.value.let { r ->
                    buildJsonObject {
                        put("y1", r.y1); put("m1", r.m1); put("y2", r.y2); put("m2", r.m2)
                        put("s1Exp", r.expenses1); put("s1Inc", r.income1)
                        put("s2Exp", r.expenses2); put("s2Inc", r.income2)
                        put("diff", r.diff); put("diffPercent", r.diffPercent)
                    }
                }
            }
        }

        else -> ReportOps.execute(op, a, repos) ?: throw UnsupportedOperationException("Op sin implementar en Kotlin: $op")
    }

    private fun jsOp(a: JsonObject): JsonElement {
        val kind = a["kind"]!!.jsonPrimitive.content
        val digits = a["digits"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        return JsonArray(
            a["values"]!!.jsonArray.map { v ->
                val p = v.jsonPrimitive
                when (kind) {
                    "toFixed" -> JsonPrimitive(JsCompat.toFixed(p.double, digits))
                    "toString" -> JsonPrimitive(JsCompat.numberToString(p.double))
                    "parseFloat" -> JsCompat.parseFloat(p.content).let { if (it.isNaN()) JsonPrimitive("NaN") else JsonPrimitive(it) }
                    "parseInt" -> JsCompat.parseInt(p.content).let { if (it.isNaN()) JsonPrimitive("NaN") else JsonPrimitive(it) }
                    "round" -> JsonPrimitive(JsCompat.round(p.double))
                    else -> error("kind desconocido $kind")
                }
            },
        )
    }

    private fun fnOp(a: JsonObject): JsonElement {
        val name = a["name"]!!.jsonPrimitive.content
        val args = a["args"]?.jsonArray ?: JsonArray(emptyList())
        fun arg(i: Int): JsonElement? = args.getOrNull(i)
        fun intArg(i: Int) = num(arg(i)).toInt()
        return when (name) {
            "roundMoney" -> JsonPrimitive(roundMoney(num(arg(0))))
            "toAmountInput" -> {
                val p = arg(0) as? JsonPrimitive
                JsonPrimitive(
                    when {
                        p == null || p is JsonNull -> toAmountInput(null as Number?)
                        p.isString -> toAmountInput(p.content)
                        else -> toAmountInput(p.double)
                    },
                )
            }
            "sanitizeDecimalInput" -> JsonPrimitive(
                if (args.size > 1) sanitizeDecimalInput(arg(0)!!.jsonPrimitive.content, intArg(1)) else sanitizeDecimalInput(arg(0)!!.jsonPrimitive.content),
            )
            "sanitizeIntegerInput" -> JsonPrimitive(sanitizeIntegerInput(arg(0)!!.jsonPrimitive.content))
            "toLocalDateStr" -> JsonPrimitive(toLocalDateStr(dateArg(arg(0))!!))
            "formatMonths" -> JsonPrimitive(formatMonths(intArg(0)))
            "getMonthName" -> JsonPrimitive(getMonthName(intArg(0)))
            "getWeekNumber" -> JsonPrimitive(getWeekNumber(dateArg(arg(0))!!))
            "getDaysInMonth" -> JsonPrimitive(getDaysInMonth(intArg(0), intArg(1)))
            "daysUntil" -> daysUntil(arg(0)!!.jsonPrimitive.content)?.let { JsonPrimitive(it) } ?: JsonNull
            "nextDateForDay" -> JsonPrimitive(dateArg(arg(1))?.let { nextDateForDay(intArg(0), it) } ?: nextDateForDay(intArg(0)))
            "lastDateForDay" -> JsonPrimitive(dateArg(arg(1))?.let { lastDateForDay(intArg(0), it) } ?: lastDateForDay(intArg(0)))
            "nextDateForMonthDay" -> JsonPrimitive(
                dateArg(arg(2))?.let { nextDateForMonthDay(intArg(0), intArg(1), it) } ?: nextDateForMonthDay(intArg(0), intArg(1)),
            )
            "getToday" -> JsonPrimitive(getToday())
            "getNow" -> JsonPrimitive(getNow())
            "formatPercent" -> JsonPrimitive(formatPercent(num(arg(0))))
            else -> error("fn sin implementar: $name")
        }
    }
}

/**
 * Operaciones de reportes: ejecutan el generador real (rango del período, CSV y modelo del PDF) y devuelven
 * lo mismo que extrae run.js del CSV/HTML original (`parsePdfHtml`).
 */
object ReportOps {
    suspend fun execute(op: String, args: JsonObject, repos: Repos): JsonElement? = when (op) {
        "reportPeriod" -> {
            val period = ReportPeriod.entries.first { it.wireName == args["period"]!!.jsonPrimitive.content }
            val range = getReportPeriod(period)
            buildJsonObject {
                range.startDate?.let { put("startDate", it) }
                range.endDate?.let { put("endDate", it) }
                put("label", range.label)
                put("fileTag", range.fileTag)
            }
        }
        "generateCsv" -> {
            val (content, fileName) = buildCsv(reportData(args, repos))
            buildJsonObject {
                put("shared", true)
                put("fileName", fileName)
                put("content", content)
            }
        }
        "pdfModel" -> pdfModelJson(PdfReport.buildModel(reportData(args, repos)))
        else -> null
    }

    private suspend fun reportData(a: JsonObject, repos: Repos) = ReportData(
        expenses = repos.expenses.getAll(),
        incomes = repos.income.getAll(),
        accounts = repos.accounts.getAll(),
        accountBalances = Aggregates(repos).getAccountBalances(),
        type = ReportType.entries.first { it.wireName == a["type"]!!.jsonPrimitive.content },
        period = ReportPeriod.entries.first { it.wireName == a["period"]!!.jsonPrimitive.content },
    )

    private fun pair(a: String, b: String) = buildJsonArray { add(JsonPrimitive(a)); add(JsonPrimitive(b)) }

    private fun pdfModelJson(m: PdfModel): JsonObject = buildJsonObject {
        put("shared", true)
        put("header", pair(m.periodLabel, m.typeLabel))
        put("stats", JsonArray(m.stats.map { pair(it.label, it.value) }))
        put(
            "donuts",
            if (m.includeIncomes && m.includeExpenses) {
                buildJsonArray { add(JsonPrimitive("${m.expensePercentage}%")); add(JsonPrimitive("${m.balancePercentage}%")) }
            } else {
                JsonArray(emptyList())
            },
        )
        // Mismo orden que el HTML: primero las categorías de gasto y luego las fuentes de ingreso.
        val bars = (if (m.includeExpenses) m.categoryBars else emptyList()) + (if (m.includeIncomes) m.sourceBars else emptyList())
        put(
            "bars",
            JsonArray(
                bars.map {
                    buildJsonObject {
                        put("label", it.label); put("width", it.widthPercent); put("color", it.color); put("value", it.value)
                    }
                },
            ),
        )
        put("balances", JsonArray(m.balances.map { pair(it.label, it.value) }))
        put(
            "movements",
            JsonArray(
                m.movements.map {
                    buildJsonArray { listOf(it.date, it.detail, it.category, it.method, it.amount).forEach { s -> add(JsonPrimitive(s)) } }
                },
            ),
        )
        // Los "Sin movimientos…" / "Sin cuentas registradas" del HTML.
        put(
            "emptyMarkers",
            listOf(
                m.includeExpenses && m.categoryBars.isEmpty(),
                m.includeIncomes && m.sourceBars.isEmpty(),
                m.noAccounts,
                m.movements.isEmpty(),
            ).count { it },
        )
    }
}
