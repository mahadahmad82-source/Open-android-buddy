package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AiProviderDao
import com.example.data.local.dao.AppSettingDao
import com.example.data.local.dao.CodeDraftDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.QueryHistoryDao
import com.example.data.local.dao.VoiceActionHistoryDao
import com.example.data.local.entity.AiProviderEntity
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.CodeDraftEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.QueryHistoryEntity
import com.example.data.local.entity.VoiceActionHistoryEntity
import com.example.data.security.SecretStore
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AiProviderEntity::class,
        AppSettingEntity::class,
        CodeDraftEntity::class,
        QueryHistoryEntity::class,
        VoiceActionHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CodePilotDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun codeDraftDao(): CodeDraftDao
    abstract fun queryHistoryDao(): QueryHistoryDao
    abstract fun voiceActionHistoryDao(): VoiceActionHistoryDao

    companion object {
        const val DATABASE_NAME = "codepilot_database"

        @Volatile
        private var INSTANCE: CodePilotDatabase? = null

        fun getInstance(context: Context): CodePilotDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): CodePilotDatabase {
            // Initialize SQLCipher native libraries
            SQLiteDatabase.loadLibs(appContext)

            // Obtain the 256-bit passphrase protected by the Android Keystore
            val secretStore = SecretStore.getInstance(appContext)
            val passphraseBytes = secretStore.getOrCreateDatabasePassphrase()
            val supportFactory = SupportFactory(passphraseBytes)

            return try {
                Room.databaseBuilder(
                    appContext,
                    CodePilotDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(supportFactory)
                    .fallbackToDestructiveMigration()
                    .build()
            } catch (e: Exception) {
                // If existing unencrypted database file was present and threw encryption error,
                // delete corrupt/unencrypted database file and recreate encrypted
                appContext.deleteDatabase(DATABASE_NAME)
                Room.databaseBuilder(
                    appContext,
                    CodePilotDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(supportFactory)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }

        @Synchronized
        fun closeAndReset() {
            try {
                INSTANCE?.close()
            } catch (_: Exception) {}
            INSTANCE = null
        }
    }
}
