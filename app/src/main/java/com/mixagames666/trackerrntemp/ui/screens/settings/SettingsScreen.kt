package com.mixagames666.trackerrntemp.ui.screens.settings

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.data.Category
import com.mixagames666.trackerrntemp.data.DEFAULT_EXPENSE_CATEGORIES
import com.mixagames666.trackerrntemp.data.Settings
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.reports.ReportFormat
import com.mixagames666.trackerrntemp.reports.ReportPeriod
import com.mixagames666.trackerrntemp.reports.ReportType
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
import com.mixagames666.trackerrntemp.ui.components.CurrencyPicker
import com.mixagames666.trackerrntemp.ui.components.FieldKeyboard
import com.mixagames666.trackerrntemp.ui.components.Ionicon
import com.mixagames666.trackerrntemp.ui.components.ToastManager
import com.mixagames666.trackerrntemp.ui.components.touchableOpacity
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.bottomInset
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.ui.theme.hexColorWithAlpha
import com.mixagames666.trackerrntemp.util.CurrencyConfig
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.detectDeviceCurrency
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.getCurrencyConfig
import com.mixagames666.trackerrntemp.util.getCurrencyOption
import com.mixagames666.trackerrntemp.util.getCurrencySymbol
import com.mixagames666.trackerrntemp.util.initCurrency
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.saveCurrency
import kotlinx.coroutines.launch

// Port de screens/SettingsScreen.tsx

// Colores hex de 6 dígitos: Chip y las listas les agregan sufijos de opacidad ('20', '40').
private val CATEGORY_PALETTE = listOf(
    "#ef4444", "#f97316", "#f59e0b", "#eab308", "#84cc16", "#22c55e",
    "#10b981", "#14b8a6", "#06b6d4", "#0ea5e9", "#3b82f6", "#6366f1",
    "#8b5cf6", "#a855f7", "#d946ef", "#ec4899", "#f43f5e", "#64748b",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val colors = AppTheme.colors
    val isDark = AppTheme.isDark
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val repos = container.repos

    var settings by remember { mutableStateOf(Settings()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var currencyConfig by remember { mutableStateOf(getCurrencyConfig()) }

    var isNameSheetOpen by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var isGoalSheetOpen by remember { mutableStateOf(false) }
    var tempGoal by remember { mutableStateOf("") }
    var isWipeSheetOpen by remember { mutableStateOf(false) }

    // Taxes
    var isTaxesSheetOpen by remember { mutableStateOf(false) }
    var taxesEnabled by remember { mutableStateOf(false) }
    var ivaRate by remember { mutableStateOf("15") }
    var comisionRate by remember { mutableStateOf("2") }
    var isdRate by remember { mutableStateOf("5") }
    var taxTargets by remember { mutableStateOf<List<String>>(emptyList()) }

    // Reports
    var isReportSheetOpen by remember { mutableStateOf(false) }
    var reportFormat by remember { mutableStateOf(ReportFormat.PDF) }
    var reportType by remember { mutableStateOf(ReportType.ALL) }
    var reportPeriod by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    var isGeneratingReport by remember { mutableStateOf(false) }

    // Moneda y colores de categorías
    var isCurrencySheetOpen by remember { mutableStateOf(false) }
    var isColorsSheetOpen by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    fun syncAfterChange() = container.dataSync.syncAfterDataChange()

    suspend fun loadSettings() {
        val s = repos.settings.get()
        categories = repos.categories.getAll()
        settings = s
        currencyConfig = getCurrencyConfig()
        tempName = s.appName
        tempGoal = JsCompat.numberToString(s.monthlyGoal)
        s.taxes?.let { t ->
            taxesEnabled = t.enabled
            ivaRate = JsCompat.numberToString(t.ivaRate)
            comisionRate = JsCompat.numberToString(t.comisionRate)
            isdRate = JsCompat.numberToString(t.isdRate)
            taxTargets = t.applyTo
        }
    }

    // useEffect(() => loadSettings(), [isDark])
    LaunchedEffect(isDark) { loadSettings() }

    val currencyCode = currencyConfig.currency
    val currencyOption = getCurrencyOption(currencyCode)
    val deviceCurrency = remember { detectDeviceCurrency() }
    val expenseCategories = categories.filter { it.type == "expense" }

    // ---- Importar backup (expo-document-picker) ----
    fun performImport(data: kotlinx.serialization.json.JsonObject) {
        scope.launch {
            container.backup.importAllData(data)
            repos.categories.seedDefaults()
            // El backup puede traer otra moneda en su configuración.
            initCurrency(repos)
            Alerts.alert("Éxito", "Datos importados correctamente")
            loadSettings()
            syncAfterChange()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val parsed = container.backupFiles.readBackup(uri)
                if (parsed == null) {
                    Alerts.alert("Error", "Archivo no válido o corrupto")
                    return@launch
                }
                if (container.backup.hasAnyData()) {
                    Alerts.alert(
                        "Datos existentes detectados",
                        "Tus datos actuales serán sobreescribidos y no podrás recuperarlos. ¿Continuar?",
                        listOf(
                            AlertButton("Cancelar", AlertButtonStyle.Cancel),
                            AlertButton("Sobreescribir", AlertButtonStyle.Destructive) { performImport(parsed) },
                        ),
                    )
                } else {
                    performImport(parsed)
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "No se pudo importar el archivo", e)
                Alerts.alert("Error", "No se pudo importar el archivo")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomInset() + 24.dp),
    ) {
        // PREFERENCES
        Section("PREFERENCIAS") {
            // Tema
            SettingsRow(
                icon = "moon", iconBackground = colors.primaryContainer, iconColor = colors.primary,
                title = "Tema de la app", subtitle = "Actual: ${if (isDark) "Oscuro" else "Claro"}",
                trailing = {
                    ThemeToggle(isDark = isDark, onSelect = { container.theme.setTheme(it) })
                },
            )
            SettingsRow(
                icon = "pencil", iconBackground = colors.primaryContainer, iconColor = colors.primary,
                title = "Nombre de la app", subtitle = settings.appName,
                onClick = { isNameSheetOpen = true }, chevron = true,
            )
            SettingsRow(
                icon = "cash", iconBackground = colors.primaryContainer, iconColor = colors.primary,
                title = "Moneda",
                subtitle = "${if (currencyOption != null) "${currencyOption.name} ($currencyCode)" else currencyCode} · ${formatCurrency(1234.5)}",
                subtitleMaxLines = 1,
                onClick = { isCurrencySheetOpen = true }, chevron = true,
            )
            SettingsRow(
                icon = "flag", iconBackground = colors.primaryContainer, iconColor = colors.primary,
                title = "Meta de gasto mensual",
                subtitle = if (settings.monthlyGoal != 0.0) formatCurrency(settings.monthlyGoal) else "No definida",
                onClick = { isGoalSheetOpen = true }, chevron = true, last = true,
            )
        }

        // TAXES ENGINE
        Section("FINANZAS") {
            SettingsRow(
                icon = "calculator", iconBackground = colors.secondaryContainer, iconColor = colors.secondary,
                title = "Motor de Impuestos",
                subtitle = settings.taxes?.takeIf { it.enabled }?.let {
                    "Activo (IVA ${JsCompat.numberToString(it.ivaRate)}%, Com. ${JsCompat.numberToString(it.comisionRate)}%)"
                } ?: "Desactivado",
                onClick = { isTaxesSheetOpen = true }, chevron = true, last = true,
            )
        }

        // CATEGORIES
        Section("CATEGORÍAS") {
            SettingsRow(
                icon = "color-palette", iconBackground = colors.tertiaryContainer, iconColor = colors.tertiary,
                title = "Colores de categorías", subtitle = null,
                subtitleContent = {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        expenseCategories.take(11).forEach { c ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(hexColor(c.color), CircleShape),
                            )
                        }
                    }
                },
                onClick = { isColorsSheetOpen = true }, chevron = true, last = true,
            )
        }

        // REPORTS
        Section("REPORTES") {
            SettingsRow(
                icon = "document-text", iconBackground = colors.secondaryContainer, iconColor = colors.secondary,
                title = "Generación de Reportes", subtitle = "PDF Gráfico o exportación CSV",
                onClick = { isReportSheetOpen = true }, chevron = true, last = true,
            )
        }

        // DATA
        Section("DATOS") {
            SettingsRow(
                icon = "cloud-download", iconBackground = colors.primaryContainer, iconColor = colors.primary,
                title = "Exportar backup", subtitle = "Exporta en formato JSON",
                onClick = {
                    scope.launch {
                        try {
                            if (!container.backupFiles.exportAndShare()) {
                                Alerts.alert("No soportado", "Exportar archivos no está soportado en este dispositivo.")
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "No se pudo exportar el backup", e)
                            Alerts.alert("Error", "No se pudo exportar el backup")
                        }
                    }
                },
                trailingIcon = "download-outline",
            )
            SettingsRow(
                icon = "cloud-upload", iconBackground = colors.primaryContainer, iconColor = colors.primary,
                title = "Importar backup", subtitle = "Restaura datos desde JSON",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                trailingIcon = "push-outline", last = true,
            )
        }

        // DANGER ZONE
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "ZONA DE PELIGRO", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = colors.error,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            AppCard(contentPadding = 0.dp, borderColor = colors.error, borderWidth = 1.dp) {
                SettingsRow(
                    icon = "warning", iconBackground = colors.errorContainer, iconColor = colors.error,
                    title = "Eliminar todos los datos", titleColor = colors.error, subtitle = "No se puede deshacer",
                    onClick = { isWipeSheetOpen = true }, trailingIcon = "trash-outline", trailingColor = colors.error, last = true,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // ---- Modals ----
    BottomSheet(visible = isNameSheetOpen, onClose = { isNameSheetOpen = false }, title = "Nombre de la app") {
        AppTextField(
            label = "Nombre (máx. 30 caracteres)", placeholder = "Ej. Mi Dinero", value = tempName,
            onValueChange = { tempName = it }, maxLength = 30, autoFocus = true,
        )
        AppButton("Guardar", modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                if (tempName.trim().isEmpty()) {
                    Alerts.alert("Error", "Ingresa un nombre válido")
                    return@launch
                }
                repos.settings.save { it.copy(appName = tempName.trim()) }
                isNameSheetOpen = false
                loadSettings()
                syncAfterChange()
            }
        })
    }

    BottomSheet(visible = isGoalSheetOpen, onClose = { isGoalSheetOpen = false }, title = "Meta mensual") {
        AppTextField(
            label = "Monto máximo (${getCurrencySymbol()})", placeholder = "Ej. 1000.00", value = tempGoal,
            onValueChange = { tempGoal = it }, keyboard = FieldKeyboard.Decimal, autoFocus = true,
        )
        AppButton("Guardar", modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                val parsed = JsCompat.parseFloat(tempGoal)
                if (parsed.isNaN() || parsed < 0) {
                    Alerts.alert("Error", "Monto inválido")
                    return@launch
                }
                repos.settings.save { it.copy(monthlyGoal = parsed) }
                isGoalSheetOpen = false
                loadSettings()
                syncAfterChange()
            }
        })
    }

    BottomSheet(visible = isTaxesSheetOpen, onClose = { isTaxesSheetOpen = false }, title = "Motor de Impuestos") {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .touchableOpacity(role = null) { taxesEnabled = !taxesEnabled }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Habilitar Cálculo de Impuestos", color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Ionicon(if (taxesEnabled) "toggle" else "toggle-outline", 32, if (taxesEnabled) colors.primary else colors.onSurfaceVariant)
            }
            HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            Spacer(Modifier.height(16.dp))

            if (taxesEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { AppTextField("Comisión (%)", comisionRate, { comisionRate = it }, keyboard = FieldKeyboard.Decimal) }
                        Box(Modifier.weight(1f)) { AppTextField("IVA (%)", ivaRate, { ivaRate = it }, keyboard = FieldKeyboard.Decimal) }
                        Box(Modifier.weight(1f)) { AppTextField("ISD (%)", isdRate, { isdRate = it }, keyboard = FieldKeyboard.Decimal) }
                    }

                    Text("¿Dónde quieres aplicar deducciones automáticas?", color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Préstamos", "Tarjetas de Crédito", "Inversiones").forEach { t ->
                            TaxPill(t, taxTargets.contains(t)) { taxTargets = if (taxTargets.contains(t)) taxTargets - t else taxTargets + t }
                        }
                    }

                    Text("O en Gastos de categorías específicas:", color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                    FlowRow(
                        Modifier.padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { c ->
                            val target = "Gastos:${c.name}"
                            TaxPill(c.name, taxTargets.contains(target)) { taxTargets = if (taxTargets.contains(target)) taxTargets - target else taxTargets + target }
                        }
                    }
                }
            }

            AppButton("Guardar cambios", modifier = Modifier.fillMaxWidth().padding(top = 16.dp), onClick = {
                scope.launch {
                    repos.settings.save {
                        it.copy(
                            taxes = TaxesConfig(
                                enabled = taxesEnabled,
                                ivaRate = JsCompat.parseFloat(ivaRate.replace(',', '.')).orZero(),
                                comisionRate = JsCompat.parseFloat(comisionRate.replace(',', '.')).orZero(),
                                isdRate = JsCompat.parseFloat(isdRate.replace(',', '.')).orZero(),
                                applyTo = taxTargets,
                            ),
                        )
                    }
                    isTaxesSheetOpen = false
                    loadSettings()
                }
            })
        }
    }

    BottomSheet(visible = isReportSheetOpen, onClose = { isReportSheetOpen = false }, title = "Generar Reporte") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Formato de Salida", fontSize = 13.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatBox(Modifier.weight(1f), "document", "PDF", reportFormat == ReportFormat.PDF) { reportFormat = ReportFormat.PDF }
                    FormatBox(Modifier.weight(1f), "grid", "CSV", reportFormat == ReportFormat.CSV) { reportFormat = ReportFormat.CSV }
                }
            }

            Column {
                Text("Tipo de Datos a Incluir", fontSize = 13.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportType.entries.forEach { tipo ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp))
                                .touchableOpacity(role = null) { reportType = tipo }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Ionicon(if (reportType == tipo) "radio-button-on" else "radio-button-off", 20, colors.primary)
                            Text(
                                text = if (tipo == ReportType.ALL) "Ingresos y Gastos (Completo)" else tipo.wireName.replaceFirstChar { it.uppercase() },
                                color = colors.onSurface,
                            )
                        }
                    }
                }
            }

            Column {
                Text("Período", fontSize = 13.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                ChipFlow {
                    ReportPeriod.entries.forEach { p -> Chip(label = p.label, active = reportPeriod == p, onClick = { reportPeriod = p }) }
                }
            }

            AppButton(
                title = "Generar Reporte", icon = "download", loading = isGeneratingReport,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    scope.launch {
                        isGeneratingReport = true
                        try {
                            val data = container.reports.loadData(reportType, reportPeriod)
                            val shared = if (reportFormat == ReportFormat.CSV) container.reports.generateCsv(data) else container.reports.generatePdf(data)
                            if (!shared) {
                                Alerts.alert("No disponible", "Compartir archivos no está soportado en este dispositivo.")
                            } else {
                                isReportSheetOpen = false
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Error al generar el reporte", e)
                            Alerts.alert("Error", "Hubo un error al generar el reporte.")
                        } finally {
                            isGeneratingReport = false
                        }
                    }
                },
            )
        }
    }

    BottomSheet(visible = isCurrencySheetOpen, onClose = { isCurrencySheetOpen = false }, title = "Moneda") {
        fun selectCurrency(currency: String, locale: String) {
            scope.launch {
                saveCurrency(repos, CurrencyConfig(currency, locale))
                isCurrencySheetOpen = false
                ToastManager.show("Moneda actualizada")
                loadSettings()
                syncAfterChange()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                .touchableOpacity(role = null) { selectCurrency(deviceCurrency.currency, deviceCurrency.locale) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ionicon("phone-portrait-outline", 20, colors.primary)
            Text("Usar la del dispositivo (${deviceCurrency.currency})", fontSize = 15.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
        }
        CurrencyPicker(selectedCode = currencyCode, onSelect = { opt -> selectCurrency(opt.code, opt.locale) })
    }

    BottomSheet(
        visible = isColorsSheetOpen,
        onClose = {
            isColorsSheetOpen = false
            editingCategory = null
        },
        title = editingCategory?.let { "Color de ${it.name}" } ?: "Colores de categorías",
    ) {
        val editing = editingCategory
        if (editing != null) {
            Column {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CATEGORY_PALETTE.forEach { color ->
                        val selected = editing.color.lowercase() == color
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(hexColor(color), CircleShape)
                                .border(3.dp, if (selected) colors.onSurface else Color.Transparent, CircleShape)
                                .touchableOpacity(role = null) {
                                    scope.launch {
                                        repos.categories.update(editing.copy(color = color, isCustomColor = true))
                                        editingCategory = null
                                        ToastManager.show("Color actualizado")
                                        loadSettings()
                                    }
                                }
                                .semantics {
                                    contentDescription = "Color $color"
                                    this.selected = selected
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Ionicon("checkmark", 20, Color.White) }
                    }
                }
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("Volver", onClick = { editingCategory = null }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
                    AppButton(
                        "Restablecer", modifier = Modifier.weight(1f), variant = ButtonVariant.Tonal, icon = "refresh",
                        onClick = {
                            scope.launch {
                                val def = DEFAULT_EXPENSE_CATEGORIES.firstOrNull { it.name == editing.name }
                                repos.categories.update(editing.copy(color = def?.color ?: editing.color, isCustomColor = false))
                                editingCategory = null
                                ToastManager.show("Color restablecido")
                                loadSettings()
                            }
                        },
                    )
                }
            }
        } else {
            Column {
                expenseCategories.forEach { cat ->
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .touchableOpacity(role = null) { editingCategory = cat }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(hexColorWithAlpha(cat.color, 0x20), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Ionicon(cat.icon, 18, hexColor(cat.color)) }
                            Text(cat.name, fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f).padding(start = 16.dp))
                            if (cat.isCustomColor == true) {
                                Text("Personalizado", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                            }
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(hexColor(cat.color), CircleShape),
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                    }
                }
            }
        }
    }

    BottomSheet(visible = isWipeSheetOpen, onClose = { isWipeSheetOpen = false }, title = "¿Eliminar todo?") {
        AppCard(borderColor = colors.error, borderWidth = 1.dp, bottomMargin = 0.dp) {
            Text("Esta acción eliminará:", color = colors.onSurface, modifier = Modifier.padding(bottom = 8.dp))
            Text("• Gastos e ingresos", color = colors.onSurfaceVariant)
            Text("• Todas las cuentas y tarjetas", color = colors.onSurfaceVariant)
            Text("• Préstamos e inversiones", color = colors.onSurfaceVariant)
            Text("Los datos NO se podrán recuperar.", color = colors.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Cancelar", onClick = { isWipeSheetOpen = false }, modifier = Modifier.weight(1f), variant = ButtonVariant.Outlined)
            AppButton(
                "Eliminar", modifier = Modifier.weight(1f), variant = ButtonVariant.Danger, icon = "trash",
                onClick = {
                    scope.launch {
                        container.backup.clearAllData()
                        isWipeSheetOpen = false
                        Alerts.alert("Éxito", "Todos los datos han sido eliminados")
                        loadSettings()
                        syncAfterChange()
                    }
                },
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            title, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        AppCard(contentPadding = 0.dp, content = content)
    }
}

/** Fila de lista de Configuración (listItem + itemContent + iconBox). */
@Composable
private fun SettingsRow(
    icon: String,
    iconBackground: Color,
    iconColor: Color,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    trailingIcon: String? = null,
    trailingColor: Color? = null,
    titleColor: Color? = null,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    subtitleContent: (@Composable () -> Unit)? = null,
    last: Boolean = false,
) {
    val colors = AppTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.touchableOpacity(role = null, onClick = onClick) else Modifier)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackground, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Ionicon(icon, 20, iconColor) }
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = titleColor ?: colors.onSurface)
                    if (subtitle != null) {
                        Text(
                            subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant,
                            maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    subtitleContent?.invoke()
                }
            }
            if (trailing != null) trailing()
            if (chevron) Ionicon("chevron-forward", 20, colors.onSurfaceVariant)
            if (trailingIcon != null) Ionicon(trailingIcon, 20, trailingColor ?: colors.onSurfaceVariant)
        }
        if (!last) HorizontalDivider(thickness = 1.dp, color = Color(0x1A969696)) // rgba(150,150,150,0.1)
    }
}

@Composable
private fun ThemeToggle(isDark: Boolean, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .background(colors.surfaceContainerLow, shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(2.dp),
    ) {
        listOf("light" to "Claro", "dark" to "Oscuro").forEach { (mode, label) ->
            val active = (mode == "dark") == isDark
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(shape)
                    .background(if (active) colors.primary else Color.Transparent, shape)
                    .touchableOpacity(role = null) { onSelect(mode) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TaxPill(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (active) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(if (active) colors.primary else colors.surfaceContainerHigh, shape)
            .border(1.dp, if (active) colors.primary else colors.outlineVariant, shape)
            .touchableOpacity(role = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FormatBox(modifier: Modifier, icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    Column(
        modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else Color.Transparent, shape)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .touchableOpacity(role = null, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ionicon(icon, 24, tint)
        Text(label, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 4.dp))
    }
}
