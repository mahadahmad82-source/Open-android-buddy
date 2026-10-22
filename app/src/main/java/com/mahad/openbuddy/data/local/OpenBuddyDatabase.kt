package com.mahad.openbuddy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mahad.openbuddy.data.local.dao.AiProviderDao
import com.mahad.openbuddy.data.local.dao.AppSettingDao
import com.mahad.openbuddy.data.local.dao.CodeDraftDao
import com.mahad.openbuddy.data.local.dao.ConversationDao
import com.mahad.openbuddy.data.local.dao.McpServerDao
import com.mahad.openbuddy.data.local.dao.MemoryDao
import com.mahad.openbuddy.data.local.dao.MessageDao
import com.mahad.openbuddy.data.local.dao.QueryHistoryDao
import com.mahad.openbuddy.data.local.dao.SkillDao
import com.mahad.openbuddy.data.local.dao.VoiceActionHistoryDao
import com.mahad.openbuddy.data.local.entity.AiProviderEntity
import com.mahad.openbuddy.data.local.entity.AppSettingEntity
import com.mahad.openbuddy.data.local.entity.CodeDraftEntity
import com.mahad.openbuddy.data.local.entity.ConversationEntity
import com.mahad.openbuddy.data.local.entity.McpServerEntity
import com.mahad.openbuddy.data.local.entity.MemoryEntity
import com.mahad.openbuddy.data.local.entity.MessageEntity
import com.mahad.openbuddy.data.local.entity.QueryHistoryEntity
import com.mahad.openbuddy.data.local.entity.SkillEntity
import com.mahad.openbuddy.data.local.entity.VoiceActionHistoryEntity
import com.mahad.openbuddy.data.security.SecretStore
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
        VoiceActionHistoryEntity::class,
        MemoryEntity::class,
        SkillEntity::class,
        McpServerEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OpenBuddyDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun codeDraftDao(): CodeDraftDao
    abstract fun queryHistoryDao(): QueryHistoryDao
    abstract fun voiceActionHistoryDao(): VoiceActionHistoryDao
    abstract fun memoryDao(): MemoryDao
    abstract fun skillDao(): SkillDao
    abstract fun mcpServerDao(): McpServerDao

    companion object {
        const val DATABASE_NAME = "codepilot_database"

        /**
         * v3 -> v4: agent tables (long-term memory, skills, MCP servers).
         * Purely additive — no existing data is touched.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (`id` TEXT NOT NULL, `fact` TEXT NOT NULL, `category` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skills` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `instructions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mcp_servers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `authToken` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: OpenBuddyDatabase? = null

        fun getInstance(context: Context): OpenBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): OpenBuddyDatabase {
            // Initialize SQLCipher native libraries
            SQLiteDatabase.loadLibs(appContext)

            // Obtain the 256-bit passphrase protected by the Android Keystore
            val secretStore = SecretStore.getInstance(appContext)
            val passphraseBytes = secretStore.getOrCreateDatabasePassphrase()
            val supportFactory = SupportFactory(passphraseBytes)

            return try {
                Room.databaseBuilder(
                    appContext,
                    OpenBuddyDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(supportFactory)
                    .addMigrations(MIGRATION_3_4)
                    // No destructive fallback: schema changes MUST ship an explicit
                    // Migration, otherwise Room fails loudly instead of wiping user chats.
                    .build()
            } catch (e: Exception) {
                // If existing unencrypted database file was present and threw encryption error,
                // delete corrupt/unencrypted database file and recreate encrypted
                appContext.deleteDatabase(DATABASE_NAME)
                Room.databaseBuilder(
                    appContext,
                    OpenBuddyDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(supportFactory)
                    .addMigrations(MIGRATION_3_4)
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
