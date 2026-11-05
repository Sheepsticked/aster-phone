package app.asterphone.ui

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.asterphone.data.AsterServer

/**
 * The Aster tab: the server it shows in a bar above the page, with the list of servers behind the bar. While no server has an Aster
 * the list is the whole tab. [sheetOpen] shows the list over the page.
 */
@Composable
fun AsterPanelTab(
    host: AsterWebHost,
    servers: List<AsterServer>,
    current: AsterServer?,
    sheetOpen: Boolean,
    actions: AsterTabActions,
) {
    val address = current?.panel
    if (current == null || address == null) {
        AsterNoServers(servers, actions)
        return
    }
    // Back walks back through the panel's own pages before it does what it does on the other tabs.
    BackHandler(enabled = host.canGoBack, onBack = host::goBack)
    Column(Modifier.fillMaxSize().imePadding()) {
        AsterServerBar(current, onClick = actions.openSheet, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        AsterWebPanel(host, address, onChangeAddress = { actions.edit(current) }, Modifier.weight(1f).fillMaxWidth())
    }
    if (sheetOpen) AsterServerSheet(servers, current.key, actions)
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

/**
 * The form for where an Aster is: a SIP server's address, or the address of a panel added by hand. A null [server] adds a panel.
 * [onUseDefault] goes back to the server's own host and [onRemove] drops an added panel; each is null where it doesn't apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsterAddressScreen(
    server: AsterServer?,
    onSave: (String) -> Unit,
    onUseDefault: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var text by rememberSaveable { mutableStateOf(server?.address.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun save() {
        when (val result = AsterAddress.check(text)) {
            is AddressCheck.Valid -> onSave(result.url)
            is AddressCheck.Invalid -> error = result.reason
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (server == null) "Add Aster panel" else if (server.added) "Edit Aster panel" else "Aster address") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (onRemove != null) {
                        IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                    TextButton(onClick = ::save) { Text(if (server == null) "Add" else "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    server == null || server.added -> "Enter the address you open in a browser, with its port if it has one."
                    else -> "Where the Aster panel of ${server.name} is. Enter the address you open in a browser, with its port if it has one."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                keyboardActions = KeyboardActions(onGo = { save() }),
            )
            if (onUseDefault != null) {
                TextButton(onClick = onUseDefault) { Text("Use ${AsterAddress.shortName(server?.defaultAddress.orEmpty())}") }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${server?.name.orEmpty()}?") },
            text = { Text("The panel is taken off this phone's list. Nothing changes on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove?.invoke()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
