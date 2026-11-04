package app.asterphone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.asterphone.data.AccountField
import app.asterphone.data.SipAccount
import app.asterphone.data.SipTransport
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    initial: SipAccount?,
    others: List<SipAccount>,
    onSave: (SipAccount) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val id = rememberSaveable { initial?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initial?.password.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(initial?.host.orEmpty()) }
    var port by rememberSaveable { mutableStateOf((initial?.port ?: SipAccount.DEFAULT_PORT).toString()) }
    var transport by rememberSaveable { mutableStateOf(initial?.transport ?: SipTransport.UDP) }
    var expiry by rememberSaveable { mutableStateOf((initial?.expirySeconds ?: SipAccount.DEFAULT_EXPIRY).toString()) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyMap<AccountField, String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { AccountField.entries.associateWith { FocusRequester() } }

    // Editing a field clears its message.
    fun edited(field: AccountField) {
        errors = errors - field
    }

    fun save() {
        val account = SipAccount(
            id = id,
            name = name.trim(),
            username = username,
            password = password,
            host = host.removePrefix("sip:"),
            port = port.toIntOrNull() ?: 0,
            transport = transport,
            expirySeconds = expiry.toIntOrNull() ?: 0,
            enabled = initial?.enabled ?: true,
        )
        val problems = account.fieldErrors().toMutableMap()
        if (AccountField.Username !in problems &&
            others.any { it.username == account.username && it.host.equals(account.host, ignoreCase = true) }
        ) {
            problems[AccountField.Username] = "This extension is already added"
        }
        errors = problems
        if (problems.isEmpty()) onSave(account) else AccountField.entries.firstOrNull { it in problems }?.let { focus.getValue(it).requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New account" else "Edit account") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                    TextButton(onClick = ::save) { Text(if (initial == null) "Add" else "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                value = name,
                onValueChange = { name = it },
                label = "Name (optional)",
                placeholder = "Office line",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            FormField(
                value = username,
                onValueChange = {
                    username = it.trim()
                    edited(AccountField.Username)
                },
                label = "Extension / username",
                placeholder = "501",
                error = errors[AccountField.Username],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus.getValue(AccountField.Username)),
            )
            FormField(
                value = password,
                onValueChange = {
                    password = it
                    edited(AccountField.Password)
                },
                label = "Password",
                error = errors[AccountField.Password],
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus.getValue(AccountField.Password)),
            )
            FormField(
                value = host,
                onValueChange = {
                    host = it.trim()
                    edited(AccountField.Host)
                },
                label = "Server",
                placeholder = "192.0.2.10",
                help = "Aster's address on your LAN or VPN",
                error = errors[AccountField.Host],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus.getValue(AccountField.Host)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    value = port,
                    onValueChange = {
                        port = it.filter(Char::isDigit).take(5)
                        edited(AccountField.Port)
                    },
                    label = "Port",
                    error = errors[AccountField.Port],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.width(120.dp).focusRequester(focus.getValue(AccountField.Port)),
                )
                // Level with the port field's box, which is 56 dp tall, not with its message below.
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).padding(top = 8.dp).semantics { contentDescription = "Transport" }) {
                    SipTransport.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = transport == option,
                            onClick = { transport = option },
                            shape = SegmentedButtonDefaults.itemShape(index, SipTransport.entries.size),
                        ) { Text(option.name) }
                    }
                }
            }
            FormField(
                value = expiry,
                onValueChange = {
                    expiry = it.filter(Char::isDigit).take(5)
                    edited(AccountField.Expiry)
                },
                label = "Re-register every (seconds)",
                help = "Higher saves battery. Network changes re-register at once.",
                error = errors[AccountField.Expiry],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus.getValue(AccountField.Expiry)),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial?.label.orEmpty()}?") },
            text = { Text("The account is removed from this phone. Nothing changes on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A single-line text field that shows [error] under it, or else [help]. */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    help: String? = null,
    error: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = (error ?: help)?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        modifier = modifier,
    )
}
