package com.retrosprite.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val endpoint: EndpointStatusProvider
) : ViewModel() {

    val status: StateFlow<UiEndpointStatus> = endpoint.status

    fun restart() {
        viewModelScope.launch { endpoint.restart() }
    }

    fun checkHealth() {
        viewModelScope.launch { endpoint.checkHealth() }
    }

    companion object {
        fun factory(endpoint: EndpointStatusProvider) = viewModelFactory {
            initializer { HomeViewModel(endpoint) }
        }
    }
}
