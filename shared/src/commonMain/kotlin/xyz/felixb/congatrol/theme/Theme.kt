package xyz.felixb.congatrol.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.bricolage_grotesque
import congatrol.shared.generated.resources.manrope
import org.jetbrains.compose.resources.Font

/**
 * Congatrol's look: Material 3 components in one dress. A soft glow of three colours behind the
 * top of every screen ([CongatrolBackground]), translucent cards on it
 * (`components/CongatrolCard.kt`), softly rounded corners, Bricolage Grotesque for headings and
 * Manrope for everything else. Light or dark as the system has it.
 *
 * Everything a screen needs comes through `MaterialTheme` or [LocalCongatrolLook]; nothing below
 * this file asks whether the system is dark.
 */
@Composable
fun CongatrolTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val look = remember(dark) { congatrolLook(dark) }
    CompositionLocalProvider(LocalCongatrolLook provides look) {
        MaterialTheme(
            colorScheme = look.scheme,
            shapes = CongatrolShapes,
            typography = congatrolTypography(),
            content = content,
        )
    }
}

/** What the look has to say beyond Material's colour roles. */
@Immutable
class CongatrolLook internal constructor(
    val dark: Boolean,
    internal val scheme: ColorScheme,
    /** The glow's colours — left, right, middle — already at the strength they are drawn with. */
    val glow: List<Color>,
    /** What a card is filled with: translucent over the glow. */
    val card: Color,
    val cardBorder: Color,
    /** The shadow a card throws. */
    val cardShadow: Color,
    /** A quiet fill for what is pressed in rather than raised: the current tab, the map's floor. */
    val well: Color,
)

val LocalCongatrolLook = staticCompositionLocalOf { congatrolLook(dark = false) }

private val AccentLight = Color(0xFF4B3AB8)
private val GlowLight = listOf(Color(0xFFE9A8C4), Color(0xFF93A7DC), Color(0xFFC9B3EE))

internal fun congatrolLook(dark: Boolean): CongatrolLook {
    val accent = if (dark) lerp(AccentLight, Color.White, DARK_ACCENT_LIFT) else AccentLight
    val glow = if (dark) GlowLight.map { it.deepened() } else GlowLight
    // The ground carries a breath of the accent, which keeps the neutrals from looking borrowed.
    val ground =
        if (dark) lerp(Color(0xFF0F0E13), accent, GROUND_TINT_DARK) else lerp(Color.White, accent, GROUND_TINT_LIGHT)
    val raised = if (dark) Color(0xFF1C1A23) else Color.White
    val ink = if (dark) Color(0xFFF3F0F9) else Color(0xFF17151E)
    val quiet = if (dark) Color(0xFFA7A1B6) else Color(0xFF6A6478)
    val well = if (dark) lerp(ground, Color.White, 0.09f) else lerp(ground, Color(0xFF1B1530), 0.07f)
    val onAccent = if (dark) Color(0xFF15131C) else Color.White
    val accentWash = accent.copy(alpha = if (dark) 0.24f else 0.13f).compositeOver(ground)
    val hairline = ink.copy(alpha = 0.12f).compositeOver(ground)
    val outline = ink.copy(alpha = 0.38f).compositeOver(ground)
    val scheme =
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentWash,
            onPrimaryContainer = ink,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accentWash,
            onSecondaryContainer = ink,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = accentWash,
            onTertiaryContainer = ink,
            background = ground,
            onBackground = ink,
            surface = ground,
            onSurface = ink,
            surfaceVariant = well,
            onSurfaceVariant = quiet,
            surfaceTint = accent,
            inverseSurface = ink,
            inverseOnSurface = ground,
            outline = outline,
            outlineVariant = hairline,
            surfaceBright = raised,
            surfaceDim = well,
            surfaceContainerLowest = if (dark) ground else raised,
            surfaceContainerLow = raised,
            surfaceContainer = raised,
            surfaceContainerHigh = if (dark) lerp(raised, Color.White, 0.04f) else raised,
            surfaceContainerHighest = well,
        )
    return CongatrolLook(
        dark = dark,
        scheme = scheme,
        glow = glow.mapIndexed { index, color -> color.copy(alpha = if (index == 2) GLOW_MIDDLE else GLOW_SIDES) },
        card = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.58f),
        cardBorder = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.75f),
        cardShadow = if (dark) Color.Black.copy(alpha = 0.28f) else Color(0xFF1E1446).copy(alpha = 0.09f),
        well = well,
    )
}

/**
 * A pastel as it glows in the dark: the same hue, darker and with more colour in it. Darkening
 * alone would leave it grey.
 */
private fun Color.deepened(): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue =
        when {
            delta == 0f -> 0f
            max == red -> 60f * (((green - blue) / delta).mod(6f))
            max == green -> 60f * ((blue - red) / delta + 2f)
            else -> 60f * ((red - green) / delta + 4f)
        }
    val saturation = if (max == 0f) 0f else delta / max
    return Color.hsv(hue, (saturation * DARK_GLOW_SATURATE + DARK_GLOW_SATURATION_FLOOR).coerceAtMost(1f), DARK_GLOW_VALUE)
}

private const val DARK_ACCENT_LIFT = 0.45f
private const val DARK_GLOW_SATURATE = 1.4f
private const val DARK_GLOW_SATURATION_FLOOR = 0.3f
private const val DARK_GLOW_VALUE = 0.5f
private const val GROUND_TINT_LIGHT = 0.0135f
private const val GROUND_TINT_DARK = 0.021f
private const val GLOW_SIDES = 0.75f
private const val GLOW_MIDDLE = 0.68f

/** Cards and everything card-like at 15dp; dialogs and sheets rounder; buttons stay pills. */
private val CongatrolShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(15.dp),
        large = RoundedCornerShape(15.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
private fun congatrolTypography(): Typography {
    // Both are variable fonts: one file each, the weight picked per use.
    val body =
        FontFamily(
            Font(Res.font.manrope, FontWeight.Normal),
            Font(Res.font.manrope, FontWeight.Medium),
            Font(Res.font.manrope, FontWeight.SemiBold),
            Font(Res.font.manrope, FontWeight.Bold),
        )
    // Headings are set at the cut drawn for large sizes.
    val heading =
        FontFamily(
            Font(
                Res.font.bricolage_grotesque,
                FontWeight.SemiBold,
                variationSettings =
                    FontVariation.Settings(FontVariation.weight(HEADING_WEIGHT), FontVariation.Setting("opsz", HEADING_OPTICAL_SIZE)),
            ),
        )
    return remember(body, heading) {
        val base = Typography()

        fun TextStyle.heading() = copy(fontFamily = heading, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)

        fun TextStyle.body(weight: FontWeight? = null) = copy(fontFamily = body, fontWeight = weight ?: fontWeight)
        Typography(
            displayLarge = base.displayLarge.heading(),
            displayMedium = base.displayMedium.heading(),
            displaySmall = base.displaySmall.heading(),
            headlineLarge = base.headlineLarge.heading(),
            headlineMedium = base.headlineMedium.heading(),
            headlineSmall = base.headlineSmall.heading(),
            titleLarge = base.titleLarge.heading(),
            titleMedium = base.titleMedium.body(FontWeight.SemiBold),
            titleSmall = base.titleSmall.body(FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.body(),
            bodyMedium = base.bodyMedium.body(),
            bodySmall = base.bodySmall.body(),
            labelLarge = base.labelLarge.body(FontWeight.SemiBold),
            labelMedium = base.labelMedium.body(FontWeight.SemiBold),
            labelSmall = base.labelSmall.body(FontWeight.SemiBold),
        )
    }
}

private const val HEADING_WEIGHT = 600
private const val HEADING_OPTICAL_SIZE = 32f

/**
 * The ground every screen stands on: the theme's background with the glow at the top. The glow
 * stays put while the content scrolls over it.
 */
@Composable
fun CongatrolBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.congatrolBackground(), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/** See [CongatrolBackground]; for a layout that is its own box. */
@Composable
fun Modifier.congatrolBackground(): Modifier {
    val look = LocalCongatrolLook.current
    val ground = look.scheme.surface
    return drawBehind {
        drawRect(ground)
        if (look.glow.size < 3) return@drawBehind
        val reach = size.height * GLOW_REACH
        clipRect(bottom = reach) {
            glow(look.glow[0], Offset.Zero, size.width * 1.2f, reach * 0.7f, 0.62f)
            glow(look.glow[1], Offset(size.width, reach * 0.04f), size.width * 1.1f, reach * 0.7f, 0.6f)
            glow(look.glow[2], Offset(size.width * 0.45f, reach * 0.34f), size.width * 0.9f, reach * 0.6f, 0.66f)
            // Fades out downwards, into the ground.
            drawRect(Brush.verticalGradient(GLOW_FADE_FROM to ground.copy(alpha = 0f), 1f to ground, endY = reach))
        }
    }
}

/** One soft ellipse of [color] around [center], gone at [end] of its radii. */
private fun DrawScope.glow(
    color: Color,
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    end: Float,
) {
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(0f to color, end to color.copy(alpha = 0f), center = center, radius = radiusX),
            radius = radiusX,
            center = center,
        )
    }
}

private const val GLOW_REACH = 0.62f
private const val GLOW_FADE_FROM = 0.35f

/**
 * Categorical palette for the rooms of the map, by their place in the list. Eight hues in a fixed
 * order, with a separate step per mode so each stays distinguishable on its own surface.
 */
private val SeriesLight =
    listOf(
        Color(0xFF2A78D6),
        Color(0xFFEB6834),
        Color(0xFF1BAF7A),
        Color(0xFFEDA100),
        Color(0xFFE87BA4),
        Color(0xFF008300),
        Color(0xFF6250D6),
        Color(0xFFE34948),
    )

private val SeriesDark =
    listOf(
        Color(0xFF3987E5),
        Color(0xFFD95926),
        Color(0xFF199E70),
        Color(0xFFC98500),
        Color(0xFFD55181),
        Color(0xFF008300),
        Color(0xFF9085E9),
        Color(0xFFE66767),
    )

/** The colour of the room in [slot]; slots beyond the palette wrap around. */
fun seriesColor(
    slot: Int,
    dark: Boolean,
): Color {
    val colors = if (dark) SeriesDark else SeriesLight
    return colors[slot.mod(colors.size)]
}
