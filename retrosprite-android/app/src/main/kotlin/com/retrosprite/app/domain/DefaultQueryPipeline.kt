package com.retrosprite.app.domain

import com.retrosprite.app.domain.models.ControllerState
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.AnswerPolicy
import com.retrosprite.app.domain.resolver.GameResolver
import com.retrosprite.app.domain.retrieval.RetrievalPipeline
import com.retrosprite.app.llm.LlmAdapter

/**
 * Default [QueryPipeline] wiring. Flow:
 *
 * 1. resolver.resolve(label, romHash) → [GameIdentity]
 * 2. retrieval.normalizeQuestion → normalized query string
 * 3. build [SessionContext] + [RetrievalQuery]
 * 4. retrieval.retrieve(query) → results (Phase 0: empty)
 * 5. policy.decide(results, ctx) → [AnswerDecision] (Phase 0: fixed)
 * 6. composer.compose(decision, ctx, llm) → final string
 *
 * All collaborators are injected to keep the pipeline deterministic and
 * easy to unit-test (see `DefaultQueryPipelineTest`).
 */
class DefaultQueryPipeline(
    private val resolver: GameResolver,
    private val retrieval: RetrievalPipeline,
    private val policy: AnswerPolicy,
    private val composer: AnswerComposer,
    private val llm: LlmAdapter,
) : QueryPipeline {

    override suspend fun answer(
        label: String,
        romHash: String?,
        question: String?,
        screenshot: String?,
        state: Map<String, Int>?,
        spoilerLevel: SpoilerLevel,
        language: String,
    ): String = answerDetailed(
        label = label,
        romHash = romHash,
        question = question,
        screenshot = screenshot,
        state = state,
        spoilerLevel = spoilerLevel,
        language = language,
    ).text

    override suspend fun answerDetailed(
        label: String,
        romHash: String?,
        question: String?,
        screenshot: String?,
        state: Map<String, Int>?,
        spoilerLevel: SpoilerLevel,
        language: String,
    ): QueryPipelineResult {
        // 1. resolve game
        val identity = resolver.resolve(label, romHash)

        // 2. normalize question (empty when null — keeps cache key stable)
        val normalized = retrieval.normalizeQuestion(question.orEmpty(), language)

        // 3. build session context
        val controllerState = state?.let { ControllerState(it) } ?: ControllerState.EMPTY
        val context = SessionContext(
            gameIdentity = identity,
            playerQuestion = question,
            screenshotBase64 = screenshot,
            state = controllerState,
            spoilerLevel = spoilerLevel,
            language = language,
            recentTurns = emptyList(),
        )

        // 4. retrieval
        val results = retrieval.retrieve(
            RetrievalQuery(
                gameId = identity.gameId,
                normalizedQuery = normalized,
                language = language,
                progressGate = null,
                spoilerLevel = spoilerLevel,
            )
        )

        // 5. policy
        val decision = policy.decide(results, context)

        // 6. compose
        val answer = composer.composeDetailed(decision, context, llm)
        return QueryPipelineResult(
            text = answer.text,
            llmTrace = answer.llmTrace,
        )
    }
}
