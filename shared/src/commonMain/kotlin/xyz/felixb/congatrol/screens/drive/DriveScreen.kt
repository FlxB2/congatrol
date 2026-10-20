package xyz.felixb.congatrol.screens.drive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.command_failed
import congatrol.shared.generated.resources.drive_backward
import congatrol.shared.generated.resources.drive_forward
import congatrol.shared.generated.resources.drive_left
import congatrol.shared.generated.resources.drive_right
import congatrol.shared.generated.resources.drive_switch
import congatrol.shared.generated.resources.drive_switch_body
import congatrol.shared.generated.resources.drive_title
import congatrol.shared.generated.resources.drive_unsupported
import congatrol.shared.generated.resources.ic_arrow_down
import congatrol.shared.generated.resources.ic_arrow_up
import congatrol.shared.generated.resources.ic_turn_left
import congatrol.shared.generated.resources.ic_turn_right
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.components.CongatrolCard
import xyz.felixb.congatrol.components.Motion
import xyz.felixb.congatrol.components.appear
import xyz.felixb.congatrol.components.pressScale
import xyz.felixb.congatrol.screens.common.DisconnectedContent
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.PreviewCongatrol

@Composable
fun DriveScreen(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val viewModel = koinViewModel<DriveViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DriveScreenContent(
        state = state,
        onEnabledChange = viewModel::onEnabledChange,
        onStep = viewModel::onStep,
        onSaveAddress = viewModel::onSaveAddress,
        modifier = modifier,
        bottomInset = bottomInset,
    )
}

@Composable
fun DriveScreenContent(
    state: DriveUiState,
    onEnabledChange: (Boolean) -> Unit,
    onStep: (DriveStep) -> Unit,
    onSaveAddress: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    when (state) {
        DriveUiState.Loading -> Box(modifier.fillMaxSize())
        is DriveUiState.Disconnected -> DisconnectedContent(state.ui, onSaveAddress, modifier, bottomInset)
        is DriveUiState.Ready ->
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.drive_title), style = MaterialTheme.typography.headlineMedium)
                if (!state.supported) {
                    Text(
                        stringResource(Res.string.drive_unsupported),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                CongatrolCard(modifier = Modifier.fillMaxWidth().appear(0)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(Res.string.drive_switch), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(Res.string.drive_switch_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.testTag("drive_switch"))
                    }
                }
                if (state.failed) {
                    Text(stringResource(Res.string.command_failed), style = MaterialTheme.typography.bodyMedium)
                }
                Pad(enabled = state.enabled, onStep = onStep, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))
                Spacer(Modifier.height(bottomInset))
            }
    }
}

/** The four steps, laid out the way the robot would take them. */
@Composable
private fun Pad(
    enabled: Boolean,
    onStep: (DriveStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The buttons arrive one after the other, the way round the pad.
        StepButton(
            DriveStep.Forward,
            Res.drawable.ic_arrow_up,
            stringResource(Res.string.drive_forward),
            enabled,
            onStep,
            Modifier.appear(1),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PadGap)) {
            StepButton(
                DriveStep.Left,
                Res.drawable.ic_turn_left,
                stringResource(Res.string.drive_left),
                enabled,
                onStep,
                Modifier.appear(2),
            )
            StepButton(
                DriveStep.Right,
                Res.drawable.ic_turn_right,
                stringResource(Res.string.drive_right),
                enabled,
                onStep,
                Modifier.appear(3),
            )
        }
        StepButton(
            DriveStep.Backward,
            Res.drawable.ic_arrow_down,
            stringResource(Res.string.drive_backward),
            enabled,
            onStep,
            Modifier.appear(4),
        )
    }
}

/** A step for a press, and another for every [REPEAT_MILLIS] it is held. */
@Composable
private fun StepButton(
    step: DriveStep,
    icon: DrawableResource,
    label: String,
    enabled: Boolean,
    onStep: (DriveStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)
    LaunchedEffect(pressed, enabled) {
        while (pressed && enabled) {
            currentOnStep(step)
            delay(REPEAT_MILLIS)
        }
    }
    val colors = MaterialTheme.colorScheme
    // Switching manual control on wakes the pad up: the buttons brighten rather than flip.
    val wake by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, Motion.settle())
    Box(
        modifier =
            modifier
                .testTag("drive_${step.name}")
                .size(StepButtonSize)
                .pressScale(interactions, pressed = 0.84f)
                .alpha(wake)
                .clip(CircleShape)
                .background(colors.primary)
                // The steps are sent from the press itself, above; the click is only there to be one.
                .clickable(
                    interactionSource = interactions,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = {},
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
    }
}

private val StepButtonSize = 84.dp
private val PadGap = 72.dp
private const val REPEAT_MILLIS = 350L
private const val DISABLED_ALPHA = 0.35f

@PreviewCongatrol
@Preview
@Composable
internal fun DriveScreenContentPreview() {
    CongatrolTheme {
        CongatrolBackground {
            DriveScreenContent(
                state = DriveUiState.Ready(supported = true, enabled = true, failed = false),
                onEnabledChange = {},
                onStep = {},
                onSaveAddress = {},
            )
        }
    }
}
