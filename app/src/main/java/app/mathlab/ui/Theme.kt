package app.mathlab.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.mathlab.data.AppState

data class PaletteOption(val id: String, val label: String, val hue: Float?)

val PALETTES = listOf(
    PaletteOption("dynamic", "Material You", null),
    PaletteOption("ocean", "Ocean", 215f),
    PaletteOption("forest", "Forest", 140f),
    PaletteOption("sunset", "Sunset", 18f),
    PaletteOption("rose", "Rose", 340f),
    PaletteOption("violet", "Violet", 268f),
    PaletteOption("amber", "Amber", 42f),
    PaletteOption("teal", "Teal", 178f),
    PaletteOption("custom", "Custom", null)
)

fun dynamicSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun hueFor(app: AppState): Float =
    PALETTES.firstOrNull { it.id == app.palette }?.hue ?: if (app.palette == "custom") app.customHue else 215f

private fun c(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

fun schemeFor(hue: Float, dark: Boolean, black: Boolean): ColorScheme {
    val h2 = hue + 28f
    val h3 = hue + 75f
    if (!dark) return lightColorScheme(
        primary = c(hue, .55f, .38f), onPrimary = Color.White,
        primaryContainer = c(hue, .80f, .88f), onPrimaryContainer = c(hue, .80f, .12f),
        secondary = c(h2, .28f, .38f), onSecondary = Color.White,
        secondaryContainer = c(h2, .45f, .88f), onSecondaryContainer = c(h2, .45f, .12f),
        tertiary = c(h3, .35f, .38f), onTertiary = Color.White,
        tertiaryContainer = c(h3, .55f, .88f), onTertiaryContainer = c(h3, .55f, .12f),
        background = c(hue, .25f, .97f), onBackground = c(hue, .15f, .10f),
        surface = c(hue, .25f, .97f), onSurface = c(hue, .15f, .10f),
        surfaceVariant = c(hue, .25f, .90f), onSurfaceVariant = c(hue, .12f, .30f),
        outline = c(hue, .08f, .46f), outlineVariant = c(hue, .15f, .80f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = c(hue, .25f, .95f),
        surfaceContainer = c(hue, .25f, .93f), surfaceContainerHigh = c(hue, .25f, .91f),
        surfaceContainerHighest = c(hue, .25f, .89f)
    )
    val s = darkColorScheme(
        primary = c(hue, .85f, .80f), onPrimary = c(hue, .70f, .20f),
        primaryContainer = c(hue, .55f, .30f), onPrimaryContainer = c(hue, .85f, .90f),
        secondary = c(h2, .45f, .78f), onSecondary = c(h2, .45f, .20f),
        secondaryContainer = c(h2, .28f, .30f), onSecondaryContainer = c(h2, .50f, .90f),
        tertiary = c(h3, .50f, .78f), onTertiary = c(h3, .50f, .20f),
        tertiaryContainer = c(h3, .30f, .30f), onTertiaryContainer = c(h3, .55f, .90f),
        background = c(hue, .15f, .09f), onBackground = c(hue, .10f, .90f),
        surface = c(hue, .15f, .09f), onSurface = c(hue, .10f, .90f),
        surfaceVariant = c(hue, .12f, .25f), onSurfaceVariant = c(hue, .12f, .78f),
        outline = c(hue, .08f, .60f), outlineVariant = c(hue, .10f, .30f),
        surfaceContainerLowest = c(hue, .15f, .06f), surfaceContainerLow = c(hue, .15f, .11f),
        surfaceContainer = c(hue, .15f, .13f), surfaceContainerHigh = c(hue, .15f, .17f),
        surfaceContainerHighest = c(hue, .15f, .21f)
    )
    return if (black) s.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = c(hue, .15f, .05f),
        surfaceContainer = c(hue, .15f, .08f), surfaceContainerHigh = c(hue, .15f, .12f),
        surfaceContainerHighest = c(hue, .15f, .16f)
    ) else s
}

@Composable
fun AppTheme(app: AppState, content: @Composable () -> Unit) {
    val sys = isSystemInDarkTheme()
    val dark = when (app.themeMode) { 1 -> false; 2 -> true; else -> sys }
    val ctx = LocalContext.current
    val scheme = if (app.palette == "dynamic" && dynamicSupported()) {
        val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        if (dark && app.pureBlack) s.copy(background = Color.Black, surface = Color.Black) else s
    } else schemeFor(hueFor(app), dark, app.pureBlack)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as? Activity)?.window ?: return@SideEffect
            val ctl = WindowCompat.getInsetsController(w, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}
