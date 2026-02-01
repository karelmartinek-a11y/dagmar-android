package cz.hcasc.dagmarng.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage:
 * - instance_id
 * - instance_token
 * - device_fingerprint
 * - device_name
 * - display_name
 *
 * Attendance data is NOT persisted.
 */
class SecureStore(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDeviceFingerprint(): String? = prefs.getString(KEY_DEVICE_FINGERPRINT, null)
    fun setDeviceFingerprint(value: String) { prefs.edit().putString(KEY_DEVICE_FINGERPRINT, value).apply() }

    fun getInstanceId(): String? = prefs.getString(KEY_INSTANCE_ID, null)
    fun setInstanceId(value: String) { prefs.edit().putString(KEY_INSTANCE_ID, value).apply() }

    fun getInstanceToken(): String? = prefs.getString(KEY_INSTANCE_TOKEN, null)
    fun setInstanceToken(value: String) { prefs.edit().putString(KEY_INSTANCE_TOKEN, value).apply() }

    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)
    fun setDisplayName(value: String) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    fun getDeviceName(): String? = prefs.getString(KEY_DEVICE_NAME, null)
    fun setDeviceName(value: String) { prefs.edit().putString(KEY_DEVICE_NAME, value).apply() }

    fun clearInstanceToken() { prefs.edit().remove(KEY_INSTANCE_TOKEN).apply() }
    fun clearInstanceId() { prefs.edit().remove(KEY_INSTANCE_ID).apply() }
    fun clearAll() { prefs.edit().clear().apply() }

    companion object {
        private const val PREFS_NAME = "dagmarng_secure_store"
        private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
        private const val KEY_INSTANCE_ID = "instance_id"
        private const val KEY_INSTANCE_TOKEN = "instance_token"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
