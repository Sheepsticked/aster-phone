package app.asterphone.ui

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.asterphone.Notifications
import app.asterphone.data.PhoneContacts
import app.asterphone.sip.AccountState
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One item of the background checklist. [ok] is null when the app can't check it. */
data class SetupCheck(
    val title: String,
    val detail: String,
    val ok: Boolean?,
    val required: Boolean,
    val actionLabel: String,
    val action: () -> Unit,
) {
    val needsAction: Boolean get() = required && ok == false
}

/** Builds the checklist and rebuilds it whenever the app comes back to the foreground. */
@Composable
fun rememberSetupChecks(): List<SetupCheck> {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var asked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        PhoneContacts.start(context)
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val unusedStatus by produceState(initialValue = UnusedAppRestrictionsConstants.ERROR, refresh) {
        value = unusedAppRestrictionsStatus(context)
    }

    val openSettings: (Intent) -> Unit = { intent ->
        try {
            settingsLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("Setup", "No settings screen for ${intent.action}", e)
            settingsLauncher.launch(appDetails(context))
        }
    }
    // Ask once; if Android no longer shows the prompt, send the user to the app's settings instead.
    val requestPermission: (String) -> Unit = { permission ->
        if (permission in asked) {
            openSettings(appDetails(context))
        } else {
            asked = asked + permission
            permissionLauncher.launch(permission)
        }
    }

    return remember(refresh, unusedStatus, asked) { buildChecks(context, unusedStatus, requestPermission, openSettings) }
}

private fun buildChecks(
    context: Context,
    unusedStatus: Int,
    requestPermission: (String) -> Unit,
    openSettings: (Intent) -> Unit,
): List<SetupCheck> = buildList {
    val packageUri = "package:${context.packageName}".toUri()
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val notificationsOk = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    add(
        SetupCheck("Notifications", "Shows incoming calls, missed calls and connection problems.", notificationsOk, true, "Allow") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        },
    )

    val statusHidden = Notifications.isStatusHidden(context)
    add(
        SetupCheck(
            "Hide the background notification",
            if (statusHidden) "Hidden. Calls, missed calls and connection problems still show."
            else "Android shows a notification while the app waits for calls. You can turn it off; calls, missed calls and connection problems still show.",
            statusHidden,
            false,
            "Hide",
        ) { openSettings(Notifications.statusSettingsIntent(context)) },
    )

    add(
        SetupCheck("Microphone", "Lets the other side hear you.", granted(Manifest.permission.RECORD_AUDIO), true, "Allow") {
            requestPermission(Manifest.permission.RECORD_AUDIO)
        },
    )

    val restricted = context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
    val exempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    add(
        SetupCheck(
            "Run in background",
            if (restricted) "Battery is set to Restricted for this app. Open app info → Battery → Unrestricted."
            else "Keeps the app connected so calls arrive while the screen is off.",
            exempt && !restricted,
            true,
            "Allow",
        ) {
            if (restricted) openSettings(appDetails(context))
            else openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
        },
    )

    if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
        add(
            SetupCheck(
                "Samsung: Never sleeping apps",
                "Settings → Battery → Background usage limits → Never sleeping apps → + → Aster Phone. " +
                    "Also check app info → Battery is Unrestricted. The app can't check this for you.",
                null,
                false,
                "Open app info",
            ) { openSettings(appDetails(context)) },
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val fullScreenOk = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        add(
            SetupCheck("Full-screen calls", "Shows the call screen over the lock screen.", fullScreenOk, true, "Allow") {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
            },
        )
    }

    val paused = unusedStatus == UnusedAppRestrictionsConstants.API_30_BACKPORT ||
        unusedStatus == UnusedAppRestrictionsConstants.API_30 ||
        unusedStatus == UnusedAppRestrictionsConstants.API_31
    add(
        SetupCheck(
            "Never pause the app",
            "Stops Android from pausing the app when you haven't opened it for a while. Turn off \"Pause app activity if unused\".",
            if (unusedStatus == UnusedAppRestrictionsConstants.ERROR) null else !paused,
            true,
            "Open",
        ) {
            openSettings(IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName))
        },
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(
            SetupCheck("Bluetooth headsets", "Optional: lets calls use a Bluetooth headset.", granted(Manifest.permission.BLUETOOTH_CONNECT), false, "Allow") {
                requestPermission(Manifest.permission.BLUETOOTH_CONNECT)
            },
        )
    }

    add(
        SetupCheck(
            "Contacts",
            "Optional: shows contact names for calls and suggests contacts as you type a number. Contacts stay on the phone.",
            granted(Manifest.permission.READ_CONTACTS),
            false,
            "Allow",
        ) { requestPermission(Manifest.permission.READ_CONTACTS) },
    )
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

private suspend fun unusedAppRestrictionsStatus(context: Context): Int = suspendCancellableCoroutine { continuation ->
    val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
    future.addListener(
        {
            val status = runCatching { future.get() }.getOrDefault(UnusedAppRestrictionsConstants.ERROR)
            if (continuation.isActive) continuation.resume(status)
        },
        ContextCompat.getMainExecutor(context),
    )
}

@Composable
fun SetupScreen(
    checks: List<SetupCheck>,
    accounts: List<AccountState>,
    detailedLog: Boolean,
    onDetailedLog: (Boolean) -> Unit,
) {
    val todo = checks.count { it.needsAction }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenTitle("Setup")
            Text(
                if (todo == 0) "All set. Calls can arrive while the phone sleeps."
                else "$todo ${if (todo == 1) "step needs" else "steps need"} attention so calls arrive while the phone sleeps.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (todo == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        items(checks) { CheckCard(it) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Accounts", style = MaterialTheme.typography.titleMedium)
                    val enabled = accounts.filter { it.account.enabled }
                    if (enabled.isEmpty()) Text("No account is turned on.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    enabled.forEach { state ->
                        Row {
                            StatusDot(state.status, Modifier.padding(top = 6.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(state.account.label, style = MaterialTheme.typography.bodyLarge)
                                AccountStatus(state)
                            }
                        }
                    }
                    OutlinedButton(onClick = { SipEngine.refreshRegistrations() }) { Text("Register again now") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Troubleshooting", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Detailed SIP log: writes every SIP message to the system log, readable with adb logcat. Leave it off otherwise.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = detailedLog, onCheckedChange = onDetailedLog, modifier = Modifier.testTag("detailed-log"))
                }
            }
        }
    }
}

@Composable
private fun CheckCard(check: SetupCheck) {
    val colors = if (check.needsAction) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(16.dp)) {
            val (icon, tint, description) = when {
                check.ok == true -> Triple(Icons.Filled.CheckCircle, CallGreen, "Done")
                check.needsAction -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, "Needs action")
                else -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, if (check.ok == null) "Check yourself" else "Optional")
            }
            Icon(icon, contentDescription = description, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.titleMedium)
                Text(check.detail, style = MaterialTheme.typography.bodyMedium)
                if (check.ok != true) {
                    Spacer(Modifier.height(8.dp))
                    if (check.needsAction) {
                        Button(
                            onClick = check.action,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(check.actionLabel) }
                    } else {
                        OutlinedButton(onClick = check.action) { Text(check.actionLabel) }
                    }
                }
            }
        }
    }
}
