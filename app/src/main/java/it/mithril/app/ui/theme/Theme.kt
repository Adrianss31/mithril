package it.mithril.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.mithril.app.R

@Immutable
data class MithrilColors(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val nav: Color,
    val ink: Color, val ink2: Color, val ink3: Color,
    val line: Color, val line2: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color, val accentInk: Color,
    val hero: Color, val onHero: Color, val heroSoft: Color, val heroLine: Color,
    val scrim: Color, val player: Color,
)

val LightColors = MithrilColors(
    dark = false,
    bg = Color(0xFFF4EEE6), surface = Color(0xFFFFFBF6), surface2 = Color(0xFFECE4D9), nav = Color(0xFFEDE5DA),
    ink = Color(0xFF2B241F), ink2 = Color(0xFF6B5F57), ink3 = Color(0xFF968A80),
    line = Color(0x1A2B241F), line2 = Color(0x382B241F),
    accent = Color(0xFFB0553A), onAccent = Color(0xFFFFF7F2), accentSoft = Color(0xFFF3DDD1), accentInk = Color(0xFF7A3620),
    hero = Color(0xFFB0553A), onHero = Color(0xFFFFF7F2), heroSoft = Color(0x29FFF7F2), heroLine = Color(0x8CFFF7F2),
    scrim = Color(0x611E1612), player = Color(0xFF14100E),
)

val DarkColors = MithrilColors(
    dark = true,
    bg = Color(0xFF171311), surface = Color(0xFF221C19), surface2 = Color(0xFF2E2723), nav = Color(0xFF1D1815),
    ink = Color(0xFFF2EAE2), ink2 = Color(0xFFB5A89D), ink3 = Color(0xFF85786E),
    line = Color(0x1AF2EAE2), line2 = Color(0x38F2EAE2),
    accent = Color(0xFFE28A6A), onAccent = Color(0xFF2B130A), accentSoft = Color(0xFF4A2D22), accentInk = Color(0xFFF6BEA6),
    hero = Color(0xFF3D251C), onHero = Color(0xFFF7DFD3), heroSoft = Color(0x1AF7DFD3), heroLine = Color(0x73F7DFD3),
    scrim = Color(0x8C000000), player = Color(0xFF0E0B0A),
)

/** Il player è sempre scuro, indipendentemente dal tema. */
val PlayerInk = Color(0xFFF2EAE2)

val Figtree = FontFamily(
    Font(R.font.figtree_300, FontWeight.Light),
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold),
)

val LocalColors = staticCompositionLocalOf { LightColors }
/** false se l'utente ha disattivato le animazioni di sistema. */
val LocalMotion = staticCompositionLocalOf { true }

object M {
    val c: MithrilColors @Composable get() = LocalColors.current
    val motion: Boolean @Composable get() = LocalMotion.current
}

private val NoTrim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Testo Figtree con cifre tabellari, come nel prototipo. */
fun ts(size: Int, weight: FontWeight = FontWeight.Normal, spacing: TextUnit = 0.em, lineHeight: TextUnit = TextUnit.Unspecified, color: Color = Color.Unspecified) =
    TextStyle(
        fontFamily = Figtree, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing, color = color,
        lineHeight = if (lineHeight == TextUnit.Unspecified) (size * 1.25).sp else lineHeight,
        fontFeatureSettings = "tnum", lineHeightStyle = NoTrim,
    )

object T {
    val display = ts(96, FontWeight.SemiBold, (-0.05).em, 96.sp)
    val weighValue = ts(52, FontWeight.SemiBold, (-0.03).em, 56.sp)
    val trendBig = ts(44, FontWeight.SemiBold, (-0.03).em, 48.sp)
    val sessionTime = ts(40, FontWeight.SemiBold, (-0.03).em, 44.sp)
    val screenTitle = ts(30, FontWeight.SemiBold, (-0.02).em, 36.sp)
    val heroTitle = ts(24, FontWeight.SemiBold, (-0.02).em, 30.sp)
    val heroTitleL = ts(28, FontWeight.SemiBold, (-0.02).em, 34.sp)
    val sheetTitle = ts(22, FontWeight.SemiBold, 0.em, 28.sp)
    val cardTitle = ts(18, FontWeight.SemiBold)
    val title16 = ts(16, FontWeight.SemiBold)
    val title15 = ts(15, FontWeight.SemiBold, lineHeight = 20.sp)
    val body = ts(15, lineHeight = (15 * 1.45).sp)
    val body14 = ts(14, lineHeight = 20.sp)
    val secondary = ts(13, lineHeight = 18.sp)
    val caption = ts(12, lineHeight = 16.sp)
    val captionBold = ts(12, FontWeight.SemiBold, lineHeight = 16.sp)
    val overline = ts(11, FontWeight.SemiBold, 0.12.em, 14.sp)
    val button = ts(16, FontWeight.SemiBold)
    val button15 = ts(15, FontWeight.SemiBold)
}

@Composable
fun MithrilTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val motion = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) != 0f
    }
    val scheme = if (dark) darkColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.bg, surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink)
    else lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.bg, surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalColors provides colors, LocalMotion provides motion, content = content)
    }
}
