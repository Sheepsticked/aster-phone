package app.asterphone.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.asterphone.sip.AccountState
import app.asterphone.sip.RegistrationStatus

@Composable
fun StatusDot(status: RegistrationStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        RegistrationStatus.Registered -> CallGreen
        RegistrationStatus.Connecting -> WaitAmber
        RegistrationStatus.Failed -> MaterialTheme.colorScheme.error
        RegistrationStatus.Off -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

fun RegistrationStatus.label(): String = when (this) {
    RegistrationStatus.Registered -> "Registered"
    RegistrationStatus.Connecting -> "Connecting…"
    RegistrationStatus.Failed -> "Not registered"
    RegistrationStatus.Off -> "Off"
}

/** The registration status, with the reason for a failure on its own line. */
@Composable
fun AccountStatus(state: AccountState, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodySmall) {
    val color = if (state.status == RegistrationStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(state.status.label(), style = style, color = color)
        if (state.detail.isNotEmpty()) Text(state.detail, style = style, color = color)
    }
}

/** Large heading at the top of a tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier)
}

fun formatDuration(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

/** A circle with the first letter of [name], or a person icon for numbers. */
@Composable
fun Avatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val initial = name.firstOrNull { it.isLetter() }?.uppercaseChar()
    Box(
        modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            val style = if (size >= 64.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium
            Text(initial.toString(), style = style, color = color)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** A large round button with a label underneath, as used for answer, decline and hang up. */
@Composable
fun RoundAction(label: String, @DrawableRes icon: Int, color: Color, onClick: () -> Unit, size: Dp = 72.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
        ) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(size * 0.44f))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** A round on/off button with a label underneath, such as mute or speaker. */
@Composable
fun RoundToggle(label: String, @DrawableRes icon: Int, checked: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private val KEYS = listOf("123", "456", "789", "*0#")
private val LETTERS = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL",
    '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
)

/** Phone keypad used by the dialer and for in-call tones. With [allowPlus], holding 0 types "+". */
@Composable
fun DialPad(onKey: (Char) -> Unit, modifier: Modifier = Modifier, allowPlus: Boolean = false, keySize: Dp = 72.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(keySize * 0.18f)) {
        for (row in KEYS) {
            Row(horizontalArrangement = Arrangement.spacedBy(keySize * 0.36f)) {
                for (key in row) {
                    val plus = allowPlus && key == '0'
                    DialKey(
                        key = key,
                        letters = if (plus) "+" else LETTERS[key].orEmpty(),
                        size = keySize,
                        onClick = { onKey(key) },
                        onLongClick = if (plus) ({ onKey('+') }) else null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(key: Char, letters: String, size: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("key-$key"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val digitStyle = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
            Text(key.toString(), style = digitStyle, color = MaterialTheme.colorScheme.onSurface)
            if (letters.isNotEmpty()) {
                Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
