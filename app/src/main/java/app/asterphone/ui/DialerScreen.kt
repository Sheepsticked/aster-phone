package app.asterphone.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.asterphone.R
import app.asterphone.data.SipAccount
import app.asterphone.sip.AccountState
import app.asterphone.sip.CallInfo
import app.asterphone.sip.RegistrationStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@Composable
fun DialerScreen(
    accounts: List<AccountState>,
    call: CallInfo?,
    selectedAccountId: String?,
    lastDialed: String?,
    onSelectAccount: (String) -> Unit,
    onDial: (accountId: String, number: String) -> Unit,
    onOpenCall: () -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
    onEditAccount: (String) -> Unit,
) {
    val usable = accounts.filter { it.account.enabled }
    val selected = usable.firstOrNull { it.account.id == selectedAccountId }
        ?: usable.firstOrNull { it.status == RegistrationStatus.Registered }
        ?: usable.firstOrNull()
    val number = rememberTextFieldState()
    var choosing by rememberSaveable { mutableStateOf(false) }

    if (selected == null) {
        NoAccounts(anyAccounts = accounts.isNotEmpty(), onAddAccount = onAddAccount)
        return
    }

    val empty = number.text.isBlank()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Size the keys to the space left over; on very small screens the dialer scrolls instead.
        val fixed = (if (call != null) 72.dp else 0.dp) + 300.dp
        val keySize = ((maxHeight - fixed) / 4.6f).coerceIn(52.dp, 78.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (call != null) ActiveCallBanner(call, onOpenCall)
                AccountSwitcher(selected) { choosing = true }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                NumberField(number)
                Spacer(Modifier.height(16.dp))
                DialPad(onKey = number::type, allowPlus = true, keySize = keySize)
                Spacer(Modifier.height(20.dp))
                // With an empty field, the first press brings back the last number dialled.
                CallButton(enabled = call == null && (!empty || lastDialed != null)) {
                    if (empty) lastDialed?.let(number::setTextAndPlaceCursorAtEnd)
                    else onDial(selected.account.id, SipAccount.cleanNumber(number.text.toString()))
                }
            }
        }
    }

    if (choosing) {
        AccountSheet(
            usable = usable,
            selectedId = selected.account.id,
            offCount = accounts.size - usable.size,
            onSelect = onSelectAccount,
            onAdd = onAddAccount,
            onManage = onManageAccounts,
            onEdit = onEditAccount,
            onDismiss = { choosing = false },
        )
    }
}

/** Types [key] at the cursor, replacing any selected text. */
private fun TextFieldState.type(key: Char) = edit {
    val at = selection
    replace(at.min, at.max, key.toString())
    selection = TextRange(at.min + 1)
}

/** Deletes the selection, or the character before the cursor. */
private fun TextFieldState.deleteBack() = edit {
    val at = selection
    val start = if (at.collapsed) (at.min - 1).coerceAtLeast(0) else at.min
    delete(start, at.max)
    selection = TextRange(start)
}

@Composable
private fun NoAccounts(anyAccounts: Boolean, onAddAccount: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (anyAccounts) "All accounts are off" else "No accounts yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            if (anyAccounts) "Turn an account on in the Accounts tab to make calls."
            else "Add your extension on the Aster server to start calling.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!anyAccounts) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddAccount) { Text("Add account") }
        }
    }
}

/** The SIP account calls go through, as a bar that opens the account list. */
@Composable
private fun AccountSwitcher(state: AccountState, onClick: () -> Unit) {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = Modifier.fillMaxWidth().testTag("account-switcher"),
    ) {
        Row(Modifier.height(56.dp).padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(state, selected = false, background = background)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.account.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${state.account.username} · ${state.status.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = state.statusColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change account")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    usable: List<AccountState>,
    selectedId: String,
    offCount: Int,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // Slide the sheet away first, then run the action.
    val close: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AccountPicker(
            usable = usable,
            selectedId = selectedId,
            offCount = offCount,
            onSelect = { id ->
                onSelect(id)
                close {}
            },
            onAdd = { close(onAdd) },
            onManage = { close(onManage) },
            onEdit = { id -> close { onEdit(id) } },
        )
    }
}

/** Every enabled account, then links to add and manage accounts. Holding an account opens its settings. */
@Composable
fun AccountPicker(
    usable: List<AccountState>,
    selectedId: String,
    offCount: Int,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    LazyColumn(Modifier.fillMaxWidth().testTag("account-list")) {
        item {
            Text("Call from", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        }
        items(usable, key = { it.account.id }) { state ->
            val isSelected = state.account.id == selectedId
            val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            ListItem(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .semantics { selected = isSelected }
                    .combinedClickable(
                        role = Role.RadioButton,
                        onLongClickLabel = "Edit account",
                        onLongClick = { onEdit(state.account.id) },
                        onClick = { onSelect(state.account.id) },
                    )
                    .testTag("pick-${state.account.id}"),
                colors = if (isSelected) ListItemDefaults.colors(containerColor = background) else transparent,
                leadingContent = { AccountAvatar(state, isSelected, background) },
                headlineContent = { Text(state.account.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text("${state.account.username} · ${state.status.label()}", color = state.statusColor())
                        if (state.detail.isNotEmpty()) Text(state.detail, color = state.statusColor())
                    }
                },
                trailingContent = if (isSelected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            ListItem(
                modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onAdd),
                colors = transparent,
                leadingContent = { RowIcon { Icon(Icons.Filled.Add, contentDescription = null) } },
                headlineContent = { Text("Add account") },
            )
        }
        item {
            ListItem(
                modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onManage),
                colors = transparent,
                leadingContent = { RowIcon { Icon(painterResource(R.drawable.ic_manage_accounts_outlined), contentDescription = null) } },
                headlineContent = { Text("Manage accounts") },
                supportingContent = if (offCount > 0) ({ Text("$offCount turned off") }) else null,
            )
        }
    }
}

/** Centres an icon in the avatar's width so it lines up with the account rows. */
@Composable
private fun RowIcon(icon: @Composable () -> Unit) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
}

/** The account's initial with its registration status as a small dot; [background] rings the dot. */
@Composable
private fun AccountAvatar(state: AccountState, selected: Boolean, background: Color) {
    Box {
        if (selected) {
            Avatar(state.account.label, 40.dp, containerColor = MaterialTheme.colorScheme.primary, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Avatar(state.account.label, 40.dp)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(state.status)
        }
    }
}

@Composable
private fun AccountState.statusColor(): Color =
    if (status == RegistrationStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun CallButton(enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CallGreen,
            contentColor = Color.White,
            disabledContainerColor = CallGreen.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.85f),
        ),
    ) {
        Icon(painterResource(R.drawable.ic_call), contentDescription = "Call", modifier = Modifier.size(32.dp))
    }
}

/** The number being dialled, with a cursor and paste; the keypad types into it, so the system keyboard stays hidden. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun NumberField(state: TextFieldState, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val text = state.text
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Balances the delete button so the number stays centred.
        Spacer(Modifier.width(48.dp))
        InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
            BasicTextField(
                state = state,
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("number"),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = (if (text.length > 12) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
                    .copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (text.isNotEmpty()) {
                // Tap deletes one character; holding clears the number.
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(onClick = state::deleteBack, onLongClick = state::clearText)
                        .semantics {
                            contentDescription = "Delete"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActiveCallBanner(call: CallInfo, onOpenCall: () -> Unit) {
    Surface(
        onClick = onOpenCall,
        color = CallGreen,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Back to call with ${call.name}", style = MaterialTheme.typography.titleMedium)
        }
    }
}
