package com.mixagames666.trackerrntemp.ui.screens.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Loan
import com.mixagames666.trackerrntemp.domain.CardCycleSummary
import com.mixagames666.trackerrntemp.domain.LoanForm
import com.mixagames666.trackerrntemp.domain.ServiceResult
import com.mixagames666.trackerrntemp.domain.availabilityLevel
import com.mixagames666.trackerrntemp.domain.calcTaxes
import com.mixagames666.trackerrntemp.domain.getCardCycleSummary
import com.mixagames666.trackerrntemp.ui.components.AlertButton
import com.mixagames666.trackerrntemp.ui.components.AlertButtonStyle
import com.mixagames666.trackerrntemp.ui.components.Alerts
import com.mixagames666.trackerrntemp.ui.components.AppButton
import com.mixagames666.trackerrntemp.ui.components.AppCard
import com.mixagames666.trackerrntemp.ui.components.AppProgressBar
import com.mixagames666.trackerrntemp.ui.components.AppTextField
import com.mixagames666.trackerrntemp.ui.components.BottomSheet
import com.mixagames666.trackerrntemp.ui.components.ButtonVariant
import com.mixagames666.trackerrntemp.ui.components.Chip
import com.mixagames666.trackerrntemp.ui.components.ChipScrollRow
import com.mixagames666.trackerrntemp.ui.components.DatePickerSheet
import com.mixagames666.trackerrntemp.ui.components.DetailGrid
import com.mixagames666.trackerrntemp.ui.components.DetailItem
import com.mixagames666.trackerrntemp.ui.components.EmptyState
import com.mixagames666.trackerrntemp.ui.components.FieldKeyboard
import com.mixagames666.trackerrntemp.ui.components.FieldLabel
import com.mixagames666.trackerrntemp.ui.components.FormColumn
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
import com.mixagames666.trackerrntemp.util.daysUntil
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.formatDateShort
import com.mixagames666.trackerrntemp.util.getCurrencySymbol
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.toAmountInput
import kotlinx.coroutines.launch

// Port de screens/LoansScreen.tsx

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoansScreen(container: CoreContainer) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val vm = appViewModel(container) { LoansViewModel(it) }
    val ui by vm.state.collectAsState()

    var paymentAccount by remember { mutableStateOf("") }

    // Modals
    var isFormOpen by remember { mutableStateOf(false) }
    var isDetailOpen by remember { mutableStateOf(false) }

    // Form state
    var editingData by remember { mutableStateOf<Loan?>(null) }
    var name by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var installments by remember { mutableStateOf("") }
    var paidInstallments by remember { mutableStateOf("") }
    var nextPaymentDate by remember { mutableStateOf("") }
    var isDatePickerOpen by remember { mutableStateOf(false) }

    // Selected for details
    var selectedLoan by remember { mutableStateOf<Loan?>(null) }
    // Tarjetas: sus compras se agrupan y se abren desde la vista de cada tarjeta.
    var selectedCardId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    fun openForm(loan: Loan? = null) {
        editingData = loan
        name = loan?.name.orEmpty()
        totalAmount = toAmountInput(loan?.totalAmount)
        interestRate = loan?.interestRate?.let { JsCompat.numberToString(it) }.orEmpty()
        installments = loan?.installments?.toString().orEmpty()
        paidInstallments = loan?.paidInstallments?.toString() ?: "0"
        nextPaymentDate = loan?.nextPaymentDate.orEmpty()
        isDetailOpen = false
        isFormOpen = true
    }

    fun saveLoan() {
        scope.launch {
            when (val result = vm.saveLoan(LoanForm(name, totalAmount, interestRate, installments, paidInstallments, nextPaymentDate), editingData)) {
                is ServiceResult.Alert -> Alerts.alert(result.title, result.message)
                is ServiceResult.Done -> {
                    isFormOpen = false
                    ToastManager.show("Préstamo guardado con éxito")
                }
            }
        }
    }

    fun deleteSelected() {
        val loan = selectedLoan ?: return
        val message = if (!loan.cardId.isNullOrEmpty()) {
            "¿Estás seguro de eliminar este préstamo? Lo que siga pendiente de la compra se quitará del saldo de la tarjeta."
        } else {
            "¿Estás seguro de eliminar este préstamo?"
        }
        Alerts.alert(
            "Eliminar",
            message,
            listOf(
                AlertButton("Cancelar", AlertButtonStyle.Cancel),
                AlertButton("Eliminar", AlertButtonStyle.Destructive) {
                    vm.deleteLoan(loan) {
                        // Desde una tarjeta se vuelve a su lista de compras.
                        if (selectedCardId != null) selectedLoan = null else isDetailOpen = false
                    }
                },
            ),
        )
    }

    fun payInstallment() {
        val loan = selectedLoan ?: return
        scope.launch {
            when (val result = vm.payInstallment(loan, paymentAccount)) {
                is ServiceResult.Alert -> Alerts.alert(result.title, result.message)
                is ServiceResult.Done -> {
                    if (selectedCardId != null) selectedLoan = null else isDetailOpen = false
                    Alerts.alert("Éxito", result.successAlert)
                    vm.reloadAfterPayment()
                }
            }
        }
    }

    // Preview computations
    val pTotalAm = JsCompat.parseFloat(totalAmount.replace(',', '.')).orZero()
    val pIntR = JsCompat.parseFloat(interestRate.replace(',', '.')).orZero()
    val pInst = JsCompat.parseIntOrZero(installments)
    val pTotalWithInterest = if (pTotalAm > 0 && pInst > 0) pTotalAm * (1 + pIntR / 100) else 0.0
    val pInstallmentAm = if (pInst > 0) pTotalWithInterest / pInst else 0.0

    // Stats
    val loans = ui.loans
    val activeLoans = loans.filter { it.status != "paid" }
    val totalDebt = activeLoans.fold(0.0) { s, l -> s + ((l.installments - l.paidInstallments) * l.monthlyQuota) }

    // Compras con tarjeta: una tarjeta por TC; el resto de préstamos se lista como siempre.
    val cardIds = ui.creditCards.map { it.id }.toSet()
    val cardGroups = ui.creditCards
        .map { card -> card to getCardCycleSummary(card, loans, ui.expensesById) }
        .filter { (_, summary) -> summary.purchases.isNotEmpty() }
    val otherLoans = loans.filter { it.cardId.isNullOrEmpty() || it.cardId !in cardIds }
    val activeCount = otherLoans.count { it.status != "paid" } + cardGroups.count { (_, s) -> s.totalPending > 0 }
    val selectedCard = ui.creditCards.firstOrNull { it.id == selectedCardId }
    val selectedSummary = selectedCard?.let { getCardCycleSummary(it, loans, ui.expensesById) }

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
                Text("Préstamos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            }

            Box(Modifier.padding(horizontal = 16.dp)) {
                AppCard(elevated = true) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Deuda pendiente", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(formatCurrency(totalDebt), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.warningText)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Activos", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(activeCount.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        }
                    }
                }
            }

            PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp)),
                ) {
                    if (loans.isEmpty()) {
                        // Heurísticas 7 y 10: el estado vacío ofrece directamente la acción siguiente.
                        EmptyState(
                            icon = "business-outline",
                            title = "Sin préstamos",
                            message = "Toca el botón + para registrar uno",
                            modifier = Modifier.padding(top = 24.dp),
                            action = {
                                AppButton("Registrar préstamo", onClick = { openForm(null) }, variant = ButtonVariant.Tonal, icon = "add")
                            },
                        )
                    } else {
                        if (cardGroups.isNotEmpty()) SectionLabel("TARJETAS DE CRÉDITO")
                        cardGroups.forEach { (card, summary) ->
                            CardGroup(
                                card = card,
                                summary = summary,
                                onClick = {
                                    selectedLoan = null
                                    selectedCardId = card.id
                                    isDetailOpen = true
                                },
                            )
                        }
                        if (cardGroups.isNotEmpty() && otherLoans.isNotEmpty()) SectionLabel("PRÉSTAMOS")
                        otherLoans.forEach { loan ->
                            LoanCard(
                                loan = loan,
                                onClick = {
                                    selectedCardId = null
                                    selectedLoan = loan
                                    isDetailOpen = true
                                },
                            )
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
                .background(colors.tertiaryDark, CircleShape)
                .touchableOpacity(activeOpacity = 0.8f, onClickLabel = "Nuevo préstamo") { openForm(null) }
                .semantics { contentDescription = "Nuevo préstamo" },
            contentAlignment = Alignment.Center,
        ) { Ionicon("add", 32, colors.onTertiary) }
    }

    // ==== DETAILS MODAL ====
    BottomSheet(
        visible = isDetailOpen,
        onClose = { isDetailOpen = false },
        title = selectedLoan?.name ?: selectedCard?.let { "${it.name} · ${it.bankName}" } ?: "Detalle",
    ) {
        val loan = selectedLoan
        if (loan != null) {
            LoanDetail(
                loan = loan,
                selectedCard = selectedCard,
                accounts = ui.accounts,
                accountBalances = ui.accountBalances,
                taxesConfig = ui.taxesConfig,
                paymentAccount = paymentAccount,
                onPaymentAccount = { paymentAccount = it },
                onBack = { selectedLoan = null },
                onPay = { payInstallment() },
                onEdit = { openForm(loan) },
                onDelete = { deleteSelected() },
            )
        } else if (selectedCard != null && selectedSummary != null) {
            CardDetail(selectedCard, selectedSummary, onOpenLoan = { selectedLoan = it })
        }
    }

    // ==== FORM MODAL ====
    BottomSheet(visible = isFormOpen, onClose = { isFormOpen = false }, title = if (editingData != null) "Editar préstamo" else "Nuevo préstamo") {
        FormColumn {
            AppTextField("Nombre / Descripción *", name, { name = it }, placeholder = "Ej. Préstamo Auto")

            AppTextField(
                "Monto total original *", totalAmount, { totalAmount = it },
                placeholder = "Ej. 15000", keyboard = FieldKeyboard.Decimal, prefix = getCurrencySymbol(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    AppTextField("Interés (%)", interestRate, { interestRate = it }, placeholder = "Ej. 10.5", keyboard = FieldKeyboard.Decimal)
                }
                Box(Modifier.weight(1f)) {
                    AppTextField("N° Cuotas *", installments, { installments = it }, placeholder = "Ej. 24", keyboard = FieldKeyboard.Number)
                }
            }

            if (pTotalWithInterest > 0) {
                AppCard(backgroundColor = colors.surfaceContainerHighest) {
                    Text("Total a pagar con interés", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(
                        formatCurrency(pTotalWithInterest), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = colors.primaryText, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text("Cuota mensual estimada", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(formatCurrency(pInstallmentAm), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                }
            }

            AppTextField("Cuotas ya pagadas", paidInstallments, { paidInstallments = it }, placeholder = "Ej. 5", keyboard = FieldKeyboard.Number)

            AppTextField(
                label = "Fecha máxima de próximo pago (Opcional)",
                placeholder = "YYYY-MM-DD",
                value = nextPaymentDate,
                onValueChange = {},
                onPress = { isDatePickerOpen = true },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Cancelar", onClick = { isFormOpen = false }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
                AppButton(if (editingData != null) "Actualizar" else "Guardar préstamo", onClick = { saveLoan() }, modifier = Modifier.weight(1f))
            }
        }
    }

    DatePickerSheet(
        visible = isDatePickerOpen,
        onClose = { isDatePickerOpen = false },
        value = nextPaymentDate,
        onSelect = { nextPaymentDate = it },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = AppTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0x20 / 255f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardGroup(card: CreditCard, summary: CardCycleSummary, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val pendingCount = summary.purchases.count { !it.isPaid }
    val dueDays = summary.nextDueDate?.let { daysUntil(it) }
    var statusColor = colors.successText
    var statusText = if (summary.totalPending > 0) "Al día" else "Sin deuda"
    if (summary.hasOverdue) {
        statusColor = colors.errorText
        statusText = "Vencido"
    } else if (summary.dueAmount > 0 && dueDays != null && dueDays <= 5) {
        statusColor = colors.warningText
        statusText = "Próximo a vencer"
    }
    val availableRatio = summary.available?.let { it / card.creditLimit }

    Box(Modifier.touchableOpacity(role = null, onClick = onClick)) {
        AppCard(
            modifier = Modifier.alpha(if (summary.totalPending > 0) 1f else 0.6f),
            contentPadding = 16.dp,
            bottomMargin = 12.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Ionicon("card", 18, colors.primary)
                        Text(card.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "${card.bankName} · ${if (pendingCount == 1) "1 compra pendiente" else "$pendingCount compras pendientes"}",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                    )
                }
                StatusBadge(statusText, statusColor)
            }

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text(
                        if (summary.nextDueDate != null) "Por pagar el ${formatDateShort(summary.nextDueDate)}" else "Por pagar",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                    )
                    Text(formatCurrency(summary.dueAmount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.errorText)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Deuda activa", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(formatCurrency(summary.activeDebt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.warningText)
                }
            }

            if (availableRatio != null) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppProgressBar(availableRatio, variant = availabilityLevel(availableRatio).toProgressVariant())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cupo disponible: ${formatCurrency(summary.available ?: 0.0)}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        Text("Límite: ${formatCurrency(card.creditLimit)}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanCard(loan: Loan, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val progress = if (loan.installments > 0) loan.paidInstallments.toDouble() / loan.installments else 0.0
    val remaining = (loan.installments - loan.paidInstallments) * loan.monthlyQuota

    val nextPDate = loan.nextPaymentDate?.ifEmpty { null }
    val days = nextPDate?.let { daysUntil(it) }
    val isPaid = loan.status == "paid"

    var statusColor = colors.successText // Green for success
    var statusText = "Al día"
    if (isPaid) {
        statusText = "Pagado"
    } else if (days != null && days < 0) {
        statusColor = colors.errorText
        statusText = "Vencido"
    } else if (days != null && days <= 5) {
        statusColor = colors.warningText
        statusText = "Próximo a vencer"
    }

    Box(Modifier.touchableOpacity(role = null, onClick = onClick)) {
        AppCard(modifier = Modifier.alpha(if (isPaid) 0.6f else 1f), contentPadding = 16.dp, bottomMargin = 12.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(loan.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${loan.paidInstallments}/${loan.installments} cuotas", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                StatusBadge(statusText, statusColor)
            }

            AppProgressBar(
                progress = progress,
                variant = if (progress >= 1) ProgressVariant.Success else if (progress > 0.5) ProgressVariant.Warning else ProgressVariant.Primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text("Cuota:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(formatCurrency(loan.monthlyQuota), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Restante:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(formatCurrency(remaining), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.errorText)
                }
                if (loan.totalWithInterest != null) {
                    Column {
                        Text("Total c/interés:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text(formatCurrency(loan.totalWithInterest), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                }
                if (nextPDate != null && !isPaid) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Próximo pago:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text(formatDate(nextPDate), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanDetail(
    loan: Loan,
    selectedCard: CreditCard?,
    accounts: List<com.mixagames666.trackerrntemp.data.Account>,
    accountBalances: Map<String, Double>,
    taxesConfig: com.mixagames666.trackerrntemp.data.TaxesConfig?,
    paymentAccount: String,
    onPaymentAccount: (String) -> Unit,
    onBack: () -> Unit,
    onPay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val item = Modifier.fillMaxWidth(0.45f)
    val isPaid = loan.status == "paid"
    Column {
        if (selectedCard != null) {
            Row(
                Modifier
                    .padding(bottom = 12.dp)
                    .touchableOpacity(role = null, onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Ionicon("chevron-back", 18, colors.primary)
                Text("Volver a ${selectedCard.name}", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Monto Original", color = colors.onSurfaceVariant)
            Text(formatCurrency(loan.totalAmount), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.primaryText)
        }

        DetailGrid {
            DetailItem("Total c/interés", formatCurrency(loan.totalWithInterest?.takeIf { it != 0.0 } ?: loan.totalAmount), item)
            DetailItem("Tasa interés", "${JsCompat.numberToString(loan.interestRate)}%", item)
            DetailItem("Cuota mensual", formatCurrency(loan.monthlyQuota), item)
            DetailItem("Progreso", "${loan.paidInstallments}/${loan.installments} cuotas", item)
            DetailItem("Monto restante", formatCurrency((loan.installments - loan.paidInstallments) * loan.monthlyQuota), item, colors.error)
            if (!loan.nextPaymentDate.isNullOrEmpty()) DetailItem("Próximo pago", formatDate(loan.nextPaymentDate), item)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Text("Progreso de pago", fontSize = 12.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            AppProgressBar(if (loan.installments > 0) loan.paidInstallments.toDouble() / loan.installments else 0.0, variant = ProgressVariant.Success)
            Text(
                "${if (loan.installments > 0) JsCompat.toFixed(loan.paidInstallments.toDouble() / loan.installments * 100, 1) else "0"}% completado",
                fontSize = 12.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (!isPaid && accounts.isNotEmpty()) {
            Column(Modifier.padding(bottom = 16.dp)) {
                FieldLabel("Cuenta origen para el pago *")
                ChipScrollRow {
                    accounts.forEach { a ->
                        Chip(
                            label = "${a.name} (${formatCurrency(accountBalances[a.id] ?: 0.0)})",
                            active = paymentAccount == a.name,
                            onClick = { onPaymentAccount(a.name) },
                        )
                    }
                }
            }
        }

        if (!isPaid) {
            val amountParsed = JsCompat.round(loan.monthlyQuota * 100) / 100
            val taxes = calcTaxes(taxesConfig, amountParsed) { it.applyTo.contains("Préstamos") }
            if (taxesConfig != null && taxes.appliesTaxes && taxes.totalTax > 0) {
                TaxBreakdownBox(taxesConfig, taxes, totalLabel = "Total retenido y debitado", modifier = Modifier.padding(bottom = 16.dp))
            }
            AppButton(
                title = "Registrar pago de cuota",
                icon = "cash-outline",
                onClick = onPay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Editar", onClick = onEdit, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined, icon = "pencil")
            AppButton("Eliminar", onClick = onDelete, modifier = Modifier.weight(1f), variant = ButtonVariant.Danger, icon = "trash")
        }
    }
}

@Composable
private fun CardDetail(card: CreditCard, summary: CardCycleSummary, onOpenLoan: (Loan) -> Unit) {
    val colors = AppTheme.colors
    val item = Modifier.fillMaxWidth(0.45f)
    Column {
        DetailGrid {
            Column(item) {
                Text(
                    if (summary.nextDueDate != null) "Por pagar el ${formatDateShort(summary.nextDueDate)}" else "Por pagar",
                    fontSize = 12.sp, color = colors.onSurfaceVariant,
                )
                Text(formatCurrency(summary.dueAmount), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.errorText)
            }
            Column(item) {
                Text("Deuda activa", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Text(formatCurrency(summary.activeDebt), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.warningText)
            }
            DetailItem("Último corte", summary.lastCutOff?.let { formatDate(it) } ?: "-", item)
            DetailItem("Cupo disponible", summary.available?.let { formatCurrency(it) } ?: "-", item)
        }
        Text(
            "\"Por pagar\" son las compras hasta el último corte; \"Deuda activa\", las posteriores al corte y las cuotas siguientes.",
            fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp),
        )

        Text(
            "COMPRAS",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        if (summary.purchases.isEmpty()) {
            Text(
                "Sin compras registradas", color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        } else {
            summary.purchases.forEach { p ->
                Column(Modifier.alpha(if (p.isPaid) 0.6f else 1f)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .touchableOpacity(role = null) { onOpenLoan(p.loan) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    p.expense?.date?.takeIf { it.isNotEmpty() }?.let { formatDate(it) },
                                    p.expense?.category?.takeIf { it.isNotEmpty() },
                                    "${p.loan.paidInstallments}/${p.loan.installments} ${if (p.loan.installments == 1) "cuota" else "cuotas"}",
                                ).joinToString(" · "),
                                fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End) {
                            Text(
                                if (p.isPaid) "Pagada" else formatCurrency(p.pending),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (p.isPaid) colors.successText else colors.errorText,
                            )
                            if (!p.isPaid && !p.loan.nextPaymentDate.isNullOrEmpty()) {
                                Text("Vence ${formatDateShort(p.loan.nextPaymentDate)}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        Ionicon("chevron-forward", 18, colors.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                }
            }
        }
    }
}
