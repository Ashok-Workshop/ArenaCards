package com.arenacards.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WalletUiState(
    val isLoading: Boolean = false,
    val isClaiming: Boolean = false,
    val isLoadingMore: Boolean = false,
    val account: ArenaAccountSnapshot? = null,
    val transactions: List<WalletTransaction> = emptyList(),
    val transactionCursor: WalletTransactionCursor? = null,
    val hasMoreTransactions: Boolean = false,
    val errorMessage: String? = null,
    val transactionError: String? = null,
    val successMessage: String? = null
)

class WalletViewModel(
    private val repository: WalletRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            successMessage = null
        )
        viewModelScope.launch {
            try {
                val account = repository.getAccount()
                _uiState.value = _uiState.value.copy(account = account)
                val page = repository.getTransactions(PAGE_SIZE, null)
                _uiState.value = _uiState.value.copy(
                    transactions = page.transactions,
                    transactionCursor = page.nextCursor,
                    hasMoreTransactions = page.nextCursor != null,
                    transactionError = null
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Arena account data could not be loaded."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun claimDailyReward() {
        if (_uiState.value.isClaiming) return
        _uiState.value = _uiState.value.copy(
            isClaiming = true,
            errorMessage = null,
            successMessage = null
        )
        viewModelScope.launch {
            try {
                val result = repository.claimDailyReward(UUID.randomUUID().toString())
                _uiState.value = _uiState.value.copy(
                    successMessage = if (result.claimed) {
                        "Daily reward claimed: ${result.rewardAmount} coins."
                    } else {
                        "Today's daily reward has already been claimed."
                    }
                )
                refreshAfterClaim()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Daily reward could not be claimed."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isClaiming = false)
            }
        }
    }

    fun claimMission(missionId: String) {
        if (_uiState.value.isClaiming) return
        _uiState.value = _uiState.value.copy(
            isClaiming = true,
            errorMessage = null,
            successMessage = null
        )
        viewModelScope.launch {
            try {
                val result = repository.claimMission(missionId, UUID.randomUUID().toString())
                _uiState.value = _uiState.value.copy(
                    successMessage = if (result.claimed) {
                        "Mission reward claimed: ${result.rewardAmount} coins and ${result.rewardXp} XP."
                    } else {
                        "That mission reward has already been claimed."
                    }
                )
                refreshAfterClaim()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Mission reward could not be claimed."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isClaiming = false)
            }
        }
    }

    fun loadMoreTransactions() {
        val cursor = _uiState.value.transactionCursor ?: return
        if (_uiState.value.isLoadingMore) return
        _uiState.value = _uiState.value.copy(isLoadingMore = true, transactionError = null)
        viewModelScope.launch {
            try {
                val page = repository.getTransactions(PAGE_SIZE, cursor)
                _uiState.value = _uiState.value.copy(
                    transactions = _uiState.value.transactions + page.transactions,
                    transactionCursor = page.nextCursor,
                    hasMoreTransactions = page.nextCursor != null
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    transactionError = error.message ?: "Older transactions could not be loaded."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun clearForSignOut() {
        _uiState.value = WalletUiState()
    }

    private suspend fun refreshAfterClaim() {
        val account = repository.getAccount()
        val page = repository.getTransactions(PAGE_SIZE, null)
        _uiState.value = _uiState.value.copy(
            account = account,
            transactions = page.transactions,
            transactionCursor = page.nextCursor,
            hasMoreTransactions = page.nextCursor != null,
            transactionError = null
        )
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}

class WalletViewModelFactory(
    private val repository: WalletRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == WalletViewModel::class.java) {
            "WalletViewModelFactory only creates WalletViewModel."
        }
        return WalletViewModel(repository) as T
    }
}
