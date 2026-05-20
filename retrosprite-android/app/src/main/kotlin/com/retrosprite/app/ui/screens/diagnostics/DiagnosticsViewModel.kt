package com.retrosprite.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val endpoint: EndpointStatusProvider,
    private val requestLog: RequestLogProvider
) : ViewModel() {

    val status: StateFlow<UiEndpointStatus> = endpoint.status
    val log: Flow<List<UiRequestLogItem>> = requestLog.log

    fun checkHealth() { viewModelScope.launch { endpoint.checkHealth() } }
    fun runConnectionTest() { viewModelScope.launch { requestLog.sendConnectionTest() } }
    fun clearLog() { viewModelScope.launch { requestLog.clear() } }

    companion object {
        fun factory(
            endpoint: EndpointStatusProvider,
            requestLog: RequestLogProvider
        ) = viewModelFactory {
            initializer { DiagnosticsViewModel(endpoint, requestLog) }
        }
    }
}

internal enum class DiagnosticsSourceFilter(
    val id: String,
    val displayName: String,
) {
    All("all", "全部"),
    RetroArch("retroarch", "RetroArch"),
    Pending("pending", "Pending"),
    App("app", "App"),
    Debug("debug", "Debug"),
}

internal data class DiagnosticsSourceCounts(
    val all: Int,
    val retroArch: Int,
    val pending: Int,
    val app: Int,
    val debug: Int,
) {
    fun countFor(filter: DiagnosticsSourceFilter): Int = when (filter) {
        DiagnosticsSourceFilter.All -> all
        DiagnosticsSourceFilter.RetroArch -> retroArch
        DiagnosticsSourceFilter.Pending -> pending
        DiagnosticsSourceFilter.App -> app
        DiagnosticsSourceFilter.Debug -> debug
    }
}

internal fun List<UiRequestLogItem>.diagnosticsSourceCounts(): DiagnosticsSourceCounts =
    DiagnosticsSourceCounts(
        all = size,
        retroArch = count { it.diagnosticsSource() == DiagnosticsSourceFilter.RetroArch },
        pending = count { it.diagnosticsSource() == DiagnosticsSourceFilter.Pending },
        app = count { it.diagnosticsSource() == DiagnosticsSourceFilter.App },
        debug = count { it.diagnosticsSource() == DiagnosticsSourceFilter.Debug },
    )

internal fun List<UiRequestLogItem>.filterByDiagnosticsSource(
    filter: DiagnosticsSourceFilter,
): List<UiRequestLogItem> =
    if (filter == DiagnosticsSourceFilter.All) {
        this
    } else {
        filter { it.diagnosticsSource() == filter }
    }

internal fun UiRequestLogItem.diagnosticsSource(): DiagnosticsSourceFilter {
    val source = questionSource?.trim().orEmpty()
    val output = rawOutputMode.trim().lowercase()
    return when {
        source == QUESTION_SOURCE_PENDING_HOTKEY -> DiagnosticsSourceFilter.Pending
        source == QUESTION_SOURCE_APP || output.startsWith(APP_OUTPUT_PREFIX) -> DiagnosticsSourceFilter.App
        source == QUESTION_SOURCE_DEBUG || isDebug || output.startsWith(DEBUG_OUTPUT_PREFIX) -> DiagnosticsSourceFilter.Debug
        else -> DiagnosticsSourceFilter.RetroArch
    }
}

private const val QUESTION_SOURCE_PENDING_HOTKEY: String = "pending_hotkey"
private const val QUESTION_SOURCE_APP: String = "app"
private const val QUESTION_SOURCE_DEBUG: String = "debug"
private const val APP_OUTPUT_PREFIX: String = "app:"
private const val DEBUG_OUTPUT_PREFIX: String = "debug:"
