package com.assistant.branch.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Хранилище чувствительных данных (API ключей) с шифрованием.
 *
 * Использует Android Keystore для мастер-ключа и AES-256-GCM для значений.
 * Если Keystore недоступен (старые устройства, кривые прошивки) — падаем на
 * обычный SharedPreferences, но флаг [isUsingFallback] становится true и
 * вызывающий код ОБЯЗАН предупредить пользователя: ключи лежат открытым
 * текстом в app-private storage.
 *
 * Хранение: файлы encrypted_prefs / secure_prefs_fallback в filesDir.
 */
class SecureSettings(context: Context) {

    private val useFallback: Boolean
    private val prefs: SharedPreferences

    init {
        var ok = false
        var sp: SharedPreferences? = null
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            sp = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            ok = true
        } catch (t: Throwable) {
            android.util.Log.w("SecureSettings", "Encrypted storage unavailable: ${t.message}")
        }
        useFallback = !ok
        prefs = sp ?: context.getSharedPreferences(
            FILE_NAME + "_fallback",
            Context.MODE_PRIVATE,
        )
    }

    /** true, если Keystore сломан и API-ключи хранятся без шифрования. */
    val isUsingFallback: Boolean get() = useFallback

    /** Имя файла prefs для исключения из бэкапа (см. backup_rules.xml). */
    val fileName: String get() = if (useFallback) FILE_NAME + "_fallback" else FILE_NAME

    /** Flow, эмитящий пару (chatApiKey, jevApiKey) при изменении. */
    val changesFlow: Flow<Pair<String, String>> = callbackFlow {
        trySend(currentChatKey() to currentJevKey())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(currentChatKey() to currentJevKey())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    var chatApiKey: String
        get() = currentChatKey()
        set(value) { prefs.edit().putString(KEY_CHAT_API, value).apply() }

    var jevApiKey: String
        get() = currentJevKey()
        set(value) { prefs.edit().putString(KEY_JEV_API, value).apply() }

    private fun currentChatKey() = prefs.getString(KEY_CHAT_API, "").orEmpty()
    private fun currentJevKey() = prefs.getString(KEY_JEV_API, "").orEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "secure_prefs"
        private const val KEY_CHAT_API = "chat_api_key"
        private const val KEY_JEV_API = "jev_api_key"
    }
}