package xyz.felixb.congatrol.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.address_label
import congatrol.shared.generated.resources.address_placeholder
import congatrol.shared.generated.resources.address_save
import congatrol.shared.generated.resources.connect_body
import congatrol.shared.generated.resources.connect_connecting
import congatrol.shared.generated.resources.connect_title
import congatrol.shared.generated.resources.connect_unreachable_body
import congatrol.shared.generated.resources.connect_unreachable_title
import org.jetbrains.compose.resources.stringResource
import xyz.felixb.congatrol.components.CongatrolCard
import xyz.felixb.congatrol.data.Connection
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.PreviewCongatrol

/** Why there is no robot to show, and the address that was tried, as the user would type it. */
@Immutable
data class DisconnectedUi(
    val kind: Kind,
    val address: String,
) {
    enum class Kind { NoAddress, Connecting, Unreachable }
}

/** What to show instead of the robot, or null while it answers. */
fun Connection.disconnectedUi(): DisconnectedUi? =
    when (this) {
        Connection.NoAddress -> DisconnectedUi(DisconnectedUi.Kind.NoAddress, "")
        is Connection.Connecting -> DisconnectedUi(DisconnectedUi.Kind.Connecting, address.shown())
        is Connection.Unreachable -> DisconnectedUi(DisconnectedUi.Kind.Unreachable, address.shown())
        is Connection.Connected -> null
    }

/** An address without the `http://` the app put in front of it. */
fun String.shown(): String = removePrefix("http://")

/**
 * What every tab shows while there is no robot: why, and the one thing that can be done about
 * it — saying where the robot is.
 */
@Composable
fun DisconnectedContent(
    ui: DisconnectedUi,
    onSaveAddress: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text =
                stringResource(
                    if (ui.kind == DisconnectedUi.Kind.Unreachable) Res.string.connect_unreachable_title else Res.string.connect_title,
                ),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text =
                if (ui.kind == DisconnectedUi.Kind.Unreachable) {
                    stringResource(Res.string.connect_unreachable_body, ui.address)
                } else {
                    stringResource(Res.string.connect_body)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ui.kind == DisconnectedUi.Kind.Connecting) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.connect_connecting), style = MaterialTheme.typography.bodyMedium)
            }
        }
        CongatrolCard(modifier = Modifier.fillMaxWidth()) {
            AddressForm(address = ui.address, onSave = onSaveAddress, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(bottomInset))
    }
}

/** The robot's address and a button that takes it. The button waits for the address to differ. */
@Composable
fun AddressForm(
    address: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(address) { mutableStateOf(address) }
    val changed = text.trim() != address
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(Res.string.address_label)) },
            placeholder = { Text(stringResource(Res.string.address_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { if (changed) onSave(text) }),
            modifier = Modifier.fillMaxWidth().testTag("address_field"),
        )
        Button(onClick = { onSave(text) }, enabled = changed, modifier = Modifier.testTag("address_save")) {
            Text(stringResource(Res.string.address_save))
        }
    }
}

@PreviewCongatrol
@Preview
@Composable
internal fun DisconnectedContentPreview() {
    CongatrolTheme {
        CongatrolBackground {
            DisconnectedContent(ui = DisconnectedUi(DisconnectedUi.Kind.Unreachable, "robot.local"), onSaveAddress = {})
        }
    }
}
