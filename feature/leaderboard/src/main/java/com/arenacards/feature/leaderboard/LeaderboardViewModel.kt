package com.arenacards.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val scope: LeaderboardScope = LeaderboardScope.GLOBAL,
    val category: LeaderboardCategory = LeaderboardCategory.RATING,
    val leaderboard: CompetitiveLeaderboardPage? = null,
    val profile: CompetitiveProfile? = null,
    val tournaments: List<TournamentSummary> = emptyList(),
    val tournamentCursor: TournamentCursor? = null,
    val hasMoreTournaments: Boolean = false,
    val selectedTournament: TournamentDetails? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isTournamentLoading: Boolean = false,
    val busyTournamentId: String? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

class LeaderboardViewModel(
    private val repository: CompetitiveRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun selectScope(scope: LeaderboardScope) {
        if (mutableUiState.value.scope == scope) return
        mutableUiState.update { it.copy(scope = scope) }
        loadLeaderboard()
    }

    fun selectCategory(category: LeaderboardCategory) {
        if (mutableUiState.value.category == category) return
        mutableUiState.update { it.copy(category = category) }
        loadLeaderboard()
    }

    fun refresh() {
        loadLeaderboard()
        loadProfile()
        loadTournaments(reset = true)
    }

    fun loadNextPage() {
        val cursor = mutableUiState.value.leaderboard?.nextCursor ?: return
        if (mutableUiState.value.isLoadingMore) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            try {
                val state = mutableUiState.value
                val page = repository.getLeaderboard(state.scope, state.category, cursor)
                mutableUiState.update { current ->
                    val old = current.leaderboard
                    current.copy(
                        leaderboard = page.copy(entries = old?.entries.orEmpty() + page.entries),
                        isLoadingMore = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(isLoadingMore = false, errorMessage = error.message ?: "Could not load more rankings.")
                }
            }
        }
    }

    fun loadTournament(tournamentId: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isTournamentLoading = true, errorMessage = null) }
            try {
                val detail = repository.getTournament(tournamentId)
                mutableUiState.update { it.copy(selectedTournament = detail, isTournamentLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(isTournamentLoading = false, errorMessage = error.message ?: "Could not load tournament.")
                }
            }
        }
    }

    fun closeTournament() {
        mutableUiState.update { it.copy(selectedTournament = null) }
    }

    fun register(tournamentId: String) {
        updateRegistration(tournamentId, register = true)
    }

    fun cancelRegistration(tournamentId: String) {
        updateRegistration(tournamentId, register = false)
    }

    fun startTournamentMatch(tournamentId: String, matchId: String, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(busyTournamentId = tournamentId, errorMessage = null) }
            try {
                val onlineMatchId = repository.startTournamentMatch(tournamentId, matchId)
                mutableUiState.update {
                    it.copy(busyTournamentId = null, actionMessage = "Tournament match is ready.")
                }
                onStarted(onlineMatchId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        busyTournamentId = null,
                        errorMessage = error.message ?: "Could not start the tournament match."
                    )
                }
            }
        }
    }

    fun loadMoreTournaments() {
        if (mutableUiState.value.hasMoreTournaments) loadTournaments(reset = false)
    }

    private fun updateRegistration(tournamentId: String, register: Boolean) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(busyTournamentId = tournamentId, errorMessage = null, actionMessage = null)
            }
            try {
                if (register) repository.registerTournament(tournamentId)
                else repository.cancelTournamentRegistration(tournamentId)
                mutableUiState.update {
                    it.copy(
                        tournaments = it.tournaments.map { tournament ->
                            if (tournament.tournamentId == tournamentId) {
                                tournament.copy(isRegistered = register)
                            } else tournament
                        },
                        busyTournamentId = null,
                        actionMessage = if (register) "Registration confirmed." else "Registration cancelled."
                    )
                }
                if (mutableUiState.value.selectedTournament?.tournamentId == tournamentId) {
                    loadTournament(tournamentId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        busyTournamentId = null,
                        errorMessage = error.message ?: "Tournament registration failed."
                    )
                }
            }
        }
    }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val state = mutableUiState.value
                val page = repository.getLeaderboard(state.scope, state.category, null)
                mutableUiState.update { it.copy(leaderboard = page, isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Could not load rankings.")
                }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val profile = repository.getProfile()
                mutableUiState.update { it.copy(profile = profile) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(errorMessage = error.message ?: "Could not load competitive profile.")
                }
            }
        }
    }

    private fun loadTournaments(reset: Boolean) {
        viewModelScope.launch {
            val cursor = if (reset) null else mutableUiState.value.tournamentCursor
            if (!reset && mutableUiState.value.isLoadingMore) return@launch
            mutableUiState.update {
                it.copy(
                    isLoadingMore = !reset,
                    isLoading = if (reset) true else it.isLoading
                )
            }
            try {
                val page = repository.getTournaments(cursor)
                mutableUiState.update {
                    it.copy(
                        tournaments = if (reset) page.tournaments else it.tournaments + page.tournaments,
                        tournamentCursor = page.nextCursor,
                        hasMoreTournaments = page.nextCursor != null,
                        isLoading = if (reset) false else it.isLoading,
                        isLoadingMore = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isLoading = if (reset) false else it.isLoading,
                        isLoadingMore = false,
                        errorMessage = error.message ?: "Could not load tournaments."
                    )
                }
            }
        }
    }
}
