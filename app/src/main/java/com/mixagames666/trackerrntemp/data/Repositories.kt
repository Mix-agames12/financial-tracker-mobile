package com.mixagames666.trackerrntemp.data

import android.util.Log
import com.mixagames666.trackerrntemp.util.Time
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

// Port de la capa de repositorios de src/db/storage.ts (getAll/get/add/update/delete + consultas).

private const val TAG = "StoreCollection"

private const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"

/** `Date.now().toString() + Math.random().toString(36).substring(7)` */
fun generateId(): String {
    val suffix = buildString { repeat(5) { append(BASE36[(Math.random() * BASE36.length).toInt().coerceIn(0, BASE36.length - 1)]) } }
    return Time.nowMillis().toString() + suffix
}

/** Una colección persistida (equivale a una clave de AsyncStorage con un arreglo JSON). */
class StoreCollection<T : Stored<T>>(
    private val store: JsonStore,
    val name: String,
    private val serializer: KSerializer<T>,
) {
    private val listSerializer = ListSerializer(serializer)

    /**
     * Registros que no se pudieron interpretar: no se muestran, pero se escriben tal cual en el siguiente
     * guardado para no perder datos en silencio (JSON.parse del original los conservaba siempre).
     */
    @Volatile
    private var unreadable: List<JsonElement> = emptyList()

    private suspend fun load(): MutableList<T> {
        val text = store.readText(name) ?: return mutableListOf()
        return try {
            val element = AppJson.parseToJsonElement(text)
            if (element !is JsonArray) return mutableListOf()
            val items = ArrayList<T>(element.size)
            val skipped = ArrayList<JsonElement>()
            for (item in element) {
                try {
                    items.add(AppJson.decodeFromJsonElement(serializer, item))
                } catch (e: Exception) {
                    Log.w(TAG, "Registro ilegible en $name, se conserva sin cambios", e)
                    skipped.add(item)
                }
            }
            unreadable = skipped
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $name", e)
            mutableListOf()
        }
    }

    private suspend fun persist(items: List<T>) {
        val kept = unreadable
        val text = if (kept.isEmpty()) {
            AppJson.encodeToString(listSerializer, items)
        } else {
            val array = JsonArray(items.map { AppJson.encodeToJsonElement(serializer, it) } + kept)
            AppJson.encodeToString(JsonArray.serializer(), array)
        }
        store.writeText(name, text)
    }

    /** Asigna id (si falta) y `createdAt` como hace `add()` de storage.ts. */
    fun stampNew(item: T): T = item.stamped(item.id.ifEmpty { generateId() }, Time.isoNow())

    suspend fun getAll(): List<T> = load()

    suspend fun get(id: String): T? = load().firstOrNull { it.id == id }

    suspend fun saveAll(items: List<T>) = store.lockFor(name).withLock { persist(items) }

    suspend fun add(data: T): T = store.lockFor(name).withLock {
        val all = load()
        val created = stampNew(data)
        all.add(created)
        persist(all)
        created
    }

    /** Sustituye el registro de ese id; igual que el original, falla si no existe. */
    suspend fun update(data: T): T = store.lockFor(name).withLock {
        val all = load()
        val index = all.indexOfFirst { it.id == data.id }
        if (index == -1) throw NoSuchElementException("Item ${data.id} not found in $name")
        all[index] = data
        persist(all)
        data
    }

    suspend fun delete(id: String) = store.lockFor(name).withLock {
        persist(load().filter { it.id != id })
    }

    /** Lectura-modificación-escritura atómica; sólo escribe si la lista cambió. */
    suspend fun <R> mutate(block: (MutableList<T>) -> R): R = store.lockFor(name).withLock {
        val all = load()
        val before = all.toList()
        val result = block(all)
        if (all != before) persist(all)
        result
    }
}

class ExpenseRepo(private val col: StoreCollection<Expense>) {
    suspend fun getAll() = col.getAll()
    suspend fun get(id: String) = col.get(id)
    suspend fun add(data: Expense) = col.add(data)
    suspend fun update(data: Expense) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)
    suspend fun getByDateRange(start: String, end: String) = col.getAll().filter { it.date >= start && it.date <= end }
}

class IncomeRepo(private val col: StoreCollection<Income>) {
    suspend fun getAll() = col.getAll()
    suspend fun get(id: String) = col.get(id)
    suspend fun add(data: Income) = col.add(data)
    suspend fun update(data: Income) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)
    suspend fun getByDateRange(start: String, end: String) = col.getAll().filter { it.date >= start && it.date <= end }
}

class LoanRepo(private val col: StoreCollection<Loan>) {
    suspend fun getAll() = col.getAll()
    suspend fun get(id: String) = col.get(id)
    suspend fun add(data: Loan) = col.add(data)
    suspend fun update(data: Loan) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)
    suspend fun getActive() = col.getAll().filter { it.status != "paid" }
}

class CreditCardRepo(private val col: StoreCollection<CreditCard>) {
    suspend fun getAll() = col.getAll()
    suspend fun get(id: String) = col.get(id)
    suspend fun add(data: CreditCard) = col.add(data)
    suspend fun update(data: CreditCard) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)
}

class AccountRepo(private val col: StoreCollection<Account>) {
    suspend fun getAll() = col.getAll()
    suspend fun get(id: String) = col.get(id)
    suspend fun add(data: Account) = col.add(data)
    suspend fun update(data: Account) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)
}

class InvestmentRepo(private val col: StoreCollection<Investment>) {
    suspend fun getAll() = col.getAll()
    suspend fun get(id: String) = col.get(id)
    suspend fun add(data: Investment) = col.add(data)
    suspend fun update(data: Investment) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)
}

val DEFAULT_EXPENSE_CATEGORIES: List<Category> = listOf(
    Category(name = "Alimentación", icon = "restaurant", color = "#f59e0b", type = "expense"),
    Category(name = "Transporte", icon = "car", color = "#3b82f6", type = "expense"),
    Category(name = "Vivienda", icon = "home", color = "#8b5cf6", type = "expense"),
    Category(name = "Salud", icon = "heart", color = "#ef4444", type = "expense"),
    Category(name = "Entretenimiento", icon = "film", color = "#ec4899", type = "expense"),
    Category(name = "Educación", icon = "school", color = "#06b6d4", type = "expense"),
    Category(name = "Ropa", icon = "shirt", color = "#f97316", type = "expense"),
    Category(name = "Tecnología", icon = "desktop", color = "#6366f1", type = "expense"),
    Category(name = "Servicios", icon = "receipt", color = "#14b8a6", type = "expense"),
    Category(name = "Suscripción", icon = "card", color = "#a855f7", type = "expense"),
    Category(name = "Otros", icon = "ellipsis-horizontal", color = "#64748b", type = "expense"),
)

class CategoryRepo(private val col: StoreCollection<Category>) {
    suspend fun getAll() = col.getAll()
    suspend fun add(data: Category) = col.add(data)
    suspend fun update(data: Category) = col.update(data)
    suspend fun delete(id: String) = col.delete(id)

    /**
     * Sincroniza icono/color de las categorías por defecto sin pisar los colores que el usuario
     * personalizó, y agrega las que falten.
     */
    suspend fun seedDefaults() {
        col.mutate { all ->
            val existing = all.toList()
            val updated = existing.map { cat ->
                val def = DEFAULT_EXPENSE_CATEGORIES.firstOrNull { it.name == cat.name } ?: return@map cat
                val color = if (cat.isCustomColor == true) cat.color else def.color
                if (cat.color != color || cat.icon != def.icon) cat.copy(color = color, icon = def.icon) else cat
            }
            all.clear()
            all.addAll(updated)
            DEFAULT_EXPENSE_CATEGORIES
                .filter { d -> existing.none { it.name == d.name } }
                .forEach { all.add(col.stampNew(it)) }
        }
    }
}

class SalaryRepo(private val col: StoreCollection<Salary>) {
    suspend fun getAll() = col.getAll()
    suspend fun delete(id: String) = col.delete(id)

    /** Actualiza el sueldo existente o crea el primero; `build` recibe el actual (si lo hay). */
    suspend fun save(build: (Salary?) -> Salary): Salary = col.mutate { all ->
        if (all.isNotEmpty()) {
            val updated = build(all[0])
            val index = all.indexOfFirst { it.id == updated.id }
            if (index == -1) throw NoSuchElementException("Item ${updated.id} not found in salary")
            all[index] = updated
            updated
        } else {
            val created = col.stampNew(build(null))
            all.add(created)
            created
        }
    }
}

class SettingsRepo(private val col: StoreCollection<Settings>) {
    suspend fun get(): Settings = col.getAll().firstOrNull() ?: Settings()

    /** `SettingsRepo.save(partial)`: aplica [transform] sobre los ajustes guardados (o los de fábrica). */
    suspend fun save(transform: (Settings) -> Settings): Settings = col.mutate { all ->
        if (all.isNotEmpty()) {
            val updated = transform(all[0])
            all[0] = updated
            updated
        } else {
            val created = col.stampNew(transform(Settings()))
            all.add(created)
            created
        }
    }
}

/** Nombres de las colecciones, en el orden de `ALL_STORES` de storage.ts. */
val ALL_STORES = listOf(
    "salary", "expenses", "income", "loans", "creditCards", "reminders", "accounts", "categories", "investments", "settings",
)

/** Todos los repositorios de la app sobre un mismo almacén. */
class Repos(val store: JsonStore) {
    val salary = SalaryRepo(StoreCollection(store, "salary", Salary.serializer()))
    val expenses = ExpenseRepo(StoreCollection(store, "expenses", Expense.serializer()))
    val income = IncomeRepo(StoreCollection(store, "income", Income.serializer()))
    val loans = LoanRepo(StoreCollection(store, "loans", Loan.serializer()))
    val creditCards = CreditCardRepo(StoreCollection(store, "creditCards", CreditCard.serializer()))
    val accounts = AccountRepo(StoreCollection(store, "accounts", Account.serializer()))
    val categories = CategoryRepo(StoreCollection(store, "categories", Category.serializer()))
    val investments = InvestmentRepo(StoreCollection(store, "investments", Investment.serializer()))
    val settings = SettingsRepo(StoreCollection(store, "settings", Settings.serializer()))
}
