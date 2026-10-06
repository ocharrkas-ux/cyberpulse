package com.cyberpulse.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cyberpulse.app.domain.Severity

/** fsociety palette: black terminal, signal red, phosphor green used sparingly. */
object Fsociety {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF0A0A0A)
    val PanelHigh = Color(0xFF141414)
    val Line = Color(0xFF262626)
    val Red = Color(0xFFE3001B)
    val RedGlow = Color(0xFFFF3B4E)
    val RedDeep = Color(0xFF2B0007)
    val White = Color(0xFFEDEDED)
    val Grey = Color(0xFF8C8C8C)
    val Green = Color(0xFF39FF14)
    val Cyan = Color(0xFF00E5FF)
}

private val FsocietyColors = darkColorScheme(
    primary = Fsociety.Red,
    onPrimary = Fsociety.White,
    primaryContainer = Fsociety.RedDeep,
    onPrimaryContainer = Fsociety.RedGlow,
    secondary = Fsociety.RedGlow,
    onSecondary = Fsociety.Black,
    secondaryContainer = Fsociety.RedDeep,
    onSecondaryContainer = Fsociety.RedGlow,
    tertiary = Fsociety.Green,
    onTertiary = Fsociety.Black,
    background = Fsociety.Black,
    onBackground = Fsociety.White,
    surface = Fsociety.Black,
    onSurface = Fsociety.White,
    surfaceVariant = Fsociety.PanelHigh,
    onSurfaceVariant = Fsociety.Grey,
    surfaceContainerLowest = Fsociety.Black,
    surfaceContainerLow = Fsociety.Panel,
    surfaceContainer = Fsociety.Panel,
    surfaceContainerHigh = Fsociety.PanelHigh,
    surfaceContainerHighest = Fsociety.PanelHigh,
    inverseSurface = Fsociety.White,
    inverseOnSurface = Fsociety.Black,
    outline = Fsociety.Line,
    outlineVariant = Color(0xFF1A1A1A),
    error = Fsociety.RedGlow,
)

private val Mono = FontFamily.Monospace

private val MonoTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Mono),
        displayMedium = displayMedium.copy(fontFamily = Mono),
        displaySmall = displaySmall.copy(fontFamily = Mono),
        headlineLarge = headlineLarge.copy(fontFamily = Mono),
        headlineMedium = headlineMedium.copy(fontFamily = Mono),
        headlineSmall = headlineSmall.copy(fontFamily = Mono),
        titleLarge = titleLarge.copy(fontFamily = Mono),
        titleMedium = titleMedium.copy(fontFamily = Mono),
        titleSmall = titleSmall.copy(fontFamily = Mono),
        bodyLarge = bodyLarge.copy(fontFamily = Mono),
        bodyMedium = bodyMedium.copy(fontFamily = Mono),
        bodySmall = bodySmall.copy(fontFamily = Mono),
        labelLarge = labelLarge.copy(fontFamily = Mono),
        labelMedium = labelMedium.copy(fontFamily = Mono),
        labelSmall = labelSmall.copy(fontFamily = Mono),
    )
}

private val SharpShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Always dark: there is no light mode in fsociety. */
@Composable
fun CyberPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FsocietyColors,
        typography = MonoTypography,
        shapes = SharpShapes,
        content = content,
    )
}

/** Faint CRT scanlines drawn over the content. Static, so it costs nothing between frames. */
fun Modifier.scanlines(): Modifier = drawWithContent {
    drawContent()
    val step = 3.dp.toPx()
    val line = Color.Black.copy(alpha = 0.22f)
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

fun Severity.color(): Color = when (this) {
    Severity.CRITICAL -> Fsociety.RedGlow
    Severity.HIGH -> Color(0xFFFF8A00)
    Severity.MEDIUM -> Color(0xFFFFD600)
    Severity.LOW -> Fsociety.Green
}

val ExploitedColor = Fsociety.Red
