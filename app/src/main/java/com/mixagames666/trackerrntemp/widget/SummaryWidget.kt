package com.mixagames666.trackerrntemp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.unit.ColorProvider
import com.mixagames666.trackerrntemp.MainActivity
import com.mixagames666.trackerrntemp.R
import com.mixagames666.trackerrntemp.TrackerApp
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDateShort
import com.mixagames666.trackerrntemp.util.getMonthName
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.applySavedCurrency
import com.mixagames666.trackerrntemp.domain.getUpcomingPayments

// Port de src/widgets/*: widget "Resumen financiero" (balance total, ingresos y gastos del mes, próximo pago).

/** Datos del widget ya formateados (WidgetSummary de widgetData.ts). */
data class WidgetSummary(
    val appName: String,
    val monthLabel: String,
    val balance: String,
    val isNegative: Boolean,
    val monthIncome: String,
    val monthExpenses: String,
    val nextPayment: String?,
    val updatedAt: String,
)

suspend fun loadWidgetSummary(app: TrackerApp): WidgetSummary {
    val container = app.container
    container.legacyMigration.await() // el widget puede dibujarse antes que la app al arrancar el proceso
    applySavedCurrency(container.repos)
    val now = Time.now()
    val settings = container.repos.settings.get()
    val totals = container.aggregates.getTotalBalance()
    val monthly = container.aggregates.getMonthlySummary(now.year, now.monthValue)
    val upcoming = getUpcomingPayments(container.repos, 30)
    val next = upcoming.firstOrNull { it.daysLeft >= 0 }

    return WidgetSummary(
        appName = settings.appName.ifEmpty { "Mi Dinero" },
        monthLabel = "${getMonthName(now.monthValue)} ${now.year}",
        balance = formatCurrency(totals.balance),
        isNegative = totals.balance < 0,
        monthIncome = formatCurrency(monthly.totalIncome),
        monthExpenses = formatCurrency(monthly.totalExpenses),
        nextPayment = next?.let { "${it.title} · ${formatDateShort(it.dueDate)} · ${formatCurrency(it.amount)}" },
        updatedAt = "%02d:%02d".format(now.hour, now.minute),
    )
}

// Paleta clara/oscura del widget (SummaryWidget.tsx -> PALETTE)
private fun palette(light: Long, dark: Long): ColorProvider = dayNightColorProvider(day = Color(light), night = Color(dark))

private val TextColor = palette(0xFF0F172A, 0xFFF1F5F9)
private val MutedColor = palette(0xFF475569, 0xFF94A3B8)
private val IncomeColor = palette(0xFF059669, 0xFF34D399)
private val ExpenseColor = palette(0xFFE11D48, 0xFFFB7185)
private val AccentColor = palette(0xFF4F46E5, 0xFF818CF8)

class SummaryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetSummary(context.applicationContext as TrackerApp)
        provideContent { WidgetContent(data) }
    }

    companion object {
        /** Redibuja el widget de resumen si está en la pantalla de inicio. */
        suspend fun updateIfPresent(context: Context) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(SummaryWidget::class.java)
            if (ids.isNotEmpty()) SummaryWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(data: WidgetSummary) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = data.appName,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentColor),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(text = data.monthLabel, style = TextStyle(fontSize = 11.sp, color = MutedColor))
        }

        Spacer(GlanceModifier.defaultWeight())

        Column {
            Text(text = "Balance total", style = TextStyle(fontSize = 11.sp, color = MutedColor))
            Text(
                text = data.balance,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (data.isNegative) ExpenseColor else TextColor),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        Row(GlanceModifier.fillMaxWidth()) {
            Column(GlanceModifier.defaultWeight()) {
                Text(text = "Ingresos del mes", style = TextStyle(fontSize = 10.sp, color = MutedColor))
                Text(text = data.monthIncome, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IncomeColor), maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(text = "Gastos del mes", style = TextStyle(fontSize = 10.sp, color = MutedColor))
                Text(text = data.monthExpenses, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ExpenseColor), maxLines = 1)
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        Text(
            text = if (data.nextPayment != null) "Próximo pago: ${data.nextPayment}" else "Actualizado ${data.updatedAt}",
            style = TextStyle(fontSize = 10.sp, color = MutedColor),
            maxLines = 1,
        )
    }
}

/**
 * Receptor del widget. Se llama `Resumen` como el widget de la versión React Native
 * (react-native-android-widget generaba `.widget.Resumen`), así al actualizar sobre esa versión el widget
 * que el usuario ya colocó en su pantalla de inicio sigue funcionando.
 */
class Resumen : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}
