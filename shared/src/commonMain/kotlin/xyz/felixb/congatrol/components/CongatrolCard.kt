package xyz.felixb.congatrol.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.LocalCongatrolLook
import xyz.felixb.congatrol.theme.PreviewCongatrol

/**
 * The card everything card-like is: a pane of the look's card material, translucent over the
 * glow, with a soft shadow under it.
 *
 * [paneModifier] goes on the pane itself, inside its rounded corners: the place for `clickable`, so the
 * press shows within the shape. [selected] marks the card as picked.
 */
@Composable
fun CongatrolCard(
    modifier: Modifier = Modifier,
    paneModifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalCongatrolLook.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Box(modifier = modifier, propagateMinConstraints = true) {
        if (look.cardShadow.alpha > 0f) {
            Box(Modifier.matchParentSize().shadowAround(shape, look.cardShadow))
        }
        Column(
            modifier =
                Modifier
                    .clip(shape)
                    .background(if (selected) colors.secondaryContainer else look.card)
                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else look.cardBorder, shape)
                    .then(paneModifier),
            content = content,
        )
    }
}

/**
 * A shadow drawn only outside [shape]: under a translucent card the part beneath it would show
 * through and grey the card.
 */
private fun Modifier.shadowAround(
    shape: Shape,
    color: Color,
): Modifier =
    drawWithContent {
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(outline, ClipOp.Difference) { this@drawWithContent.drawContent() }
    }.dropShadow(shape, Shadow(radius = 30.dp, color = color, offset = DpOffset(0.dp, 10.dp)))

@PreviewCongatrol
@Preview
@Composable
internal fun CongatrolCardPreview() {
    CongatrolTheme {
        CongatrolBackground {
            Column(modifier = Modifier.padding(24.dp)) {
                CongatrolCard { Text("Docked, fully charged", modifier = Modifier.padding(16.dp)) }
                CongatrolCard(modifier = Modifier.padding(top = 16.dp), selected = true) {
                    Text("Picked", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
