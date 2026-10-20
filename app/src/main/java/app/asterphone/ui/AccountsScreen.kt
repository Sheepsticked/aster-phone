package app.asterphone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.asterphone.R
import app.asterphone.sip.AccountState
import app.asterphone.sip.RegistrationStatus

@Composable
fun AccountsScreen(
    accounts: List<AccountState>,
    onAdd: () -> Unit,
    onEdit: (id: String) -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onRegisterAgain: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (accounts.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                ScreenTitle("Accounts", Modifier.padding(horizontal = 16.dp))
                EmptyState(
                    icon = R.drawable.ic_manage_accounts_outlined,
                    title = "No accounts yet",
                    text = "Add your extension on the Aster server to start calling.",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    actionLabel = "Add account",
                    onAction = onAdd,
                )
            }
            return@Box
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenTitle("Accounts") }
            items(accounts, key = { it.account.id }) { state ->
                AccountCard(state, onEdit = { onEdit(state.account.id) }, onToggle = { onToggle(state.account.id, it) }, onRegisterAgain = onRegisterAgain)
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add account") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun AccountCard(state: AccountState, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onRegisterAgain: () -> Unit) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val account = state.account
    Card(onClick = onEdit, colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(state, selected = false, background = container)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(account.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    account.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccountStatus(state, Modifier.padding(top = 2.dp))
                if (state.status == RegistrationStatus.Failed) {
                    TextButton(onClick = onRegisterAgain, contentPadding = PaddingValues(end = 12.dp)) { Text("Register again") }
                }
            }
            Switch(checked = account.enabled, onCheckedChange = onToggle)
        }
    }
}
