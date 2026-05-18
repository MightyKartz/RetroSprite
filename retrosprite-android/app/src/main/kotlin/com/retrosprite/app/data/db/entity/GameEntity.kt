package com.retrosprite.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A registered game / GKP (Game Knowledge Pack) header.
 *
 * Only ROM identifiers (CRC32 / SHA1) and metadata are stored. ROM
 * content itself is never persisted by RetroSprite.
 */
@Entity(
    tableName = "games",
    indices = [
        Index(value = ["rom_sha1"]),
        Index(value = ["rom_crc32"]),
        Index(value = ["platform", "title"])
    ]
)
data class GameEntity(
    @PrimaryKey
    @ColumnInfo(name = "game_id")
    val gameId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "platform")
    val platform: String,

    @ColumnInfo(name = "region")
    val region: String?,

    /** JSON array string of language codes (BCP-47-ish), e.g. `["en","ja"]`. */
    @ColumnInfo(name = "languages")
    val languages: String,

    @ColumnInfo(name = "rom_crc32")
    val romCrc32: String?,

    @ColumnInfo(name = "rom_sha1")
    val romSha1: String?,

    @ColumnInfo(name = "pack_version")
    val packVersion: String,

    @ColumnInfo(name = "schema_version")
    val schemaVersion: String,

    /** "official" | "community" | "personal" */
    @ColumnInfo(name = "trust_level")
    val trustLevel: String,

    @ColumnInfo(name = "installed_at")
    val installedAt: Long
)
