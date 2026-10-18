package app.asterphone.call

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    call: CallInfo?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onMute: (Boolean) -> Unit,
    onSpeaker: (Boolean) -> Unit,
    onDtmf: (Char) -> Unit,
) {
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var tones by rememberSaveable { mutableStateOf("") }
    val active = call != null && (call.phase == CallPhase.Dialing || call.phase == CallPhase.Connected)
    val keypadShown = showKeypad && active

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
            Text(
                call?.name.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (call != null && call.number.isNotEmpty() && call.number != call.name) {
                Text(call.number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (call != null && call.accountLabel.isNotEmpty()) {
                Text("via ${call.accountLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            val failed = call?.endReason != null
            Text(
                statusText(call),
                style = MaterialTheme.typography.titleMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            call?.endReason?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
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
                call == null || call.phase == CallPhase.Ended -> Unit
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
                    }
                    Spacer(Modifier.height(32.dp))
                    RoundAction("Hang up", R.drawable.ic_call_end, CallRed, onHangUp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun statusText(call: CallInfo?): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val connectedAt = call?.connectedAt
    LaunchedEffect(connectedAt) {
        while (connectedAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    return when (call?.phase) {
        null, CallPhase.Ended -> if (call?.endReason != null) "Call failed" else "Call ended"
        CallPhase.Ringing -> "Incoming call"
        CallPhase.Dialing -> "Calling…"
        CallPhase.Connected -> connectedAt?.let { formatDuration(((now - it) / 1000).toInt().coerceAtLeast(0)) } ?: "Connected"
    }
}
