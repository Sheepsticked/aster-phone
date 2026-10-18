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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.asterphone.sip.AccountState

@Composable
fun AccountsScreen(
    accounts: List<AccountState>,
    onAdd: () -> Unit,
    onEdit: (id: String) -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (accounts.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No accounts yet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap + to add your extension on the Aster server.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (accounts.isNotEmpty()) item { ScreenTitle("Accounts") }
            items(accounts, key = { it.account.id }) { state ->
                Card(onClick = { onEdit(state.account.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(state.status)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.account.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.account.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AccountStatus(state, Modifier.padding(top = 2.dp))
                        }
                        Switch(checked = state.account.enabled, onCheckedChange = { onToggle(state.account.id, it) })
                    }
                }
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add account")
        }
    }
}
