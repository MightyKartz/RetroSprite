package com.retrosprite.app.ui.screens.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.GkpLibraryProvider
import com.retrosprite.app.ui.viewmodel.GkpPreflightProvider
import com.retrosprite.app.ui.viewmodel.UiGkpLibraryState
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PacksViewModel(
    private val library: GkpLibraryProvider,
    private val preflightProvider: GkpPreflightProvider,
) : ViewModel() {

    val state: StateFlow<UiGkpLibraryState> = library.state
    val preflight: StateFlow<UiGkpPreflightState> = preflightProvider.state

    fun preflightExternalTree(uriString: String) {
        viewModelScope.launch { preflightProvider.preflightTree(uriString) }
    }

    fun installPreflightedTree() {
        viewModelScope.launch { preflightProvider.installPreflightedTree() }
    }

    fun disablePack(gameId: String) {
        viewModelScope.launch { library.disablePack(gameId) }
    }

    fun enablePack(gameId: String) {
        viewModelScope.launch { library.enablePack(gameId) }
    }

    fun requestDeletePack(gameId: String) {
        viewModelScope.launch { library.requestDelete(gameId) }
    }

    fun confirmDeletePack() {
        viewModelScope.launch { library.confirmDelete() }
    }

    fun cancelDeletePack() {
        viewModelScope.launch { library.cancelDelete() }
    }

    fun clearPreflight() {
        viewModelScope.launch { preflightProvider.clearPreflight() }
    }

    companion object {
        fun factory(
            library: GkpLibraryProvider,
            preflight: GkpPreflightProvider,
        ) = viewModelFactory {
            initializer { PacksViewModel(library, preflight) }
        }
    }
}
