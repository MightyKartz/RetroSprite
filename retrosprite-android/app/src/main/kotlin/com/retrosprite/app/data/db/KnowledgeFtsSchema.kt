package com.retrosprite.app.data.db

/**
 * Centralised location for raw SQL related to the FTS5 virtual table that
 * mirrors [com.retrosprite.app.data.db.entity.KnowledgeEntity].
 *
 * Room cannot manage FTS5 directly (it only ships @Fts3/@Fts4), so the
 * statements below are executed via [androidx.room.RoomDatabase.Callback].
 *
 * The virtual table is configured as an external-content FTS5 table that
 * mirrors the `knowledge` table by `rowid`. Three triggers keep both
 * tables in sync on insert/update/delete.
 */
internal object KnowledgeFtsSchema {

    const val CREATE_VIRTUAL_TABLE = """
        CREATE VIRTUAL TABLE IF NOT EXISTS knowledge_fts USING fts5(
            canonical_name,
            aliases,
            description_short,
            description_long,
            content='knowledge',
            content_rowid='id',
            tokenize='unicode61'
        )
    """

    const val CREATE_INSERT_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS knowledge_ai
        AFTER INSERT ON knowledge BEGIN
            INSERT INTO knowledge_fts(
                rowid, canonical_name, aliases, description_short, description_long
            ) VALUES (
                new.id,
                new.canonical_name,
                new.aliases_json,
                new.description_short,
                new.description_long
            );
        END
    """

    const val CREATE_DELETE_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS knowledge_ad
        AFTER DELETE ON knowledge BEGIN
            INSERT INTO knowledge_fts(
                knowledge_fts, rowid, canonical_name, aliases,
                description_short, description_long
            ) VALUES (
                'delete',
                old.id,
                old.canonical_name,
                old.aliases_json,
                old.description_short,
                old.description_long
            );
        END
    """

    const val CREATE_UPDATE_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS knowledge_au
        AFTER UPDATE ON knowledge BEGIN
            INSERT INTO knowledge_fts(
                knowledge_fts, rowid, canonical_name, aliases,
                description_short, description_long
            ) VALUES (
                'delete',
                old.id,
                old.canonical_name,
                old.aliases_json,
                old.description_short,
                old.description_long
            );
            INSERT INTO knowledge_fts(
                rowid, canonical_name, aliases, description_short, description_long
            ) VALUES (
                new.id,
                new.canonical_name,
                new.aliases_json,
                new.description_short,
                new.description_long
            );
        END
    """

    /**
     * Rebuilds the FTS index from the contents of `knowledge`. Useful when
     * the virtual table was just created on a database that already had
     * knowledge rows (e.g. FTS5 became available after an OS update).
     */
    const val REBUILD_INDEX = """
        INSERT INTO knowledge_fts(knowledge_fts) VALUES('rebuild')
    """

    val ALL_DDL: List<String> = listOf(
        CREATE_VIRTUAL_TABLE,
        CREATE_INSERT_TRIGGER,
        CREATE_DELETE_TRIGGER,
        CREATE_UPDATE_TRIGGER
    )
}
