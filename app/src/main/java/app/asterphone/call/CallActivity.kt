package app.asterphone.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asterphone.sip.CallInfo
import app.asterphone.sip.CallPhase
import app.asterphone.sip.CallService
import app.asterphone.sip.SipEngine
import app.asterphone.ui.AsterPhoneTheme
import kotlinx.coroutines.delay

/** Incoming and in-call screen. It can show over the lock screen. */
class CallActivity : ComponentActivity() {
    private var answerAfterPermission = false
    private var proximityLock: PowerManager.WakeLock? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (answerAfterPermission) {
            answerAfterPermission = false
            answerNow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handle(intent)
        setContent {
            AsterPhoneTheme {
                val call by SipEngine.call.collectAsStateWithLifecycle()
                val ended by SipEngine.ended.collectAsStateWithLifecycle()
                var last by remember { mutableStateOf<CallInfo?>(null) }
                LaunchedEffect(call) { call?.let { last = it } }
                // Keep showing the last call as ended for a moment after it goes away.
                val shown = call ?: ended ?: last?.copy(phase = CallPhase.Ended)
                val finished = call == null || call?.phase == CallPhase.Ended
                LaunchedEffect(finished) {
                    if (finished) {
                        // Leave a failure reason on screen long enough to read.
                        delay(if (shown?.endReason != null) FAILED_SCREEN_MILLIS else ENDED_SCREEN_MILLIS)
                        finish()
                    }
                }
                LaunchedEffect(shown?.phase, shown?.speaker) {
                    setProximity(shown != null && !shown.speaker && (shown.phase == CallPhase.Dialing || shown.phase == CallPhase.Connected))
                }
                CallScreen(
                    call = shown,
                    onAnswer = ::answer,
                    onDecline = SipEngine::decline,
                    onHangUp = SipEngine::hangUp,
                    onMute = SipEngine::setMuted,
                    onSpeaker = SipEngine::setSpeaker,
                    onDtmf = SipEngine::sendDtmf,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) answer()
    }

    private fun answer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            answerNow()
        } else {
            answerAfterPermission = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun answerNow() {
        // This screen is visible, so the call service may now use the microphone in the background too.
        CallService.enableMicrophone(this)
        SipEngine.answer()
    }

    /** Turns the screen off when the phone is held to the ear. */
    private fun setProximity(on: Boolean) {
        if (on) {
            if (proximityLock != null) return
            val power = getSystemService(PowerManager::class.java)
            if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "asterphone:proximity")
                .apply { acquire(MAX_CALL_MILLIS) }
        } else {
            proximityLock?.let { if (it.isHeld) it.release() }
            proximityLock = null
        }
    }

    companion object {
        const val ACTION_SHOW = "app.asterphone.action.SHOW_CALL"
        const val ACTION_ANSWER = "app.asterphone.action.ANSWER"
        private const val ENDED_SCREEN_MILLIS = 1200L
        private const val FAILED_SCREEN_MILLIS = 4000L
        private const val MAX_CALL_MILLIS = 4 * 60 * 60 * 1000L
    }
}
