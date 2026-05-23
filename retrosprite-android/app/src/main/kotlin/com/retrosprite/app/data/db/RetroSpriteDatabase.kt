package com.retrosprite.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.retrosprite.app.data.db.converters.StringListConverter
import com.retrosprite.app.data.db.dao.GameDao
import com.retrosprite.app.data.db.dao.KnowledgeDao
import com.retrosprite.app.data.db.dao.KnowledgeFtsDao
import com.retrosprite.app.data.db.dao.RequestLogDao
import com.retrosprite.app.data.db.entity.GameEntity
import com.retrosprite.app.data.db.entity.KnowledgeEntity
import com.retrosprite.app.data.db.entity.RequestLogEntity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root Room database for RetroSprite.
 *
 * Schema v8 entities:
 *  - request_logs (RequestLogEntity)
 *  - games        (GameEntity)
 *  - knowledge    (KnowledgeEntity)
 *
 * In addition to the Room-managed tables above, [knowledge_fts] is an
 * FTS5 virtual table created via [RoomDatabase.Callback] (Room has no
 * native @Fts5 support yet). When FTS5 is unavailable on the device the
 * virtual table is skipped and consumers must fall back to LIKE queries
 * via [KnowledgeDao.fallbackSearch].
 */
@Database(
    entities = [
        RequestLogEntity::class,
        GameEntity::class,
        KnowledgeEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class RetroSpriteDatabase : RoomDatabase() {

    abstract fun requestLogDao(): RequestLogDao
    abstract fun gameDao(): GameDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun knowledgeFtsDao(): KnowledgeFtsDao

    /**
     * Whether the FTS5 virtual table is present and should be queried.
     * Set during the [RoomDatabase.Callback] lifecycle and observed by
     * the knowledge repository.
     */
    fun isFtsAvailable(): Boolean = ftsAvailable.get()

    private val ftsAvailable: AtomicBoolean = AtomicBoolean(false)

    private fun markFtsAvailable(value: Boolean) {
        ftsAvailable.set(value)
    }

    companion object {
        const val DATABASE_NAME = "retrosprite.db"
        private const val TAG = "RetroSpriteDatabase"

        @Volatile
        private var instance: RetroSpriteDatabase? = null

        fun getInstance(context: Context): RetroSpriteDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Visible for tests that need a clean in-memory database.
         */
        fun buildInMemory(context: Context): RetroSpriteDatabase {
            return buildInternal(context, inMemory = true)
        }

        private fun build(context: Context): RetroSpriteDatabase {
            return buildInternal(context, inMemory = false)
        }

        private fun buildInternal(context: Context, inMemory: Boolean): RetroSpriteDatabase {
            // The callback needs to mark FTS availability on the resulting
            // database; we capture it via a holder to break the cycle.
            val holder = arrayOfNulls<RetroSpriteDatabase>(1)
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    installFts(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    if (!SQLiteCapabilities.knowledgeFtsTableExists(db)) {
                        installFts(db)
                    }
                    holder[0]?.markFtsAvailable(
                        SQLiteCapabilities.knowledgeFtsTableExists(db)
                    )
                }
            }

            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(
                    context.applicationContext,
                    RetroSpriteDatabase::class.java
                ).allowMainThreadQueries()
            } else {
                Room.databaseBuilder(
                    context.applicationContext,
                    RetroSpriteDatabase::class.java,
                    DATABASE_NAME
                )
            }

            val db = builder
                .addMigrations(*MIGRATIONS)
                .addCallback(callback)
                .build()
            holder[0] = db
            return db
        }

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE request_logs ADD COLUMN duration_millis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_status TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_provider TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_model TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_max_tokens INTEGER")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_timeout_ms INTEGER")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_latency_ms INTEGER")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_tokens_in INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_tokens_out INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN llm_error TEXT")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE request_logs ADD COLUMN request_key TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN feedback TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN feedback_timestamp INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_request_logs_request_key " +
                        "ON request_logs(request_key)"
                )
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN pack_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN provenance TEXT NOT NULL DEFAULT 'unknown'")
                db.execSQL("ALTER TABLE games ADD COLUMN signature_status TEXT NOT NULL DEFAULT 'unsigned'")
                db.execSQL("ALTER TABLE games ADD COLUMN signature_key_id TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN content_digest TEXT")
                db.execSQL(
                    """
                    UPDATE games
                    SET pack_id = CASE game_id
                        WHEN '2048' THEN 'sample.2048'
                        WHEN 'relay_station' THEN 'sample.relay-station'
                        ELSE game_id
                    END
                    WHERE pack_id = ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE games
                    SET provenance = CASE
                        WHEN game_id IN ('2048', 'relay_station') THEN 'bundled'
                        ELSE 'external'
                    END
                    WHERE provenance = 'unknown'
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_pack_id ON games(pack_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_provenance ON games(provenance)")
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE games ADD COLUMN disabled_at INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_enabled ON games(enabled)")
            }
        }

        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE request_logs ADD COLUMN question TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN question_source TEXT")
            }
        }

        private val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE request_logs ADD COLUMN answer_short TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN answer_detail TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN answer_type TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN answer_confidence TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN spoiler_level_used TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN next_actions TEXT")
            }
        }

        private val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE request_logs ADD COLUMN raw_question TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN normalized_question TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN question_normalization_reason TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN normalized_question_matched_term TEXT")
                db.execSQL("ALTER TABLE request_logs ADD COLUMN normalized_question_matched_entity_id TEXT")
            }
        }

        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )

        private fun installFts(db: SupportSQLiteDatabase) {
            if (!SQLiteCapabilities.supportsFts5(db)) {
                Log.w(TAG, "FTS5 not supported; skipping knowledge_fts creation")
                return
            }
            try {
                KnowledgeFtsSchema.ALL_DDL.forEach { db.execSQL(it) }
                // Rebuild in case knowledge rows already exist.
                db.execSQL(KnowledgeFtsSchema.REBUILD_INDEX)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create knowledge_fts; using LIKE fallback", t)
            }
        }

        /** Visible for tests. */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
