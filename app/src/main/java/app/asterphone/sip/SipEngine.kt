package app.asterphone.sip

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.core.content.edit
import app.asterphone.BuildConfig
import app.asterphone.call.CallNotifications
import app.asterphone.data.SipAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.LogLevel
import org.linphone.core.Reason
import org.linphone.core.RegistrationState

enum class RegistrationStatus { Off, Connecting, Registered, Failed }

data class AccountState(val account: SipAccount, val status: RegistrationStatus, val detail: String = "")

enum class CallPhase { Ringing, Dialing, Connected, OnHold, Ended }

data class CallInfo(
    val name: String,
    val number: String,
    val accountLabel: String,
    val incoming: Boolean,
    val phase: CallPhase,
    /** [SystemClock.elapsedRealtime] when the call connected, or null before that. */
    val connectedAt: Long?,
    val muted: Boolean,
    val speaker: Boolean,
    /** Why the call ended, when it wasn't a normal hang-up. */
    val endReason: String? = null,
)

data class HistoryEntry(
    val name: String,
    val number: String,
    val accountId: String?,
    val accountLabel: String,
    val incoming: Boolean,
    val missed: Boolean,
    val startMillis: Long,
    val durationSeconds: Int,
)

/** Owns the single Linphone Core. Call everything on the main thread, which is where the Core runs. */
object SipEngine {
    private const val MAX_HISTORY = 200
    private const val SETTINGS = "settings"
    private const val KEY_DETAILED_LOG = "detailed_sip_log"
    private const val KEY_DIAL_ACCOUNT = "dial_account"
    private val AUDIO_CODECS = setOf("PCMU", "PCMA", "telephone-event")
    private val FINISHED = setOf(Call.State.End, Call.State.Error, Call.State.Released)
    private const val PAUSED_RETRY_MILLIS = 60 * 60 * 1000L

    // Only ever the application context, which lives as long as the process.
    @SuppressLint("StaticFieldLeak")
    private lateinit var context: Context
    private lateinit var core: Core
    private var applied: List<SipAccount>? = null
    private val linked = mutableMapOf<String, Account>()
    private val failures = mutableMapOf<String, String>()
    // Accounts whose password the server refused. They stop registering until something may have changed,
    // because retrying every minute keeps the phone awake for nothing.
    private val paused = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val retryPaused = Runnable { resumePaused() }
    private var connectedAt: Long? = null
    private var endReason: String? = null
    // The call shown in the UI, tracked from callbacks so it is known before the Core lists it.
    private var trackedCall: Call? = null
    private var telecom: TelecomCalls? = null

    private val _accounts = MutableStateFlow<List<AccountState>>(emptyList())
    val accounts: StateFlow<List<AccountState>> = _accounts.asStateFlow()

    private val _call = MutableStateFlow<CallInfo?>(null)
    val call: StateFlow<CallInfo?> = _call.asStateFlow()

    // Kept after the call is released, so the call screen can still show how it ended.
    private val _ended = MutableStateFlow<CallInfo?>(null)
    val ended: StateFlow<CallInfo?> = _ended.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    // The account the dialer calls through, remembered across restarts.
    private val _dialAccount = MutableStateFlow<String?>(null)
    val dialAccount: StateFlow<String?> = _dialAccount.asStateFlow()

    // Writes every SIP message to the system log, for troubleshooting over adb.
    private val _detailedLog = MutableStateFlow(false)
    val detailedLog: StateFlow<Boolean> = _detailedLog.asStateFlow()

    @MainThread
    fun start(appContext: Context) {
        if (::core.isInitialized) return
        context = appContext.applicationContext
        val factory = Factory.instance()
        factory.setLoggerDomain("Linphone")
        factory.enableLogcatLogs(true)
        _detailedLog.value = BuildConfig.DEBUG || settings().getBoolean(KEY_DETAILED_LOG, false)
        _dialAccount.value = settings().getString(KEY_DIAL_ACCOUNT, null)
        applyLogLevel()
        // With no config file the Core keeps its database, including call history, in the app's files directory.
        core = factory.createCore(null, null, context)
        configure(core)
        telecom = TelecomCalls.create(context)
        // Telecom takes audio focus for its calls, so the SIP library must not compete for it.
        if (telecom != null) core.config.setBool("audio", "android_disable_audio_focus_requests", true)
        core.addListener(listener)
        core.start()
        loadHistory()
        NetworkWatcher(context, ::onNetworkChanged).start()
    }

    /**
     * The SIP library only reconnects when the network type changes, which misses Wi-Fi to Wi-Fi moves and
     * any change under a VPN. Reconnect on every address change so the server learns the new address at once.
     */
    private fun onNetworkChanged() {
        if (linked.isEmpty()) return
        resumePaused()
        if (core.callsNb == 0) {
            // Rebuilds the SIP sockets on the new network and registers again from there.
            core.isNetworkReachable = false
            core.isNetworkReachable = true
        } else {
            core.refreshRegisters()
        }
    }

    private fun configure(core: Core) {
        core.setUserAgent("AsterPhone", BuildConfig.VERSION_NAME)
        core.isAutoIterateEnabled = true
        // No client keepalives: on a LAN or VPN nothing in between needs them, and each one wakes the phone.
        core.isKeepAliveEnabled = false
        core.isPushNotificationEnabled = false
        core.isVideoCaptureEnabled = false
        core.isVideoDisplayEnabled = false
        core.isIpv6Enabled = false
        core.isNativeRingingEnabled = true
        core.isVibrationOnIncomingCallEnabled = true
        core.setMaxCalls(1)
        core.setUseRfc2833ForDtmf(true)
        core.setUseInfoForDtmf(false)
        // Random local ports for UDP and TCP; TLS is not used.
        val transports = core.transports
        transports.udpPort = -1
        transports.tcpPort = -1
        transports.tlsPort = 0
        core.setTransports(transports)
        // G.711 only: GSM gateway audio is narrowband, so Asterisk never has to transcode.
        for (payload in core.audioPayloadTypes) payload.enable(payload.mimeType in AUDIO_CODECS)
    }

    /** Replaces the Core's accounts with [list]; does nothing if the list is unchanged. */
    @MainThread
    fun applyAccounts(list: List<SipAccount>) {
        if (!::core.isInitialized || list == applied) return
        applied = list
        core.clearAccounts()
        core.clearAllAuthInfo()
        linked.clear()
        failures.clear()
        paused.clear()
        val factory = Factory.instance()
        for (account in list.filter { it.enabled }) {
            val identity = factory.createAddress(account.identityUri())
            val server = factory.createAddress(account.serverUri())
            if (identity == null || server == null) {
                failures[account.id] = "Invalid server or username"
                continue
            }
            val params = core.createAccountParams()
            params.setIdentityAddress(identity)
            params.setServerAddress(server)
            params.isOutboundProxyEnabled = true
            params.isRegisterEnabled = true
            params.expires = account.expirySeconds
            params.isPublishEnabled = false
            params.pushNotificationAllowed = false
            params.remotePushNotificationAllowed = false
            core.addAuthInfo(factory.createAuthInfo(account.username, null, account.password, null, null, identity.domain))
            val linphoneAccount = core.createAccount(params)
            core.addAccount(linphoneAccount)
            linked[account.id] = linphoneAccount
        }
        core.defaultAccount = linked.values.firstOrNull()
        publishAccounts()
        loadHistory()
        syncKeepAlive()
    }

    @MainThread
    fun setDialAccount(id: String) {
        settings().edit { putString(KEY_DIAL_ACCOUNT, id) }
        _dialAccount.value = id
    }

    @MainThread
    fun setDetailedLog(on: Boolean) {
        settings().edit { putBoolean(KEY_DETAILED_LOG, on) }
        _detailedLog.value = on
        applyLogLevel()
    }

    private fun applyLogLevel() =
        Factory.instance().loggingService.setLogLevel(if (_detailedLog.value) LogLevel.Message else LogLevel.Warning)

    private fun settings() = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    /** Starts or stops the keep-alive service to match whether any account is enabled. */
    @MainThread
    fun syncKeepAlive() {
        val list = applied ?: return
        if (list.any { it.enabled }) KeepAliveService.start(context) else KeepAliveService.stop(context)
    }

    /** Sends REGISTER again for every account, including those paused after a refused password. */
    @MainThread
    fun refreshRegistrations() {
        if (!::core.isInitialized) return
        // Paused accounts register on their own once resumed.
        val resumed = paused.toSet()
        resumePaused()
        linked.filterKeys { it !in resumed }.values.forEach { it.refreshRegister() }
    }

    /** Stops registering [id] after the server refused its password; Linphone would otherwise retry every minute. */
    private fun pause(id: String, account: Account) {
        if (!paused.add(id)) return
        account.params = account.params.clone().apply { isRegisterEnabled = false }
        handler.removeCallbacks(retryPaused)
        handler.postDelayed(retryPaused, PAUSED_RETRY_MILLIS)
    }

    private fun resumePaused() {
        handler.removeCallbacks(retryPaused)
        if (paused.isEmpty()) return
        val ids = paused.toList()
        paused.clear()
        for (id in ids) {
            val account = linked[id] ?: continue
            account.params = account.params.clone().apply { isRegisterEnabled = true }
        }
    }

    /** Starts a call and returns null, or returns a message explaining why it couldn't start. */
    @MainThread
    fun dial(accountId: String, number: String): String? {
        val account = applied.orEmpty().firstOrNull { it.id == accountId } ?: return "Choose an account"
        val linphoneAccount = linked[accountId] ?: return "${account.label} is turned off"
        if (core.callsNb > 0) return "Already in a call"
        val uri = account.uriFor(number) ?: return "Enter a number"
        val address = Factory.instance().createAddress(uri) ?: return "Can't call $number"
        val params = core.createCallParams(null) ?: return "Can't start a call"
        params.account = linphoneAccount
        params.isVideoEnabled = false
        connectedAt = null
        endReason = null
        _ended.value = null
        trackedCall = core.inviteAddressWithParams(address, params) ?: return "The call didn't start"
        publishCall()
        return null
    }

    @MainThread
    fun answer() {
        val call = ringingCall() ?: return
        val params = core.createCallParams(call)
        params?.isVideoEnabled = false
        call.acceptWithParams(params)
    }

    @MainThread
    fun decline() {
        ringingCall()?.decline(Reason.Declined)
    }

    @MainThread
    fun hangUp() {
        val ringing = ringingCall()
        if (ringing != null) ringing.decline(Reason.Declined) else activeCall()?.terminate()
    }

    @MainThread
    fun setMuted(muted: Boolean) {
        activeCall()?.microphoneMuted = muted
        publishCall()
    }

    /** Routes audio to the loudspeaker, or back to a headset or the earpiece. */
    @MainThread
    fun setSpeaker(on: Boolean) {
        val call = activeCall() ?: return
        val preferred = if (on) {
            listOf(AudioDevice.Type.Speaker)
        } else {
            listOf(AudioDevice.Type.Bluetooth, AudioDevice.Type.Headset, AudioDevice.Type.Headphones, AudioDevice.Type.Earpiece)
        }
        val device = preferred.firstNotNullOfOrNull { type ->
            core.audioDevices.firstOrNull { it.type == type && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
        }
        if (device != null) call.outputAudioDevice = device
        publishCall()
    }

    /** The call in progress as it is right now, which can be ahead of [call] inside a state change. */
    @MainThread
    fun currentCall(): CallInfo? = if (::core.isInitialized) activeCall()?.let { describe(it) } else null

    /** Puts the call on hold, or takes it off hold. */
    @MainThread
    fun setHeld(held: Boolean) {
        val call = activeCall() ?: return
        if (held && call.state == Call.State.StreamsRunning) call.pause()
        if (!held && call.state == Call.State.Paused) call.resume()
    }

    @MainThread
    fun sendDtmf(digit: Char) {
        activeCall()?.sendDtmf(digit)
    }

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState?, message: String) {
            val id = idOf(account) ?: return
            when {
                state == RegistrationState.Failed -> {
                    failures[id] = describeFailure(account, message)
                    if (account.error == Reason.Unauthorized) pause(id, account)
                }
                // Keep showing why a paused account isn't registered.
                id !in paused -> failures.remove(id)
            }
            publishAccounts()
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
            if (state != null) telecom?.onCallState(call, state)
            val tracked = track(call, state)
            when (state) {
                Call.State.IncomingReceived -> if (tracked) {
                    endReason = null
                    _ended.value = null
                }
                Call.State.Connected -> if (connectedAt == null) connectedAt = SystemClock.elapsedRealtime()
                Call.State.End, Call.State.Error -> if (tracked) {
                    endReason = describeEnd(call)
                    _ended.value = describe(call)
                }
                Call.State.Released -> {
                    if (call.callLog.status == Call.Status.Missed) {
                        val info = describe(call)
                        CallNotifications.showMissed(context, info.name, info.accountLabel)
                    }
                    if (core.callsNb == 0) connectedAt = null
                }
                else -> Unit
            }
            publishCall()
        }

        override fun onCallLogUpdated(core: Core, callLog: CallLog) = loadHistory()

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) = publishCall()
    }

    private fun publishAccounts() {
        _accounts.value = applied.orEmpty().map { account ->
            val linphoneAccount = linked[account.id]
            val status = when {
                !account.enabled -> RegistrationStatus.Off
                linphoneAccount == null || account.id in paused -> RegistrationStatus.Failed
                else -> when (linphoneAccount.state) {
                    RegistrationState.Ok, RegistrationState.Refreshing -> RegistrationStatus.Registered
                    RegistrationState.Failed -> RegistrationStatus.Failed
                    RegistrationState.Cleared -> RegistrationStatus.Off
                    else -> RegistrationStatus.Connecting
                }
            }
            val detail = if (status == RegistrationStatus.Failed) failures[account.id].orEmpty() else ""
            AccountState(account, status, detail)
        }
    }

    private fun publishCall() {
        val info = activeCall()?.let { describe(it) }
        _call.value = info
        if (info == null || info.phase == CallPhase.Ended) {
            CallNotifications.cancelCall(context)
        } else {
            CallNotifications.showCall(context, info)
        }
    }

    private fun loadHistory() {
        val accounts = applied.orEmpty()
        _history.value = core.callLogs.sortedByDescending { it.startDate }.take(MAX_HISTORY).map { log ->
            val remote = log.remoteAddress
            val local = log.localAddress
            val account = accounts.firstOrNull { it.username == local.username && it.host == local.domain }
                ?: accounts.firstOrNull { it.username == local.username }
            val number = remote.username.orEmpty()
            HistoryEntry(
                name = remote.displayName?.takeIf { it.isNotBlank() } ?: number,
                number = number,
                accountId = account?.id,
                accountLabel = account?.label.orEmpty(),
                incoming = log.dir == Call.Dir.Incoming,
                missed = log.status == Call.Status.Missed,
                startMillis = log.startDate * 1000,
                durationSeconds = log.duration,
            )
        }
    }

    private fun describe(call: Call): CallInfo {
        val remote = call.remoteAddress
        val number = remote.username.orEmpty()
        val name = remote.displayName?.takeIf { it.isNotBlank() } ?: number.ifEmpty { remote.asStringUriOnly() }
        val phase = when (call.state) {
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia, Call.State.PushIncomingReceived -> CallPhase.Ringing
            Call.State.Idle, Call.State.OutgoingInit, Call.State.OutgoingProgress,
            Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> CallPhase.Dialing
            Call.State.End, Call.State.Error, Call.State.Released -> CallPhase.Ended
            Call.State.Pausing, Call.State.Paused -> CallPhase.OnHold
            else -> CallPhase.Connected
        }
        return CallInfo(
            name = name,
            number = number,
            accountLabel = accountFor(call)?.label.orEmpty(),
            incoming = call.dir == Call.Dir.Incoming,
            phase = phase,
            connectedAt = if (phase == CallPhase.Connected || phase == CallPhase.OnHold) connectedAt else null,
            muted = call.microphoneMuted,
            speaker = call.outputAudioDevice?.type == AudioDevice.Type.Speaker,
            endReason = if (phase == CallPhase.Ended) endReason else null,
        )
    }

    /**
     * Follows [call] unless another call is already in progress, such as one being rejected as busy.
     * Returns whether [call] is the one shown in the UI.
     */
    private fun track(call: Call, state: Call.State?): Boolean {
        val current = trackedCall
        val busyWithOther = current != null && current.state !in FINISHED &&
            current.callLog.callId != call.callLog.callId
        if (busyWithOther) return false
        trackedCall = if (state == Call.State.Released) null else call
        return true
    }

    /** A reason for outgoing calls that didn't end with a normal hang-up; incoming calls end as the user chose. */
    private fun describeEnd(call: Call): String? {
        if (call.dir == Call.Dir.Incoming) return null
        val info = call.errorInfo
        // Asterisk ends answered calls with a Q.850 cause such as 16 (normal clearing); only SIP codes are shown.
        val sipCode = if (info.protocol.equals("SIP", ignoreCase = true)) info.protocolCode else 0
        return callEndText(call.reason, sipCode, info.phrase, answered = call.callLog.status == Call.Status.Success)
    }

    private fun accountFor(call: Call): SipAccount? {
        val accounts = applied.orEmpty()
        idOf(call.params.account)?.let { id -> return accounts.firstOrNull { it.id == id } }
        // Asterisk addresses incoming calls to the registered extension, so match on that.
        val user = call.toAddress.username
        return accounts.firstOrNull { it.enabled && it.username == user }
    }

    private fun idOf(account: Account?): String? {
        if (account == null) return null
        linked.entries.firstOrNull { it.value === account }?.let { return it.key }
        val identity = account.params.identityAddress?.asStringUriOnly() ?: return null
        return linked.entries.firstOrNull { it.value.params.identityAddress?.asStringUriOnly() == identity }?.key
    }

    private fun describeFailure(account: Account, message: String): String {
        val info = account.errorInfo
        val sipCode = if (info.protocol.equals("SIP", ignoreCase = true)) info.protocolCode else 0
        return registrationFailureText(account.error, message, sipCode, info.phrase)
    }

    private fun activeCall(): Call? =
        trackedCall?.takeIf { it.state != Call.State.Released } ?: core.currentCall ?: core.calls.firstOrNull()

    private fun ringingCall(): Call? = activeCall()?.takeIf {
        it.state == Call.State.IncomingReceived || it.state == Call.State.IncomingEarlyMedia
    }
}
