package com.mixagames666.trackerrntemp.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.domain.ANALYTICS_COLORS
import com.mixagames666.trackerrntemp.domain.AnalyticsPeriod
import com.mixagames666.trackerrntemp.domain.ComparisonOutcome
import com.mixagames666.trackerrntemp.domain.ComparisonResult
import com.mixagames666.trackerrntemp.ui.charts.SimplePieChart
import com.mixagames666.trackerrntemp.ui.charts.SpendingLineChart
import com.mixagames666.trackerrntemp.ui.components.Alerts
import com.mixagames666.trackerrntemp.ui.components.AppButton
import com.mixagames666.trackerrntemp.ui.components.AppCard
import com.mixagames666.trackerrntemp.ui.components.AppTextField
import com.mixagames666.trackerrntemp.ui.components.AutoSizeText
import com.mixagames666.trackerrntemp.ui.components.BottomSheet
import com.mixagames666.trackerrntemp.ui.components.Ionicon
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.screens.appViewModel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.ui.theme.topInset
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.getCurrencySymbol
import com.mixagames666.trackerrntemp.util.getMonthName
import com.mixagames666.trackerrntemp.util.jsDate
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// Port de screens/AnalyticsScreen.tsx

private class PickerConfig(
    val title: String,
    val options: List<String>,
    val value: String,
    val onSelect: (String) -> Unit,
)

private fun pad2(n: Int) = n.toString().padStart(2, '0')

/** Los últimos 24 meses con el desborde de `Date.setMonth` (un día 31 puede repetir o saltar meses). */
private fun recentMonths(): List<String> {
    val list = mutableListOf<String>()
    var d = Time.today()
    repeat(24) {
        list.add("${d.year}-${pad2(d.monthValue)}")
        d = jsDate(d.year, d.monthValue - 2, d.dayOfMonth)
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(container: AppContainer) {
    val colors = AppTheme.colors
    val isDark = AppTheme.isDark
    val scope = rememberCoroutineScope()
    val vm = appViewModel(container) { AnalyticsViewModel(it) }
    val ui by vm.state.collectAsState()
    val data = ui.data

    // Se recalcula con rotaciones, pantallas plegables o multiventana.
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val chartWidth = (screenWidth - 32).dp

    // Comparison state
    val now = Time.today()
    var compareP1 by remember { mutableStateOf("${now.year}-${pad2(now.monthValue)}") }
    var compareP2 by remember { mutableStateOf("${now.year}-${pad2(max(1, now.monthValue - 1))}") }
    var compCategory by remember { mutableStateOf("") }
    var compMethod by remember { mutableStateOf("") }
    var compIncomeSource by remember { mutableStateOf("") }
    var comparisonResult by remember { mutableStateOf<ComparisonResult?>(null) }
    val recentMonthsList = remember { recentMonths() }

    var pickerVisible by remember { mutableStateOf(false) }
    var pickerConfig by remember { mutableStateOf(PickerConfig("", emptyList(), "") {}) }

    var isInfoOpen by remember { mutableStateOf(false) }
    var infoText by remember { mutableStateOf("") }

    // useFocusEffect(…, [period])
    LaunchedEffect(ui.period) { vm.load() }

    fun showPicker(title: String, options: List<String>, value: String, onSelect: (String) -> Unit) {
        pickerConfig = PickerConfig(title, options, value, onSelect)
        pickerVisible = true
    }

    fun handleComparison() {
        scope.launch {
            when (val outcome = vm.compare(compareP1, compareP2, compCategory, compMethod, compIncomeSource)) {
                null -> Unit
                is ComparisonOutcome.Invalid -> Alerts.alert("Error", outcome.message)
                is ComparisonOutcome.Result -> comparisonResult = outcome.value
            }
        }
    }

    fun showInfo(type: String) {
        infoText = when (type) {
            "spent" -> "Suma total de todos los gastos registrados en el período seleccionado (diario: últimos 30 días, semanal: últimas 12 semanas, mensual: últimos 12 meses)."
            "saved" -> "Diferencia entre ingresos totales y gastos totales del período. Ahorro = Ingresos - Gastos. Si es negativo se muestra en rojo."
            else -> "Gasto promedio por día. Se calcula dividiendo el total gastado entre la cantidad de días del período."
        }
        isInfoOpen = true
    }

    val totalSpent = data?.totalSpent ?: 0.0
    val totalIncome = data?.totalIncome ?: 0.0
    val daysInPeriod = when (ui.period) {
        AnalyticsPeriod.DAILY -> 30
        AnalyticsPeriod.WEEKLY -> 84
        AnalyticsPeriod.MONTHLY -> 365
    }
    val avgSpent = totalSpent / max(1, daysInPeriod)
    val saved = totalIncome - totalSpent

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = topInset()),
    ) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Análisis", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        }

        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp)),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Toggle Period
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainer, RoundedCornerShape(20.dp))
                        .padding(4.dp),
                ) {
                    listOf(AnalyticsPeriod.DAILY to "Diario", AnalyticsPeriod.WEEKLY to "Semanal", AnalyticsPeriod.MONTHLY to "Mensual").forEach { (p, label) ->
                        val active = ui.period == p
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) colors.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
                                .touchableOpacity(role = null) { vm.setPeriod(p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (active) colors.onPrimary else colors.onSurfaceVariant)
                        }
                    }
                }

                // Overview Stats
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Gastado", formatCurrency(totalSpent), colors.error, onInfo = { showInfo("spent") }, modifier = Modifier.weight(1f))
                    StatCard("Ahorrado", formatCurrency(saved), if (saved < 0) colors.error else colors.secondary, onInfo = { showInfo("saved") }, modifier = Modifier.weight(1f))
                    StatCard("Promedio/día", formatCurrency(avgSpent), colors.tertiary, onInfo = { showInfo("avg") }, modifier = Modifier.weight(1f))
                }

                // Charts
                Column {
                    SectionTitle("trending-up", "Gastos en el tiempo")
                    if (data != null) {
                        SpendingLineChart(
                            labels = data.labels,
                            data = data.expData,
                            lineColor = hexColor("#f43f5e"),
                            labelColor = if (isDark) hexColor("#94a3b8") else hexColor("#64748b"),
                            yPrefix = getCurrencySymbol(),
                            backgroundColor = colors.background,
                            visibleWidth = chartWidth,
                            onPointClick = { index, value ->
                                Alerts.alert(data.labels[index], "Total gastado: ${formatCurrency(value)}")
                            },
                        )
                    }
                }

                Column {
                    SectionTitle("pie-chart", "Por categoría")
                    if (data != null && data.pieCategory.isNotEmpty()) {
                        SimplePieChart(data.pieCategory, colors.onSurfaceVariant, chartWidth)
                    } else {
                        NoData()
                    }
                }

                Column {
                    SectionTitle("card", "Por método de pago")
                    if (data != null && data.pieMethod.isNotEmpty()) {
                        SimplePieChart(data.pieMethod, colors.onSurfaceVariant, chartWidth)
                    } else {
                        NoData()
                    }
                }

                Column {
                    SectionTitle("business", "Gastos por cuenta")
                    AppCard(contentPadding = 16.dp, bottomMargin = 16.dp) {
                        val rows = data?.byAccountExpense.orEmpty()
                        if (rows.isNotEmpty()) {
                            val maxVal = rows.maxOf { it.second }
                            rows.forEachIndexed { i, (label, value) ->
                                val color = hexColor(ANALYTICS_COLORS[i % ANALYTICS_COLORS.size])
                                val pct = if (maxVal > 0) value / maxVal else 0.0
                                Column(Modifier.padding(bottom = 12.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(label, color = colors.onSurface, fontSize = 14.sp)
                                        Text(formatCurrency(value), color = colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.surfaceContainerHighest, RoundedCornerShape(6.dp)),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(pct.toFloat().coerceIn(0f, 1f))
                                                .height(12.dp)
                                                .background(color, RoundedCornerShape(6.dp)),
                                        )
                                    }
                                }
                            }
                        } else {
                            NoData()
                        }
                    }
                }

                // Comparison
                Column(Modifier.padding(top = 16.dp)) {
                    SectionTitle("swap-horizontal", "Comparativa mensual")

                    AppCard {
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                AppTextField("Período 1", compareP1, {}, placeholder = "YYYY-MM", onPress = {
                                    showPicker("Seleccionar Período 1", recentMonthsList, compareP1) { compareP1 = it }
                                })
                            }
                            Box(Modifier.weight(1f)) {
                                AppTextField("Período 2", compareP2, {}, placeholder = "YYYY-MM", onPress = {
                                    showPicker("Seleccionar Período 2", recentMonthsList, compareP2) { compareP2 = it }
                                })
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                AppTextField("Filtro Categoría", compCategory.ifEmpty { "Todas" }, {}, placeholder = "Todas", onPress = {
                                    showPicker("Seleccionar Categoría", listOf("Todas") + (data?.optCategories.orEmpty()), compCategory.ifEmpty { "Todas" }) {
                                        compCategory = if (it == "Todas") "" else it
                                    }
                                })
                            }
                            Box(Modifier.weight(1f)) {
                                AppTextField("Filtro de Pago", compMethod.ifEmpty { "Todos" }, {}, placeholder = "Todos", onPress = {
                                    showPicker("Seleccionar Método", listOf("Todos") + (data?.optMethods.orEmpty()), compMethod.ifEmpty { "Todos" }) {
                                        compMethod = if (it == "Todos") "" else it
                                    }
                                })
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                AppTextField("Filtro de Ingresos", compIncomeSource.ifEmpty { "Todas" }, {}, placeholder = "Todas las fuentes", onPress = {
                                    showPicker("Seleccionar Ingreso", listOf("Todas") + (data?.optIncomeSources.orEmpty()), compIncomeSource.ifEmpty { "Todas" }) {
                                        compIncomeSource = if (it == "Todas") "" else it
                                    }
                                })
                            }
                        }
                        AppButton("Comparar", onClick = { handleComparison() }, modifier = Modifier.fillMaxWidth(), icon = "analytics")

                        comparisonResult?.let { r -> ComparisonSummary(r) }
                    }
                }
            }
        }
    }

    BottomSheet(visible = isInfoOpen, onClose = { isInfoOpen = false }, title = "¿Cómo se calcula?") {
        Text(infoText, fontSize = 16.sp, lineHeight = 24.sp, color = colors.onSurface, modifier = Modifier.padding(bottom = 24.dp))
        AppButton("Entendido", onClick = { isInfoOpen = false }, modifier = Modifier.fillMaxWidth())
    }

    BottomSheet(visible = pickerVisible, onClose = { pickerVisible = false }, title = pickerConfig.title) {
        Column(
            Modifier
                .height(300.dp.coerceAtMost((pickerConfig.options.size * 55).dp))
                .verticalScroll(rememberScrollState()),
        ) {
            pickerConfig.options.forEach { opt ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .touchableOpacity(role = null) {
                                pickerConfig.onSelect(opt)
                                pickerVisible = false
                            }
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(opt, fontSize = 16.sp, color = if (pickerConfig.value == opt) colors.primary else colors.onSurface)
                        if (pickerConfig.value == opt) Ionicon("checkmark", 20, colors.primary)
                    }
                    HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, onInfo: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier, contentPadding = 0.dp, bottomMargin = 0.dp) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                AutoSizeText(
                    text = value, fontSize = 16.sp, color = valueColor, fontWeight = FontWeight.Bold,
                    minimumScale = 0.6f, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .touchableOpacity(onClick = onInfo)
                    .padding(4.dp),
            ) { Ionicon("information-circle", 16, colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionTitle(icon: String, title: String) {
    val colors = AppTheme.colors
    Row(
        Modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ionicon(icon, 20, colors.onSurface)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    }
}

@Composable
private fun NoData() {
    Text(
        "Sin datos",
        textAlign = TextAlign.Center,
        color = AppTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}

@Composable
private fun ComparisonSummary(r: ComparisonResult) {
    val colors = AppTheme.colors
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MonthBox(Modifier.weight(1f), "${getMonthName(r.m1)} ${r.y1}", r.expenses1, r.income1)
            MonthBox(Modifier.weight(1f), "${getMonthName(r.m2)} ${r.y2}", r.expenses2, r.income2)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHighest, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Resultado Comparativo", fontSize = 13.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            val percent = JsCompat.numberToString(abs(r.diffPercent))
            Text(
                text = when {
                    r.diff > 0 -> "Has gastado más en el Período 1 respecto al Período 2.\nEl sobrecosto aumentó un $percent%."
                    r.diff < 0 -> "¡Excelente! Has ahorrado en el Período 1 frente al Período 2.\nTus gastos se redujeron un $percent%."
                    else -> "El volumen de gastos es idéntico en ambos períodos."
                },
                textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                "${if (r.diff > 0) "+" else ""}${formatCurrency(r.diff)}",
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (r.diff > 0) colors.error else colors.secondary,
            )
        }
    }
}

@Composable
private fun MonthBox(modifier: Modifier, title: String, expenses: Double, income: Double) {
    val colors = AppTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(formatCurrency(expenses), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.error, modifier = Modifier.padding(vertical = 4.dp))
        Text("Ingreso: ${formatCurrency(income)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
    }
}
