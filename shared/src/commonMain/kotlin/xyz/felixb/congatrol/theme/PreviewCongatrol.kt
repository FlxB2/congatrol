package xyz.felixb.congatrol.theme

import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.compose.ui.tooling.preview.Preview

/**
 * Congatrol multipreview. Renders a composable three times: default, dark, and at the largest
 * accessibility settings — font scale 2× on a narrow display — which is what surfaces overflow and
 * squish bugs.
 *
 * Use this in place of a bare `@Preview`.
 */
@Preview(name = "Congatrol · Default")
@Preview(
    name = "Congatrol · Dark",
    uiMode = AndroidUiModes.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Congatrol · Large",
    fontScale = 2.0f,
    device = "spec:width=720px,height=1280px,dpi=360",
)
annotation class PreviewCongatrol
