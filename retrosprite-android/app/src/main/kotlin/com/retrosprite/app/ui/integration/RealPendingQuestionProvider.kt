package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.PendingQuestion
import com.retrosprite.app.endpoint.PendingQuestionStore
import com.retrosprite.app.ui.viewmodel.PendingQuestionProvider
import com.retrosprite.app.ui.viewmodel.UiPendingQuestion
import com.retrosprite.app.ui.viewmodel.UiPendingQuestionState
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RealPendingQuestionProvider(
    private val store: PendingQuestionStore,
    private val settings: StateFlow<UiSettings>,
    scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PendingQuestionProvider {

    private val _state = MutableStateFlow(
        UiPendingQuestionState(pending = store.pending.value?.toUiPendingQuestion())
    )
    override val state: StateFlow<UiPendingQuestionState> = _state.asStateFlow()

    init {
        scope.launch {
            store.pending.collect { pending ->
                _state.value = UiPendingQuestionState(pending = pending?.toUiPendingQuestion())
            }
        }
    }

    override suspend fun prepare(
        label: String,
        question: String,
        spoilerLevelOverride: UiSpoilerLevel?,
    ) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            store.clear()
            return
        }

        val spoilerLevel = spoilerLevelOverride ?: settings.value.spoilerLevel
        val pending = PendingQuestion(
            label = label.trim().ifBlank { RealPlayerQuestionProvider.DEFAULT_LABEL },
            question = cleanQuestion,
            spoilerLevel = spoilerLevel.id,
            createdAtMillis = clock(),
        )
        store.set(pending)
        _state.value = UiPendingQuestionState(pending = pending.toUiPendingQuestion())
    }

    override suspend fun clear() {
        store.clear()
        _state.value = UiPendingQuestionState()
    }

    private fun PendingQuestion.toUiPendingQuestion(): UiPendingQuestion = UiPendingQuestion(
        label = label,
        question = question,
        spoilerLevel = spoilerLevel.toUiSpoilerLevel(),
        createdAtMillis = createdAtMillis,
    )

    private fun String.toUiSpoilerLevel(): UiSpoilerLevel = when (trim().lowercase()) {
        UiSpoilerLevel.Clear.id -> UiSpoilerLevel.Clear
        UiSpoilerLevel.Direct.id -> UiSpoilerLevel.Direct
        else -> UiSpoilerLevel.Light
    }
}
