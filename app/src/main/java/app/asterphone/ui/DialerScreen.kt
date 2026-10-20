package app.asterphone.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.asterphone.R
import app.asterphone.data.ContactDirectory
import app.asterphone.data.SipAccount
import app.asterphone.sip.AccountState
import app.asterphone.sip.CallInfo
import app.asterphone.sip.CallPhase
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
    contacts: ContactDirectory = ContactDirectory(emptyList()),
    onAllowContacts: (() -> Unit)? = null,
    prefill: String? = null,
    onPrefillConsumed: () -> Unit = {},
) {
    val usable = accounts.filter { it.account.enabled }
    val selected = usable.firstOrNull { it.account.id == selectedAccountId }
        ?: usable.firstOrNull { it.status == RegistrationStatus.Registered }
        ?: usable.firstOrNull()
    val number = rememberTextFieldState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var listingContacts by rememberSaveable { mutableStateOf(false) }
    val matches = remember(number.text, contacts) { contacts.search(number.text.toString()) }
    val fill: (String) -> Unit = { number.setTextAndPlaceCursorAtEnd(SipAccount.cleanNumber(it)) }
    val pickContact = rememberContactPicker(fill)
    val haptics = rememberHaptics()
    // A number sent from elsewhere, such as a Recents entry, lands in the field once.
    LaunchedEffect(prefill) {
        if (prefill != null) {
            fill(prefill)
            onPrefillConsumed()
        }
    }

    if (selected == null) {
        NoAccounts(anyAccounts = accounts.isNotEmpty(), onAddAccount = onAddAccount)
        return
    }

    val empty = number.text.isBlank()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Size the keys to the space left over; on very small screens the dialer scrolls instead.
        // The keypad and the call row are five rows of keys with gaps of 0.18 key between them.
        val fixed = (if (call != null) 80.dp else 0.dp) + 238.dp
        val keySize = ((maxHeight - fixed) / 5.72f).coerceIn(52.dp, 78.dp)
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
                Spacer(Modifier.height(12.dp))
                NumberField(number)
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val best = matches.firstOrNull()
                    when {
                        best != null -> ContactSuggestion(
                            match = best,
                            more = matches.size - 1,
                            onPick = { fill(best.contact.number) },
                            onMore = { listingContacts = true },
                        )
                        onAllowContacts != null -> TextButton(onClick = onAllowContacts) {
                            Icon(painterResource(R.drawable.ic_contacts), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Show contacts as you type")
                        }
                    }
                }
                DialPad(onKey = number::type, allowPlus = true, keySize = keySize)
                Spacer(Modifier.height(keySize * 0.18f))
                // The bottom row, where the thumb reaches easily: contacts, call and delete under * 0 #.
                Row(horizontalArrangement = Arrangement.spacedBy(keySize * 0.36f), verticalAlignment = Alignment.CenterVertically) {
                    RowButton("Contacts", R.drawable.ic_contacts, keySize, onClick = pickContact)
                    // With an empty field, the first press brings back the last number dialled.
                    CallButton(
                        enabled = call == null && (!empty || lastDialed != null),
                        size = keySize,
                        description = if (empty && lastDialed != null) "Call last number" else "Call",
                    ) {
                        haptics.tap()
                        if (empty) lastDialed?.let(number::setTextAndPlaceCursorAtEnd)
                        else onDial(selected.account.id, SipAccount.cleanNumber(number.text.toString()))
                    }
                    if (empty) {
                        Spacer(Modifier.size(keySize))
                    } else {
                        // Tap deletes one character; holding clears the number.
                        RowButton(
                            "Delete",
                            R.drawable.ic_backspace,
                            keySize,
                            onClick = {
                                haptics.tap()
                                number.deleteBack()
                            },
                            onLongClick = { number.clearText() },
                            onLongClickLabel = "Clear number",
                        )
                    }
                }
            }
        }
    }

    if (listingContacts) {
        ContactMatchesSheet(matches = matches, onPick = { fill(it.number) }, onDismiss = { listingContacts = false })
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
    EmptyState(
        icon = R.drawable.ic_dialpad_outlined,
        title = if (anyAccounts) "All accounts are off" else "No accounts yet",
        text = if (anyAccounts) "Turn an account on in the Accounts tab to make calls." else "Add your extension on the Aster server to start calling.",
        modifier = Modifier.fillMaxSize(),
        actionLabel = if (anyAccounts) null else "Add account",
        onAction = onAddAccount,
    )
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
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun AccountState.statusColor(): Color =
    if (status == RegistrationStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

/** A plain round icon button the size of a key, for the row beside the call button. */
@Composable
private fun RowButton(
    label: String,
    @DrawableRes icon: Int,
    size: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun CallButton(enabled: Boolean, size: Dp, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CallGreen,
            contentColor = Color.White,
            disabledContainerColor = CallGreen.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.85f),
        ),
    ) {
        Icon(painterResource(R.drawable.ic_call), contentDescription = description, modifier = Modifier.size(32.dp))
    }
}

/** The number being dialled, with a cursor and paste; the keypad types into it, so the system keyboard stays hidden. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NumberField(state: TextFieldState, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val text = state.text
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Number" }.testTag("number"),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = (if (text.length > 14) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
                    .copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ActiveCallBanner(call: CallInfo, onOpenCall: () -> Unit) {
    val seconds = rememberElapsedSeconds(call.connectedAt)
    val text = when {
        call.phase == CallPhase.OnHold -> "Call on hold · Back to call"
        seconds != null -> "Back to call · ${formatDuration(seconds)}"
        else -> "Back to call"
    }
    Surface(
        onClick = onOpenCall,
        color = CallBannerGreen,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
