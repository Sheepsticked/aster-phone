package app.asterphone.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The phone's contacts, read once the user allows it and read again when they change. Kept in memory only. */
object PhoneContacts {
    private const val TAG = "PhoneContacts"
    private const val PREFS = "settings"
    private const val KEY_ASKED = "contacts_asked"
    private const val RELOAD_DELAY_MILLIS = 3_000L

    // Only ever the application context, which lives as long as the process.
    @SuppressLint("StaticFieldLeak")
    private lateinit var context: Context
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val reload = Runnable { load() }
    private var observer: ContentObserver? = null
    private var loading: Job? = null

    private val _directory = MutableStateFlow(ContactDirectory(emptyList()))
    val directory: StateFlow<ContactDirectory> = _directory.asStateFlow()

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Whether the dialer already asked for contacts; after that only the Setup tab offers it. */
    fun asked(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun setAsked(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }

    /** Reads the contacts if allowed and not read yet, then follows changes. Cheap to call again. */
    @MainThread
    fun start(appContext: Context) {
        context = appContext.applicationContext
        if (observer != null || !allowed(context)) return
        val watcher = object : ContentObserver(handler) {
            // Syncing contacts sends bursts of changes, so wait for them to settle.
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                handler.removeCallbacks(reload)
                handler.postDelayed(reload, RELOAD_DELAY_MILLIS)
            }
        }
        try {
            context.contentResolver.registerContentObserver(Phone.CONTENT_URI, true, watcher)
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't follow contact changes", e)
            return
        }
        observer = watcher
        load()
    }

    /** The contact name for a caller's [number], or null. */
    fun nameFor(number: String): String? = _directory.value.nameFor(number)

    private fun load() {
        loading?.cancel()
        loading = scope.launch {
            val contacts = withContext(Dispatchers.IO) { read(context) }
            _directory.value = ContactDirectory(contacts)
        }
    }

    private fun read(context: Context): List<PhoneContact> {
        val projection = arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL)
        val cursor = try {
            context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to read contacts", e)
            null
        } ?: return emptyList()
        val contacts = ArrayList<PhoneContact>(cursor.count)
        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(1)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val name = it.getString(0)?.takeIf { name -> name.isNotBlank() } ?: number
                val type = Phone.getTypeLabel(context.resources, it.getInt(2), it.getString(3)).toString()
                contacts += PhoneContact(name, number, type)
            }
        }
        // The same number often comes from several linked accounts.
        return contacts.distinctBy { it.name to it.digits }
    }
}
