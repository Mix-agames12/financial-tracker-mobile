package com.mixagames666.trackerrntemp.domain

import android.util.Log
import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Investment
import com.mixagames666.trackerrntemp.data.Loan
import com.mixagames666.trackerrntemp.data.PaymentMethod
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.jsDate
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.roundMoney
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

// Lógica de guardado que en la app React Native vive dentro de los handlers de las pantallas
// (ExpensesScreen.handleSaveExpense, LoansScreen.handlePayInstallment, IncomeScreen.handleSave*…).
// Se separa de la UI para poder probarla; mensajes, validaciones y orden de operaciones son los del original.

/** Resultado de una operación: un aviso (Alert.alert) o la operación hecha (con aviso de éxito opcional). */
sealed interface ServiceResult {
    data class Alert(val title: String, val message: String) : ServiceResult

    /** `successAlert`: texto del `Alert.alert('Éxito', …)` que el original muestra al terminar. */
    data class Done(val successAlert: String? = null) : ServiceResult
}

/** Desglose del motor de impuestos (comisión, IVA sobre la comisión, ISD sobre el principal). */
data class TaxBreakdown(
    val appliesTaxes: Boolean,
    val comision: Double,
    val iva: Double,
    val isd: Double,
    val totalTax: Double,
    val totalCharge: Double,
)

fun calcTaxes(config: TaxesConfig?, amount: Double, appliesTo: (TaxesConfig) -> Boolean): TaxBreakdown {
    var comision = 0.0
    var iva = 0.0
    var isd = 0.0
    var applies = false
    if (config != null && config.enabled && appliesTo(config)) {
        applies = true
        comision = (amount * config.comisionRate) / 100
        iva = (comision * config.ivaRate) / 100
        isd = (amount * config.isdRate) / 100
    }
    val totalTax = comision + iva + isd
    return TaxBreakdown(applies, comision, iva, isd, totalTax, amount + totalTax)
}

private fun nowHHmm(): String = Time.now().let { "%02d:%02d".format(it.hour, it.minute) }

// ===================================================================================================
// Gastos
// ===================================================================================================

/** Campos del formulario de gasto, tal como están en pantalla (textos sin interpretar). */
@Serializable
data class ExpenseForm(
    val amountStr: String = "",
    val categoryType: String = "",
    val detail: String = "",
    val paymentMethod: String = PaymentMethod.DEBIT,
    val selectedDebit: String = "",
    val selectedCredit: String = "",
    val isDeferred: Boolean = false,
    val deferredMonths: String = "",
    val dateStr: String = "",
    val timeStr: String = "",
    val tags: String = "",
    val isRecurring: Boolean = false,
    val recurringFrequency: String = "monthly",
    val recurringDay: String = "",
    val recurringDate: String = "", // cobro anual (YYYY-MM-DD)
    val subscriptionType: String = "",
)

class ExpenseService(
    private val repos: Repos,
    private val aggregates: Aggregates,
    private val cardPurchases: CardPurchases,
) {
    /** `handleSaveExpense`: valida, aplica impuestos, comprueba cupo/saldo/balance y guarda (alta o edición). */
    suspend fun save(form: ExpenseForm, editingId: String?, taxesConfig: TaxesConfig?): ServiceResult {
        val creditCards = repos.creditCards.getAll()
        val accounts = repos.accounts.getAll()
        val selectedExpense = if (editingId != null) repos.expenses.get(editingId) else null

        val amount = JsCompat.parseFloat(form.amountStr.replace(',', '.'))
        if (amount.isNaN() || amount <= 0) return ServiceResult.Alert("Error", "Ingresa un monto válido")
        if (form.categoryType.isEmpty()) return ServiceResult.Alert("Error", "Selecciona una categoría")
        if (form.detail.trim().isEmpty()) return ServiceResult.Alert("Error", "Ingresa el detalle del gasto")

        val taxes = calcTaxes(taxesConfig, amount) { it.applyTo.contains("Gastos:${form.categoryType}") }
        val totalCharge = taxes.totalCharge

        // Recurrente anual: día y mes de la fecha de cobro elegida (por defecto, la del gasto).
        val annualParts = form.recurringDate.ifEmpty { form.dateStr.ifEmpty { getToday() } }.split('-').map { JsCompat.number(it) }
        val annualMonth = annualParts.getOrNull(1)?.takeIf { it.isFinite() }?.toInt()
        val annualDay = annualParts.getOrNull(2)?.takeIf { it.isFinite() }?.toInt()

        val pm = form.paymentMethod
        val payload = Expense(
            amount = JsCompat.round(amount * 100) / 100,
            category = form.categoryType,
            detail = form.detail.trim(),
            paymentMethod = pm,
            accountName = if (pm == PaymentMethod.DEBIT || pm == PaymentMethod.CASH) form.selectedDebit else "",
            cardName = if (pm == PaymentMethod.CREDIT) form.selectedCredit else "",
            isDeferred = if (pm == PaymentMethod.CREDIT) form.isDeferred else false,
            deferredMonths = if (pm == PaymentMethod.CREDIT && form.isDeferred) JsCompat.parseIntOrZero(form.deferredMonths) else 0,
            date = form.dateStr.ifEmpty { getToday() },
            time = form.timeStr,
            tags = form.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            isRecurring = form.isRecurring,
            recurringFrequency = if (form.isRecurring) form.recurringFrequency else null,
            recurringDay = when {
                !form.isRecurring -> null
                form.recurringFrequency == "specific" -> min(31, max(1, JsCompat.parseIntOrZero(form.recurringDay).let { if (it == 0) 1 else it }))
                form.recurringFrequency == "yearly" -> annualDay
                else -> null
            },
            recurringMonth = if (form.isRecurring && form.recurringFrequency == "yearly") annualMonth else null,
            subscriptionType = if (form.categoryType == "Suscripción") form.subscriptionType else "",
        )

        try {
            if (payload.paymentMethod == PaymentMethod.CREDIT && !payload.cardName.isNullOrEmpty()) {
                val card = creditCards.firstOrNull { it.name == payload.cardName }
                if (card != null && card.creditLimit > 0) {
                    var balanceAfter = card.currentBalance + totalCharge
                    var available = card.creditLimit - card.currentBalance
                    // Al editar una compra de esta misma tarjeta, su monto anterior ya está en el saldo.
                    if (editingId != null && selectedExpense?.paymentMethod == PaymentMethod.CREDIT && selectedExpense.cardName == card.name) {
                        balanceAfter = card.currentBalance - selectedExpense.amount + payload.amount
                        available += selectedExpense.amount
                    }
                    if (balanceAfter > card.creditLimit) {
                        return ServiceResult.Alert("Cupo excedido", "El gasto supera el límite. Disponible estimado: ${formatCurrency(max(0.0, available))}")
                    }
                }
            } else if ((payload.paymentMethod == PaymentMethod.DEBIT || payload.paymentMethod == PaymentMethod.CASH) && !payload.accountName.isNullOrEmpty()) {
                val accBals = aggregates.getAccountBalances()
                val acc = accounts.firstOrNull { it.name == payload.accountName }
                if (acc != null) {
                    var balanceAfter = (accBals[acc.id] ?: 0.0).orZero()
                    if (editingId != null && selectedExpense != null) balanceAfter += selectedExpense.amount
                    if (balanceAfter < totalCharge) {
                        return ServiceResult.Alert(
                            "Saldo Insuficiente",
                            "La cuenta ${payload.accountName} no tiene fondos suficientes. Disponible estimado: ${formatCurrency(balanceAfter)}",
                        )
                    }
                }
            }

            val chargeToDeductGlobal = if (editingId != null) payload.amount else totalCharge
            if (payload.paymentMethod != PaymentMethod.CREDIT) {
                val tb = aggregates.getTotalBalance()
                var globalAfter = tb.balance
                if (editingId != null && selectedExpense != null) globalAfter += selectedExpense.amount
                if (globalAfter < chargeToDeductGlobal) {
                    return ServiceResult.Alert(
                        "Balance Negativo",
                        "No se permite un balance total negativo. Disponible global estimado: ${formatCurrency(globalAfter)}",
                    )
                }
            }

            // Compras con tarjeta: saldo de la tarjeta y préstamo quedan vinculados al gasto.
            val card = if (payload.paymentMethod == PaymentMethod.CREDIT && !payload.cardName.isNullOrEmpty()) {
                creditCards.firstOrNull { it.name == payload.cardName }
            } else {
                null
            }
            val nextPaymentDate = if (card != null) calculateCreditCardNextPaymentDate(payload.date, card.cutOffDay, card.paymentDueDay) else ""

            if (editingId != null) {
                val before = repos.expenses.get(editingId)
                // `{ id, ...payload }` sobre el registro guardado: se conservan createdAt, parentExpenseId, loanId…
                val updated = repos.expenses.update(
                    (before ?: throw NoSuchElementException("Item $editingId not found in expenses")).let { old ->
                        payload.copy(
                            id = old.id,
                            createdAt = old.createdAt,
                            monthlyQuota = old.monthlyQuota,
                            parentExpenseId = old.parentExpenseId,
                            isLoanPayment = old.isLoanPayment,
                            loanId = old.loanId,
                            isInvestmentDeposit = old.isInvestmentDeposit,
                            investmentId = old.investmentId,
                            totalCharge = old.totalCharge,
                        )
                    },
                )
                cardPurchases.updateCardPurchase(before, updated, card, nextPaymentDate)
            } else {
                val expense = repos.expenses.add(payload)
                if (taxes.appliesTaxes && taxes.totalTax > 0) {
                    repos.expenses.add(
                        payload.copy(
                            detail = "${payload.detail}${TAX_DETAIL_SUFFIX}",
                            amount = roundMoney(taxes.totalTax),
                            parentExpenseId = expense.id,
                        ),
                    )
                }
                if (card != null) {
                    cardPurchases.registerCardPurchase(expense, card, totalCharge, nextPaymentDate)
                }
            }
            return ServiceResult.Done()
        } catch (e: Exception) {
            Log.e("ExpenseService", "No se pudo guardar el gasto", e)
            return ServiceResult.Alert("Error", "No se pudo guardar el gasto")
        }
    }
}

// ===================================================================================================
// Préstamos
// ===================================================================================================

@Serializable
data class LoanForm(
    val name: String = "",
    val totalAmount: String = "",
    val interestRate: String = "",
    val installments: String = "",
    val paidInstallments: String = "",
    val nextPaymentDate: String = "",
)

class LoanService(
    private val repos: Repos,
    private val aggregates: Aggregates,
    private val cardPurchases: CardPurchases,
) {
    /** `handleSaveLoan` */
    suspend fun save(form: LoanForm, editing: Loan?): ServiceResult {
        val tAmount = JsCompat.parseFloat(form.totalAmount.replace(',', '.'))
        val iRate = JsCompat.parseFloat(form.interestRate.replace(',', '.')).orZero()
        val inst = JsCompat.parseInt(form.installments)

        if (form.name.trim().isEmpty() || tAmount.isNaN() || tAmount <= 0 || inst.isNaN() || inst <= 0) {
            return ServiceResult.Alert("Error", "Completa los campos obligatorios correctamente")
        }

        val totalWithInterest = tAmount * (1 + iRate / 100)
        val installmentAmount = totalWithInterest / inst
        val paidInst = JsCompat.parseInt(form.paidInstallments).orZero()

        if (paidInst < 0 || paidInst > inst) {
            return ServiceResult.Alert("Error", "Las cuotas pagadas no pueden ser negativas ni superan el total de cuotas")
        }

        val instInt = JsCompat.toIntClamped(inst)
        val paidInt = JsCompat.toIntClamped(paidInst)
        val base = editing ?: Loan()
        val payload = base.copy(
            name = form.name.trim(),
            totalAmount = tAmount,
            interestRate = iRate,
            installments = instInt,
            paidInstallments = paidInt,
            monthlyQuota = JsCompat.round(installmentAmount * 100) / 100,
            // Campos dinámicos que se conservan explícitamente, como en la lógica original
            totalWithInterest = roundMoney(totalWithInterest),
            nextPaymentDate = form.nextPaymentDate,
            status = if (paidInst >= inst) "paid" else "active",
        )

        return try {
            if (editing != null) {
                // Préstamo de una compra con tarjeta: la tarjeta pasa a reflejar lo que queda por pagar.
                val cardOutstanding = cardPurchases.syncLoanEditWithCard(editing, payload)
                repos.loans.update(if (cardOutstanding == null) payload else payload.copy(cardOutstanding = cardOutstanding))
            } else {
                repos.loans.add(payload)
            }
            ServiceResult.Done()
        } catch (e: Exception) {
            Log.e("LoanService", "No se pudo guardar el préstamo", e)
            ServiceResult.Alert("Error", "No se pudo guardar el préstamo")
        }
    }

    /** Eliminar un préstamo: lo que seguía pendiente de una compra con tarjeta se quita del saldo. */
    suspend fun delete(loan: Loan) {
        cardPurchases.releaseLoanFromCard(loan)
        repos.loans.delete(loan.id)
    }

    /** `handlePayInstallment`: descuenta la cuota de una cuenta, registra el gasto y mueve el saldo de la tarjeta. */
    suspend fun payInstallment(loan: Loan, paymentAccount: String, taxesConfig: TaxesConfig?): ServiceResult {
        if (paymentAccount.isEmpty()) return ServiceResult.Alert("Error", "Debes seleccionar una cuenta origen para pagar la cuota")
        val acc = repos.accounts.getAll().firstOrNull { it.name == paymentAccount }
            ?: return ServiceResult.Alert("Error", "Cuenta inválida")
        val accountBalances = aggregates.getAccountBalances()

        val amount = JsCompat.round(loan.monthlyQuota * 100) / 100
        val taxes = calcTaxes(taxesConfig, amount) { it.applyTo.contains("Préstamos") }
        val totalCharge = taxes.totalCharge

        val tb = aggregates.getTotalBalance()
        if (tb.balance < totalCharge) {
            return ServiceResult.Alert("Balance Negativo", "No se permite un balance total negativo. Disponible global: ${formatCurrency(tb.balance)}")
        }

        val accountBalance = accountBalances[acc.id] ?: 0.0
        if (accountBalance < totalCharge) {
            return ServiceResult.Alert(
                "Saldo Insuficiente",
                "La cuenta $paymentAccount no tiene fondos suficientes para cubrir la cuota de ${formatCurrency(totalCharge)}. Disponible: ${formatCurrency(accountBalance)}",
            )
        }

        val newPaidCount = loan.paidInstallments + 1
        val isNowPaid = newPaidCount >= loan.installments
        val newNextDate = advancePaymentDate(loan.nextPaymentDate.orEmpty())

        return try {
            // Compra con tarjeta: la cuota pagada deja de estar pendiente en la tarjeta.
            val cardOutstanding = cardPurchases.applyLoanPaymentToCard(loan, amount, isNowPaid)
            var updatedLoan = loan.copy(
                paidInstallments = newPaidCount,
                status = if (isNowPaid) "paid" else "active",
                nextPaymentDate = if (isNowPaid) "" else if (!loan.nextPaymentDate.isNullOrEmpty()) newNextDate else "",
            )
            if (cardOutstanding != null) updatedLoan = updatedLoan.copy(cardOutstanding = cardOutstanding)
            repos.loans.update(updatedLoan)

            repos.expenses.add(
                Expense(
                    amount = amount,
                    category = "Préstamo",
                    detail = "Cuota $newPaidCount/${loan.installments} — ${loan.name}",
                    accountName = paymentAccount,
                    paymentMethod = PaymentMethod.DEBIT,
                    date = getToday(),
                    isLoanPayment = true,
                    loanId = loan.id,
                    time = nowHHmm(),
                    tags = listOf("préstamo", "cuota"),
                ),
            )

            if (taxes.appliesTaxes && taxes.totalTax > 0) {
                repos.expenses.add(
                    Expense(
                        amount = JsCompat.round(taxes.totalTax * 100) / 100,
                        category = "Otros",
                        detail = "Cuota $newPaidCount/${loan.installments} (Retenciones) — ${loan.name}",
                        accountName = paymentAccount,
                        paymentMethod = PaymentMethod.DEBIT,
                        date = getToday(),
                        time = nowHHmm(),
                        tags = listOf("impuestos", "préstamo"),
                    ),
                )
            }

            ServiceResult.Done(
                if (!loan.cardId.isNullOrEmpty()) {
                    "Cuota de ${formatCurrency(loan.monthlyQuota)} descontada del balance general y del saldo de la tarjeta"
                } else {
                    "Cuota de ${formatCurrency(loan.monthlyQuota)} descontada del balance general"
                },
            )
        } catch (e: Exception) {
            Log.e("LoanService", "No se pudo registrar la cuota", e)
            ServiceResult.Alert("Error", "No se pudo registrar la cuota")
        }
    }

    /** Siguiente mes con el mismo día (o el último del mes si es más corto). */
    private fun advancePaymentDate(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        val parts = dateStr.split('-').map { JsCompat.number(it) }
        val y = parts.getOrNull(0) ?: return ""
        val m = parts.getOrNull(1) ?: return ""
        val d = parts.getOrNull(2) ?: return ""
        if (!y.isFinite() || y == 0.0 || !m.isFinite() || m == 0.0 || !d.isFinite() || d == 0.0) return ""
        var newM = m.toInt() + 1
        var newY = y.toInt()
        if (newM > 12) {
            newM = 1
            newY++
        }
        val maxDays = jsDate(newY, newM, 0).dayOfMonth
        val newD = min(d.toInt(), maxDays)
        return "$newY-${newM.toString().padStart(2, '0')}-${newD.toString().padStart(2, '0')}"
    }
}

// ===================================================================================================
// Tarjetas de crédito e inversiones (IncomeScreen)
// ===================================================================================================

@Serializable
data class CreditCardForm(
    val name: String = "",
    val bankName: String = "",
    val creditLimit: String = "",
    val available: String = "", // el formulario pide el cupo disponible; internamente se guarda lo usado
    val cutOffDay: String = "",
    val paymentDay: String = "",
)

class CreditCardService(private val repos: Repos) {
    /** `handleSaveCreditCard` */
    suspend fun save(form: CreditCardForm, editing: CreditCard?): ServiceResult {
        if (form.name.trim().isEmpty() || form.bankName.trim().isEmpty()) return ServiceResult.Alert("Error", "Nombre y banco son obligatorios")
        val limit = JsCompat.parseFloat(form.creditLimit).orZero()
        val available = JsCompat.parseFloat(form.available)
        if (limit <= 0) return ServiceResult.Alert("Error", "Ingresa el límite de crédito de la tarjeta")
        if (available.isNaN() || available < 0) return ServiceResult.Alert("Error", "Ingresa el cupo disponible (0 si ya no te queda cupo)")
        if (available > limit) return ServiceResult.Alert("Error", "El cupo disponible no puede ser mayor al límite de crédito")
        val cutOff = JsCompat.parseIntOrZero(form.cutOffDay)
        val dueDay = JsCompat.parseIntOrZero(form.paymentDay)
        if (cutOff > 31 || dueDay > 31) return ServiceResult.Alert("Error", "Los días de corte y de pago deben estar entre 1 y 31")

        val payload = (editing ?: CreditCard()).copy(
            name = form.name.trim(),
            bankName = form.bankName.trim(),
            cutOffDay = cutOff,
            paymentDueDay = dueDay,
            creditLimit = roundMoney(limit),
            // Se guarda lo usado (deuda): compras, pagos y validaciones trabajan con ese valor.
            currentBalance = roundMoney(limit - available),
        )
        if (editing != null) repos.creditCards.update(payload) else repos.creditCards.add(payload)
        return ServiceResult.Done()
    }
}

class InvestmentService(private val repos: Repos, private val aggregates: Aggregates) {
    /** `handleInvestmentDeposit`: el depósito mensual sale de una cuenta y se registra como gasto. */
    suspend fun deposit(inv: Investment, sourceAccount: String, taxesConfig: TaxesConfig?): ServiceResult {
        val acc = repos.accounts.getAll().firstOrNull { it.name == sourceAccount }
            ?: return ServiceResult.Alert("Error", "Cuenta origen inválida")
        val accountBalances = aggregates.getAccountBalances()

        val amount = inv.monthlyDeposit
        val taxes = calcTaxes(taxesConfig, amount) { it.applyTo.contains("Inversiones") }
        val totalCharge = taxes.totalCharge

        val balance = accountBalances[acc.id] ?: 0.0
        if (balance < totalCharge) {
            return ServiceResult.Alert(
                "Saldo Insuficiente",
                "La cuenta $sourceAccount no tiene fondos suficientes para descontar ${formatCurrency(totalCharge)}. Disponible: ${formatCurrency(balance)}",
            )
        }

        repos.expenses.add(
            Expense(
                amount = inv.monthlyDeposit,
                category = "Inversión",
                detail = "Depósito mensual — ${inv.entity}",
                accountName = sourceAccount,
                paymentMethod = PaymentMethod.DEBIT,
                date = getToday(),
                isInvestmentDeposit = true,
                investmentId = inv.id,
                time = nowHHmm(),
                tags = listOf("inversión", "depósito"),
            ),
        )

        if (taxes.appliesTaxes && taxes.totalTax > 0) {
            repos.expenses.add(
                Expense(
                    amount = JsCompat.round(taxes.totalTax * 100) / 100,
                    category = "Otros",
                    detail = "Depósito mensual (Retenciones) — ${inv.entity}",
                    accountName = sourceAccount,
                    paymentMethod = PaymentMethod.DEBIT,
                    date = getToday(),
                    time = nowHHmm(),
                    tags = listOf("impuestos", "inversión"),
                ),
            )
        }

        repos.investments.update(
            inv.copy(
                totalDeposited = roundMoney(inv.totalDeposited.orZero() + inv.monthlyDeposit),
                remainingMonths = max(0, inv.remainingMonths - 1),
            ),
        )

        return ServiceResult.Done("Depósito de ${formatCurrency(inv.monthlyDeposit)} registrado como gasto")
    }
}
