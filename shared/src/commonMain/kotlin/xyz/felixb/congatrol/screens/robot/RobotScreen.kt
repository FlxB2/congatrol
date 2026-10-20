package xyz.felixb.congatrol.screens.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.command_failed
import congatrol.shared.generated.resources.consumable_filter
import congatrol.shared.generated.resources.consumable_hours
import congatrol.shared.generated.resources.consumable_main_brush
import congatrol.shared.generated.resources.consumable_mop
import congatrol.shared.generated.resources.consumable_percent
import congatrol.shared.generated.resources.consumable_sensor
import congatrol.shared.generated.resources.consumable_side_brush
import congatrol.shared.generated.resources.ic_locate
import congatrol.shared.generated.resources.robot_about
import congatrol.shared.generated.resources.robot_cancel
import congatrol.shared.generated.resources.robot_carpet
import congatrol.shared.generated.resources.robot_carpet_body
import congatrol.shared.generated.resources.robot_cleaning
import congatrol.shared.generated.resources.robot_connection
import congatrol.shared.generated.resources.robot_consumables
import congatrol.shared.generated.resources.robot_licenses
import congatrol.shared.generated.resources.robot_locate
import congatrol.shared.generated.resources.robot_locate_body
import congatrol.shared.generated.resources.robot_model
import congatrol.shared.generated.resources.robot_name
import congatrol.shared.generated.resources.robot_name_body
import congatrol.shared.generated.resources.robot_name_label
import congatrol.shared.generated.resources.robot_name_save
import congatrol.shared.generated.resources.robot_quiet
import congatrol.shared.generated.resources.robot_quiet_off
import congatrol.shared.generated.resources.robot_quiet_on
import congatrol.shared.generated.resources.robot_reset
import congatrol.shared.generated.resources.robot_reset_body
import congatrol.shared.generated.resources.robot_reset_title
import congatrol.shared.generated.resources.robot_sound
import congatrol.shared.generated.resources.robot_title
import congatrol.shared.generated.resources.robot_version
import congatrol.shared.generated.resources.robot_volume
import congatrol.shared.generated.resources.robot_wifi
import congatrol.shared.generated.resources.robot_wifi_value
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.components.CongatrolCard
import xyz.felixb.congatrol.components.appear
import xyz.felixb.congatrol.screens.common.AddressForm
import xyz.felixb.congatrol.screens.common.DisconnectedContent
import xyz.felixb.congatrol.screens.licenses.LinkRow
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.PreviewCongatrol
import kotlin.math.roundToInt

@Composable
fun RobotScreen(
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val viewModel = koinViewModel<RobotViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    RobotScreenContent(
        state = state,
        onLicensesClick = onLicensesClick,
        onSaveName = viewModel::onSaveName,
        onSaveAddress = viewModel::onSaveAddress,
        onLocate = viewModel::onLocate,
        onVolume = viewModel::onVolume,
        onCarpetMode = viewModel::onCarpetMode,
        onResetConsumable = viewModel::onResetConsumable,
        modifier = modifier,
        bottomInset = bottomInset,
    )
}

@Composable
fun RobotScreenContent(
    state: RobotUiState,
    onLicensesClick: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveAddress: (String) -> Unit,
    onLocate: () -> Unit,
    onVolume: (Int) -> Unit,
    onCarpetMode: (Boolean) -> Unit,
    onResetConsumable: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    when (state) {
        RobotUiState.Loading -> Box(modifier.fillMaxSize())
        // The licences must not need a robot, so they are reached from here as well.
        is RobotUiState.Disconnected ->
            DisconnectedContent(state.ui, onSaveAddress, modifier, bottomInset) {
                CongatrolCard(modifier = Modifier.fillMaxWidth()) {
                    LinkRow(title = stringResource(Res.string.robot_licenses), subtitle = null, onClick = onLicensesClick)
                }
            }
        is RobotUiState.Ready ->
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.name.ifEmpty { stringResource(Res.string.robot_title) },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.failed) Text(stringResource(Res.string.command_failed), style = MaterialTheme.typography.bodyMedium)
                Section(stringResource(Res.string.robot_name)) {
                    NameForm(name = state.name, onSave = onSaveName)
                }
                Section(stringResource(Res.string.robot_connection), index = 1) {
                    AddressForm(address = state.address, onSave = onSaveAddress)
                }
                if (state.canLocate || state.volume != null) {
                    Section(stringResource(Res.string.robot_sound), index = 2) {
                        if (state.canLocate) {
                            FilledTonalButton(onClick = onLocate, modifier = Modifier.testTag("locate")) {
                                Icon(painterResource(Res.drawable.ic_locate), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(Res.string.robot_locate), modifier = Modifier.padding(start = 8.dp))
                            }
                            Quiet(stringResource(Res.string.robot_locate_body))
                        }
                        state.volume?.let { VolumeSlider(it, onVolume) }
                    }
                }
                if (state.carpetMode != null || state.doNotDisturb != null) {
                    Section(stringResource(Res.string.robot_cleaning), index = 3) {
                        state.carpetMode?.let { enabled ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(Res.string.robot_carpet), style = MaterialTheme.typography.bodyLarge)
                                    Quiet(stringResource(Res.string.robot_carpet_body))
                                }
                                Switch(checked = enabled, onCheckedChange = onCarpetMode, modifier = Modifier.testTag("carpet_switch"))
                            }
                        }
                        state.doNotDisturb?.let { enabled ->
                            Fact(
                                label = stringResource(Res.string.robot_quiet),
                                value =
                                    if (enabled) {
                                        stringResource(Res.string.robot_quiet_on, state.doNotDisturbHours.orEmpty())
                                    } else {
                                        stringResource(Res.string.robot_quiet_off)
                                    },
                            )
                        }
                    }
                }
                if (state.consumables.isNotEmpty()) {
                    Section(stringResource(Res.string.robot_consumables), index = 4) {
                        state.consumables.forEach { ConsumableRow(it, onResetConsumable) }
                    }
                }
                if (state.model != null || state.version != null || state.wifiSignal != null) {
                    Section(stringResource(Res.string.robot_about), index = 5) {
                        state.model?.let { Fact(stringResource(Res.string.robot_model), it) }
                        state.version?.let { Fact(stringResource(Res.string.robot_version), it) }
                        state.wifiSignal?.let {
                            Fact(
                                stringResource(Res.string.robot_wifi),
                                stringResource(Res.string.robot_wifi_value, it),
                            )
                        }
                    }
                }
                CongatrolCard(modifier = Modifier.fillMaxWidth()) {
                    LinkRow(
                        title = stringResource(Res.string.robot_licenses),
                        subtitle = null,
                        onClick = onLicensesClick,
                        modifier = Modifier.testTag("licenses"),
                    )
                }
                Spacer(Modifier.height(bottomInset))
            }
    }
}

/** A card with its heading, and what belongs under it. */
@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    /** Its place down the screen, which is the order the cards arrive in. */
    index: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    CongatrolCard(modifier = modifier.fillMaxWidth().appear(index)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Quiet(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A name and what is known about it, side by side. */
@Composable
private fun Fact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Quiet(value)
    }
}

/**
 * What to call the robot, and a button that takes it — once it differs from the name it has.
 * Saving it empty takes the name away again.
 */
@Composable
private fun NameForm(
    name: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(name) { mutableStateOf(name) }
    val changed = text.trim() != name
    val focusManager = LocalFocusManager.current
    // Saving lets go of the field, which is what puts the keyboard away.
    val save = {
        focusManager.clearFocus()
        if (changed) onSave(text)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(Res.string.robot_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth().testTag("name_field"),
        )
        Quiet(stringResource(Res.string.robot_name_body))
        Button(onClick = save, enabled = changed, modifier = Modifier.testTag("name_save")) {
            Text(stringResource(Res.string.robot_name_save))
        }
    }
}

/** The volume follows the finger and is sent when it is let go. */
@Composable
private fun VolumeSlider(
    volume: Int,
    onVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
    Column(modifier = modifier) {
        Text(stringResource(Res.string.robot_volume, dragged.roundToInt()), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onVolume(dragged.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.testTag("volume"),
        )
    }
}

@Composable
private fun ConsumableRow(
    consumable: ConsumableUi,
    onReset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val name =
        when (consumable.kind) {
            ConsumableUi.Kind.MainBrush -> stringResource(Res.string.consumable_main_brush)
            ConsumableUi.Kind.SideBrush -> stringResource(Res.string.consumable_side_brush)
            ConsumableUi.Kind.Filter -> stringResource(Res.string.consumable_filter)
            ConsumableUi.Kind.Mop -> stringResource(Res.string.consumable_mop)
            ConsumableUi.Kind.Sensor -> stringResource(Res.string.consumable_sensor)
            ConsumableUi.Kind.Other -> consumable.rawName
        }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Quiet(
                stringResource(
                    if (consumable.hours) Res.string.consumable_hours else Res.string.consumable_percent,
                    consumable.remaining,
                ),
            )
        }
        TextButton(onClick = { asking = true }) { Text(stringResource(Res.string.robot_reset)) }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(Res.string.robot_reset_title, name)) },
            text = { Text(stringResource(Res.string.robot_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onReset(consumable.key)
                    },
                ) { Text(stringResource(Res.string.robot_reset)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(Res.string.robot_cancel)) } },
        )
    }
}

@PreviewCongatrol
@Preview
@Composable
internal fun RobotScreenContentPreview() {
    CongatrolTheme {
        CongatrolBackground {
            RobotScreenContent(
                state =
                    RobotUiState.Ready(
                        name = "Rosie",
                        address = "robot.local",
                        canLocate = true,
                        volume = 70,
                        carpetMode = true,
                        doNotDisturb = true,
                        doNotDisturbHours = "22:00–07:00",
                        consumables =
                            persistentListOf(
                                ConsumableUi("brush/main", ConsumableUi.Kind.MainBrush, "Brush main", 238, hours = true),
                                ConsumableUi("filter/main", ConsumableUi.Kind.Filter, "Filter main", 35, hours = true),
                            ),
                        model = "Demo robot",
                        version = "2024.04",
                        wifiSignal = -52,
                        failed = false,
                    ),
                onLicensesClick = {},
                onSaveName = {},
                onSaveAddress = {},
                onLocate = {},
                onVolume = {},
                onCarpetMode = {},
                onResetConsumable = {},
            )
        }
    }
}
