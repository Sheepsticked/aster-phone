package app.asterphone.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.asterphone.data.ContactMatch
import app.asterphone.data.PhoneContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The best contact for the typed digits; [more] other matches open from the button on the right. */
@Composable
fun ContactSuggestion(match: ContactMatch, more: Int, onPick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().height(56.dp).testTag("suggestion"),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(match.contact.name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name(match), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    numberLine(match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (more > 0) {
                TextButton(
                    onClick = onMore,
                    modifier = Modifier.testTag("more-contacts").semantics {
                        contentDescription = if (more == 1) "1 more contact" else "$more more contacts"
                    },
                ) {
                    Text("+$more")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
        }
    }
}

/** Every contact matching the typed digits; picking one fills in its number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactMatchesSheet(matches: List<ContactMatch>, onPick: (PhoneContact) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth().testTag("contact-list")) {
            item {
                Text(
                    if (matches.size == 1) "1 contact" else "${matches.size} contacts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(matches) { index, match ->
                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onPick(match.contact)
                            }
                        }
                        .testTag("contact-$index"),
                    colors = transparent,
                    leadingContent = { Avatar(match.contact.name, 40.dp) },
                    headlineContent = { Text(name(match), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(numberLine(match), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun highlight() = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

@Composable
private fun name(match: ContactMatch): AnnotatedString = highlighted(match.contact.name, match.nameRange, highlight())

/** "Mobile · +1 234…", with the typed digits picked out. */
@Composable
private fun numberLine(match: ContactMatch): AnnotatedString {
    val number = highlighted(match.contact.number, match.numberRange, highlight())
    if (match.contact.type.isEmpty()) return number
    return buildAnnotatedString {
        append(match.contact.type)
        append(" · ")
        append(number)
    }
}

private fun highlighted(text: String, range: IntRange?, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    if (range != null) addStyle(style, range.first, range.last + 1)
}

/** Opens the phone's contact picker; [onPicked] receives the chosen phone number. */
@Composable
fun rememberContactPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch { pickedNumber(context, uri)?.let(onPicked) }
    }
    return {
        try {
            launcher.launch(Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE))
        } catch (e: ActivityNotFoundException) {
            Log.w("ContactPicker", "No contact picker", e)
            Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show()
        }
    }
}

// The picker grants access to the chosen entry, so this works without the contacts permission.
private suspend fun pickedNumber(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: SecurityException) {
        Log.w("ContactPicker", "Can't read the picked contact", e)
        null
    }
}
