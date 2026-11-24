package com.matiassoto.ioslauncher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val base = Typography()
private fun TextStyle.inter() = copy(fontFamily = InterFamily)
val LauncherTypography = Typography(
    displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter(), headlineMedium = base.headlineMedium.inter(), headlineSmall = base.headlineSmall.inter(),
    titleLarge = base.titleLarge.inter(), titleMedium = base.titleMedium.inter(), titleSmall = base.titleSmall.inter(),
    bodyLarge = base.bodyLarge.inter().copy(fontSize = 17.sp), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter(), labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
)

val IosBlue = Color(0xFF0A84FF)
val IosRed = Color(0xFFFF3B30)
val IosGreen = Color(0xFF34C759)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = IosBlue, surface = Color(0xFF1C1C1E), background = Color.Black)
        else lightColorScheme(primary = IosBlue, surface = Color(0xFFF2F2F7), background = Color.White),
        typography = LauncherTypography,
        content = content,
    )
}

/** Information needed to draw "frosted glass": a blurred copy of the wallpaper mapped to the root. */
data class GlassInfo(val blurred: ImageBitmap?, val rootSize: IntSize, val dark: Boolean)

val LocalGlass = staticCompositionLocalOf { GlassInfo(null, IntSize.Zero, false) }

fun glassTint(dark: Boolean, strong: Boolean = false): Color =
    if (dark) Color(0xFF1C1C1E).copy(alpha = if (strong) 0.72f else 0.42f)
    else Color.White.copy(alpha = if (strong) 0.72f else 0.38f)

/** Frosted-glass background: draws the blurred wallpaper aligned to screen + tint, clipped to [shape]. */
fun Modifier.glass(shape: Shape, tint: Color? = null, strong: Boolean = false): Modifier = composed {
    val g = LocalGlass.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val t = tint ?: glassTint(g.dark, strong)
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            val b = g.blurred
            if (b != null && g.rootSize.width > 0) {
                translate(-pos.x, -pos.y) {
                    drawImage(
                        b, srcOffset = IntOffset.Zero, srcSize = IntSize(b.width, b.height),
                        dstOffset = IntOffset.Zero, dstSize = g.rootSize, filterQuality = FilterQuality.High,
                    )
                }
            }
            drawRect(t)
        }
}

/** Superellipse ("squircle") shape: |x|^n + |y|^n = 1, n = 5, for continuous-looking corners. */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = 5.0
        val path = Path()
        val a = size.width / 2f; val b = size.height / 2f
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = a + a * (abs(c).pow(2.0 / n) * sign(c)).toFloat()
            val y = b + b * (abs(s).pow(2.0 / n) * sign(s)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Text colour for content sitting on a "strong" glass surface. */
@Composable
fun glassTextColor(): Color = if (LocalGlass.current.dark) Color.White else Color(0xFF111111)
