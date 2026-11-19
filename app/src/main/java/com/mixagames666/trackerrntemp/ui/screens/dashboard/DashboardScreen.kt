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
import com.mixagames666.trackerrntemp.ui.components.EmptyState
import com.mixagames666.trackerrntemp.ui.components.Ionicon
import com.mixagames666.trackerrntemp.ui.components.MdiPiggyBank
import com.mixagames666.trackerrntemp.ui.components.ProgressVariant
import com.mixagames666.trackerrntemp.ui.components.WelcomeSheet
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.screens.appViewModel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.MoneyStyle
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.ui.theme.hexColorWithAlpha
import com.mixagames666.trackerrntemp.ui.theme.topInset
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.formatPercentLocalized
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
    val savingsColor = if ((balance?.balance ?: 0.0) < 0) colors.errorText else colors.successText
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
                        Ionicon("cash-outline", 48, colors.successText)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Parece que ya llegó tu fecha de cobro. ¿Ya recibiste el abono de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.successText)) { append(formatCurrency(prompt.amount)) }
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
                EmptyState(
                    icon = "checkmark-circle-outline",
                    title = "No tienes pagos próximos",
                    message = "Aquí verás cuotas de préstamos, tarjetas y gastos recurrentes de los próximos 30 días",
                )
            } else {
                upcoming.forEachIndexed { i, payment ->
                    val statusColor = when {
                        payment.daysLeft < 0 -> colors.errorText
                        payment.daysLeft <= 3 -> colors.warningText
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
                        Text("Balance total", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, letterSpacing = 0.2.sp)
                        AutoSizeText(
                            text = formatCurrency(data.balance.balance),
                            fontSize = 38.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            minimumScale = 0.5f,
                            style = MoneyStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BalancePill("arrow-down-circle", "Ingresos: ${formatCurrency(data.balance.totalIncome)}")
                            BalancePill("arrow-up-circle", "Gastos: ${formatCurrency(data.balance.totalExpenses)}")
                        }
                    }

                    // Quick Stats
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = { MdiPiggyBank(26, savingsColor) },
                            tint = savingsColor,
                            label = "Ahorro",
                            value = formatCurrency(data.balance.balance),
                            valueColor = savingsColor,
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = { Ionicon("card", 26, colors.warningText) },
                            tint = colors.warningText,
                            label = "Deuda activa",
                            value = formatCurrency(data.totalLoanDebt),
                            valueColor = colors.warningText,
                        )
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
                                    color = colors.successText,
                                    fontWeight = FontWeight.Bold,
                                    minimumScale = 0.6f,
                                    style = MoneyStyle,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Gastos del mes", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                AutoSizeText(
                                    text = formatCurrency(data.monthly.totalExpenses),
                                    fontSize = 18.sp,
                                    color = colors.errorText,
                                    fontWeight = FontWeight.Bold,
                                    minimumScale = 0.6f,
                                    style = MoneyStyle,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (monthlyGoal > 0) {
                            // El objetivo es ahorrar: la barra mide cuánto del tope máximo de gasto ya se usó.
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Límite de gastos mensual", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                Text(formatCurrency(monthlyGoal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            }
                        }

                        AppProgressBar(
                            progress = goalPercent,
                            variant = when (goalStatus) {
                                GoalStatus.Success -> ProgressVariant.Success
                                GoalStatus.Warning -> ProgressVariant.Warning
                                GoalStatus.Danger -> ProgressVariant.Danger
                            },
                            modifier = Modifier.padding(top = if (monthlyGoal > 0) 8.dp else 12.dp, bottom = 12.dp),
                        )

                        val spent = data.monthly.totalExpenses
                        Text(
                            text = when {
                                monthlyGoal > 0 && spent <= monthlyGoal ->
                                    "Has usado el ${formatPercentLocalized(goalPercent)} de tu límite · Te quedan ${formatCurrency(max(0.0, monthlyGoal - spent))}"
                                monthlyGoal > 0 ->
                                    "Has usado el ${formatPercentLocalized(goalPercent)} de tu límite · Te pasaste por ${formatCurrency(spent - monthlyGoal)}"
                                data.monthly.totalIncome > 0 -> "Has gastado el ${formatPercentLocalized(goalPercent)} de tus ingresos del mes"
                                else -> "Aún no hay ingresos registrados este mes"
                            },
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (monthlyGoal <= 0) {
                            // Heurísticas 6 y 10: sin límite definido, la propia tarjeta explica cómo establecerlo.
                            LimitPrompt(onClick = onOpenSettings)
                        }
                        if (goalStatus == GoalStatus.Danger && monthlyGoal > 0) {
                            AlertBox(colors.errorContainer, "alert-circle", colors.error, colors.onErrorContainer, "¡Superaste tu límite de gastos mensual!")
                        }
                        if (goalStatus == GoalStatus.Warning && monthlyGoal > 0) {
                            AlertBox(colors.tertiaryContainer, "warning", colors.tertiaryDark, colors.onTertiaryContainer, "Estás muy cerca de tu límite de gastos mensual.")
                        }
                    }

                    // Recent Transactions
                    SectionHeader(icon = "time-outline", title = "Últimas transacciones")

                    if (data.recentTx.isEmpty()) {
                        EmptyState(
                            icon = "receipt-outline",
                            title = "Sin transacciones",
                            message = "Agrega tu primer gasto o ingreso desde las pestañas Gastos e Ingresos",
                        )
                    } else {
                        Column(Modifier.padding(bottom = 24.dp)) {
                            data.recentTx.forEachIndexed { i, tx ->
                                val isExpense = tx.isExpense
                                // Los gastos usan el icono y el color (personalizable) de su categoría.
                                val category = if (isExpense) data.categories.firstOrNull { it.name == tx.expense!!.category } else null
                                val iconName = category?.icon?.takeIf { it.isNotEmpty() } ?: if (isExpense) "arrow-up-outline" else "arrow-down-outline"
                                val iconColor = category?.color?.takeIf { it.isNotEmpty() }?.let { hexColor(it) }
                                    ?: if (isExpense) colors.onErrorContainer else colors.onSecondaryContainer
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
                                    amountColor = if (isExpense) colors.errorText else colors.successText,
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
        Box(Modifier.padding(12.dp)) { Ionicon(icon, 24, colors.onSurface) } // 48 dp: objetivo táctil recomendado
        if (showBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
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
        Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = colors.onSurfaceVariant)
    }
}

/** Píldora translúcida sobre el degradado de la tarjeta de balance (ingresos / gastos totales). */
@Composable
private fun BalancePill(icon: String, text: String) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ionicon(icon, 16, Color.White)
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = MoneyStyle)
    }
}

/** Tarjeta de cifra clave: ícono en un círculo tintado, etiqueta y valor (con el color que corresponde al signo). */
@Composable
private fun StatTile(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    tint: Color,
    label: String,
    value: String,
    valueColor: Color,
) {
    val colors = AppTheme.colors
    AppCard(modifier, contentPadding = 0.dp, bottomMargin = 0.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            AutoSizeText(
                text = value,
                fontSize = 18.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                minimumScale = 0.6f,
                textAlign = TextAlign.Center,
                style = MoneyStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

/** Invitación a fijar el límite de gastos mensual cuando aún no hay uno (abre Configuración). */
@Composable
private fun LimitPrompt(onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(colors.primaryContainer, shape)
            .touchableOpacity(onClickLabel = "Definir límite de gastos mensual", onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ionicon("speedometer", 20, colors.primary)
        Text(
            text = "Define un límite de gastos mensual para cuidar tu ahorro",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Ionicon("chevron-forward", 18, colors.primary)
    }
}

/** Aviso sobre un contenedor pastel: el ícono usa el tono fuerte y el texto un tono oscuro que se lee ≥ 4,5:1. */
@Composable
private fun AlertBox(background: Color, icon: String, tint: Color, textColor: Color, message: String) {
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
        Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
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
            Text(amount, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = amountColor, style = MoneyStyle)
        }
        if (!isLast) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
    }
}
