package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.CurrencyConfig
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.getCurrencyConfig
import com.mixagames666.trackerrntemp.util.getCurrencyOption
import com.mixagames666.trackerrntemp.util.saveCurrency
import kotlinx.coroutines.launch

/** Port de components/WelcomeSheet.tsx: primer inicio, confirma o cambia la moneda detectada. */
@Composable
fun WelcomeSheet(visible: Boolean, appName: String, repos: Repos, onDone: () -> Unit) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(getCurrencyConfig().currency) }

    LaunchedEffect(visible) { if (visible) currency = getCurrencyConfig().currency }

    fun finish() {
        scope.launch {
            repos.settings.save { it.copy(onboardingCompleted = true) }
            choosing = false
            onDone()
        }
    }

    val option = getCurrencyOption(currency)

    BottomSheet(
        visible = visible,
        onClose = { finish() },
        title = if (choosing) "Elige tu moneda" else "¡Bienvenido a $appName!",
    ) {
        if (choosing) {
            CurrencyPicker(
                selectedCode = currency,
                onSelect = { selected ->
                    scope.launch {
                        saveCurrency(repos, CurrencyConfig(selected.code, selected.locale))
                        currency = selected.code
                        choosing = false
                    }
                },
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Ionicon("earth", 48, colors.primary, Modifier.padding(bottom = 12.dp))
                Text(
                    text = "Configuramos la moneda según la región de tu dispositivo. Puedes cambiarla ahora o después en Configuración.",
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (option != null) "${option.name} ($currency)" else currency,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = formatCurrency(1234.5),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                AppButton(title = "Continuar", onClick = { finish() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                AppButton(
                    title = "Cambiar moneda",
                    variant = ButtonVariant.Outlined,
                    onClick = { choosing = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
