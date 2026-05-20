package com.retrosprite.app.endpoint

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingQuestion(
    val label: String,
    val question: String,
    val spoilerLevel: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
)

interface PendingQuestionStore {
    val pending: StateFlow<PendingQuestion?>
    fun set(question: PendingQuestion)
    fun clear()
    fun consumeFor(label: String): PendingQuestion?
}

class InMemoryPendingQuestionStore(
    initial: PendingQuestion? = null,
) : PendingQuestionStore {

    private val lock = Any()
    private val _pending = MutableStateFlow(initial?.normalizedOrNull())
    override val pending: StateFlow<PendingQuestion?> = _pending.asStateFlow()

    override fun set(question: PendingQuestion) = synchronized(lock) {
        _pending.value = question.normalizedOrNull()
    }

    override fun clear() = synchronized(lock) {
        _pending.value = null
    }

    override fun consumeFor(label: String): PendingQuestion? = synchronized(lock) {
        val current = _pending.value ?: return@synchronized null
        if (!current.matches(label)) return@synchronized null
        _pending.value = null
        current
    }

    private fun PendingQuestion.normalizedOrNull(): PendingQuestion? {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) return null
        return copy(
            label = label.trim(),
            question = cleanQuestion,
            spoilerLevel = spoilerLevel.trim(),
        )
    }

    private fun PendingQuestion.matches(label: String): Boolean {
        val cleanRequestLabel = label.trim()
        return this.label.isNotBlank() && this.label == cleanRequestLabel
    }
}
