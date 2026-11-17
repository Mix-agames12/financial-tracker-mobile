package com.mixagames666.trackerrntemp.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.PaymentMethod
import com.mixagames666.trackerrntemp.domain.ExpenseForm
import com.mixagames666.trackerrntemp.domain.ServiceResult
import com.mixagames666.trackerrntemp.domain.calcTaxes
import com.mixagames666.trackerrntemp.ui.components.AlertButton
import com.mixagames666.trackerrntemp.ui.components.AlertButtonStyle
import com.mixagames666.trackerrntemp.ui.components.Alerts
import com.mixagames666.trackerrntemp.ui.components.AppButton
import com.mixagames666.trackerrntemp.ui.components.AppCard
import com.mixagames666.trackerrntemp.ui.components.AppTextField
import com.mixagames666.trackerrntemp.ui.components.BottomSheet
import com.mixagames666.trackerrntemp.ui.components.ButtonVariant
import com.mixagames666.trackerrntemp.ui.components.Chip
import com.mixagames666.trackerrntemp.ui.components.ChipFlow
import com.mixagames666.trackerrntemp.ui.components.ChipRow
import com.mixagames666.trackerrntemp.ui.components.ChipScrollRow
import com.mixagames666.trackerrntemp.ui.components.DatePickerSheet
import com.mixagames666.trackerrntemp.ui.components.DetailGrid
import com.mixagames666.trackerrntemp.ui.components.DetailItem
import com.mixagames666.trackerrntemp.ui.components.FieldKeyboard
import com.mixagames666.trackerrntemp.ui.components.FieldLabel
import com.mixagames666.trackerrntemp.ui.components.Ionicon
import com.mixagames666.trackerrntemp.ui.components.TaxBreakdownBox
import com.mixagames666.trackerrntemp.ui.components.ToastManager
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.screens.appViewModel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.ui.theme.hexColorWithAlpha
import com.mixagames666.trackerrntemp.ui.theme.topInset
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.getNow
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.nextDateForMonthDay
import com.mixagames666.trackerrntemp.util.toAmountInput
import kotlinx.coroutines.launch

// Port de screens/ExpensesScreen.tsx

private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun describeRecurrence(exp: Expense): String {
    val month = exp.recurringMonth ?: 0
    val day = exp.recurringDay ?: 0
    if (exp.recurringFrequency == "yearly" && month != 0 && day != 0) {
        return "Anual · $day ${MONTHS_SHORT.getOrElse(month - 1) { "undefined" }}"
    }
    if (exp.recurringFrequency == "specific" && day != 0) return "Día $day"
    return "Mensual"
}

/** Estado del formulario de gasto: los mismos campos que los useState del original. */
private class ExpenseFormState {
    var editingId by mutableStateOf<String?>(null)
    var amountStr by mutableStateOf("")
    var categoryType by mutableStateOf("")
    var detail by mutableStateOf("")
    var paymentMethod by mutableStateOf(PaymentMethod.DEBIT)
    var selectedDebit by mutableStateOf("")
    var selectedCredit by mutableStateOf("")
    var isDeferred by mutableStateOf(false)
    var deferredMonths by mutableStateOf("")
    var dateStr by mutableStateOf(getToday())
    var timeStr by mutableStateOf(getNow())
    var tags by mutableStateOf("")
    var isRecurring by mutableStateOf(false)
    var recurringFrequency by mutableStateOf("monthly")
    var recurringDay by mutableStateOf("")
    var recurringDate by mutableStateOf("") // cobro anual (YYYY-MM-DD)
    var subscriptionType by mutableStateOf("")

    fun openNew() {
        editingId = null
        amountStr = ""
        categoryType = ""
        detail = ""
        paymentMethod = ExpenseFormMemory.paymentMethod
        selectedDebit = ExpenseFormMemory.debitAccount
        selectedCredit = ExpenseFormMemory.creditAccount
        isDeferred = false
        deferredMonths = ""
        dateStr = getToday()
        timeStr = getNow()
        tags = ""
        isRecurring = false
        recurringFrequency = "monthly"
        recurringDay = ""
        recurringDate = ""
        subscriptionType = ""
    }

    fun openEdit(exp: Expense) {
        editingId = exp.id
        amountStr = toAmountInput(exp.amount)
        categoryType = exp.category
        detail = exp.detail
        paymentMethod = exp.paymentMethod.ifEmpty { PaymentMethod.CASH }
        selectedDebit = exp.accountName.orEmpty()
        selectedCredit = exp.cardName.orEmpty()
        isDeferred = exp.isDeferred ?: false
        deferredMonths = exp.deferredMonths?.toString().orEmpty()
        dateStr = exp.date
        timeStr = exp.time?.ifEmpty { null } ?: getNow()
        tags = exp.tags?.joinToString(", ").orEmpty()
        isRecurring = exp.isRecurring ?: false
        recurringFrequency = exp.recurringFrequency?.ifEmpty { null } ?: "monthly"
        recurringDay = exp.recurringDay?.toString().orEmpty()
        recurringDate = if (exp.recurringFrequency == "yearly" && (exp.recurringMonth ?: 0) != 0 && (exp.recurringDay ?: 0) != 0) {
            nextDateForMonthDay(exp.recurringMonth!!, exp.recurringDay!!)
        } else {
            ""
        }
        subscriptionType = exp.subscriptionType.orEmpty()
    }

    fun toForm() = ExpenseForm(
        amountStr = amountStr, categoryType = categoryType, detail = detail, paymentMethod = paymentMethod,
        selectedDebit = selectedDebit, selectedCredit = selectedCredit, isDeferred = isDeferred, deferredMonths = deferredMonths,
        dateStr = dateStr, timeStr = timeStr, tags = tags, isRecurring = isRecurring, recurringFrequency = recurringFrequency,
        recurringDay = recurringDay, recurringDate = recurringDate, subscriptionType = subscriptionType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(container: AppContainer) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val vm = appViewModel(container) { ExpensesViewModel(it) }
    val ui by vm.state.collectAsState()

    // Filtros: borrador en la hoja; sólo se aplica al pulsar "Aplicar filtros".
    var isFilterOpen by remember { mutableStateOf(false) }
    var filterFrom by remember { mutableStateOf("") }
    var filterTo by remember { mutableStateOf("") }
    var filterCats by remember { mutableStateOf<List<String>>(emptyList()) }
    var filterMethod by remember { mutableStateOf("") }
    val activeFilters = ui.appliedFilters.isActive

    var isDetailOpen by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var isFormOpen by remember { mutableStateOf(false) }
    var isDatePickerOpen by remember { mutableStateOf(false) }
    val form = remember { ExpenseFormState() }

    // useFocusEffect(…, [appliedFilters])
    LaunchedEffect(ui.appliedFilters) { vm.load() }

    fun openFilters() {
        // El borrador parte de los filtros aplicados.
        filterFrom = ui.appliedFilters.from
        filterTo = ui.appliedFilters.to
        filterCats = ui.appliedFilters.categories
        filterMethod = ui.appliedFilters.method
        isFilterOpen = true
    }

    fun applyFilters() {
        val next = ExpenseFilters(filterFrom, filterTo, filterCats, filterMethod)
        if (!next.isActive) {
            Alerts.alert("Aviso", "Elige al menos un criterio: fechas, categorías o método de pago.")
            return
        }
        if (next.from.isNotEmpty() && next.to.isNotEmpty() && next.from > next.to) {
            Alerts.alert("Aviso", "La fecha \"Desde\" no puede ser posterior a \"Hasta\".")
            return
        }
        vm.setAppliedFilters(next)
        isFilterOpen = false
    }

    fun clearFilters() {
        filterFrom = ""
        filterTo = ""
        filterCats = emptyList()
        filterMethod = ""
        vm.setAppliedFilters(ExpenseFilters())
        isFilterOpen = false
    }

    fun deleteSelected() {
        val expense = selectedExpense ?: return
        val isCardPurchase = expense.paymentMethod == PaymentMethod.CREDIT && !expense.cardName.isNullOrEmpty()
        val message = if (isCardPurchase) {
            "¿Estás seguro de eliminar este gasto? Lo que siga pendiente de la compra se quitará de la tarjeta y se eliminarán su préstamo y su cargo de impuestos, si los tiene."
        } else {
            "¿Estás seguro de eliminar este gasto?"
        }
        Alerts.alert(
            "Eliminar Gasto",
            message,
            listOf(
                AlertButton("Cancelar", AlertButtonStyle.Cancel),
                AlertButton("Eliminar", AlertButtonStyle.Destructive) { vm.delete(expense.id) { isDetailOpen = false } },
            ),
        )
    }

    fun saveExpense() {
        scope.launch {
            when (val result = vm.save(form.toForm(), form.editingId)) {
                is ServiceResult.Alert -> Alerts.alert(result.title, result.message)
                is ServiceResult.Done -> {
                    isFormOpen = false
                    ToastManager.show("Gasto guardado con éxito")
                }
            }
        }
    }

    val totalSpent = remember(ui.expenses) { ui.expenses.fold(0.0) { s, e -> s + e.amount } }

    // Categorías configuradas + las que sólo aparecen en los gastos (sin color propio).
    val filterCategoryOptions = remember(ui.categories, ui.expenseCategoryNames) {
        ui.categories.map { it.name to (it.color as String?) } +
            ui.expenseCategoryNames.filter { name -> ui.categories.none { it.name == name } }.map { it to null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = topInset()),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Gastos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (activeFilters) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                        .touchableOpacity(onClick = { openFilters() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Ionicon("filter", 24, if (activeFilters) colors.primary else colors.onSurface)
                }
            }

            // Summary Card
            Box(Modifier.padding(horizontal = 16.dp)) {
                AppCard(elevated = true) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Total gastado", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(formatCurrency(totalSpent), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Transacciones", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(ui.expenses.size.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        }
                    }
                }
            }

            // List
            PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                if (ui.expenses.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Ionicon("receipt-outline", 48, colors.outline)
                        Text("Sin gastos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                        Text("Toca el botón + para registrar uno", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                    ) {
                        itemsIndexed(ui.expenses, key = { _, e -> e.id }) { index, exp ->
                            // El encabezado de fecha aparece cuando cambia el día respecto del gasto anterior.
                            val showDateHeader = index == 0 || ui.expenses[index - 1].date != exp.date
                            val cat = ui.categories.firstOrNull { it.name == exp.category }
                            val iconName = cat?.icon?.takeIf { it.isNotEmpty() } ?: "receipt"
                            val iconColor = cat?.color?.takeIf { it.isNotEmpty() }?.let { hexColor(it) } ?: colors.error
                            val iconBg = cat?.color?.takeIf { it.isNotEmpty() }?.let { hexColorWithAlpha(it, 0x20) } ?: colors.errorContainer

                            Column {
                                if (showDateHeader) {
                                    Text(
                                        formatDate(exp.date),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                    )
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .touchableOpacity(role = null) {
                                            selectedExpense = exp
                                            isDetailOpen = true
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(iconBg, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) { Ionicon(iconName, 20, iconColor) }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp, end = 8.dp),
                                    ) {
                                        Text(exp.detail, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = buildString {
                                                append(exp.category)
                                                if (exp.paymentMethod == PaymentMethod.DEBIT && !exp.accountName.isNullOrEmpty()) append(" · ${exp.accountName}")
                                                if (exp.paymentMethod == PaymentMethod.CREDIT && !exp.cardName.isNullOrEmpty()) append(" · ${exp.cardName}")
                                                if (exp.isDeferred == true) append(" · Diferido ${exp.deferredMonths}m")
                                                if (exp.isRecurring == true) append(if (exp.recurringFrequency == "yearly") " · Anual" else " · Recurrente")
                                            },
                                            fontSize = 12.sp,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("-${formatCurrency(exp.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.error)
                                        Text(exp.time.orEmpty(), fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // FAB
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .shadow(4.dp, CircleShape)
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.error, CircleShape)
                .touchableOpacity(activeOpacity = 0.8f) {
                    form.openNew()
                    isDetailOpen = false
                    isFormOpen = true
                },
            contentAlignment = Alignment.Center,
        ) { Ionicon("add", 32, colors.onError) }
    }

    // ==== DETAILS MODAL ====
    BottomSheet(visible = isDetailOpen, onClose = { isDetailOpen = false }, title = "Detalle del gasto") {
        selectedExpense?.let { exp ->
            Column {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Monto", color = colors.onSurfaceVariant)
                    Text("-${formatCurrency(exp.amount)}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = colors.error)
                }

                DetailGrid {
                    DetailItem("Categoría", exp.category, Modifier.fillMaxWidth(0.45f))
                    DetailItem("Método", exp.paymentMethod, Modifier.fillMaxWidth(0.45f))
                    DetailItem(
                        "Cuenta/Tarjeta",
                        exp.accountName?.takeIf { it.isNotEmpty() } ?: exp.cardName?.takeIf { it.isNotEmpty() } ?: "-",
                        Modifier.fillMaxWidth(0.45f),
                    )
                    DetailItem("Fecha", formatDate(exp.date), Modifier.fillMaxWidth(0.45f))
                    if (exp.isDeferred == true) DetailItem("Diferido", "${exp.deferredMonths} meses", Modifier.fillMaxWidth(0.45f))
                    if (exp.isRecurring == true) DetailItem("Recurrente", describeRecurrence(exp), Modifier.fillMaxWidth(0.45f))
                }

                Column(Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                    Column(Modifier.padding(top = 16.dp)) {
                        Text("Detalle", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text(exp.detail, fontSize = 16.sp, color = colors.onSurface)
                    }
                }

                Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("Editar", onClick = {
                        form.openEdit(exp)
                        isDetailOpen = false
                        isFormOpen = true
                    }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined, icon = "pencil")
                    AppButton("Eliminar", onClick = { deleteSelected() }, modifier = Modifier.weight(1f), variant = ButtonVariant.Danger, icon = "trash")
                }
            }
        }
    }

    // ==== FILTER MODAL ====
    BottomSheet(visible = isFilterOpen, onClose = { isFilterOpen = false }, title = "Filtrar gastos") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { AppTextField("Desde (opcional)", filterFrom, { filterFrom = it }, isDate = true) }
                Box(Modifier.weight(1f)) { AppTextField("Hasta (opcional)", filterTo, { filterTo = it }, isDate = true) }
            }

            Column {
                FieldLabel("Método de pago")
                ChipRow {
                    listOf("", PaymentMethod.CASH, PaymentMethod.DEBIT, PaymentMethod.CREDIT).forEach { m ->
                        Chip(label = m.ifEmpty { "Todos" }, active = filterMethod == m, onClick = { filterMethod = m })
                    }
                }
            }

            Column {
                FieldLabel("Categorías (puedes elegir varias)")
                ChipFlow {
                    Chip(label = "Todas", active = filterCats.isEmpty(), onClick = { filterCats = emptyList() })
                    filterCategoryOptions.forEach { (name, color) ->
                        Chip(
                            label = name,
                            active = filterCats.contains(name),
                            color = color,
                            onClick = { filterCats = if (filterCats.contains(name)) filterCats.filter { it != name } else filterCats + name },
                        )
                    }
                }
            }

            Column(Modifier.padding(top = 16.dp)) {
                AppButton("Aplicar filtros", onClick = { applyFilters() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                AppButton("Limpiar filtros", onClick = { clearFilters() }, modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Outlined)
            }
        }
    }

    // ==== FORM MODAL ====
    BottomSheet(visible = isFormOpen, onClose = { isFormOpen = false }, title = if (form.editingId != null) "Editar gasto" else "Nuevo gasto") {
        ExpenseFormContent(
            form = form,
            ui = ui,
            onOpenDatePicker = { isDatePickerOpen = true },
            onCancel = { isFormOpen = false },
            onSave = { saveExpense() },
        )
    }

    DatePickerSheet(
        visible = isDatePickerOpen,
        onClose = { isDatePickerOpen = false },
        value = form.dateStr,
        onSelect = { form.dateStr = it },
    )
}

@Composable
private fun ExpenseFormContent(
    form: ExpenseFormState,
    ui: ExpensesViewModel.UiState,
    onOpenDatePicker: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppTextField(
            label = "Monto *",
            placeholder = "Ej. 25.50",
            keyboard = FieldKeyboard.Decimal,
            value = form.amountStr,
            onValueChange = { form.amountStr = it },
        )

        run {
            val amountParsed = JsCompat.parseFloat(form.amountStr.replace(',', '.')).let { if (it.isNaN()) 0.0 else it }
            val config = ui.taxesConfig
            val taxes = calcTaxes(config, amountParsed) { it.applyTo.contains("Gastos:${form.categoryType}") }
            if (config != null && taxes.appliesTaxes && taxes.totalTax > 0) {
                TaxBreakdownBox(config, taxes, totalLabel = "Total retenido y monto debitado")
            }
        }

        Column {
            FieldLabel("Categoría *")
            ChipFlow {
                ui.categories.forEach { c ->
                    Chip(label = c.name, active = form.categoryType == c.name, onClick = { form.categoryType = c.name }, icon = c.icon, color = c.color)
                }
            }
        }

        AppTextField(
            label = "Detalle *",
            placeholder = "Ej. Compra supermercado",
            value = form.detail,
            onValueChange = { form.detail = it },
        )

        Column {
            FieldLabel("Método de pago")
            ChipRow {
                listOf(PaymentMethod.CASH, PaymentMethod.DEBIT, PaymentMethod.CREDIT).forEach { m ->
                    Chip(label = m, active = form.paymentMethod == m, onClick = { form.paymentMethod = m })
                }
            }
        }

        if ((form.paymentMethod == PaymentMethod.DEBIT || form.paymentMethod == PaymentMethod.CASH) && ui.accounts.isNotEmpty()) {
            Column {
                FieldLabel(if (form.paymentMethod == PaymentMethod.CASH) "Extraído de cuenta (Opcional)" else "Cuenta de débito")
                ChipScrollRow {
                    if (form.paymentMethod == PaymentMethod.CASH) {
                        Chip(label = "Efectivo físico (Bolsillo)", active = form.selectedDebit.isEmpty(), onClick = { form.selectedDebit = "" })
                    }
                    ui.accounts.forEach { a ->
                        Chip(label = "${a.name} · ${a.bankName}", active = form.selectedDebit == a.name, onClick = { form.selectedDebit = a.name })
                    }
                }
            }
        }

        if (form.paymentMethod == PaymentMethod.CREDIT && ui.creditCards.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    FieldLabel("Tarjeta de crédito")
                    ChipScrollRow {
                        ui.creditCards.forEach { c ->
                            Chip(label = "${c.name} · ${c.bankName}", active = form.selectedCredit == c.name, onClick = { form.selectedCredit = c.name })
                        }
                    }
                }

                Column {
                    FieldLabel("¿Diferido?")
                    ChipRow {
                        Chip(label = "Sí", active = form.isDeferred, onClick = { form.isDeferred = true })
                        Chip(label = "No", active = !form.isDeferred, onClick = { form.isDeferred = false })
                    }
                }

                if (form.isDeferred) {
                    AppTextField(
                        label = "Meses diferidos",
                        placeholder = "Ej. 3",
                        keyboard = FieldKeyboard.Number,
                        value = form.deferredMonths,
                        onValueChange = { form.deferredMonths = it },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                AppTextField(label = "Fecha *", placeholder = "YYYY-MM-DD", value = form.dateStr, onValueChange = {}, onPress = onOpenDatePicker)
            }
            Box(Modifier.weight(1f)) {
                AppTextField(label = "Hora (Opcional)", placeholder = "Ej. 14:30", value = form.timeStr, onValueChange = { form.timeStr = it })
            }
        }

        Column(Modifier.padding(top = 8.dp)) {
            HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            Column(Modifier.padding(top = 8.dp)) {
                FieldLabel("¿Es un gasto recurrente / suscripción?")
                ChipRow {
                    Chip(label = "Sí", active = form.isRecurring, onClick = { form.isRecurring = true })
                    Chip(label = "No", active = !form.isRecurring, onClick = { form.isRecurring = false })
                }
            }
        }

        if (form.isRecurring) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    FieldLabel("Frecuencia")
                    ChipFlow {
                        Chip(label = "Mensual", active = form.recurringFrequency == "monthly", onClick = { form.recurringFrequency = "monthly" })
                        Chip(label = "Día específico", active = form.recurringFrequency == "specific", onClick = { form.recurringFrequency = "specific" })
                        Chip(
                            label = "Anual",
                            active = form.recurringFrequency == "yearly",
                            onClick = {
                                form.recurringFrequency = "yearly"
                                if (form.recurringDate.isEmpty()) form.recurringDate = form.dateStr
                            },
                        )
                    }
                }
                if (form.recurringFrequency == "specific") {
                    AppTextField(
                        label = "Día de cobro (1-31)",
                        placeholder = "Ej. 15",
                        keyboard = FieldKeyboard.Number,
                        value = form.recurringDay,
                        onValueChange = { form.recurringDay = it },
                    )
                }
                if (form.recurringFrequency == "yearly") {
                    AppTextField(
                        label = "Fecha de cobro anual",
                        placeholder = "YYYY-MM-DD",
                        value = form.recurringDate.ifEmpty { form.dateStr },
                        onValueChange = { form.recurringDate = it },
                        isDate = true,
                        help = "Se repite cada año en este día y mes; te avisaremos antes de cada cobro.",
                    )
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Cancelar", onClick = onCancel, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
            AppButton(if (form.editingId != null) "Actualizar gasto" else "Guardar gasto", onClick = onSave, modifier = Modifier.weight(1f))
        }
    }
}
