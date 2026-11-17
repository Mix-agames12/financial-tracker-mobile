package com.mixagames666.trackerrntemp.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.domain.UpcomingPayment
import com.mixagames666.trackerrntemp.domain.UpcomingPaymentKind
import com.mixagames666.trackerrntemp.ui.components.AppButton
import com.mixagames666.trackerrntemp.ui.components.AppCard
import com.mixagames666.trackerrntemp.ui.components.AppProgressBar
import com.mixagames666.trackerrntemp.ui.components.AutoSizeText
import com.mixagames666.trackerrntemp.ui.components.BottomSheet
import com.mixagames666.trackerrntemp.ui.components.ButtonVariant
import com.mixagames666.trackerrntemp.ui.components.Ionicon
import com.mixagames666.trackerrntemp.ui.components.MdiPiggyBank
import com.mixagames666.trackerrntemp.ui.components.ProgressVariant
import com.mixagames666.trackerrntemp.ui.components.WelcomeSheet
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.screens.appViewModel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.ui.theme.hexColorWithAlpha
import com.mixagames666.trackerrntemp.ui.theme.topInset
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.getMonthName
import kotlin.math.abs
import kotlin.math.max

// Port de screens/DashboardScreen.tsx

private fun paymentIcon(kind: UpcomingPaymentKind): String = when (kind) {
    UpcomingPaymentKind.LOAN -> "business-outline"
    UpcomingPaymentKind.CARD -> "card-outline"
    UpcomingPaymentKind.RECURRING -> "repeat-outline"
}

private fun describeDueDate(daysLeft: Int): String {
    if (daysLeft < 0) {
        val days = abs(daysLeft)
        return "Vencido hace $days ${if (days == 1) "día" else "días"}"
    }
    if (daysLeft == 0) return "Vence hoy"
    if (daysLeft == 1) return "Vence mañana"
    return "En $daysLeft días"
}

private enum class GoalStatus { Success, Warning, Danger }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val colors = AppTheme.colors
    val vm = appViewModel(container) { DashboardViewModel(it) }
    val ui by vm.state.collectAsState()
    var isPaymentsOpen by remember { mutableStateOf(false) }

    // useFocusEffect: se recarga cada vez que la pestaña entra en pantalla
    LaunchedEffect(Unit) { vm.load() }

    val data = ui.data
    val now = Time.now()
    val monthName = getMonthName(now.monthValue)
    val year = now.year

    val monthlyGoal = data?.monthlyGoal ?: 0.0
    val monthly = data?.monthly
    val goalBase = if (monthlyGoal > 0) monthlyGoal else if ((monthly?.totalIncome ?: 0.0) > 0) monthly!!.totalIncome else 1.0
    val goalPercent = (monthly?.totalExpenses ?: 0.0) / goalBase

    var goalStatus = GoalStatus.Success
    if (monthlyGoal > 0) {
        if (goalPercent >= 1) goalStatus = GoalStatus.Danger
        else if (goalPercent >= 0.9) goalStatus = GoalStatus.Warning
    } else {
        if (goalPercent >= 0.9) goalStatus = GoalStatus.Danger
        else if (goalPercent >= 0.6) goalStatus = GoalStatus.Warning
    }

    // Feedback visual: el ahorro negativo se muestra en rojo.
    val balance = data?.balance
    val savingsColor = if ((balance?.balance ?: 0.0) < 0) colors.error else colors.secondary
    val upcoming = data?.upcoming.orEmpty()
    val hasUrgentPayments = upcoming.any { it.daysLeft <= 3 }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = topInset()),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text("Bienvenido de vuelta", fontSize = 14.sp, color = colors.onSurfaceVariant)
                Text(
                    text = data?.appName ?: "Mi Dinero",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderButton(
                    icon = "notifications-outline",
                    description = if (hasUrgentPayments) "Próximos pagos, hay pagos por vencer" else "Próximos pagos",
                    showBadge = hasUrgentPayments,
                    onClick = { isPaymentsOpen = true },
                )
                HeaderButton(icon = "settings-outline", description = "Configuración", onClick = onOpenSettings)
            }
        }

        ui.salaryPrompt?.let { prompt ->
            BottomSheet(visible = true, onClose = { vm.dismissSalaryPrompt() }, title = "¡Día de Sueldo!") {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Ionicon("cash-outline", 48, colors.secondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Parece que ya llegó tu fecha de cobro. ¿Ya recibiste el abono de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.secondary)) { append(formatCurrency(prompt.amount)) }
                            append(" en tu cuenta de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(prompt.account) }
                            append("?")
                        },
                        color = colors.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    )
                    AppButton(title = "Sí, ya se acreditó", onClick = { vm.acceptSalary() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        title = "Aún no (Preguntar mañana)",
                        variant = ButtonVariant.Tonal,
                        onClick = { vm.postponeSalary() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        title = "Modificar tarjeta de sueldo",
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            vm.dismissSalaryPrompt()
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        WelcomeSheet(
            visible = ui.showWelcome && !ui.isLoading,
            appName = data?.appName ?: "Mi Dinero",
            repos = container.repos,
            onDone = { vm.welcomeDone() },
        )

        BottomSheet(visible = isPaymentsOpen, onClose = { isPaymentsOpen = false }, title = "Próximos pagos") {
            if (upcoming.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Ionicon("checkmark-circle-outline", 48, colors.outline)
                    Text(
                        "No tienes pagos próximos",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        "Aquí verás cuotas de préstamos, tarjetas y gastos recurrentes de los próximos 30 días",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                upcoming.forEachIndexed { i, payment ->
                    val statusColor = when {
                        payment.daysLeft < 0 -> colors.error
                        payment.daysLeft <= 3 -> colors.tertiary
                        else -> colors.onSurfaceVariant
                    }
                    TxRow(
                        isLast = i == upcoming.size - 1,
                        iconName = paymentIcon(payment.kind),
                        iconColor = colors.primary,
                        iconBackground = colors.primaryContainer,
                        title = payment.title,
                        subtitle = "${describeDueDate(payment.daysLeft)} · ${formatDate(payment.dueDate)}",
                        subtitleColor = statusColor,
                        amount = formatCurrency(payment.amount),
                        amountColor = colors.onSurface,
                    )
                }
            }
        }

        if (ui.isLoading) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(36.dp))
                Text("Cargando tus finanzas…", fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
        } else if (data != null) {
            PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
                ) {
                    // Balance Card
                    AppCard(gradient = true) {
                        Text("Balance total", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        AutoSizeText(
                            text = formatCurrency(data.balance.balance),
                            fontSize = 36.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            minimumScale = 0.5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Ionicon("arrow-down-circle", 16, Color.White.copy(alpha = 0.8f))
                                Text("Ingresos: ${formatCurrency(data.balance.totalIncome)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Ionicon("arrow-up-circle", 16, Color.White.copy(alpha = 0.8f))
                                Text("Gastos: ${formatCurrency(data.balance.totalExpenses)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                            }
                        }
                    }

                    // Quick Stats
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppCard(Modifier.weight(1f), contentPadding = 0.dp, bottomMargin = 0.dp) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MdiPiggyBank(28, savingsColor)
                                Text("Ahorro", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                                AutoSizeText(
                                    text = formatCurrency(data.balance.balance),
                                    fontSize = 18.sp,
                                    color = savingsColor,
                                    fontWeight = FontWeight.Bold,
                                    minimumScale = 0.6f,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                )
                            }
                        }
                        AppCard(Modifier.weight(1f), contentPadding = 0.dp, bottomMargin = 0.dp) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Ionicon("card", 28, colors.tertiary)
                                Text("Deuda activa", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                                AutoSizeText(
                                    text = formatCurrency(data.totalLoanDebt),
                                    fontSize = 18.sp,
                                    color = colors.tertiary,
                                    fontWeight = FontWeight.Bold,
                                    minimumScale = 0.6f,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    // Monthly Summary
                    SectionHeader(icon = "calendar-outline", title = "$monthName $year")

                    AppCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Ingresos del mes", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                AutoSizeText(
                                    text = formatCurrency(data.monthly.totalIncome),
                                    fontSize = 18.sp,
                                    color = colors.secondary,
                                    fontWeight = FontWeight.Bold,
                                    minimumScale = 0.6f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Gastos del mes", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                AutoSizeText(
                                    text = formatCurrency(data.monthly.totalExpenses),
                                    fontSize = 18.sp,
                                    color = colors.error,
                                    fontWeight = FontWeight.Bold,
                                    minimumScale = 0.6f,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        AppProgressBar(
                            progress = goalPercent,
                            variant = when (goalStatus) {
                                GoalStatus.Success -> ProgressVariant.Success
                                GoalStatus.Warning -> ProgressVariant.Warning
                                GoalStatus.Danger -> ProgressVariant.Danger
                            },
                            modifier = Modifier.padding(vertical = 12.dp),
                        )

                        Text(
                            text = when {
                                monthlyGoal > 0 ->
                                    "${JsCompat.toFixed(goalPercent * 100, 1)}% de meta (${formatCurrency(monthlyGoal)} máx.) · Restante: ${
                                        formatCurrency(max(0.0, monthlyGoal - data.monthly.totalExpenses))
                                    }"
                                data.monthly.totalIncome > 0 -> "${JsCompat.toFixed(goalPercent * 100, 1)}% del ingreso gastado"
                                else -> "Sin ingresos registrados"
                            },
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (goalStatus == GoalStatus.Danger && monthlyGoal > 0) {
                            AlertBox(colors.errorContainer, "alert-circle", colors.error, "¡Has superado tu meta mensual!")
                        }
                        if (goalStatus == GoalStatus.Warning && monthlyGoal > 0) {
                            AlertBox(colors.tertiaryContainer, "warning", colors.tertiary, "Estás muy cerca de tu meta mensual.")
                        }
                    }

                    // Recent Transactions
                    SectionHeader(icon = "time-outline", title = "Últimas transacciones")

                    if (data.recentTx.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Ionicon("receipt-outline", 48, colors.outline)
                            Text(
                                "Sin transacciones",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            Text("Agrega tu primer gasto o ingreso", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else {
                        Column(Modifier.padding(bottom = 24.dp)) {
                            data.recentTx.forEachIndexed { i, tx ->
                                val isExpense = tx.isExpense
                                // Los gastos usan el icono y el color (personalizable) de su categoría.
                                val category = if (isExpense) data.categories.firstOrNull { it.name == tx.expense!!.category } else null
                                val iconName = category?.icon?.takeIf { it.isNotEmpty() } ?: if (isExpense) "arrow-up-outline" else "arrow-down-outline"
                                val iconColor = category?.color?.takeIf { it.isNotEmpty() }?.let { hexColor(it) }
                                    ?: if (isExpense) colors.error else colors.secondary
                                val iconBg = category?.color?.takeIf { it.isNotEmpty() }?.let { hexColorWithAlpha(it, 0x20) }
                                    ?: if (isExpense) colors.errorContainer else colors.secondaryContainer
                                val title = if (isExpense) {
                                    tx.expense!!.detail.ifEmpty { tx.expense.category.ifEmpty { "Transacción" } }
                                } else {
                                    tx.income!!.detail.ifEmpty { tx.income.source.ifEmpty { "Transacción" } }
                                }
                                val sub = if (isExpense) tx.expense!!.category else tx.income!!.source.ifEmpty { "" }
                                val date = if (isExpense) tx.expense!!.date else tx.income!!.date
                                val amount = if (isExpense) tx.expense!!.amount else tx.income!!.amount
                                TxRow(
                                    isLast = i == data.recentTx.size - 1,
                                    iconName = iconName,
                                    iconColor = iconColor,
                                    iconBackground = iconBg,
                                    title = title,
                                    subtitle = "$sub · ${formatDate(date)}",
                                    subtitleColor = colors.onSurfaceVariant,
                                    amount = "${if (isExpense) "-" else "+"}${formatCurrency(amount)}",
                                    amountColor = if (isExpense) colors.error else colors.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: String, description: String, onClick: () -> Unit, showBadge: Boolean = false) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .touchableOpacity(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.padding(10.dp)) { Ionicon(icon, 24, colors.onSurface) }
        if (showBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.error, CircleShape)
                    .border(2.dp, colors.surfaceContainerHighest, CircleShape),
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: String, title: String) {
    val colors = AppTheme.colors
    Row(
        Modifier.padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ionicon(icon, 20, colors.onSurfaceVariant)
        Text(title.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun AlertBox(background: Color, icon: String, tint: Color, message: String) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(background, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ionicon(icon, 20, tint)
        Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

/** Fila de lista (txItem): ícono 40×40 + título/subtítulo + monto, con borde inferior salvo la última. */
@Composable
private fun TxRow(
    isLast: Boolean,
    iconName: String,
    iconColor: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    amount: String,
    amountColor: Color,
) {
    val colors = AppTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackground, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Ionicon(iconName, 20, iconColor) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = subtitleColor, modifier = Modifier.padding(top = 2.dp))
            }
            Text(amount, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = amountColor)
        }
        if (!isLast) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
    }
}
