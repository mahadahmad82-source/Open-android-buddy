package com.mahad.openbuddy.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Unified, hardware-backed SecretStore utilizing the Android KeyStore provider with AES-256-GCM.
 * All API keys, GitHub tokens, Supabase tokens, and the database passphrase are encrypted at rest
 * with keys held exclusively inside the device KeyStore.
 *
 * Secrets are never logged or included in error traces.
 */
class SecretStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "codepilot_secure_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "CodePilotMasterKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        // Well-known secret keys
        const val KEY_GITHUB_TOKEN = "vault_github_token"
        const val KEY_SUPABASE_TOKEN = "vault_supabase_token"
        const val KEY_SUPABASE_URL = "vault_supabase_url"
        const val KEY_SUPABASE_PROJECT_REF = "vault_supabase_project_ref"
        private const val KEY_DB_PASSPHRASE = "vault_internal_db_passphrase_v1"

        // Security preferences keys
        const val PREF_BIOMETRIC_LOCK_ENABLED = "pref_biometric_lock_enabled"
        const val PREF_AUTO_LOCK_TIMEOUT_MINUTES = "pref_auto_lock_timeout_minutes"
        const val PREF_REDACTION_ENABLED = "pref_redaction_enabled"
        const val PREF_LAST_BACKUP_TIMESTAMP = "pref_last_backup_timestamp"
        const val PREF_LAST_BACKUP_SIZE_BYTES = "pref_last_backup_size_bytes"
        const val PREF_LAST_BACKUP_FILE_NAME = "pref_last_backup_file_name"

        @Volatile
        private var INSTANCE: SecretStore? = null

        fun getInstance(context: Context): SecretStore {
            return INSTANCE ?: synchronized(this) {
                val instance = SecretStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        migrateLegacySecrets()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encryptBytes(plainBytes: ByteArray): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainBytes)

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // Never leak secret details or stack traces
            null
        }
    }

    private fun decryptBytes(encryptedBase64: String): ByteArray? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // Generic Encrypted Secret Operations
    // ==========================================

    @Synchronized
    fun putSecret(key: String, value: String) {
        if (value.isBlank()) {
            deleteSecret(key)
            return
        }
        val encrypted = encryptBytes(value.toByteArray(Charsets.UTF_8))
        if (encrypted != null) {
            prefs.edit().putString(key, encrypted).apply()
        }
    }

    @Synchronized
    fun getSecret(key: String): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        val decryptedBytes = decryptBytes(encrypted) ?: return null
        return String(decryptedBytes, Charsets.UTF_8)
    }

    @Synchronized
    fun hasSecret(key: String): Boolean {
        return prefs.contains(key) && !prefs.getString(key, null).isNullOrBlank()
    }

    @Synchronized
    fun deleteSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ==========================================
    // AI Provider API Keys
    // ==========================================

    @Synchronized
    fun storeApiKey(providerId: String, apiKey: String) {
        putSecret(providerId, apiKey)
    }

    @Synchronized
    fun getApiKey(providerId: String): String? {
        return getSecret(providerId)
    }

    @Synchronized
    fun hasApiKey(providerId: String): Boolean {
        return hasSecret(providerId)
    }

    @Synchronized
    fun deleteApiKey(providerId: String) {
        deleteSecret(providerId)
    }

    // ==========================================
    // GitHub Tokens
    // ==========================================

    @Synchronized
    fun storeGitHubToken(token: String) {
        putSecret(KEY_GITHUB_TOKEN, token)
    }

    @Synchronized
    fun getGitHubToken(): String? {
        return getSecret(KEY_GITHUB_TOKEN)
    }

    @Synchronized
    fun hasGitHubToken(): Boolean {
        return hasSecret(KEY_GITHUB_TOKEN)
    }

    @Synchronized
    fun deleteGitHubToken() {
        deleteSecret(KEY_GITHUB_TOKEN)
    }

    // ==========================================
    // Supabase Tokens & Project Ref
    // ==========================================

    @Synchronized
    fun storeSupabaseToken(token: String) {
        putSecret(KEY_SUPABASE_TOKEN, token)
    }

    @Synchronized
    fun getSupabaseToken(): String? {
        return getSecret(KEY_SUPABASE_TOKEN)
    }

    @Synchronized
    fun hasSupabaseToken(): Boolean {
        return hasSecret(KEY_SUPABASE_TOKEN)
    }

    @Synchronized
    fun deleteSupabaseToken() {
        deleteSecret(KEY_SUPABASE_TOKEN)
    }

    @Synchronized
    fun storeSupabaseProjectRef(ref: String) {
        val cleanRef = cleanProjectRef(ref)
        putSecret(KEY_SUPABASE_PROJECT_REF, cleanRef)
        // Also keep URL in sync for backward compatibility
        putSecret(KEY_SUPABASE_URL, "https://$cleanRef.supabase.co")
    }

    @Synchronized
    fun getSupabaseProjectRef(): String? {
        val ref = getSecret(KEY_SUPABASE_PROJECT_REF)
        if (!ref.isNullOrBlank()) return ref
        val url = getSecret(KEY_SUPABASE_URL) ?: return null
        return cleanProjectRef(url)
    }

    @Synchronized
    fun hasSupabaseProjectRef(): Boolean {
        return hasSecret(KEY_SUPABASE_PROJECT_REF) || hasSecret(KEY_SUPABASE_URL)
    }

    @Synchronized
    fun deleteSupabaseProjectRef() {
        deleteSecret(KEY_SUPABASE_PROJECT_REF)
        deleteSecret(KEY_SUPABASE_URL)
    }

    @Synchronized
    fun storeSupabaseUrl(url: String) {
        storeSupabaseProjectRef(url)
    }

    @Synchronized
    fun getSupabaseUrl(): String? {
        val ref = getSupabaseProjectRef() ?: return null
        return "https://$ref.supabase.co"
    }

    @Synchronized
    fun hasSupabaseUrl(): Boolean {
        return hasSupabaseProjectRef()
    }

    @Synchronized
    fun deleteSupabaseUrl() {
        deleteSupabaseProjectRef()
    }

    private fun cleanProjectRef(input: String): String {
        var clean = input.trim()
        val match = Regex("""https?://([a-zA-Z0-9_-]+)\.supabase\.co""").find(clean)
        if (match != null) {
            return match.groupValues[1]
        }
        clean = clean.removePrefix("https://").removePrefix("http://")
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        return clean.removeSuffix(".supabase.co")
    }

    // ==========================================
    // Database Passphrase (for SQLCipher)
    // ==========================================

    /**
     * Retrieves or generates a 256-bit cryptographically secure passphrase for SQLCipher,
     * encrypted and protected by the Android Keystore.
     */
    @Synchronized
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val existingEncrypted = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existingEncrypted != null) {
            val decrypted = decryptBytes(existingEncrypted)
            if (decrypted != null && decrypted.size == 32) {
                return decrypted
            }
        }

        // Generate 32 bytes (256 bits) of high-entropy cryptographic randomness
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val encrypted = encryptBytes(randomBytes)
        if (encrypted != null) {
            prefs.edit().putString(KEY_DB_PASSPHRASE, encrypted).commit()
        }
        return randomBytes
    }

    // ==========================================
    // Security & Privacy Settings
    // ==========================================

    fun isBiometricLockEnabled(): Boolean {
        return prefs.getBoolean(PREF_BIOMETRIC_LOCK_ENABLED, false)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_BIOMETRIC_LOCK_ENABLED, enabled).apply()
    }

    fun getAutoLockTimeoutMinutes(): Int {
        return prefs.getInt(PREF_AUTO_LOCK_TIMEOUT_MINUTES, 5)
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        val validMinutes = when (minutes) {
            1, 5, 15 -> minutes
            else -> 5
        }
        prefs.edit().putInt(PREF_AUTO_LOCK_TIMEOUT_MINUTES, validMinutes).apply()
    }

    fun isRedactionEnabled(): Boolean {
        return prefs.getBoolean(PREF_REDACTION_ENABLED, true)
    }

    fun setRedactionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_REDACTION_ENABLED, enabled).apply()
    }

    fun getLastBackupInfo(): com.mahad.openbuddy.domain.model.LastBackupInfo? {
        val timestamp = prefs.getLong(PREF_LAST_BACKUP_TIMESTAMP, 0L)
        if (timestamp <= 0L) return null
        val sizeBytes = prefs.getLong(PREF_LAST_BACKUP_SIZE_BYTES, 0L)
        val fileName = prefs.getString(PREF_LAST_BACKUP_FILE_NAME, "backup.cpbackup") ?: "backup.cpbackup"
        return com.mahad.openbuddy.domain.model.LastBackupInfo(timestamp, sizeBytes, fileName)
    }

    fun setLastBackupInfo(timestamp: Long, sizeBytes: Long, fileName: String) {
        prefs.edit()
            .putLong(PREF_LAST_BACKUP_TIMESTAMP, timestamp)
            .putLong(PREF_LAST_BACKUP_SIZE_BYTES, sizeBytes)
            .putString(PREF_LAST_BACKUP_FILE_NAME, fileName)
            .apply()
    }

    // ==========================================
    // Migration of Earlier Unencrypted Keys
    // ==========================================

    @Synchronized
    fun migrateLegacySecrets() {
        try {
            // Check old default shared preferences if any unencrypted keys were stored
            val legacyPrefsNames = listOf("codepilot_prefs", "user_settings", "default_preferences")
            for (name in legacyPrefsNames) {
                val oldPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val allEntries = oldPrefs.all
                if (allEntries.isNotEmpty()) {
                    val editor = oldPrefs.edit()
                    for ((key, value) in allEntries) {
                        if (value is String && value.isNotBlank()) {
                            when {
                                key.contains("github", ignoreCase = true) -> {
                                    if (!hasGitHubToken()) storeGitHubToken(value)
                                    editor.remove(key)
                                }
                                key.contains("supabase_key", ignoreCase = true) || key.contains("supabase_token", ignoreCase = true) -> {
                                    if (!hasSupabaseToken()) storeSupabaseToken(value)
                                    editor.remove(key)
                                }
                                key.contains("supabase_url", ignoreCase = true) -> {
                                    if (!hasSupabaseUrl()) storeSupabaseUrl(value)
                                    editor.remove(key)
                                }
                                key.contains("api_key", ignoreCase = true) -> {
                                    if (!hasSecret(key)) putSecret(key, value)
                                    editor.remove(key)
                                }
                            }
                        }
                    }
                    editor.apply()
                }
            }
        } catch (_: Exception) {
            // Migration is best-effort and silent
        }
    }

    // ==========================================
    // Wipe All Secrets (for "Delete Everything")
    // ==========================================

    @Synchronized
    fun wipeAllSecrets() {
        prefs.edit().clear().commit()
    }
}
