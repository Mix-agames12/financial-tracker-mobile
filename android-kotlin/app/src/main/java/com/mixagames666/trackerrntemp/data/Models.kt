@file:UseSerializers(
    LenientDoubleSerializer::class,
    LenientIntSerializer::class,
    LenientLongSerializer::class,
    LenientBooleanSerializer::class,
    LenientStringSerializer::class,
)

package com.mixagames666.trackerrntemp.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Port de src/types/index.ts. Los nombres de los campos son los mismos que en los JSON de AsyncStorage
// (también en el backup), por eso un backup exportado por la app React Native se importa tal cual.
// Los campos "ad hoc" que la app escribe sin declararlos en types/index.ts (time, tags, loanId…) son
// opcionales y tipados aquí.

/** Registro de una colección: id y `createdAt` (ISO) los asigna `add()` como en storage.ts. */
interface Stored<T : Stored<T>> {
    val id: String
    val createdAt: String?
    fun stamped(id: String, createdAt: String): T
}

@Serializable
data class Expense(
    override val id: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val category: String = "",
    val detail: String = "",
    val paymentMethod: String = "", // 'Efectivo' | 'Débito' | 'Crédito' | otro
    val accountName: String? = null,
    val cardName: String? = null,
    val isDeferred: Boolean? = null,
    val deferredMonths: Int? = null,
    val monthlyQuota: Double? = null,
    val isRecurring: Boolean? = null,
    val recurringFrequency: String? = null, // 'monthly' | 'specific' | 'yearly'
    val recurringDay: Int? = null,
    val recurringMonth: Int? = null, // 1-12, sólo en recurrentes anuales
    val parentExpenseId: String? = null, // cargo de impuestos/comisiones → id de la compra que lo originó
    val time: String? = null,
    val tags: List<String>? = null,
    val subscriptionType: String? = null,
    val isLoanPayment: Boolean? = null,
    val loanId: String? = null,
    val isInvestmentDeposit: Boolean? = null,
    val investmentId: String? = null,
    val totalCharge: Double? = null,
    override val createdAt: String? = null,
) : Stored<Expense> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class Category(
    override val id: String = "",
    val name: String = "",
    val type: String = "expense", // 'expense' | 'income'
    val color: String = "",
    val icon: String = "",
    val isCustomColor: Boolean? = null,
    override val createdAt: String? = null,
) : Stored<Category> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class Account(
    override val id: String = "",
    val name: String = "",
    val referenceNumber: String? = null,
    val accountType: String = "", // 'Ahorro' | 'Corriente'
    val bankName: String = "",
    val initialBalance: Double? = null,
    override val createdAt: String? = null,
) : Stored<Account> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class CreditCard(
    override val id: String = "",
    val name: String = "",
    val bankName: String = "",
    val cutOffDay: Int = 0, // 1-31
    val paymentDueDay: Int = 0, // 1-31
    val creditLimit: Double = 0.0,
    val currentBalance: Double = 0.0,
    override val createdAt: String? = null,
) : Stored<CreditCard> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class Loan(
    override val id: String = "",
    val name: String = "",
    val totalAmount: Double = 0.0,
    val installments: Int = 0,
    val paidInstallments: Int = 0,
    val monthlyQuota: Double = 0.0,
    val interestRate: Double = 0.0,
    val totalWithInterest: Double? = null,
    val nextPaymentDate: String? = null,
    val status: String? = null, // 'active' | 'paid'
    val cardId: String? = null, // tarjeta en la que se cargó la compra
    val sourceExpenseId: String? = null, // gasto (compra con tarjeta) que creó el préstamo
    val cardOutstanding: Double? = null, // parte de la compra que sigue cargada en la tarjeta
    override val createdAt: String? = null,
) : Stored<Loan> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class Investment(
    override val id: String = "",
    val entity: String = "",
    val monthlyDeposit: Double = 0.0,
    val totalMonths: Int = 0,
    val remainingMonths: Int = 0,
    val detail: String = "",
    val totalDeposited: Double = 0.0,
    override val createdAt: String? = null,
) : Stored<Investment> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class Salary(
    override val id: String = "",
    val amount: Double = 0.0,
    val payDateType: String = "specific", // 'specific' | 'last' | 'lastBusiness'
    val payDate: String = "",
    val recurrence: String = "Mensual", // 'Mensual' | 'Quincenal' | 'Semanal'
    val bankAccount: String? = null,
    override val createdAt: String? = null,
) : Stored<Salary> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class Income(
    override val id: String = "",
    val amount: Double = 0.0,
    val source: String = "",
    val detail: String = "",
    val bankAccount: String = "",
    val bankName: String = "",
    val date: String = "",
    val isSalary: Boolean? = null,
    override val createdAt: String? = null,
) : Stored<Income> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

@Serializable
data class TaxesConfig(
    val enabled: Boolean = false,
    val ivaRate: Double = 0.0,
    val comisionRate: Double = 0.0,
    val isdRate: Double = 0.0,
    val applyTo: List<String> = emptyList(),
)

@Serializable
data class Settings(
    override val id: String = "default",
    val appName: String = "Mi Dinero",
    val monthlyGoal: Double = 0.0,
    val theme: String = "dark", // 'light' | 'dark' | 'system'
    val salaryPromptPostponedUntil: Long? = null,
    val taxes: TaxesConfig? = null,
    val currency: String? = null, // ISO 4217, p. ej. 'USD'
    val currencyLocale: String? = null, // p. ej. 'es-EC'
    val onboardingCompleted: Boolean? = null,
    val lastBudgetAlert: String? = null, // 'YYYY-MM:near' | 'YYYY-MM:over'
    override val createdAt: String? = null,
) : Stored<Settings> {
    override fun stamped(id: String, createdAt: String) = copy(id = id, createdAt = createdAt)
}

/** Métodos de pago usados en toda la app. */
object PaymentMethod {
    const val CASH = "Efectivo"
    const val DEBIT = "Débito"
    const val CREDIT = "Crédito"
}
