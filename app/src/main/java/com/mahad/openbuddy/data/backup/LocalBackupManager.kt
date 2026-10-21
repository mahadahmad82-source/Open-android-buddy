package com.mahad.openbuddy.data.backup

import android.util.Base64
import com.mahad.openbuddy.data.local.entity.AiProviderEntity
import com.mahad.openbuddy.data.local.entity.CodeDraftEntity
import com.mahad.openbuddy.data.local.entity.ConversationEntity
import com.mahad.openbuddy.data.local.entity.MessageEntity
import com.mahad.openbuddy.data.local.entity.QueryHistoryEntity
import com.mahad.openbuddy.domain.model.BackupEnvelope
import com.mahad.openbuddy.domain.model.BackupRestoreException
import com.mahad.openbuddy.domain.model.LocalBackupData
import com.mahad.openbuddy.domain.model.RestorePreviewSummary
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LocalBackupManager {

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

    /**
     * Serializes local database state and non-secret metadata into a structured JSON string.
     * Guaranteed never to include API keys, tokens, or passwords.
     */
    fun serializeBackupData(data: LocalBackupData): String {
        val root = JSONObject()
        root.put("magic", BackupEnvelope.MAGIC_HEADER)
        root.put("version", data.version)
        root.put("appName", data.appName)
        root.put("exportedAt", data.exportedAt)
        root.put("notice", "Zero keys, tokens, or passwords are included in this export.")

        // 1. Conversations
        val convArray = JSONArray()
        data.conversations.forEach { conv ->
            val obj = JSONObject().apply {
                put("id", conv.id)
                put("title", conv.title)
                put("createdAt", conv.createdAt)
                put("updatedAt", conv.updatedAt)
                put("providerId", conv.providerId ?: JSONObject.NULL)
                put("modelName", conv.modelName ?: JSONObject.NULL)
            }
            convArray.put(obj)
        }
        root.put("conversations", convArray)

        // 2. Messages
        val msgArray = JSONArray()
        data.messages.forEach { msg ->
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("conversationId", msg.conversationId)
                put("role", msg.role)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                put("status", msg.status)
                put("errorMessage", msg.errorMessage ?: JSONObject.NULL)
            }
            msgArray.put(obj)
        }
        root.put("messages", msgArray)

        // 3. AI Providers (strictly without API keys)
        val provArray = JSONArray()
        data.aiProviders.forEach { prov ->
            val obj = JSONObject().apply {
                put("id", prov.id)
                put("name", prov.name)
                put("type", prov.type)
                put("modelName", prov.modelName)
                put("baseUrl", prov.baseUrl ?: JSONObject.NULL)
                put("isDefault", prov.isDefault)
                put("createdAt", prov.createdAt)
            }
            provArray.put(obj)
        }
        root.put("aiProviders", provArray)

        // 4. Query History
        val queryArray = JSONArray()
        data.queryHistory.forEach { q ->
            val obj = JSONObject().apply {
                put("id", q.id)
                put("query", q.query)
                put("category", q.category)
                put("timestamp", q.timestamp)
                put("resultSummary", q.resultSummary)
                put("executionTimeMs", q.executionTimeMs)
                put("isSuccess", q.isSuccess)
                put("errorMessage", q.errorMessage ?: JSONObject.NULL)
            }
            queryArray.put(obj)
        }
        root.put("queryHistory", queryArray)

        // 5. Code Drafts
        val draftArray = JSONArray()
        data.codeDrafts.forEach { draft ->
            val obj = JSONObject().apply {
                put("id", draft.id)
                put("filename", draft.filename)
                put("language", draft.language)
                put("code", draft.code)
                put("createdAt", draft.createdAt)
            }
            draftArray.put(obj)
        }
        root.put("codeDrafts", draftArray)

        // 6. Settings (non-secret preferences)
        val settingsObj = JSONObject()
        data.settings.forEach { (key, value) ->
            settingsObj.put(key, value)
        }
        root.put("settings", settingsObj)

        // 7. Integrations metadata (labels only, no secrets!)
        val integrationsObj = JSONObject()
        data.integrationsMetadata.forEach { (key, value) ->
            integrationsObj.put(key, value ?: JSONObject.NULL)
        }
        root.put("integrationsMetadata", integrationsObj)

        return root.toString()
    }

    /**
     * Encrypts the plain backup JSON using AES-256-GCM derived from the user's passphrase.
     * Returns the formatted BackupEnvelope as a serialized JSON string ready to save.
     */
    fun encryptBackup(plainJson: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }

        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { random.nextBytes(it) }

        val keySpec = PBEKeySpec(passphrase, salt, BackupEnvelope.PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKeyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.doFinal(plainBytes)

        val envelope = JSONObject().apply {
            put("magic", BackupEnvelope.MAGIC_HEADER)
            put("version", BackupEnvelope.CURRENT_VERSION)
            put("kdf", PBKDF2_ALGORITHM)
            put("iterations", BackupEnvelope.PBKDF2_ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            put("createdAt", System.currentTimeMillis())
        }

        return envelope.toString(2)
    }

    /**
     * Validates and parses the outer encrypted envelope, derives key with passphrase,
     * decrypts the AES-256-GCM payload, validates the internal structure, and produces
     * a RestorePreviewSummary.
     */
    fun decryptAndValidateBackup(envelopeJson: String, passphrase: CharArray): RestorePreviewSummary {
        val envelopeObj = try {
            JSONObject(envelopeJson.trim())
        } catch (e: Exception) {
            throw BackupRestoreException.CorruptedFile("Invalid JSON envelope format.")
        }

        val magic = envelopeObj.optString("magic", "")
        if (magic != BackupEnvelope.MAGIC_HEADER && magic != BackupEnvelope.LEGACY_MAGIC_HEADER) {
            throw BackupRestoreException.CorruptedFile("Missing Open Buddy backup header.")
        }

        val version = envelopeObj.optInt("version", 1)
        if (version > BackupEnvelope.CURRENT_VERSION) {
            throw BackupRestoreException.IncompatibleVersion(version, BackupEnvelope.CURRENT_VERSION)
        }

        val iterations = envelopeObj.optInt("iterations", BackupEnvelope.PBKDF2_ITERATIONS)
        val saltBase64 = envelopeObj.optString("salt", "")
        val ivBase64 = envelopeObj.optString("iv", "")
        val ciphertextBase64 = envelopeObj.optString("ciphertext", "")

        if (saltBase64.isBlank() || ivBase64.isBlank() || ciphertextBase64.isBlank()) {
            throw BackupRestoreException.CorruptedFile("Missing encryption parameters in backup file.")
        }

        val salt = try {
            Base64.decode(saltBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            throw BackupRestoreException.CorruptedFile("Corrupt salt parameter.")
        }

        val iv = try {
            Base64.decode(ivBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            throw BackupRestoreException.CorruptedFile("Corrupt IV parameter.")
        }

        val cipherBytes = try {
            Base64.decode(ciphertextBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            throw BackupRestoreException.CorruptedFile("Corrupt ciphertext payload.")
        }

        // Derive key
        val keySpec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKeyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val decryptedBytes = try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.doFinal(cipherBytes)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw BackupRestoreException.WrongPassphrase()
        } catch (e: javax.crypto.BadPaddingException) {
            throw BackupRestoreException.WrongPassphrase()
        } catch (e: Exception) {
            throw BackupRestoreException.CorruptedFile("Decryption failed: ${e.message}")
        }

        val plainJson = String(decryptedBytes, Charsets.UTF_8)
        val backupData = parseBackupData(plainJson)

        return RestorePreviewSummary(
            backupCreatedAt = backupData.exportedAt,
            conversationCount = backupData.conversations.size,
            messageCount = backupData.messages.size,
            queryHistoryCount = backupData.queryHistory.size,
            draftCount = backupData.codeDrafts.size,
            providerCount = backupData.aiProviders.size,
            settingsCount = backupData.settings.size,
            rawBackupData = backupData
        )
    }

    /**
     * Parses the decrypted JSON into structured LocalBackupData.
     */
    private fun parseBackupData(json: String): LocalBackupData {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BackupRestoreException.InvalidStructure("Decrypted content is not valid JSON.")
        }

        val version = root.optInt("version", 1)
        val appName = root.optString("appName", "Open Buddy")
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        // 1. Conversations
        val conversations = mutableListOf<ConversationEntity>()
        val convArray = root.optJSONArray("conversations")
        if (convArray != null) {
            for (i in 0 until convArray.length()) {
                val obj = convArray.getJSONObject(i)
                conversations.add(
                    ConversationEntity(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        createdAt = obj.getLong("createdAt"),
                        updatedAt = obj.getLong("updatedAt"),
                        providerId = if (obj.isNull("providerId")) null else obj.optString("providerId"),
                        modelName = if (obj.isNull("modelName")) null else obj.optString("modelName")
                    )
                )
            }
        }

        // 2. Messages
        val messages = mutableListOf<MessageEntity>()
        val msgArray = root.optJSONArray("messages")
        if (msgArray != null) {
            for (i in 0 until msgArray.length()) {
                val obj = msgArray.getJSONObject(i)
                messages.add(
                    MessageEntity(
                        id = obj.getString("id"),
                        conversationId = obj.getString("conversationId"),
                        role = obj.getString("role"),
                        content = obj.getString("content"),
                        timestamp = obj.getLong("timestamp"),
                        status = obj.optString("status", "COMPLETED"),
                        errorMessage = if (obj.isNull("errorMessage")) null else obj.optString("errorMessage")
                    )
                )
            }
        }

        // 3. AI Providers
        val providers = mutableListOf<AiProviderEntity>()
        val provArray = root.optJSONArray("aiProviders")
        if (provArray != null) {
            for (i in 0 until provArray.length()) {
                val obj = provArray.getJSONObject(i)
                providers.add(
                    AiProviderEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = obj.getString("type"),
                        modelName = obj.getString("modelName"),
                        baseUrl = if (obj.isNull("baseUrl")) null else obj.optString("baseUrl"),
                        isDefault = obj.optBoolean("isDefault", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 4. Query History
        val queryHistory = mutableListOf<QueryHistoryEntity>()
        val queryArray = root.optJSONArray("queryHistory")
        if (queryArray != null) {
            for (i in 0 until queryArray.length()) {
                val obj = queryArray.getJSONObject(i)
                queryHistory.add(
                    QueryHistoryEntity(
                        id = obj.getString("id"),
                        query = obj.getString("query"),
                        category = obj.getString("category"),
                        timestamp = obj.getLong("timestamp"),
                        resultSummary = obj.optString("resultSummary", ""),
                        executionTimeMs = obj.optLong("executionTimeMs", 0),
                        isSuccess = obj.optBoolean("isSuccess", true),
                        errorMessage = if (obj.isNull("errorMessage")) null else obj.optString("errorMessage")
                    )
                )
            }
        }

        // 5. Code Drafts
        val codeDrafts = mutableListOf<CodeDraftEntity>()
        val draftArray = root.optJSONArray("codeDrafts")
        if (draftArray != null) {
            for (i in 0 until draftArray.length()) {
                val obj = draftArray.getJSONObject(i)
                codeDrafts.add(
                    CodeDraftEntity(
                        id = obj.getString("id"),
                        filename = obj.getString("filename"),
                        language = obj.getString("language"),
                        code = obj.getString("code"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        // 6. Settings
        val settings = mutableMapOf<String, String>()
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                settings[k] = settingsObj.getString(k)
            }
        }

        // 7. Integrations Metadata
        val integrations = mutableMapOf<String, String?>()
        val integrationsObj = root.optJSONObject("integrationsMetadata")
        if (integrationsObj != null) {
            val keys = integrationsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                integrations[k] = if (integrationsObj.isNull(k)) null else integrationsObj.getString(k)
            }
        }

        return LocalBackupData(
            version = version,
            appName = appName,
            exportedAt = exportedAt,
            conversations = conversations,
            messages = messages,
            aiProviders = providers,
            queryHistory = queryHistory,
            codeDrafts = codeDrafts,
            settings = settings,
            integrationsMetadata = integrations
        )
    }
}
