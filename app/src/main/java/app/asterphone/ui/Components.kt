package app.asterphone.ui

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.asterphone.sip.AccountState
import app.asterphone.sip.RegistrationStatus
import kotlinx.coroutines.delay
import java.util.Locale

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

/** Large heading at the top of a tab; the screen supplies the side padding. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.padding(top = 16.dp, bottom = 4.dp))
}

/** A call timer such as "2:05" or "1:02:05". */
fun formatDuration(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

/** A call length in words, such as "2 min, 5 sec" or "1 hr, 2 min", so it isn't mistaken for a time of day. */
fun formatDurationWords(seconds: Int, locale: Locale = Locale.getDefault()): String {
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    val secs = seconds % 60
    // Seconds are left out once a call lasts an hour.
    val parts = when {
        hours > 0 -> listOf(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
        minutes > 0 -> listOf(Measure(minutes, MeasureUnit.MINUTE), Measure(secs, MeasureUnit.SECOND))
        else -> listOf(Measure(secs, MeasureUnit.SECOND))
    }.filter { it.number.toInt() > 0 }
    // Non-breaking spaces keep the whole length on one line when a row wraps.
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*parts.toTypedArray()).replace(' ', '\u00A0')
}

/** Whole seconds since [since] (an elapsed-realtime stamp), counted up live; null when there is no start yet. */
@Composable
fun rememberElapsedSeconds(since: Long?): Int? {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (since != null) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    return since?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) }
}

/** Key feedback through the view, which follows the system's touch-feedback setting. */
class Haptics(private val view: View) {
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** What a list or screen shows when it has nothing yet: an icon, a title, a line of text and an optional button. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

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

/** The account's initial with its registration status as a small dot; [background] rings the dot. */
@Composable
fun AccountAvatar(state: AccountState, selected: Boolean, background: Color) {
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
        // The icon's description already says this; reading the visible text too would repeat it.
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
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
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
    }
}

private val KEYS = listOf("123", "456", "789", "*0#")
private val LETTERS = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL",
    '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
)
private val CYRILLIC_LETTERS = mapOf(
    '2' to "АБВГ", '3' to "ДЕЖЗ", '4' to "ИЙКЛ", '5' to "МНОП",
    '6' to "РСТУ", '7' to "ФХЦЧ", '8' to "ШЩЪЫ", '9' to "ЬЭЮЯ",
)

/** Phone keypad used by the dialer and for in-call tones. With [allowPlus], holding 0 types "+". */
@Composable
fun DialPad(onKey: (Char) -> Unit, modifier: Modifier = Modifier, allowPlus: Boolean = false, keySize: Dp = 72.dp) {
    // Phones in Cyrillic-script languages print both alphabets; small keys have room for one line only.
    val locale = LocalConfiguration.current.locales[0]
    val cyrillic = remember(locale) { ULocale.addLikelySubtags(ULocale.forLocale(locale)).script == "Cyrl" } && keySize >= 64.dp
    val haptics = rememberHaptics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(keySize * 0.18f)) {
        for (row in KEYS) {
            Row(horizontalArrangement = Arrangement.spacedBy(keySize * 0.36f)) {
                for (key in row) {
                    val plus = allowPlus && key == '0'
                    DialKey(
                        key = key,
                        letters = if (plus) "+" else LETTERS[key].orEmpty(),
                        secondLetters = if (cyrillic) CYRILLIC_LETTERS[key].orEmpty() else "",
                        size = keySize,
                        onClick = {
                            haptics.tap()
                            onKey(key)
                        },
                        onLongClick = if (plus) ({
                            haptics.longPress()
                            onKey('+')
                        }) else null,
                        longClickLabel = if (plus) "Type plus" else null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    key: Char,
    letters: String,
    secondLetters: String,
    size: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel)
            .testTag("key-$key"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val digitStyle = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
            Text(key.toString(), style = digitStyle, color = MaterialTheme.colorScheme.onSurface)
            val letterStyle = MaterialTheme.typography.labelSmall.let { it.copy(lineHeight = it.fontSize * 1.1f) }
            if (letters.isNotEmpty()) {
                Text(letters, style = letterStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (secondLetters.isNotEmpty()) {
                Text(secondLetters, style = letterStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
