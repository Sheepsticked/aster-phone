package app.asterphone.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.asterphone.data.AsterReach
import app.asterphone.data.AsterServer
import kotlinx.coroutines.launch

/** What the Aster tab's list can ask for. */
class AsterTabActions(
    /** Opens an Aster server in the tab. */
    val select: (AsterServer) -> Unit,
    /** Looks at a server's address again at once. */
    val recheck: (AsterServer) -> Unit,
    /** Edits a server's address, or with null adds a panel. */
    val edit: (AsterServer?) -> Unit,
    val openSheet: () -> Unit,
    val closeSheet: () -> Unit,
)

private fun AsterReach?.label(): String = when (this) {
    AsterReach.Aster -> "Aster"
    AsterReach.NoAster -> "No Aster panel"
    AsterReach.Unreachable -> "Not reachable"
    null -> "Checking…"
}

@Composable
private fun AsterReach?.dotColor(): Color = when (this) {
    AsterReach.Aster -> CallGreen
    AsterReach.Unreachable -> MaterialTheme.colorScheme.error
    AsterReach.NoAster -> MaterialTheme.colorScheme.outline
    null -> WaitAmber
}

/** The server's initial with what the last look at its address found as a small dot; [background] rings the dot. */
@Composable
private fun ServerAvatar(server: AsterServer, selected: Boolean, background: Color) {
    Box {
        if (selected) {
            Avatar(server.name, 40.dp, containerColor = MaterialTheme.colorScheme.primary, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Avatar(server.name, 40.dp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(background),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(server.reach.dotColor()))
        }
    }
}

/** The Aster server the tab shows, as a bar that opens the list. It reads like the dialer's account bar. */
@Composable
fun AsterServerBar(server: AsterServer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = modifier.fillMaxWidth().testTag("aster-server-bar"),
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ServerAvatar(server, selected = false, background = background)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val problem = server.reach == AsterReach.Unreachable || server.reach == AsterReach.NoAster
                Text(
                    if (problem) "${server.detail} · ${server.reach.label()}" else server.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (server.reach == AsterReach.Unreachable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change Aster server")
        }
    }
}

/** The list as a sheet over the page, like the dialer's list of accounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsterServerSheet(servers: List<AsterServer>, currentKey: String?, actions: AsterTabActions) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // Slide the sheet away first, then run the action.
    val close: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            actions.closeSheet()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = actions.closeSheet, sheetState = sheetState) {
        AsterServerPicker(
            servers = servers,
            currentKey = currentKey,
            onSelect = { server -> close { actions.select(server) } },
            onRecheck = actions.recheck,
            onEdit = { server -> close { actions.edit(server) } },
            onAdd = { close { actions.edit(null) } },
        )
    }
}

/**
 * Every server: those with an Aster first, then the others, greyed, each with a "Set address" action, then "Add Aster panel".
 * Holding a server edits its address. [header] goes above the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AsterServerPicker(
    servers: List<AsterServer>,
    currentKey: String?,
    onSelect: (AsterServer) -> Unit,
    onRecheck: (AsterServer) -> Unit,
    onEdit: (AsterServer) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {
        Text("Aster servers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
    },
) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    val (withAster, without) = servers.partition { it.isAster }
    LazyColumn(modifier.fillMaxWidth().testTag("aster-server-list")) {
        item { header() }
        items(withAster, key = { it.key }) { server ->
            val isCurrent = server.key == currentKey
            ListItem(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .semantics { selected = isCurrent }
                    .combinedClickable(
                        role = Role.RadioButton,
                        onLongClickLabel = "Edit address",
                        onLongClick = { onEdit(server) },
                        onClick = { onSelect(server) },
                    )
                    .testTag("pick-${server.key}"),
                colors = if (isCurrent) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else transparent,
                leadingContent = {
                    ServerAvatar(server, isCurrent, if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                },
                headlineContent = { Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(server.detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = if (isCurrent) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
            )
        }
        if (without.isNotEmpty()) {
            if (withAster.isNotEmpty()) item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Text(
                    "Other servers",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                )
            }
            items(without, key = { it.key }) { server ->
                val grey = MaterialTheme.colorScheme.onSurfaceVariant
                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClickLabel = "Check again",
                            onLongClickLabel = "Edit address",
                            onLongClick = { onEdit(server) },
                            onClick = { onRecheck(server) },
                        )
                        .testTag("pick-${server.key}"),
                    colors = transparent,
                    leadingContent = { ServerAvatar(server, false, MaterialTheme.colorScheme.surfaceContainerLow) },
                    headlineContent = { Text(server.name, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            Text(server.detail, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                server.reach.label(),
                                color = if (server.reach == AsterReach.Unreachable) MaterialTheme.colorScheme.error else grey,
                            )
                        }
                    },
                    trailingContent = { TextButton(onClick = { onEdit(server) }) { Text("Set address") } },
                )
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            ListItem(
                modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onAdd).testTag("add-panel"),
                colors = transparent,
                leadingContent = { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = null) } },
                headlineContent = { Text("Add Aster panel") },
            )
        }
    }
}

/** The tab while no server has an Aster: the list itself, with what it means and what to do. */
@Composable
fun AsterNoServers(servers: List<AsterServer>, actions: AsterTabActions, modifier: Modifier = Modifier) {
    val looking = servers.any { it.reach == null }
    AsterServerPicker(
        servers = servers,
        currentKey = null,
        onSelect = actions.select,
        onRecheck = actions.recheck,
        onEdit = { actions.edit(it) },
        onAdd = { actions.edit(null) },
        modifier = modifier.fillMaxSize().testTag("aster-no-servers"),
        header = {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                ScreenTitle("Aster")
                Text(
                    when {
                        looking -> "Looking for Aster on the servers of your accounts…"
                        servers.isEmpty() -> "There is no server to look at yet. Add an account, or add the address of an Aster panel."
                        else -> "No Aster panel found. Aster Phone looks for one on the server of each account, on the plain web port. " +
                            "If yours is somewhere else, set its address. Tap a server to look again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
