package app.asterphone.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.asterphone.R
import app.asterphone.sip.CallInfo
import app.asterphone.sip.CallPhase
import app.asterphone.ui.Avatar
import app.asterphone.ui.CallGreen
import app.asterphone.ui.CallRed
import app.asterphone.ui.DialPad
import app.asterphone.ui.RoundAction
import app.asterphone.ui.RoundToggle
import app.asterphone.ui.formatDuration
import app.asterphone.ui.rememberElapsedSeconds

@Composable
fun CallScreen(
    call: CallInfo?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onMute: (Boolean) -> Unit,
    onSpeaker: (Boolean) -> Unit,
    onHold: (Boolean) -> Unit,
    onDtmf: (Char) -> Unit,
    onClose: () -> Unit,
    onCallAgain: () -> Unit,
) {
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var tones by rememberSaveable { mutableStateOf("") }
    val active = call != null && (call.phase == CallPhase.Dialing || call.phase == CallPhase.Connected || call.phase == CallPhase.OnHold)
    val keypadShown = showKeypad && active
    val seconds = rememberElapsedSeconds(call?.connectedAt)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (keypadShown) 8.dp else 40.dp))
            if (!keypadShown) {
                Avatar(call?.name.orEmpty(), 112.dp)
                Spacer(Modifier.height(20.dp))
            }
            if (call != null) AccountChip(call)
            Text(
                call?.name.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (call != null && call.number.isNotEmpty() && call.number != call.name) {
                Text(call.number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            val failed = call?.endReason != null
            val status = statusText(call, seconds)
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            call?.endReason?.let { reason ->
                val (text, code) = splitEndReason(reason)
                if (text != status) {
                    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                code?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (call?.phase == CallPhase.OnHold) {
                Spacer(Modifier.height(8.dp))
                HoldChip()
            }
            if (keypadShown) {
                Text(
                    tones.takeLast(20),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp).height(36.dp),
                )
            }
            Spacer(Modifier.weight(1f))

            when {
                call == null -> Unit
                call.phase == CallPhase.Ended -> if (failed) FailedActions(canCallAgain = !call.incoming, onClose = onClose, onCallAgain = onCallAgain)
                call.phase == CallPhase.Ringing -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction("Decline", R.drawable.ic_call_end, CallRed, onDecline)
                    RoundAction("Answer", R.drawable.ic_call, CallGreen, onAnswer)
                }
                else -> {
                    if (keypadShown) {
                        DialPad(onKey = {
                            tones += it
                            onDtmf(it)
                        }, keySize = 64.dp)
                        Spacer(Modifier.height(24.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundToggle("Mute", R.drawable.ic_mic_off, call.muted) { onMute(!call.muted) }
                        RoundToggle("Speaker", R.drawable.ic_volume_up, call.speaker) { onSpeaker(!call.speaker) }
                        RoundToggle("Keypad", R.drawable.ic_dialpad, showKeypad) { showKeypad = !showKeypad }
                        RoundToggle("Hold", R.drawable.ic_pause, call.phase == CallPhase.OnHold) { onHold(call.phase != CallPhase.OnHold) }
                    }
                    Spacer(Modifier.height(32.dp))
                    RoundAction("Hang up", R.drawable.ic_call_end, CallRed, onHangUp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Which account the call uses: "Office · 501", or just the label when there is no extension to add. */
@Composable
private fun AccountChip(call: CallInfo) {
    val text = listOf(call.accountLabel, call.accountExtension).filter { it.isNotEmpty() }.distinct().joinToString(" · ")
    if (text.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("account-chip"),
    ) {
        Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HoldChip() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pause), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("On hold", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** What follows a failed call: Close, and Call again for an outgoing call. */
@Composable
private fun FailedActions(canCallAgain: Boolean, onClose: () -> Unit, onCallAgain: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (canCallAgain) Arrangement.SpaceEvenly else Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        // Level with the 72 dp call button, not with its label.
        Box(Modifier.height(72.dp), contentAlignment = Alignment.Center) {
            FilledTonalButton(onClick = onClose) { Text("Close") }
        }
        if (canCallAgain) RoundAction("Call again", R.drawable.ic_call, CallGreen, onCallAgain)
    }
}

/** The plain reason and, when the server gave one, its status code only: "Busy · SIP 486 Busy Here" gives "Busy" and "SIP 486". */
internal fun splitEndReason(reason: String): Pair<String, String?> {
    val text = reason.substringBefore(" · ")
    val code = Regex("""SIP \d+""").find(reason.substringAfter(" · ", ""))?.value
    return text to code
}

private fun statusText(call: CallInfo?, seconds: Int?): String? = when (call?.phase) {
    null, CallPhase.Ended -> if (call?.endReason != null) "Call failed" else "Call ended"
    CallPhase.Ringing -> "Incoming call"
    CallPhase.Dialing -> "Calling…"
    // The hold chip says "On hold"; the timer keeps running underneath.
    CallPhase.Connected -> seconds?.let(::formatDuration) ?: "Connected"
    CallPhase.OnHold -> seconds?.let(::formatDuration)
}
