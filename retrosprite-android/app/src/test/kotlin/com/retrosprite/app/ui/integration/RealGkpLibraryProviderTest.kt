package com.retrosprite.app.ui.integration

import com.retrosprite.app.data.gkp.BundledGkpImportPhase
import com.retrosprite.app.data.gkp.BundledGkpImportStatus
import com.retrosprite.app.data.gkp.GkpPackProvenance
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePhase
import com.retrosprite.app.ui.viewmodel.UiGkpImportPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealGkpLibraryProviderTest {

    @Test
    fun `maps installed games and knowledge counts into packs ui state`() = runTest {
        val importStatus = MutableStateFlow(
            BundledGkpImportStatus(
                phase = BundledGkpImportPhase.Ready,
                totalPacks = 2,
                importedPacks = 2,
                failedPacks = 0,
                message = "imported 2 bundled GKP packs",
                updatedAtMillis = 42L,
            )
        )
        val provider = RealGkpLibraryProvider(
            gameRepository = FakeGameRepository(listOf(sample2048())),
            knowledgeRepository = FakeKnowledgeRepository(
                listOf(
                    knowledge("2048", "mechanic.merge", listOf("sample.2048.rules")),
                    knowledge("2048", "strategy.corner", listOf("sample.2048.rules", "sample.2048.strategy")),
                )
            ),
            importStatus = importStatus,
            scope = backgroundScope,
        )

        val state = provider.state.first { it.importStatus.phase == UiGkpImportPhase.Ready }
        assertEquals(UiGkpImportPhase.Ready, state.importStatus.phase)
        assertEquals("已导入 2 个内置知识包", state.importStatus.message)
        assertEquals(1, state.packs.size)
        assertEquals(2, state.totalKnowledgeRows)
        assertEquals(2, state.totalSources)

        val pack = state.packs.first()
        assertEquals("sample.2048", pack.packId)
        assertEquals("2048", pack.gameId)
        assertEquals("自写样例", pack.trustLabel)
        assertEquals("内置", pack.provenanceLabel)
        assertEquals("未签名", pack.signatureLabel)
        assertEquals("自写 / 本地夹具", pack.licenseSummary)
        assertEquals(2, pack.knowledgeCount)
        assertEquals(2, pack.sourceCount)
    }

    @Test
    fun `surfaces bundled import errors without hiding installed packs`() = runTest {
        val importStatus = MutableStateFlow(
            BundledGkpImportStatus(
                phase = BundledGkpImportPhase.Error,
                totalPacks = 2,
                importedPacks = 1,
                failedPacks = 1,
                message = "imported 1 bundled GKP packs; failed 1: bad manifest",
            )
        )
        val provider = RealGkpLibraryProvider(
            gameRepository = FakeGameRepository(listOf(relayStation())),
            knowledgeRepository = FakeKnowledgeRepository(listOf(knowledge("relay_station", "item.blue-fuse"))),
            importStatus = importStatus,
            scope = backgroundScope,
        )

        val state = provider.state.first { it.importStatus.phase == UiGkpImportPhase.Error }
        assertEquals(UiGkpImportPhase.Error, state.importStatus.phase)
        assertEquals(1, state.importStatus.importedPacks)
        assertEquals(1, state.importStatus.failedPacks)
        assertTrue(state.importStatus.message.contains("失败 1"))
        assertEquals("sample.relay-station", state.packs.single().packId)
    }

    @Test
    fun `request delete creates confirmation plan with row counts`() = runTest {
        val provider = RealGkpLibraryProvider(
            gameRepository = FakeGameRepository(listOf(relayStation())),
            knowledgeRepository = FakeKnowledgeRepository(
                listOf(
                    knowledge("relay_station", "item.blue-fuse", listOf("sample.relay.items")),
                    knowledge("relay_station", "loc.east-bay", listOf("sample.relay.map")),
                )
            ),
            importStatus = MutableStateFlow(BundledGkpImportStatus(phase = BundledGkpImportPhase.Ready)),
            scope = backgroundScope,
            nowMillis = { 123L },
        )

        provider.requestDelete("relay_station")

        val delete = provider.state.first {
            it.deleteState.phase == UiGkpDeletePhase.AwaitingConfirmation
        }.deleteState
        assertEquals("请确认删除 Relay Station。", delete.message)
        val plan = requireNotNull(delete.plan)
        assertEquals("sample.relay-station", plan.packId)
        assertEquals("relay_station", plan.gameId)
        assertEquals("0.1.0", plan.packVersion)
        assertEquals(2, plan.knowledgeCount)
        assertEquals(2, plan.sourceCount)
        assertTrue(plan.warning.orEmpty().contains("内置样例包"))
    }

    @Test
    fun `confirm delete removes game and knowledge rows inside transaction`() = runTest {
        var transactionCalls = 0
        val games = FakeGameRepository(listOf(relayStation()))
        val knowledge = FakeKnowledgeRepository(listOf(knowledge("relay_station", "item.blue-fuse")))
        val provider = RealGkpLibraryProvider(
            gameRepository = games,
            knowledgeRepository = knowledge,
            importStatus = MutableStateFlow(BundledGkpImportStatus(phase = BundledGkpImportPhase.Ready)),
            scope = backgroundScope,
            nowMillis = { 456L },
            runInTransaction = { block ->
                transactionCalls += 1
                block()
            },
        )

        provider.requestDelete("relay_station")
        provider.confirmDelete()

        val deleted = provider.state.first {
            it.deleteState.phase == UiGkpDeletePhase.Deleted
        }
        assertEquals(1, transactionCalls)
        assertEquals("已删除 Relay Station，移除 1 条知识。", deleted.deleteState.message)
        assertTrue(deleted.packs.isEmpty())
        assertTrue(games.rows.isEmpty())
        assertTrue(knowledge.rows.isEmpty())
    }

    @Test
    fun `disable and enable pack updates availability without removing rows`() = runTest {
        val games = FakeGameRepository(listOf(relayStation()))
        val knowledge = FakeKnowledgeRepository(listOf(knowledge("relay_station", "item.blue-fuse")))
        val provider = RealGkpLibraryProvider(
            gameRepository = games,
            knowledgeRepository = knowledge,
            importStatus = MutableStateFlow(BundledGkpImportStatus(phase = BundledGkpImportPhase.Ready)),
            scope = backgroundScope,
            nowMillis = { 777L },
        )

        provider.disablePack("relay_station")

        val disabled = provider.state.first {
            it.packs.singleOrNull()?.isEnabled == false
        }.packs.single()
        assertEquals("已禁用", disabled.availabilityLabel)
        assertEquals(777L, disabled.disabledAtMillis)
        assertEquals(1, knowledge.rows.size)
        assertEquals(false, games.rows.getValue("relay_station").isEnabled)

        provider.enablePack("relay_station")

        val enabled = provider.state.first {
            it.packs.singleOrNull()?.isEnabled == true &&
                it.packs.single().disabledAtMillis == null
        }.packs.single()
        assertEquals("启用", enabled.availabilityLabel)
        assertEquals(1, knowledge.rows.size)
        assertEquals(true, games.rows.getValue("relay_station").isEnabled)
    }

    private class FakeGameRepository(
        games: List<GameDomain>,
    ) : GameRepository {
        val rows: MutableMap<String, GameDomain> = games.associateBy { it.gameId }.toMutableMap()
        private val flow = MutableStateFlow(rows.values.toList())

        override fun observeAll(): Flow<List<GameDomain>> = flow

        override suspend fun getById(gameId: String): GameDomain? =
            rows[gameId]

        override suspend fun getByRomSha1(sha1: String): GameDomain? =
            rows.values.firstOrNull { it.romSha1 == sha1 }

        override suspend fun getByRomCrc32(crc32: String): GameDomain? =
            rows.values.firstOrNull { it.romCrc32 == crc32 }

        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> =
            rows.values.filter { it.platform == platform && it.title.contains(titleQuery.trim('%'), ignoreCase = true) }

        override suspend fun upsert(game: GameDomain) {
            rows[game.gameId] = game
            flow.value = rows.values.toList()
        }

        override suspend fun delete(gameId: String) {
            rows.remove(gameId)
            flow.value = rows.values.toList()
        }
    }

    private class FakeKnowledgeRepository(
        initialRows: List<KnowledgeChunkDomain>,
    ) : KnowledgeRepository {
        val rows: MutableList<KnowledgeChunkDomain> = initialRows.toMutableList()

        override suspend fun searchFts(
            gameId: String,
            query: String,
            limit: Int,
        ): List<KnowledgeChunkDomain> = rows.filter { it.gameId == gameId }.take(limit)

        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? =
            rows.firstOrNull { it.gameId == gameId && it.entityId == entityId }

        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId }

        override suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId && it.entityType == entityType }

        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) {
            rows += chunks
        }

        override suspend fun clearForGame(gameId: String) {
            rows.removeAll { it.gameId == gameId }
        }
    }

    private fun sample2048() = GameDomain(
        gameId = "2048",
        packId = "sample.2048",
        title = "2048",
        platform = "libretro",
        region = null,
        languages = listOf("zh", "en"),
        romCrc32 = null,
        romSha1 = null,
        packVersion = "0.1.1",
        schemaVersion = "gkp.v0",
        trustLevel = "sample",
        provenance = GkpPackProvenance.Bundled.id,
        signatureStatus = GkpSignatureStatus.Unsigned.id,
        installedAt = 1L,
    )

    private fun relayStation() = sample2048().copy(
        gameId = "relay_station",
        packId = "sample.relay-station",
        title = "Relay Station",
        platform = "sample",
        packVersion = "0.1.0",
    )

    private fun knowledge(
        gameId: String,
        entityId: String,
        sourceRefs: List<String> = listOf("sample.source"),
    ) = KnowledgeChunkDomain(
        id = 0L,
        gameId = gameId,
        entityId = entityId,
        entityType = "mechanic",
        canonicalName = entityId,
        aliases = emptyList(),
        descriptionShort = entityId,
        descriptionLong = null,
        progressGate = null,
        spoilerLevel = "none",
        sourceRefs = sourceRefs,
        confidence = "verified",
        answerTemplates = emptyList(),
    )
}
