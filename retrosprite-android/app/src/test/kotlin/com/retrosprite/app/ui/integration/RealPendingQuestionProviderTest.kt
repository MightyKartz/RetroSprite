package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.InMemoryPendingQuestionStore
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealPendingQuestionProviderTest {

    @Test
    fun `prepare writes trimmed question with settings spoiler default`() = runTest {
        val store = InMemoryPendingQuestionStore()
        val provider = RealPendingQuestionProvider(
            store = store,
            settings = MutableStateFlow(UiSettings(spoilerLevel = UiSpoilerLevel.Clear)),
            scope = backgroundScope,
            clock = { 123L },
        )

        provider.prepare(
            label = " 2048__ ",
            question = " 两个 2 怎么合并？ ",
            spoilerLevelOverride = null,
        )
        advanceUntilIdle()

        assertEquals("2048__", store.pending.value?.label)
        assertEquals("两个 2 怎么合并？", store.pending.value?.question)
        assertEquals("clear", store.pending.value?.spoilerLevel)
        assertEquals(123L, store.pending.value?.createdAtMillis)
        assertEquals(UiSpoilerLevel.Clear, provider.state.value.pending?.spoilerLevel)
    }

    @Test
    fun `prepare can override spoiler level for one hotkey request`() = runTest {
        val store = InMemoryPendingQuestionStore()
        val provider = RealPendingQuestionProvider(
            store = store,
            settings = MutableStateFlow(UiSettings(spoilerLevel = UiSpoilerLevel.Light)),
            scope = backgroundScope,
        )

        provider.prepare(
            label = "relay_station__",
            question = "直接告诉我路线",
            spoilerLevelOverride = UiSpoilerLevel.Direct,
        )
        advanceUntilIdle()

        assertEquals("direct", store.pending.value?.spoilerLevel)
        assertEquals(UiSpoilerLevel.Direct, provider.state.value.pending?.spoilerLevel)
    }

    @Test
    fun `blank question clears queue`() = runTest {
        val store = InMemoryPendingQuestionStore()
        val provider = RealPendingQuestionProvider(
            store = store,
            settings = MutableStateFlow(UiSettings()),
            scope = backgroundScope,
        )

        provider.prepare("2048__", "queued", null)
        provider.prepare("2048__", "   ", null)

        assertNull(store.pending.value)
    }
}
