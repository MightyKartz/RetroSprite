package com.retrosprite.app.data.db

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Runtime detection of optional SQLite features. RetroSprite gracefully
 * degrades when an Android device ships a SQLite build without FTS5
 * (rare on API 26+ but documented in the AOSP matrix).
 */
internal object SQLiteCapabilities {

    private const val TAG = "SQLiteCapabilities"

    /**
     * Probes whether `CREATE VIRTUAL TABLE ... USING fts5` is supported by
     * the underlying SQLite. The probe uses a temporary in-database virtual
     * table that is dropped immediately, so it is safe to call on the
     * production database.
     */
    fun supportsFts5(db: SupportSQLiteDatabase): Boolean = try {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS __retrosprite_fts5_probe " +
                "USING fts5(probe, tokenize='unicode61')"
        )
        db.execSQL("DROP TABLE IF EXISTS __retrosprite_fts5_probe")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "FTS5 unavailable on this device, falling back to LIKE search", t)
        false
    }

    /**
     * Returns true once the `knowledge_fts` virtual table is observable
     * via sqlite_master. Useful from repositories deciding between FTS
     * and LIKE fallback at query time.
     */
    fun knowledgeFtsTableExists(db: SupportSQLiteDatabase): Boolean = try {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='knowledge_fts' LIMIT 1"
        ).use { cursor -> cursor.moveToFirst() }
    } catch (t: Throwable) {
        false
    }
}
