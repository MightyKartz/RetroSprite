package com.retrosprite.app.endpoint

import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.QueryPipeline
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.normalization.GameTermNormalizationResult
import com.retrosprite.app.domain.normalization.GameTermNormalizer
import com.retrosprite.app.domain.resolver.GameResolver
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.ResponseDiagnostics

/**
 * Endpoint-layer [ResponseGenerator] that delegates to the domain [QueryPipeline].
 *
 * Lives in the endpoint package (not domain) because the integration boundary is
 * here: the endpoint speaks `RetroArchRequest` / `RetroArchResponse` while the
 * domain speaks raw primitives. This adapter is the single funnel that
 * translates one to the other.
 *
 * Mapping rules:
 *  - the `state` object becomes a `Map<String, Int>` with non-zero flags only
 *    (mirrors the protocol's "absent ≡ 0" convention)
 *  - `screenshot` falls through verbatim — `null` and empty string are both
 *    treated as "no screenshot" downstream
 *  - `output` is currently advisory; Phase 0 always returns text
 *  - `romHash` is not part of the AI-Service request body, so we always pass
 *    `null` (Task 4 may extract it from the screenshot in a future overload)
 *
 * Replaces [PlaceholderResponseGenerator] as the production wiring; the
 * placeholder is kept in the codebase as a fallback for offline diagnostics.
 */
class QueryPipelineResponseGenerator(
    private val pipeline: QueryPipeline,
    private val defaultSpoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
    private val spoilerLevelProvider: () -> SpoilerLevel = { defaultSpoilerLevel },
    private val defaultLanguage: String = "zh",
    private val gameResolver: GameResolver? = null,
    private val knowledgeRepository: KnowledgeRepository? = null,
    private val gameTermNormalizer: GameTermNormalizer = GameTermNormalizer(),
) : ResponseGenerator {

    override suspend fun generate(
        request: RetroArchRequest,
        outputMode: String,
    ): RetroArchResponse {
        val normalization = normalizeQuestionIfVoice(request, outputMode)
        val pipelineQuestion = normalization.normalizedQuestion.takeIf { it.isNotBlank() }
        val rawRequestQuestion = request.question.trim()
        val questionChanged = pipelineQuestion != null && rawRequestQuestion.isNotBlank() &&
            rawRequestQuestion != pipelineQuestion
        val result = pipeline.answerDetailed(
            label = request.label,
            romHash = null,
            // Official RetroArch AI-Service bodies do not currently include a
            // question field. The optional field is for app/debug text entry.
            question = pipelineQuestion,
            screenshot = request.image.takeIf { it.isNotBlank() },
            state = request.state.toFlagMap().ifEmpty { null },
            spoilerLevel = request.spoilerLevel.toSpoilerLevelOrNull() ?: spoilerLevelProvider(),
            language = defaultLanguage,
        )
        return RetroArchResponse.text(
            content = result.text,
            diagnostics = ResponseDiagnostics(
                question = pipelineQuestion,
                rawQuestion = normalization.rawQuestion.takeIf { normalization.applied }
                    ?: rawRequestQuestion.takeIf { questionChanged },
                normalizedQuestion = normalization.normalizedQuestion.takeIf { normalization.applied }
                    ?: pipelineQuestion.takeIf { questionChanged },
                questionNormalizationReason = normalization.reason ?: "normalized".takeIf { questionChanged },
                normalizedQuestionMatchedTerm = normalization.matchedTerm,
                normalizedQuestionMatchedEntityId = normalization.matchedEntityId,
                answerShort = result.answerResult.answerShort,
                answerDetail = result.answerResult.answerDetail,
                answerType = result.answerResult.answerType.wireName,
                answerConfidence = result.answerResult.confidence.wireName,
                spoilerLevelUsed = result.answerResult.spoilerLevelUsed.wireName,
                nextActions = result.answerResult.nextActions.map { it.label },
                suggestedQuestions = result.answerResult.suggestedQuestions,
                llmStatus = result.llmTrace.status,
                llmProvider = result.llmTrace.providerName,
                llmModel = result.llmTrace.modelName,
                llmMaxTokens = result.llmTrace.maxTokens,
                llmTimeoutMs = result.llmTrace.timeoutMs,
                llmLatencyMs = result.llmTrace.latencyMs,
                llmTokensIn = result.llmTrace.tokensIn,
                llmTokensOut = result.llmTrace.tokensOut,
                llmError = result.llmTrace.errorMessage,
            )
        )
    }

    private suspend fun normalizeQuestionIfVoice(
        request: RetroArchRequest,
        outputMode: String,
    ): GameTermNormalizationResult {
        val rawQuestion = request.question.trim()
        if (rawQuestion.isBlank()) {
            return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
        }
        if (!outputMode.startsWith(HOTKEY_VOICE_OUTPUT_PREFIX)) {
            return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
        }
        val resolver = gameResolver
            ?: return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
        val repository = knowledgeRepository
            ?: return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
        val identity = resolver.resolve(label = request.label, romHash = null)
        val gameId = identity.gameId
            ?: return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
        val rows = repository.listByGame(gameId)
        return gameTermNormalizer.normalize(rawQuestion = rawQuestion, rows = rows)
    }
}

private const val HOTKEY_VOICE_OUTPUT_PREFIX = "hotkey_voice"

private fun String.toSpoilerLevelOrNull(): SpoilerLevel? = when (trim().lowercase()) {
    "light", "none", "hint" -> SpoilerLevel.LIGHT
    "clear", "medium", "more" -> SpoilerLevel.CLEAR
    "direct", "full", "heavy" -> SpoilerLevel.FULL
    else -> null
}

/**
 * Converts the typed [com.retrosprite.app.endpoint.model.RetroArchState] into
 * the `Map<String, Int>` shape the domain pipeline expects.
 *
 * Only buttons that are *pressed* (1) are included — this matches the
 * "absent ≡ 0" semantics used by the policy layer and keeps maps small.
 * `paused` is intentionally excluded: the pipeline already receives it via
 * dedicated parameters.
 */
internal fun com.retrosprite.app.endpoint.model.RetroArchState.toFlagMap(): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    if (a == 1) out["a"] = 1
    if (b == 1) out["b"] = 1
    if (x == 1) out["x"] = 1
    if (y == 1) out["y"] = 1
    if (select == 1) out["select"] = 1
    if (start == 1) out["start"] = 1
    if (up == 1) out["up"] = 1
    if (down == 1) out["down"] = 1
    if (left == 1) out["left"] = 1
    if (right == 1) out["right"] = 1
    if (l == 1) out["l"] = 1
    if (r == 1) out["r"] = 1
    if (l2 == 1) out["l2"] = 1
    if (r2 == 1) out["r2"] = 1
    if (l3 == 1) out["l3"] = 1
    if (r3 == 1) out["r3"] = 1
    return out
}
