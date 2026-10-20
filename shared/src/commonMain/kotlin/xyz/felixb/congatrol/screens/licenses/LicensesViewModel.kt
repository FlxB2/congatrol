package xyz.felixb.congatrol.screens.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.felixb.congatrol.data.LicenseRepository
import xyz.felixb.congatrol.data.OpenSourceLibrary

sealed interface LicensesUiState {
    /** The report is still being read; distinct from [Content] with nothing in it. */
    data object Loading : LicensesUiState

    data class Content(
        val libraries: ImmutableList<OpenSourceLibrary> = persistentListOf(),
    ) : LicensesUiState
}

/** Backs the open source licence list. The report is bundled with the app, so there is nothing to refresh. */
class LicensesViewModel(
    repository: LicenseRepository,
) : ViewModel() {
    val state: StateFlow<LicensesUiState> =
        flow<LicensesUiState> { emit(LicensesUiState.Content(repository.librariesOrEmpty())) }
            .stateIn(viewModelScope, SharingStarted.Lazily, LicensesUiState.Loading)
}

sealed interface LicenseDetailUiState {
    data object Loading : LicenseDetailUiState

    /** The id names no library in this build's report. Unreachable from the list, which is where ids come from. */
    data object NotFound : LicenseDetailUiState

    data class Content(
        val library: OpenSourceLibrary,
    ) : LicenseDetailUiState
}

/** Backs one library's licence page; [load] takes the route argument. */
class LicenseDetailViewModel(
    private val repository: LicenseRepository,
) : ViewModel() {
    private val libraryId = MutableStateFlow<String?>(null)

    val state: StateFlow<LicenseDetailUiState> =
        libraryId
            .filterNotNull()
            .map { id ->
                repository.librariesOrEmpty().firstOrNull { it.id == id }?.let { LicenseDetailUiState.Content(it) }
                    ?: LicenseDetailUiState.NotFound
            }.stateIn(viewModelScope, SharingStarted.Lazily, LicenseDetailUiState.Loading)

    fun load(id: String) {
        libraryId.value = id
    }
}

/**
 * The report's libraries, or none if it cannot be read. It ships inside the app, so the only way
 * to fail is a malformed file — which is a build mistake, and better shown as an empty list than
 * as a crash on a screen nobody needs to get anything done.
 */
private suspend fun LicenseRepository.librariesOrEmpty(): ImmutableList<OpenSourceLibrary> =
    try {
        libraries()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        persistentListOf()
    }
