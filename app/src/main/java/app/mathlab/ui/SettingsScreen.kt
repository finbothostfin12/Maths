@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.mathlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mathlab.data.AppState
import app.mathlab.math.AngleMode

@Composable
fun SettingsScreen(app: AppState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("Colour palette")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PALETTES.filter { it.id != "dynamic" || dynamicSupported() }.forEach { opt ->
                Swatch(opt, app, app.palette == opt.id) { app.setPalette(opt.id) }
            }
        }
        if (app.palette == "custom") {
            Text("Hue", style = MaterialTheme.typography.labelLarge)
            Slider(value = app.customHue, onValueChange = { app.setCustomHue(it) }, valueRange = 0f..360f)
        } else {
            TextButton(onClick = { app.setPalette("custom") }) { Text("Pick any colour with the hue slider") }
        }

        SectionTitle("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, l ->
                FilterChip(selected = app.themeMode == i, onClick = { app.setThemeMode(i) }, label = { Text(l) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pure black (AMOLED)", style = MaterialTheme.typography.bodyLarge)
                Text("Used in dark theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = app.pureBlack, onCheckedChange = { app.setPureBlack(it) })
        }

        SectionTitle("Angle unit")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = app.angle == AngleMode.DEG, onClick = { app.setAngle(AngleMode.DEG) }, label = { Text("Degrees") })
            FilterChip(selected = app.angle == AngleMode.RAD, onClick = { app.setAngle(AngleMode.RAD) }, label = { Text("Radians") })
        }

        SectionTitle("About")
        Text(
            "MathLab runs entirely on your device. Photo scanning uses on-device text recognition (ML Kit) – " +
                    "no image ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Swatch(opt: PaletteOption, app: AppState, selected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val sc = if (opt.id == "dynamic") (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
    else schemeFor(opt.hue ?: app.customHue, false, false)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(sc.primaryContainer)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(sc.primary))
        }
        Spacer(Modifier.height(4.dp))
        Text(opt.label, style = MaterialTheme.typography.labelMedium)
    }
}
