package com.mixagames666.trackerrntemp.ui.screens.income

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.data.Account
import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.data.Investment
import com.mixagames666.trackerrntemp.domain.CreditCardForm
import com.mixagames666.trackerrntemp.domain.ServiceResult
import com.mixagames666.trackerrntemp.domain.availabilityLevel
import com.mixagames666.trackerrntemp.domain.calcTaxes
import com.mixagames666.trackerrntemp.ui.components.AlertButton
import com.mixagames666.trackerrntemp.ui.components.AlertButtonStyle
import com.mixagames666.trackerrntemp.ui.components.Alerts
import com.mixagames666.trackerrntemp.ui.components.AppButton
import com.mixagames666.trackerrntemp.ui.components.AppCard
import com.mixagames666.trackerrntemp.ui.components.AppProgressBar
import com.mixagames666.trackerrntemp.ui.components.AppTextField
import com.mixagames666.trackerrntemp.ui.components.AutoSizeText
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
import com.mixagames666.trackerrntemp.ui.components.ProgressVariant
import com.mixagames666.trackerrntemp.ui.components.TaxBreakdownBox
import com.mixagames666.trackerrntemp.ui.components.ToastManager
import com.mixagames666.trackerrntemp.ui.components.toProgressVariant
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.screens.appViewModel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.topInset
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.formatMonths
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.toAmountInput
import kotlinx.coroutines.launch
import kotlin.math.max

// Port de screens/IncomeScreen.tsx

// "Nómina" ya no se ofrece como tipo de cuenta.
private val ACCOUNT_TYPES = listOf("Ahorro", "Corriente")

private enum class IncomeSheet { Salary, Account, CreditCard, Investment, Income }

private enum class DetailType { Account, CreditCard, Investment, Income }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncomeScreen(container: AppContainer) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val vm = appViewModel(container) { IncomeViewModel(it) }
    val ui by vm.state.collectAsState()

    // Modals visibility + datos en edición
    var sheet by remember { mutableStateOf<IncomeSheet?>(null) }
    var editingData by remember { mutableStateOf<Any?>(null) }

    // General form states
    var formName by remember { mutableStateOf("") }
    var formAmount by remember { mutableStateOf("") }
    var formDetail by remember { mutableStateOf("") }
    // Salary
    var payDateType by remember { mutableStateOf("specific") }
    var payDate by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("Mensual") }
    var salaryAccount by remember { mutableStateOf("") }
    // Account
    var initialBalanceForm by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("Ahorro") }
    var bankName by remember { mutableStateOf("") }
    var refNumber by remember { mutableStateOf("") }
    // Credit card
    var cutOffDay by remember { mutableStateOf("") }
    var paymentDay by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    // Investment
    var remainingMonths by remember { mutableStateOf("") }
    var totalMonths by remember { mutableStateOf("") }
    var totalDeposited by remember { mutableStateOf("") }
    var depositSourceAccount by remember { mutableStateOf("") }
    // Income
    var incomeSource by remember { mutableStateOf("") }
    var incomeDate by remember { mutableStateOf(getToday()) }
    var isDatePickerOpen by remember { mutableStateOf(false) }
    var incomeAccount by remember { mutableStateOf("") }

    // Detailed modals
    var isDetailOpen by remember { mutableStateOf(false) }
    var detailType by remember { mutableStateOf<DetailType?>(null) }
    var detailData by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    fun closeCurrentSheet() {
        sheet = null
        editingData = null
        isDetailOpen = false
    }

    fun openForm(type: IncomeSheet, data: Any? = null) {
        editingData = data
        sheet = type
        when (type) {
            IncomeSheet.Salary -> {
                val s = data as? com.mixagames666.trackerrntemp.data.Salary
                formAmount = toAmountInput(s?.amount)
                payDateType = s?.payDateType?.ifEmpty { null } ?: "specific"
                payDate = s?.payDate.orEmpty()
                recurrence = s?.recurrence?.ifEmpty { null } ?: "Mensual"
                salaryAccount = s?.bankAccount.orEmpty()
            }
            IncomeSheet.Account -> {
                val a = data as? Account
                formName = a?.name.orEmpty()
                bankName = a?.bankName.orEmpty()
                accountType = if (a != null && ACCOUNT_TYPES.contains(a.accountType)) a.accountType else "Ahorro"
                refNumber = a?.referenceNumber.orEmpty()
                initialBalanceForm = toAmountInput(a?.initialBalance)
            }
            IncomeSheet.CreditCard -> {
                val c = data as? CreditCard
                formName = c?.name.orEmpty()
                bankName = c?.bankName.orEmpty()
                cutOffDay = c?.cutOffDay?.toString().orEmpty()
                paymentDay = c?.paymentDueDay?.toString().orEmpty()
                creditLimit = toAmountInput(c?.creditLimit)
                // El formulario pide el cupo disponible; internamente se guarda lo usado.
                formAmount = if (c != null && c.creditLimit > 0) toAmountInput(max(0.0, c.creditLimit - c.currentBalance)) else ""
            }
            IncomeSheet.Investment -> {
                val i = data as? Investment
                formName = i?.entity.orEmpty()
                formAmount = toAmountInput(i?.monthlyDeposit)
                remainingMonths = i?.remainingMonths?.toString().orEmpty()
                totalMonths = i?.totalMonths?.toString().orEmpty()
                formDetail = i?.detail.orEmpty()
                totalDeposited = toAmountInput(i?.totalDeposited)
            }
            IncomeSheet.Income -> {
                val inc = data as? Income
                formAmount = toAmountInput(inc?.amount)
                incomeSource = inc?.source.orEmpty()
                formDetail = inc?.detail.orEmpty()
                incomeAccount = inc?.bankAccount.orEmpty()
                incomeDate = inc?.date?.ifEmpty { null } ?: getToday()
            }
        }
    }

    fun openDetail(type: DetailType, data: Any) {
        detailType = type
        detailData = data
        isDetailOpen = true
        depositSourceAccount = ""
    }

    /** Muestra el aviso del resultado o, si se guardó, cierra la hoja y muestra el toast. */
    fun handleResult(result: ServiceResult, toast: String) {
        when (result) {
            is ServiceResult.Alert -> Alerts.alert(result.title, result.message)
            is ServiceResult.Done -> {
                closeCurrentSheet()
                ToastManager.show(toast)
            }
        }
    }

    fun confirmDelete(name: String, delete: (onDone: () -> Unit) -> Unit) {
        Alerts.alert(
            "Eliminar",
            "¿Estás seguro de eliminar $name?",
            listOf(
                AlertButton("Cancelar", AlertButtonStyle.Cancel),
                AlertButton("Eliminar", AlertButtonStyle.Destructive) { delete { closeCurrentSheet() } },
            ),
        )
    }

    val totalExtraIncome = remember(ui.incomes) { ui.incomes.fold(0.0) { s, i -> s + i.amount } }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = topInset()),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Ingresos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            }

            PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Salary
                    SectionHeader("cash-outline", "Sueldo")
                    Box(Modifier.touchableOpacity(activeOpacity = 0.8f, role = null) { openForm(IncomeSheet.Salary, ui.salary) }) {
                        AppCard(gradientGreen = true) {
                            val salary = ui.salary
                            if (salary != null) {
                                Column {
                                    Text("Sueldo configurado", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                    AutoSizeText(
                                        text = formatCurrency(salary.amount),
                                        fontSize = 32.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        minimumScale = 0.6f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    )
                                    Text(
                                        text = buildString {
                                            append(
                                                "Día de pago: " + when (salary.payDateType) {
                                                    "last" -> "Último día del mes"
                                                    "lastBusiness" -> "Último hábil"
                                                    else -> "Día ${salary.payDate}"
                                                } + " · ${salary.recurrence.ifEmpty { "Mensual" }}",
                                            )
                                            if (!salary.bankAccount.isNullOrEmpty()) append("\nDepositado en: ${salary.bankAccount}")
                                        },
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                    )
                                }
                            } else {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Ionicon("add-circle-outline", 40, Color.White.copy(alpha = 0.7f))
                                    Text("Configurar sueldo", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Accounts
                    SectionHeader("wallet-outline", "Cuentas Bancarias", onAdd = { openForm(IncomeSheet.Account) })
                    if (ui.accounts.isEmpty()) {
                        EmptyText("Sin cuentas registradas")
                    } else {
                        ui.accounts.forEach { acc ->
                            Box(Modifier.touchableOpacity(role = null) { openDetail(DetailType.Account, acc) }) {
                                SmallCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(acc.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                            Text("${acc.bankName} · ${acc.accountType}", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Text(
                                            formatCurrency(ui.accountBalances[acc.id] ?: 0.0),
                                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.secondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Credit Cards
                    SectionHeader("card-outline", "Tarjetas de Crédito", onAdd = { openForm(IncomeSheet.CreditCard) })
                    if (ui.creditCards.isEmpty()) {
                        EmptyText("Sin tarjetas registradas")
                    } else {
                        ui.creditCards.forEach { cc ->
                            // Se destaca el cupo disponible: verde con cupo de sobra, rojo cuando casi no queda.
                            val hasLimit = cc.creditLimit > 0
                            val available = if (hasLimit) max(0.0, cc.creditLimit - cc.currentBalance) else 0.0
                            val level = if (hasLimit) availabilityLevel(available / cc.creditLimit) else com.mixagames666.trackerrntemp.domain.AvailabilityLevel.SUCCESS
                            val levelColor = when (level) {
                                com.mixagames666.trackerrntemp.domain.AvailabilityLevel.DANGER -> colors.error
                                com.mixagames666.trackerrntemp.domain.AvailabilityLevel.WARNING -> colors.tertiary
                                com.mixagames666.trackerrntemp.domain.AvailabilityLevel.SUCCESS -> colors.secondary
                            }
                            val danger = hasLimit && level == com.mixagames666.trackerrntemp.domain.AvailabilityLevel.DANGER
                            Box(Modifier.touchableOpacity(role = null) { openDetail(DetailType.CreditCard, cc) }) {
                                SmallCard(
                                    backgroundColor = if (danger) colors.error.copy(alpha = 0x15 / 255f) else null,
                                    borderColor = if (danger) colors.error else null,
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = if (hasLimit) 8.dp else 0.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(cc.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(cc.bankName, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(if (hasLimit) "Disponible" else "Usado", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                            Text(
                                                formatCurrency(if (hasLimit) available else cc.currentBalance),
                                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                                color = if (hasLimit) levelColor else colors.onSurface,
                                            )
                                        }
                                    }
                                    if (hasLimit) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AppProgressBar(available / cc.creditLimit, variant = level.toProgressVariant())
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Usado: ${formatCurrency(cc.currentBalance)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                                Text("Límite: ${formatCurrency(cc.creditLimit)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Investments
                    SectionHeader("trending-up-outline", "Fondos de Inversión", onAdd = { openForm(IncomeSheet.Investment) })
                    if (ui.investments.isEmpty()) {
                        EmptyText("Sin fondos registrados")
                    } else {
                        ui.investments.forEach { inv ->
                            Box(Modifier.touchableOpacity(role = null) { openDetail(DetailType.Investment, inv) }) {
                                SmallCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(inv.entity, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(inv.detail, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${formatCurrency(inv.monthlyDeposit)}/mes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                                            Text(
                                                text = if (inv.remainingMonths > 0) {
                                                    "${formatMonths(inv.remainingMonths)} ${if (inv.remainingMonths == 1) "restante" else "restantes"}"
                                                } else {
                                                    "Completado"
                                                },
                                                fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = buildAnnotatedString {
                                                append("Total depositado actualmente: ")
                                                withStyle(SpanStyle(color = colors.secondary, fontWeight = FontWeight.SemiBold)) { append(formatCurrency(inv.totalDeposited)) }
                                            },
                                            fontSize = 12.sp,
                                            color = colors.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (inv.totalMonths > 0) {
                                            Text(
                                                "${max(0, inv.totalMonths - inv.remainingMonths)} de ${formatMonths(inv.totalMonths)}",
                                                fontSize = 12.sp, color = colors.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (inv.remainingMonths > 0) {
                                        Box(Modifier.padding(top = 12.dp)) {
                                            AppProgressBar(
                                                progress = max(0.05, 1 - (inv.remainingMonths.toDouble() / (if (inv.totalMonths != 0) inv.totalMonths else inv.remainingMonths))),
                                                variant = ProgressVariant.Primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Extra Incomes
                    Row(
                        Modifier.padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Ionicon("cash", 20, colors.onSurfaceVariant)
                        Text("INGRESOS EXTRA", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                    }

                    AppCard(elevated = true) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Total extra", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                Text(formatCurrency(totalExtraIncome), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.secondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Registros", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                Text(ui.incomes.size.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            }
                        }
                    }

                    if (ui.incomes.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Ionicon("wallet-outline", 48, colors.outline)
                            Text("Sin ingresos extra", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        }
                    } else {
                        Column(Modifier.padding(top = 16.dp)) {
                            ui.incomes.forEach { inc ->
                                Column {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .touchableOpacity(role = null) { openDetail(DetailType.Income, inc) }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.secondaryContainer, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) { Ionicon("arrow-down", 20, colors.secondary) }
                                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                            Text(inc.source.ifEmpty { "Ingreso" }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                            Text(
                                                "${inc.detail} ${if (inc.bankName.isNotEmpty()) "· ${inc.bankName}" else ""}",
                                                fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("+${formatCurrency(inc.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.secondary)
                                            Text(formatDate(inc.date), fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                    HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB Extra Income
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .shadow(4.dp, CircleShape)
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.secondary, CircleShape)
                .touchableOpacity(activeOpacity = 0.8f) { openForm(IncomeSheet.Income) },
            contentAlignment = Alignment.Center,
        ) { Ionicon("add", 32, colors.onSecondary) }
    }

    // ================= MODALS: FORMS =================

    BottomSheet(visible = sheet == IncomeSheet.Salary, onClose = { closeCurrentSheet() }, title = "Configurar sueldo") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField("Monto *", formAmount, { formAmount = it }, keyboard = FieldKeyboard.Decimal)

            Column {
                FieldLabel("Tipo de día de pago")
                ChipFlow {
                    listOf("Día específico" to "specific", "Último día mes" to "last", "Último hábil" to "lastBusiness").forEach { (label, value) ->
                        Chip(label = label, active = payDateType == value, onClick = { payDateType = value })
                    }
                }
            }

            if (payDateType == "specific") {
                AppTextField(
                    label = "Día de pago (1-31)",
                    placeholder = "Ej. 15",
                    keyboard = FieldKeyboard.Number,
                    value = payDate,
                    onValueChange = { text ->
                        var v = text.replace(Regex("[^0-9]"), "")
                        if (v.isNotEmpty()) {
                            val num = JsCompat.parseInt(v)
                            if (num > 31) v = "31"
                            if (num == 0.0) v = ""
                        }
                        payDate = v
                    },
                )
            }

            Column {
                FieldLabel("Recurrencia")
                ChipFlow {
                    listOf("Mensual", "Quincenal", "Semanal").forEach { r -> Chip(label = r, active = recurrence == r, onClick = { recurrence = r }) }
                }
            }

            Column {
                FieldLabel("Depositar sueldo en cuenta (Opcional)")
                ChipScrollRow {
                    ui.accounts.forEach { a ->
                        Chip(label = a.name, active = salaryAccount == a.name, onClick = { salaryAccount = if (salaryAccount == a.name) "" else a.name })
                    }
                }
            }

            AppButton(
                title = "Guardar sueldo",
                icon = "save",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        handleResult(
                            vm.saveSalary(
                                IncomeViewModel.SalaryForm(formAmount, payDateType, payDate, recurrence, salaryAccount),
                                editingData as? com.mixagames666.trackerrntemp.data.Salary,
                            ),
                            "Sueldo configurado con éxito",
                        )
                    }
                },
            )
        }
    }

    BottomSheet(
        visible = sheet == IncomeSheet.Account,
        onClose = { closeCurrentSheet() },
        title = if (editingData != null) "Editar cuenta" else "Nueva cuenta",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField("Nombre de cuenta *", formName, { formName = it }, placeholder = "Ej. Ahorros Principales")
            AppTextField("Número de referencia", refNumber, { refNumber = it }, placeholder = "Ej. 123456789")

            Column {
                FieldLabel("Tipo")
                ChipRow { ACCOUNT_TYPES.forEach { t -> Chip(label = t, active = accountType == t, onClick = { accountType = t }) } }
            }

            AppTextField("Banco *", bankName, { bankName = it })
            AppTextField(
                "Monto inicial actual (Opcional)", initialBalanceForm, { initialBalanceForm = it },
                placeholder = "Ej. 1500.00", keyboard = FieldKeyboard.Decimal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Cancelar", onClick = { closeCurrentSheet() }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
                AppButton(
                    title = "Guardar cuenta", icon = "save", modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            handleResult(
                                vm.saveAccount(
                                    IncomeViewModel.AccountForm(formName, bankName, refNumber, accountType, initialBalanceForm),
                                    editingData as? Account,
                                ),
                                "Cuenta guardada con éxito",
                            )
                        }
                    },
                )
            }
        }
    }

    BottomSheet(
        visible = sheet == IncomeSheet.CreditCard,
        onClose = { closeCurrentSheet() },
        title = if (editingData != null) "Editar tarjeta" else "Nueva tarjeta",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField("Nombre o Alias *", formName, { formName = it }, placeholder = "Ej. Visa Oro")
            AppTextField("Banco *", bankName, { bankName = it }, placeholder = "Ej. Santander")
            AppTextField(
                label = "Límite de crédito *",
                placeholder = "Ej. 5000",
                keyboard = FieldKeyboard.Decimal,
                value = creditLimit,
                onValueChange = { creditLimit = it },
                help = "Monto máximo que el banco te permite usar con esta tarjeta.",
            )
            AppTextField(
                label = "Cupo disponible *",
                placeholder = "Ej. 3750.50",
                keyboard = FieldKeyboard.Decimal,
                value = formAmount,
                onValueChange = { formAmount = it },
                help = "Lo que aún puedes gastar hoy. Si no has usado la tarjeta es igual al límite; si ya no te queda cupo, escribe 0. No puede ser mayor al límite.",
            )
            run {
                val limit = JsCompat.parseFloat(creditLimit).let { if (it.isNaN()) 0.0 else it }
                val available = JsCompat.parseFloat(formAmount)
                if (!(limit <= 0 || available.isNaN() || available > limit)) {
                    Text("Usado actualmente: ${formatCurrency(limit - available)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    AppTextField(
                        label = "Día de corte", placeholder = "Ej. 15", keyboard = FieldKeyboard.Number,
                        value = cutOffDay, onValueChange = { cutOffDay = it },
                        help = "Día del mes en que el banco cierra tu estado de cuenta. Las compras posteriores se cobran en el pago siguiente.",
                    )
                }
                Box(Modifier.weight(1f)) {
                    AppTextField(
                        label = "Día de pago", placeholder = "Ej. 5", keyboard = FieldKeyboard.Number,
                        value = paymentDay, onValueChange = { paymentDay = it },
                        help = "Fecha máxima del mes para pagar lo facturado al corte.",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Cancelar", onClick = { closeCurrentSheet() }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
                AppButton(
                    title = "Guardar tarjeta", icon = "save", modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            handleResult(
                                vm.saveCreditCard(
                                    CreditCardForm(formName, bankName, creditLimit, formAmount, cutOffDay, paymentDay),
                                    editingData as? CreditCard,
                                ),
                                "Tarjeta guardada con éxito",
                            )
                        }
                    },
                )
            }
        }
    }

    BottomSheet(
        visible = sheet == IncomeSheet.Investment,
        onClose = { closeCurrentSheet() },
        title = if (editingData != null) "Editar fondo" else "Nuevo fondo",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField("Entidad *", formName, { formName = it }, placeholder = "Ej. GBM+")
            AppTextField("Depósito mensual *", formAmount, { formAmount = it }, placeholder = "Ej. 200.00", keyboard = FieldKeyboard.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    AppTextField("Plazo total (meses)", totalMonths, { totalMonths = it }, placeholder = "Ej. 24", keyboard = FieldKeyboard.Number)
                }
                Box(Modifier.weight(1f)) {
                    AppTextField("Meses restantes", remainingMonths, { remainingMonths = it }, placeholder = "Ej. 12", keyboard = FieldKeyboard.Number)
                }
            }
            AppTextField("Detalle", formDetail, { formDetail = it }, placeholder = "Ej. Fondo para retiro")
            AppTextField("Total depositado actualmente", totalDeposited, { totalDeposited = it }, placeholder = "Ej. 2400.00", keyboard = FieldKeyboard.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Cancelar", onClick = { closeCurrentSheet() }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
                AppButton(
                    title = "Guardar fondo", icon = "save", modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            handleResult(
                                vm.saveInvestment(
                                    IncomeViewModel.InvestmentForm(formName, formAmount, totalMonths, remainingMonths, formDetail, totalDeposited),
                                    editingData as? Investment,
                                ),
                                "Inversión guardada con éxito",
                            )
                        }
                    },
                )
            }
        }
    }

    BottomSheet(
        visible = sheet == IncomeSheet.Income,
        onClose = { closeCurrentSheet() },
        title = if (editingData != null) "Editar ingreso" else "Nuevo ingreso",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField("Monto *", formAmount, { formAmount = it }, placeholder = "Ej. 550.00", keyboard = FieldKeyboard.Decimal)
            AppTextField("Fuente", incomeSource, { incomeSource = it }, placeholder = "Ej. Venta garaje")
            AppTextField("Detalle *", formDetail, { formDetail = it }, placeholder = "Ej. Venta bicicleta")

            Column {
                FieldLabel("Cuenta destino *")
                ChipScrollRow {
                    ui.accounts.forEach { a ->
                        Chip(
                            label = "${a.name} · ${a.bankName}",
                            active = incomeAccount == "${a.name} - ${a.bankName}",
                            onClick = { incomeAccount = "${a.name} - ${a.bankName}" },
                        )
                    }
                }
            }

            AppTextField("Fecha *", incomeDate, {}, placeholder = "YYYY-MM-DD", onPress = { isDatePickerOpen = true })

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Cancelar", onClick = { closeCurrentSheet() }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
                AppButton(
                    title = "Guardar ingreso", icon = "save", modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            handleResult(
                                vm.saveIncome(
                                    IncomeViewModel.IncomeForm(formAmount, incomeSource, formDetail, incomeAccount, incomeDate),
                                    editingData as? Income,
                                ),
                                "Ingreso guardado con éxito",
                            )
                        }
                    },
                )
            }
        }
    }

    DatePickerSheet(
        visible = isDatePickerOpen,
        onClose = { isDatePickerOpen = false },
        value = incomeDate,
        onSelect = { incomeDate = it },
    )

    // ================= MODALS: DETAILS =================

    BottomSheet(
        visible = isDetailOpen,
        onClose = { closeCurrentSheet() },
        title = when (detailType) {
            DetailType.Account -> "Cuenta"
            DetailType.CreditCard -> "Tarjeta"
            DetailType.Investment -> "Inversión"
            else -> "Ingreso"
        },
    ) {
        val data = detailData
        if (data != null) {
            Column {
                DetailGrid {
                    val item = Modifier.fillMaxWidth(0.45f)
                    when (val d = data) {
                        is Account -> {
                            DetailItem("Banco", d.bankName, item)
                            DetailItem("Tipo", d.accountType, item)
                            DetailItem("Referencia", d.referenceNumber?.ifEmpty { null } ?: "-", item)
                            DetailItem("Balance Calculado", formatCurrency(ui.accountBalances[d.id] ?: 0.0), item, colors.secondary, bold = true)
                        }
                        is CreditCard -> {
                            DetailItem("Banco", d.bankName, item)
                            DetailItem("Límite", formatCurrency(d.creditLimit), item)
                            DetailItem("Día corte", if (d.cutOffDay != 0) d.cutOffDay.toString() else "-", item)
                            DetailItem("Día pago", if (d.paymentDueDay != 0) d.paymentDueDay.toString() else "-", item)
                            DetailItem(
                                "Cupo disponible",
                                if (d.creditLimit > 0) formatCurrency(max(0.0, d.creditLimit - d.currentBalance)) else "-",
                                item, colors.secondary, bold = true,
                            )
                            DetailItem("Usado", formatCurrency(d.currentBalance), item)
                        }
                        is Investment -> {
                            DetailItem("Plazo total", if (d.totalMonths != 0) formatMonths(d.totalMonths) else "-", item)
                            DetailItem("Meses restantes", formatMonths(d.remainingMonths), item)
                            if (d.totalMonths > 0) {
                                DetailItem("Meses depositados", formatMonths(max(0, d.totalMonths - d.remainingMonths)), item)
                            }
                            DetailItem("Depósito mensual", formatCurrency(d.monthlyDeposit), item)
                            DetailItem("Total depositado actualmente", formatCurrency(d.totalDeposited), item, colors.secondary)
                            DetailItem("Detalle", d.detail.ifEmpty { "-" }, item)
                        }
                        is Income -> {
                            DetailItem("Monto", "+${formatCurrency(d.amount)}", item, colors.secondary)
                            DetailItem("Fuente", d.source.ifEmpty { "-" }, item)
                            DetailItem("Cuenta", d.bankAccount, item)
                            DetailItem("Fecha", formatDate(d.date), item)
                            DetailItem("Detalle", d.detail, Modifier.fillMaxWidth())
                        }
                    }
                }

                if (data is Investment && data.remainingMonths > 0 && ui.accounts.isNotEmpty()) {
                    AppCard(
                        modifier = Modifier.padding(top = 24.dp),
                        contentPadding = 12.dp,
                        bottomMargin = 0.dp,
                        borderColor = colors.primary,
                        borderWidth = 1.dp,
                    ) {
                        Text("Registrar depósito mensual", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.padding(bottom = 16.dp))

                        FieldLabel("Cuenta origen")
                        ChipScrollRow(bottomPadding = 16.dp) {
                            ui.accounts.forEach { a ->
                                Chip(
                                    label = "${a.name} · ${a.bankName}",
                                    active = depositSourceAccount == a.name,
                                    onClick = { depositSourceAccount = a.name },
                                )
                            }
                        }

                        run {
                            val config = ui.taxesConfig
                            val taxes = calcTaxes(config, data.monthlyDeposit) { it.applyTo.contains("Inversiones") }
                            if (config != null && taxes.appliesTaxes && taxes.totalTax > 0) {
                                TaxBreakdownBox(config, taxes, totalLabel = "Total retenido y debitado", modifier = Modifier.padding(bottom = 16.dp))
                            }
                        }

                        AppButton(
                            title = "Depositar ${formatCurrency(data.monthlyDeposit)}",
                            enabled = depositSourceAccount.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    when (val result = vm.investmentDeposit(data, depositSourceAccount)) {
                                        is ServiceResult.Alert -> Alerts.alert(result.title, result.message)
                                        is ServiceResult.Done -> {
                                            closeCurrentSheet()
                                            Alerts.alert("Éxito", result.successAlert)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(
                        title = "Editar", icon = "pencil", modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined,
                        onClick = {
                            isDetailOpen = false
                            val type = when (data) {
                                is Account -> IncomeSheet.Account
                                is CreditCard -> IncomeSheet.CreditCard
                                is Investment -> IncomeSheet.Investment
                                else -> IncomeSheet.Income
                            }
                            openForm(type, data)
                        },
                    )
                    AppButton(
                        title = "Eliminar", icon = "trash", modifier = Modifier.weight(1f), variant = ButtonVariant.Danger,
                        onClick = {
                            when (data) {
                                is Account -> confirmDelete(data.name) { done -> vm.deleteAccount(data.id, done) }
                                is CreditCard -> confirmDelete(data.name) { done -> vm.deleteCreditCard(data.id, done) }
                                is Investment -> confirmDelete(data.entity) { done -> vm.deleteInvestment(data.id, done) }
                                is Income -> confirmDelete(data.source.ifEmpty { "Ingreso" }) { done -> vm.deleteIncome(data.id, done) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: String, title: String, onAdd: (() -> Unit)? = null) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ionicon(icon, 20, colors.onSurfaceVariant)
            Text(title.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
        }
        if (onAdd != null) {
            Box(Modifier.touchableOpacity(onClick = onAdd)) { Ionicon("add-circle", 28, colors.primary) }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

/** Tarjeta compacta de las listas (smallCard): padding 12, margen inferior 8, radio 12. */
@Composable
private fun SmallCard(
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    AppCard(
        contentPadding = 12.dp,
        bottomMargin = 8.dp,
        cornerRadius = 12.dp,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        borderWidth = if (borderColor != null) 1.dp else 0.dp,
        content = content,
    )
}
