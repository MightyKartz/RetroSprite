package com.retrosprite.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
 * Schema v1 entities:
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
    version = 1,
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

            val db = builder.addCallback(callback).build()
            holder[0] = db
            return db
        }

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
