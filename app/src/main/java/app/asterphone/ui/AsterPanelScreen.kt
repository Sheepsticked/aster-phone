package app.asterphone.ui

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.asterphone.R
import app.asterphone.data.AddressCheck
import app.asterphone.data.AsterAddress

/**
 * The Aster tab: the address form until an address is saved (or while it is being changed), then the web panel.
 * [suggestedAddress] fills the form the first time.
 */
@Composable
fun AsterPanelTab(
    host: AsterWebHost,
    savedAddress: String?,
    suggestedAddress: String,
    changingAddress: Boolean,
    onSaveAddress: (String) -> Unit,
    onChangeAddress: () -> Unit,
    onCancelChange: () -> Unit,
) {
    val address = savedAddress?.let { AsterAddress.check(it) as? AddressCheck.Valid }
    if (address == null || changingAddress) {
        BackHandler(enabled = address != null, onBack = onCancelChange)
        AsterAddressScreen(
            initial = savedAddress ?: suggestedAddress,
            onOpen = onSaveAddress,
            onCancel = if (address != null) onCancelChange else null,
        )
        return
    }
    // Back walks back through the panel's own pages before it does what it does on the other tabs.
    BackHandler(enabled = host.canGoBack, onBack = host::goBack)
    AsterWebPanel(host, address, onChangeAddress, Modifier.fillMaxSize().imePadding())
}

/** The web view, or the not-reachable screen over it while the main page can't be loaded. */
@Composable
fun AsterWebPanel(host: AsterWebHost, address: AddressCheck.Valid, onChangeAddress: () -> Unit, modifier: Modifier = Modifier) {
    // No view is made while the app is in the background, whatever recomposes.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    Box(modifier) {
        if (lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
            key(host.generation, address) {
                AndroidView(
                    factory = { host.obtainView(address) },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.visibility = if (host.status == PanelStatus.Failed) View.INVISIBLE else View.VISIBLE },
                    onRelease = host::detach,
                )
            }
        }
        when (host.status) {
            PanelStatus.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            PanelStatus.Failed -> AsterUnreachableScreen(
                address = address.url,
                retrying = host.retrying,
                onRetry = host::retry,
                onChangeAddress = onChangeAddress,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            )
            PanelStatus.Ready -> {}
        }
    }
}

/** Shown in place of the panel when its main page didn't load. */
@Composable
fun AsterUnreachableScreen(
    address: String,
    retrying: Boolean,
    onRetry: () -> Unit,
    onChangeAddress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = R.drawable.ic_cloud_off,
        title = "Can't reach Aster at $address",
        text = "The phone may need Wi-Fi or the VPN to get there.",
        modifier = modifier.fillMaxSize(),
        actionLabel = if (retrying) "Retrying…" else "Retry",
        onAction = onRetry,
        actionEnabled = !retrying,
        secondaryLabel = "Change address",
        onSecondary = onChangeAddress,
    )
}

/** One field for the panel's address, with Open; [onCancel] is null the first time, when there is nothing to go back to. */
@Composable
fun AsterAddressScreen(initial: String, onOpen: (String) -> Unit, onCancel: (() -> Unit)?, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    fun open() {
        when (val result = AsterAddress.check(text)) {
            is AddressCheck.Valid -> onOpen(result.url)
            is AddressCheck.Invalid -> error = result.reason
        }
    }

    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenTitle("Aster")
        Text(
            "Show the Aster web panel in this app. Enter the address you open in a browser, with its port if it has one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        FormField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            label = "Address",
            placeholder = "http://192.0.2.10/",
            error = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { open() }),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = ::open) { Text("Open") }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
