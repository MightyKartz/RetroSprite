package com.retrosprite.app.domain

import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.resolver.RepositoryGameResolver
import com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipeline
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerResult
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.normalization.GameTermNormalizer
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.EvidenceAnswerPolicy
import com.retrosprite.app.endpoint.QueryPipelineResponseGenerator
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.llm.LlmAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SampleShiningForceIIQuestionPipelineTest {

    @Test
    fun `shining force ii promotion question returns local evidence answer`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "什么时候转职？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("转职"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii chinese RetroArch playlist label resolves to local evidence`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__光明力量2",
            question = "什么时候转职？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii true-device mega drive label resolves to local evidence`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "什么时候转职？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii low spoiler next step returns early direction`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "不要剧透下一步去哪？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("城堡"))
        assertTrue("answer=<$text>", text.contains("周边"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii unknown system question returns uncertainty and no llm call`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "这个游戏有没有交易系统？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue(text.contains("没有足够证据"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii production question returns source backed developer answer`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "谁开发的？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("世嘉"))
        assertTrue("answer=<$text>", text.contains("索尼克"))
        assertFalse("answer=<$text>", text.contains("SEGA"))
        assertFalse("answer=<$text>", text.contains("Sonic"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii early character question answers Sarah role without spoilers`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "Sarah 值得练吗？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("治疗"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii early item question explains medical herb use`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "医疗草怎么用？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("10 生命值"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii noisy vigor ball usage question hits bundled template`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "气合之玉怎么又",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.answerResult.answerDetail.contains("气合之玉"))
        assertTrue("answer=<${result.text}>", result.text.contains("气合之玉"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.Usage, result.answerResult.answerType)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed qi he river asr variant resolves vigor ball usage`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "气河之欲怎么用",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.answerResult.answerDetail.contains("气合之玉"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.Usage, result.answerResult.answerType)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii chinese usage answers and suggestions prefer localized names`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val vigor = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "气合之玉怎么用？",
            spoilerLevel = SpoilerLevel.LIGHT,
        ).answerResult
        val mithril = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "米斯里鲁银有什么用？",
            spoilerLevel = SpoilerLevel.LIGHT,
        ).answerResult

        assertVisibleChineseOnly(
            answer = vigor,
            requiredTerms = listOf("气合之玉", "僧侣", "武僧"),
            forbiddenTerms = listOf("Vigor Ball", "Priest", "Master Monk"),
        )
        assertFalse(
            "answer=<${vigor.answerDetail}>",
            vigor.answerDetail.contains("气合之玉（活力球/气合之玉）"),
        )
        assertVisibleChineseOnly(
            answer = mithril,
            requiredTerms = listOf("米斯里鲁银", "矮人工匠"),
            forbiddenTerms = listOf("Mithril", "Dwarven Blacksmith"),
        )
        assertFalse(
            "answer=<${mithril.answerDetail}>",
            mithril.answerDetail.contains("米斯里鲁银（米斯里鲁银）"),
        )
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii mithril location stays low spoiler with layered template`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "md__Shining Force II",
            question = "Mithril 在哪里？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.text.contains("低剧透"))
        assertTrue("answer=<${result.text}>", result.text.contains("位置清单"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.Location, result.answerResult.answerType)
        assertEquals(AnswerConfidence.High, result.answerResult.confidence)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii hidden content overview stays low spoiler`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "这里有隐藏物品吗？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("不直接列清单"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed asr miss still resolves promotion intent`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "接受他几部这个角色",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed asr homophones still resolve promotion intent`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        listOf(
            "角色怎么转直",
            "角色什么软直啊",
            "觉得角色怎么转直",
        ).forEach { question ->
            val text = pipeline.answer(
                label = "mega_drive__光明力量2",
                question = question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue("question=<$question> answer=<$text>", text.contains("20"))
            assertTrue("question=<$question> answer=<$text>", text.contains("来源：本地知识"))
        }
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed asr promotion questions keep mechanic answer type`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        listOf(
            "角色怎么专职",
            "角色怎么转直",
            "接受他几部这个角色",
        ).forEach { question ->
            val result = pipeline.answerDetailed(
                label = "mega_drive__光明力量2",
                question = question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue("question=<$question> answer=<${result.text}>", result.text.contains("转职"))
            assertEquals("question=<$question>", AnswerType.Mechanic, result.answerResult.answerType)
        }
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii core gameplay question returns zero llm overview`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "这个游戏主要是玩什么？乐趣在哪里？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("网格"))
        assertTrue("answer=<$text>", text.contains("队伍"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii core gameplay variants include enjoyment hooks`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        listOf(
            "好玩在哪？",
            "核心玩法是什么？",
            "适合什么玩家？",
        ).forEach { question ->
            val text = pipeline.answer(
                label = "md__Shining Force II",
                question = question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue("question=<$question> answer=<$text>", text.contains("队伍"))
            assertTrue("question=<$question> answer=<$text>", text.contains("隐藏"))
            assertTrue("question=<$question> answer=<$text>", text.contains("来源：本地知识"))
        }
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii broad asr questions return local starter answers`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val intro = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "姐介绍下这个游戏",
            spoilerLevel = SpoilerLevel.LIGHT,
        )
        assertTrue("answer=<$intro>", intro.contains("网格"))
        assertTrue("answer=<$intro>", intro.contains("来源：本地知识"))

        val basics = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "多基础是什么",
            spoilerLevel = SpoilerLevel.LIGHT,
        )
        assertTrue("answer=<$basics>", basics.contains("队伍"))
        assertTrue("answer=<$basics>", basics.contains("来源：本地知识"))

        val items = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "道具和装备",
            spoilerLevel = SpoilerLevel.LIGHT,
        )
        assertTrue("answer=<$items>", items.contains("回复"))
        assertTrue("answer=<$items>", items.contains("来源：本地知识"))

        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii localized character name question returns english mapping`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "莎拉英文是谁？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )
        val text = result.text

        assertTrue("answer=<$text>", text.contains("莎拉对应英文名是 Sarah"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertFalse(
            "suggestions=<${result.answerResult.suggestedQuestions}>",
            result.answerResult.suggestedQuestions.any { question -> question.contains("Sarah") },
        )
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii yzzl translation aliases resolve characters items and places`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val cases = listOf(
            TranslationAliasCase("修伊怎么用？", "修伊", "sf2.manual_translation"),
            TranslationAliasCase("佳佳值得练吗？", "佳佳", "sf2.manual_translation"),
            TranslationAliasCase("卡森怎么用？", "卡森", "sf2.manual_translation"),
            TranslationAliasCase("吉布是谁？", "吉布", "sf2.characters"),
            TranslationAliasCase("皮特是谁？", "皮特", "sf2.characters"),
            TranslationAliasCase("气合之玉怎么用？", "气合之玉", "sf2.promotion"),
            TranslationAliasCase("米斯里鲁银有什么用？", "米斯里鲁银", "sf2.items"),
            TranslationAliasCase("精灵森林是什么？", "精灵森林", "sf2.secrets"),
            TranslationAliasCase("精灵村是不是隐藏地点？", "精灵", "sf2.secrets"),
        )

        cases.forEach { case ->
            val result = pipeline.answerDetailed(
                label = "mega_drive__光明力量2",
                question = case.question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue(
                "question=<${case.question}> answer=<${result.text}>",
                result.text.contains(case.expectedPhrase),
            )
            assertTrue(
                "question=<${case.question}> answer=<${result.text}>",
                result.text.contains("来源：本地知识"),
            )
            assertEquals("question=<${case.question}>", "skipped", result.llmTrace.status)
        }
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii voice homophone question normalizes to character evidence`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val games = FakeGameRepository(listOf(fixture.game))
        val knowledge = FakeKnowledgeRepository(fixture.knowledge)
        val directNormalization = GameTermNormalizer().normalize("修医是谁", fixture.knowledge)
        assertEquals(directNormalization.toString(), "修伊是谁", directNormalization.normalizedQuestion)
        val generator = QueryPipelineResponseGenerator(
            pipeline = DefaultQueryPipeline(
                resolver = RepositoryGameResolver(games),
                retrieval = LocalKnowledgeRetrievalPipeline(knowledge),
                policy = EvidenceAnswerPolicy(),
                composer = AnswerComposer(),
                llm = llm,
            ),
            gameResolver = RepositoryGameResolver(games),
            knowledgeRepository = knowledge,
        )

        val response = generator.generate(
            request = RetroArchRequest(
                image = "",
                label = "mega_drive__光明力量2",
                question = "修医是谁",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        val text = response.text.orEmpty()
        assertEquals("修伊是谁", response.diagnostics.question)
        assertEquals("修医是谁", response.diagnostics.rawQuestion)
        assertEquals("修伊是谁", response.diagnostics.normalizedQuestion)
        assertEquals("homophone", response.diagnostics.questionNormalizationReason)
        assertEquals("修伊", response.diagnostics.normalizedQuestionMatchedTerm)
        assertTrue("answer=<$text>", text.contains("修伊"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals("skipped", response.diagnostics.llmStatus)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii localized item name question bypasses route spoilers`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "勇者之证英文叫什么？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        val text = result.text
        assertTrue("answer=<$text>", text.contains("勇者之证对应英文名是 Warrior Pride"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals("勇者之证对应英文名是 Warrior Pride。", result.answerResult.answerShort)
        assertEquals(AnswerType.NameMapping, result.answerResult.answerType)
        assertEquals(AnswerConfidence.High, result.answerResult.confidence)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii natural gameplay question returns typed zero llm answer`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "这游戏怎么玩？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.text.contains("队伍"))
        assertTrue("answer=<${result.text}>", result.text.contains("网格"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.GameOverview, result.answerResult.answerType)
        assertEquals("skipped", result.llmTrace.status)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii current team building question asks for progress while giving safe principles`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "现在哪些角色适合培养？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.text.contains("通用原则"))
        assertTrue("answer=<${result.text}>", result.text.contains("先不列全角色"))
        assertTrue("answer=<${result.text}>", result.text.contains("莎拉"))
        assertTrue("answer=<${result.text}>", result.text.contains("修伊"))
        assertTrue("answer=<${result.text}>", result.text.contains("到哪一章"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertTrue("answerShort=<${result.answerResult.answerShort}>", result.answerResult.answerShort.contains("卡森"))
        assertFalse("answerShort=<${result.answerResult.answerShort}>", result.answerResult.answerShort.contains("..."))
        assertEquals(AnswerType.TeamBuild, result.answerResult.answerType)
        assertEquals("skipped", result.llmTrace.status)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii early team question gives concrete early names without full roster spoilers`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "开局哪些角色值得练？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        listOf("博伊", "莎拉", "修伊", "佳佳", "卡森").forEach { name ->
            assertTrue("answer=<${result.text}> missing=<$name>", result.text.contains(name))
        }
        assertTrue("answer=<${result.text}>", result.text.contains("不展开后期"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.TeamBuild, result.answerResult.answerType)
        assertEquals("skipped", result.llmTrace.status)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii direct roster question warns before spoiling later characters`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "直接告诉我强力角色名单",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.text.contains("会涉及后期加入角色"))
        assertTrue("answer=<${result.text}>", result.text.contains("确认要高剧透名单"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.TeamBuild, result.answerResult.answerType)
        assertEquals("skipped", result.llmTrace.status)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii leveling question returns zero llm mechanics answer`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "怎么玩经验高？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<${result.text}>", result.text.contains("低等级"))
        assertTrue("answer=<${result.text}>", result.text.contains("补刀"))
        assertTrue("answer=<${result.text}>", result.text.contains("来源：本地知识"))
        assertEquals(AnswerType.Leveling, result.answerResult.answerType)
        assertEquals("skipped", result.llmTrace.status)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed natural variants resolve local answers without llm`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val cases = listOf(
            NaturalVariantCase(
                question = "这个游戏到底要怎么玩？",
                type = AnswerType.GameOverview,
                sourceId = "sf2.official_overview",
                phrase = "网格",
            ),
            NaturalVariantCase(
                question = "这游戏要怎么玩？",
                type = AnswerType.GameOverview,
                sourceId = "sf2.official_overview",
                phrase = "网格",
            ),
            NaturalVariantCase(
                question = "这游戏该怎么玩？",
                type = AnswerType.GameOverview,
                sourceId = "sf2.official_overview",
                phrase = "队伍",
            ),
            NaturalVariantCase(
                question = "这游戏玩什么？",
                type = AnswerType.GameOverview,
                sourceId = "sf2.official_overview",
                phrase = "队伍",
            ),
            NaturalVariantCase(
                question = "这个游戏主要是干嘛的？",
                type = AnswerType.GameOverview,
                sourceId = "sf2.official_overview",
                phrase = "队伍",
            ),
            NaturalVariantCase(
                question = "刚开始应该干嘛？",
                type = AnswerType.BeginnerGuide,
                sourceId = "sf2.early_route",
                phrase = "保护主角",
            ),
            NaturalVariantCase(
                question = "新手前期怎么玩稳？",
                type = AnswerType.BeginnerGuide,
                sourceId = "sf2.early_route",
                phrase = "抱团",
            ),
            NaturalVariantCase(
                question = "角色练哪些比较稳？",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "通用原则",
            ),
            NaturalVariantCase(
                question = "队伍怎么搭配？",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "治疗",
            ),
            NaturalVariantCase(
                question = "角色如何搭配？",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "治疗",
            ),
            NaturalVariantCase(
                question = "职业怎么搭配？",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "前排",
            ),
            NaturalVariantCase(
                question = "对我怎么搭配",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "治疗",
            ),
            NaturalVariantCase(
                question = "对于我怎么搭配",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "治疗",
            ),
            NaturalVariantCase(
                question = "哪些角色直练",
                type = AnswerType.TeamBuild,
                sourceId = "sf2.project_mechanics",
                phrase = "通用原则",
            ),
            NaturalVariantCase(
                question = "升级有什么技巧？",
                type = AnswerType.Leveling,
                sourceId = "sf2.project_mechanics",
                phrase = "补刀",
            ),
            NaturalVariantCase(
                question = "打不过敌人怎么办？",
                type = AnswerType.Strategy,
                sourceId = "sf2.project_mechanics",
                phrase = "撤退",
            ),
            NaturalVariantCase(
                question = "怎么才能赢？",
                type = AnswerType.Strategy,
                sourceId = "sf2.project_mechanics",
                phrase = "抱团",
            ),
            NaturalVariantCase(
                question = "这个游戏玩的话有什么技巧吗？",
                type = AnswerType.Strategy,
                sourceId = "sf2.project_mechanics",
                phrase = "前排",
            ),
            NaturalVariantCase(
                question = "米斯里鲁有什么用？",
                type = AnswerType.Usage,
                sourceId = "sf2.items",
                phrase = "锻造",
            ),
            NaturalVariantCase(
                question = "米斯里鲁在哪里？",
                type = AnswerType.Location,
                sourceId = "sf2.items",
                phrase = "低剧透",
            ),
            NaturalVariantCase(
                question = "妖精粉是干嘛的？",
                type = AnswerType.Usage,
                sourceId = "sf2.items",
                phrase = "妖精粉",
            ),
        )

        cases.forEach { case ->
            val result = pipeline.answerDetailed(
                label = "mega_drive__光明力量2",
                question = case.question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue(
                "question=<${case.question}> answer=<${result.text}>",
                result.text.contains(case.phrase),
            )
            assertTrue(
                "question=<${case.question}> answer=<${result.text}>",
                result.text.contains("来源：本地知识"),
            )
            assertEquals("question=<${case.question}>", case.type, result.answerResult.answerType)
            assertEquals("question=<${case.question}>", "skipped", result.llmTrace.status)
        }
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii gated names return low spoiler hints without runtime progress gate`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val cases = listOf(
            NaturalVariantCase(
                question = "帕卡隆是什么地方？",
                type = AnswerType.Location,
                sourceId = "sf2.promotion",
                phrase = "帕卡隆",
            ),
            NaturalVariantCase(
                question = "克拉肯怎么过？",
                type = AnswerType.Strategy,
                sourceId = "sf2.enemy_boss_notes",
                phrase = "克拉肯",
            ),
            NaturalVariantCase(
                question = "红男爵是谁？",
                type = AnswerType.NameMapping,
                sourceId = "sf2.enemy_boss_notes",
                phrase = "红男爵",
            ),
        )

        cases.forEach { case ->
            val result = pipeline.answerDetailed(
                label = "mega_drive__光明力量2",
                question = case.question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue(
                "question=<${case.question}> answer=<${result.text}>",
                result.text.contains(case.phrase),
            )
            assertTrue(
                "question=<${case.question}> answer=<${result.text}>",
                result.text.contains("来源：本地知识"),
            )
            assertEquals("question=<${case.question}>", case.type, result.answerResult.answerType)
            assertEquals("question=<${case.question}>", "skipped", result.llmTrace.status)
        }
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed kraken pan asr variant returns local boss strategy`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val games = FakeGameRepository(listOf(fixture.game))
        val knowledge = FakeKnowledgeRepository(fixture.knowledge)
        val generator = QueryPipelineResponseGenerator(
            pipeline = DefaultQueryPipeline(
                resolver = RepositoryGameResolver(games),
                retrieval = LocalKnowledgeRetrievalPipeline(knowledge),
                policy = EvidenceAnswerPolicy(),
                composer = AnswerComposer(),
                llm = llm,
            ),
            gameResolver = RepositoryGameResolver(games),
            knowledgeRepository = knowledge,
        )

        val response = generator.generate(
            request = RetroArchRequest(
                image = "",
                label = "mega_drive__光明力量2",
                question = "克拉盆怎么",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )
        val text = response.text.orEmpty()

        assertEquals("克拉肯怎么过", response.diagnostics.question)
        assertEquals("克拉盆怎么", response.diagnostics.rawQuestion)
        assertEquals("克拉肯怎么过", response.diagnostics.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant", response.diagnostics.questionNormalizationReason)
        assertEquals("克拉肯怎么过", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("boss.kraken", response.diagnostics.normalizedQuestionMatchedEntityId)
        assertTrue("answer=<$text>", text.contains("克拉肯"))
        assertTrue("answer=<$text>", text.contains("来源：本地知识"))
        assertEquals("skipped", response.diagnostics.llmStatus)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed asr team building variants return local principles`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        listOf(
            "那些角色适合培养",
            "那这些角色适合培养",
            "现在哪先角色适合培养",
            "那些人物适合培养",
            "哪些人物适合培养",
            "那些队员适合培养",
        ).forEach { question ->
            val result = pipeline.answerDetailed(
                label = "mega_drive__光明力量2",
                question = question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue(
                "question=<$question> answer=<${result.text}>",
                result.text.contains("通用原则") || result.text.contains("治疗"),
            )
            assertTrue(
                "question=<$question> answer=<${result.text}>",
                result.text.contains("来源：本地知识"),
            )
            assertEquals("question=<$question>", AnswerType.TeamBuild, result.answerResult.answerType)
            assertEquals("question=<$question>", "skipped", result.llmTrace.status)
        }
        assertEquals(0, llm.callCount)
    }

    private fun newPipeline(
        fixture: SamplePackFixture,
        llm: LlmAdapter,
    ): DefaultQueryPipeline =
        DefaultQueryPipeline(
            resolver = RepositoryGameResolver(FakeGameRepository(listOf(fixture.game))),
            retrieval = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(fixture.knowledge)),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llm,
        )

    private data class SamplePackFixture(
        val game: GameDomain,
        val knowledge: List<KnowledgeChunkDomain>,
    )

    private data class NaturalVariantCase(
        val question: String,
        val type: AnswerType,
        val sourceId: String,
        val phrase: String,
    )

    private data class TranslationAliasCase(
        val question: String,
        val expectedPhrase: String,
        val expectedSource: String,
    )

    private fun assertVisibleChineseOnly(
        answer: AnswerResult,
        requiredTerms: List<String>,
        forbiddenTerms: List<String>,
    ) {
        val visibleText = buildString {
            appendLine(answer.answerShort)
            appendLine(answer.answerDetail)
            answer.suggestedQuestions.forEach(::appendLine)
        }
        requiredTerms.forEach { term ->
            assertTrue("visible=<$visibleText> missing=<$term>", visibleText.contains(term))
        }
        forbiddenTerms.forEach { term ->
            assertFalse("visible=<$visibleText> forbidden=<$term>", visibleText.contains(term))
        }
    }

    private class FakeGameRepository(
        private val games: List<GameDomain>,
    ) : GameRepository {
        override fun observeAll(): Flow<List<GameDomain>> = flowOf(games)
        override suspend fun getById(gameId: String): GameDomain? =
            games.firstOrNull { it.gameId == gameId }

        override suspend fun getByRomSha1(sha1: String): GameDomain? =
            games.firstOrNull { it.romSha1 == sha1 }

        override suspend fun getByRomCrc32(crc32: String): GameDomain? =
            games.firstOrNull { it.romCrc32 == crc32 }

        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> =
            games.filter {
                it.platform == platform && it.title.contains(titleQuery.trim('%'), ignoreCase = true)
            }

        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }

    private class FakeKnowledgeRepository(
        private val rows: List<KnowledgeChunkDomain>,
    ) : KnowledgeRepository {
        override suspend fun searchFts(
            gameId: String,
            query: String,
            limit: Int,
        ): List<KnowledgeChunkDomain> {
            val tokens = normalize(query).split(WHITESPACE)
                .filter { it.length >= 2 }
            if (tokens.isEmpty()) return emptyList()
            return rows.filter { row ->
                row.gameId == gameId && tokens.any { token -> row.searchText().contains(token) }
            }.take(limit)
        }

        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? =
            rows.firstOrNull { it.gameId == gameId && it.entityId == entityId }

        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId }

        override suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId && it.entityType == entityType }

        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit

        private fun KnowledgeChunkDomain.searchText(): String =
            listOf(
                entityId,
                entityType,
                canonicalName,
                aliases.joinToString(" "),
                descriptionShort,
                descriptionLong.orEmpty(),
            ).joinToString(" ")
                .let(::normalize)
    }

    private class CountingLlmAdapter : LlmAdapter {
        override val providerName: String = "counting"
        var callCount: Int = 0
            private set

        override suspend fun complete(request: LlmRequest): LlmResponse {
            callCount += 1
            return LlmResponse(text = "LLM should not be needed for this fixture")
        }
    }

    private fun loadSamplePack(): SamplePackFixture {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/shining-force-ii-md")
            .normalize()
        val manifestText = readText(packDir.resolve("manifest.json"))
        val parser = GkpV0Parser(nowMillis = { 0L })
        val knowledgeFiles = parser.knowledgePaths(manifestText)
            .associateWith { relative -> readText(packDir.resolve(relative)) }
        val aliasFiles = parser.aliasPath(manifestText)
            ?.let { path -> mapOf(path to readText(packDir.resolve(path))) }
            .orEmpty()
        val parsed = parser.parse(manifestText, knowledgeFiles, aliasFiles)
        return SamplePackFixture(parsed.game, parsed.knowledge)
    }

    private fun moduleRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(8) {
            if (Files.isDirectory(current.resolve("src/main/assets"))) return current
            if (Files.isDirectory(current.resolve("app/src/main/assets"))) return current.resolve("app")
            current = current.parent ?: current
        }
        error("Could not locate Android app module from ${Paths.get("").toAbsolutePath()}")
    }

    private fun readText(path: Path): String =
        Files.readAllBytes(path).toString(Charsets.UTF_8)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PUNCTUATION = Regex("[\\p{Punct}，。？！、；：]+")

        fun normalize(value: String): String =
            value.lowercase()
                .replace(PUNCTUATION, " ")
                .replace(WHITESPACE, " ")
                .trim()
    }
}
