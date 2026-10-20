package app.asterphone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.asterphone.R
import app.asterphone.sip.AccountState
import app.asterphone.sip.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Call history. Tapping an entry shows its number in the dialer through [onOpenInDialer], with the entry's account
 * or null when that account is gone or off; the phone icon calls back at once through [onCallBack].
 */
@Composable
fun RecentsScreen(
    history: List<HistoryEntry>,
    accounts: List<AccountState>,
    onCallBack: (accountId: String, number: String) -> Unit,
    onOpenInDialer: (accountId: String?, number: String) -> Unit,
) {
    if (history.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Recents", Modifier.padding(horizontal = 16.dp))
            EmptyState(
                icon = R.drawable.ic_history_outlined,
                title = "No calls yet",
                text = "Calls you make and receive show up here.",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        return
    }
    val context = LocalContext.current
    val enabled = accounts.filter { it.account.enabled }
    val enabledIds = enabled.map { it.account.id }
    val groups = history.groupBy { dayLabel(context, it.startMillis) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Recents", Modifier.padding(horizontal = 16.dp)) }
        groups.forEach { (day, entries) ->
            item(key = "day-$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(entries) { entry ->
                // Call back through the same account when it is still enabled.
                val sameAccount = entry.accountId?.takeIf { it in enabledIds }
                val accountId = sameAccount ?: enabledIds.firstOrNull()
                val callable = entry.number.isNotEmpty()
                RecentRow(
                    context = context,
                    entry = entry,
                    onOpen = if (callable) ({ onOpenInDialer(sameAccount, entry.number) }) else null,
                    onCall = accountId?.takeIf { callable }?.let { { onCallBack(it, entry.number) } },
                    // Other accounts to call back through, for the menu.
                    others = if (callable) enabled.filter { it.account.id != accountId } else emptyList(),
                    onCallVia = { onCallBack(it, entry.number) },
                )
            }
        }
    }
}

@Composable
private fun RecentRow(
    context: Context,
    entry: HistoryEntry,
    onOpen: (() -> Unit)?,
    onCall: (() -> Unit)?,
    others: List<AccountState>,
    onCallVia: (accountId: String) -> Unit,
) {
    val details = buildList {
        if (entry.number.isNotEmpty() && entry.number != entry.name) add(entry.number)
        if (entry.accountLabel.isNotEmpty()) add(entry.accountLabel)
        add(DateUtils.formatDateTime(context, entry.startMillis, DateUtils.FORMAT_SHOW_TIME))
        if (entry.durationSeconds > 0) add(formatDurationWords(entry.durationSeconds))
    }
    ListItem(
        modifier = if (onOpen != null) Modifier.clickable(onClickLabel = "Show in dialer", onClick = onOpen) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { DirectionIcon(entry) },
        headlineContent = {
            Text(
                entry.name.ifEmpty { "Unknown" },
                color = if (entry.missed) MaterialTheme.colorScheme.error else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(details.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCall != null) {
                    IconButton(onClick = onCall) { Icon(Icons.Filled.Call, contentDescription = "Call back") }
                }
                if (entry.number.isNotEmpty()) RowMenu(context, entry.number, others, onCallVia)
            }
        },
    )
}

/** "Call via" each other account, and copying the number. */
@Composable
private fun RowMenu(context: Context, number: String, others: List<AccountState>, onCallVia: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options for $number") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            others.forEach { state ->
                DropdownMenuItem(
                    text = { Text("Call via ${state.account.label}") },
                    leadingIcon = { StatusDot(state.status) },
                    onClick = {
                        open = false
                        onCallVia(state.account.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Copy number") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                onClick = {
                    open = false
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Phone number", number))
                },
            )
        }
    }
}

@Composable
private fun DirectionIcon(entry: HistoryEntry) {
    val (icon, label) = when {
        entry.missed -> R.drawable.ic_call_missed to "Missed call"
        entry.incoming -> R.drawable.ic_call_received to "Incoming call"
        else -> R.drawable.ic_call_made to "Outgoing call"
    }
    val tint = if (entry.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** "Today", "Yesterday", or a date such as "Mon, 12 Oct" for older calls. */
private fun dayLabel(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
            if (day.year == today.year) flags = flags or DateUtils.FORMAT_NO_YEAR
            DateUtils.formatDateTime(context, millis, flags)
        }
    }
}
