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
