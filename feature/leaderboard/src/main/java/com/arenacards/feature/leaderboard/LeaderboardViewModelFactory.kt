package com.arenacards.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LeaderboardViewModelFactory(
    private val repository: CompetitiveRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LeaderboardViewModel::class.java))
        return LeaderboardViewModel(repository) as T
    }
}
