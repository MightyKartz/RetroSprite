package com.retrosprite.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.retrosprite.app.data.db.RetroSpriteDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetroSpriteDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RetroSpriteDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration3To5BackfillsGkpMetadataAndEnableState() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO games (
                    game_id, title, platform, region, languages, rom_crc32, rom_sha1,
                    pack_version, schema_version, trust_level, installed_at
                ) VALUES (
                    'relay_station', 'Relay Station', 'sample', NULL, '["zh","en"]', NULL, NULL,
                    '0.1.0', 'gkp.v0', 'sample', 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO games (
                    game_id, title, platform, region, languages, rom_crc32, rom_sha1,
                    pack_version, schema_version, trust_level, installed_at
                ) VALUES (
                    'custom_game', 'Custom Game', 'sample', NULL, '["zh"]', NULL, NULL,
                    '1.0.0', 'gkp.v0', 'personal', 2
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            *RetroSpriteDatabase.MIGRATIONS,
        )

        db.query(
            "SELECT pack_id, provenance, signature_status, enabled, disabled_at FROM games WHERE game_id = 'relay_station'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("sample.relay-station", cursor.getString(0))
            assertEquals("bundled", cursor.getString(1))
            assertEquals("unsigned", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(true, cursor.isNull(4))
        }
        db.query(
            "SELECT pack_id, provenance, signature_status, enabled, disabled_at FROM games WHERE game_id = 'custom_game'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("custom_game", cursor.getString(0))
            assertEquals("external", cursor.getString(1))
            assertEquals("unsigned", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(true, cursor.isNull(4))
        }
        db.close()
    }

    @Test
    fun migration5To6AddsQuestionColumnsToRequestLogs() {
        helper.createDatabase(TEST_DB_5_6, 5).apply {
            execSQL(
                """
                INSERT INTO request_logs (
                    timestamp, request_key, label, system, game, image_size, paused,
                    output_mode, response_text, error_message,
                    duration_millis, llm_tokens_in, llm_tokens_out
                ) VALUES (
                    1, 'request-1', '2048__', '2048', '', 0, 1,
                    'text', 'ok', NULL,
                    0, 0, 0
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_5_6,
            6,
            true,
            *RetroSpriteDatabase.MIGRATIONS,
        )

        db.query("SELECT question, question_source FROM request_logs WHERE label = '2048__'")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
            }
        db.close()
    }

    @Test
    fun migration7To8AddsQuestionNormalizationColumnsToRequestLogs() {
        helper.createDatabase(TEST_DB_7_8, 7).apply {
            execSQL(
                """
                INSERT INTO request_logs (
                    timestamp, request_key, label, system, game, image_size, paused,
                    output_mode, question, question_source,
                    answer_short, answer_detail, answer_type, answer_confidence,
                    spoiler_level_used, next_actions, response_text, error_message,
                    duration_millis, llm_tokens_in, llm_tokens_out
                ) VALUES (
                    1, 'request-1', 'mega_drive__光明力量2', 'mega_drive', '光明力量2', 0, 1,
                    'hotkey_voice:text', '修医是谁', 'hotkey_voice',
                    NULL, NULL, NULL, NULL,
                    NULL, NULL, 'ok', NULL,
                    0, 0, 0
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_7_8,
            8,
            true,
            *RetroSpriteDatabase.MIGRATIONS,
        )

        db.query(
            """
            SELECT raw_question, normalized_question, question_normalization_reason,
                   normalized_question_matched_term, normalized_question_matched_entity_id
            FROM request_logs WHERE label = 'mega_drive__光明力量2'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
            assertEquals(true, cursor.isNull(1))
            assertEquals(true, cursor.isNull(2))
            assertEquals(true, cursor.isNull(3))
            assertEquals(true, cursor.isNull(4))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-3-5"
        const val TEST_DB_5_6 = "migration-5-6"
        const val TEST_DB_7_8 = "migration-7-8"
    }
}
