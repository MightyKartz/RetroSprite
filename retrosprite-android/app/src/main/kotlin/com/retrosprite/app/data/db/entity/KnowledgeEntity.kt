package com.retrosprite.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single knowledge chunk belonging to a registered [GameEntity].
 *
 * Hard rule: this row stores plain text only. No executable code, no
 * binary blobs, no ROM data of any kind. Schema validation is performed
 * by the GKP Manager prior to insertion (Phase 2).
 */
@Entity(
    tableName = "knowledge",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["game_id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["game_id"]),
        Index(value = ["game_id", "entity_type"]),
        Index(value = ["game_id", "entity_id"], unique = true)
    ]
)
data class KnowledgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "game_id")
    val gameId: String,

    /** Stable identifier within a GKP, e.g. `enemy.metroid`. */
    @ColumnInfo(name = "entity_id")
    val entityId: String,

    /** "enemy" | "item" | "boss" | "location" | "mechanic" | ... */
    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,

    /** JSON array string of localized aliases. */
    @ColumnInfo(name = "aliases_json")
    val aliasesJson: String,

    @ColumnInfo(name = "description_short")
    val descriptionShort: String,

    @ColumnInfo(name = "description_long")
    val descriptionLong: String?,

    /** Optional progression gate identifier, e.g. `"after_kraid"`. */
    @ColumnInfo(name = "progress_gate")
    val progressGate: String?,

    /** "none" | "light" | "medium" | "heavy" */
    @ColumnInfo(name = "spoiler_level")
    val spoilerLevel: String = "light",

    /** JSON array of source references (urls / book pages / authors). */
    @ColumnInfo(name = "source_refs_json")
    val sourceRefsJson: String,

    /** "low" | "medium" | "high" */
    @ColumnInfo(name = "confidence")
    val confidence: String,

    /** JSON array of pre-baked answer templates (optional). */
    @ColumnInfo(name = "answer_templates_json")
    val answerTemplatesJson: String?
)
