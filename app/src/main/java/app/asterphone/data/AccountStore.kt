package app.asterphone.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountsDataStore by preferencesDataStore(name = "accounts")

/** Persists SIP accounts in app-private storage with passwords encrypted by [SecretBox]. */
class AccountStore(context: Context) {
    private val store = context.applicationContext.accountsDataStore
    private val box = SecretBox()

    val accounts: Flow<List<SipAccount>> = store.data.map { read(it) }

    suspend fun upsert(account: SipAccount) = update { list ->
        if (list.any { it.id == account.id }) list.map { if (it.id == account.id) account else it } else list + account
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun setEnabled(id: String, enabled: Boolean) = update { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    private suspend fun update(change: (List<SipAccount>) -> List<SipAccount>) {
        store.edit { prefs -> prefs[KEY] = AccountCodec.encode(change(read(prefs)), box::seal) }
    }

    private fun read(prefs: Preferences): List<SipAccount> =
        prefs[KEY]?.let { AccountCodec.decode(it, box::open) } ?: emptyList()

    private companion object {
        val KEY = stringPreferencesKey("accounts_json")
    }
}
