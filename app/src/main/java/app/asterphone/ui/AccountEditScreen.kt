package app.asterphone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text("Office line") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = { Text("Extension / username") },
                placeholder = { Text("501") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("Server") },
                placeholder = { Text("192.0.2.10") },
                supportingText = { Text("Aster's address on your LAN or VPN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Transport", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SipTransport.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = transport == option,
                        onClick = { transport = option },
                        shape = SegmentedButtonDefaults.itemShape(index, SipTransport.entries.size),
                    ) { Text(option.name) }
                }
            }
            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = it.filter(Char::isDigit).take(5) },
                label = { Text("Re-register every (seconds)") },
                supportingText = { Text("Higher saves battery. Network changes re-register at once.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
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
                    val problems = account.validate().toMutableList()
                    if (others.any { it.username == account.username && it.host.equals(account.host, ignoreCase = true) }) {
                        problems += "This extension is already added"
                    }
                    errors = problems
                    if (problems.isEmpty()) onSave(account)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
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
