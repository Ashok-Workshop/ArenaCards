package com.arenacards.feature.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun backendFailureIsVisibleAndRefreshCanRecover() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWalletRepository().apply {
            accountError = IllegalStateException("Backend unavailable.")
        }
        val viewModel = WalletViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("Backend unavailable.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.accountError = null
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.account?.wallet?.coinsBalance)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun successfulDailyAndMissionClaimsRefreshTheBackendSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWalletRepository()
        val viewModel = WalletViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.claimDailyReward()
        advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.account?.wallet?.coinsBalance)
        assertEquals("Daily reward claimed: 50 coins.", viewModel.uiState.value.successMessage)
        assertEquals(1, repository.dailyClaimCount)

        viewModel.claimMission("daily_login")
        advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.account?.wallet?.coinsBalance)
        assertEquals(
            "Mission reward claimed: 0 coins and 25 XP.",
            viewModel.uiState.value.successMessage
        )
        assertEquals(listOf("daily_login"), repository.claimedMissions)
    }

    @Test
    fun transactionPagingAppendsOnlyTheReturnedNextPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWalletRepository()
        val viewModel = WalletViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(listOf("daily_1"), viewModel.uiState.value.transactions.map {
            it.transactionId
        })

        viewModel.loadMoreTransactions()
        advanceUntilIdle()

        assertEquals(
            listOf("daily_1", "daily_2"),
            viewModel.uiState.value.transactions.map { it.transactionId }
        )
        assertFalse(viewModel.uiState.value.hasMoreTransactions)
    }

    private class FakeWalletRepository : WalletRepository {
        private val balance = MutableStateFlow(0)
        var accountError: Exception? = null
        var dailyClaimCount = 0
        val claimedMissions = mutableListOf<String>()

        override suspend fun getAccount(): ArenaAccountSnapshot {
            accountError?.let { throw it }
            return account(balance.value)
        }

        override suspend fun claimDailyReward(requestId: String): RewardClaimResult {
            assertTrue(requestId.isNotBlank())
            dailyClaimCount += 1
            balance.value += 50
            return RewardClaimResult(
                claimed = true,
                alreadyClaimed = false,
                rewardAmount = 50,
                rewardXp = 25,
                coinsBalance = balance.value,
                streakDays = 1,
                level = 1
            )
        }

        override suspend fun claimMission(
            missionId: String,
            requestId: String
        ): RewardClaimResult {
            claimedMissions += missionId
            return RewardClaimResult(
                claimed = true,
                alreadyClaimed = false,
                rewardAmount = 0,
                rewardXp = 25,
                coinsBalance = balance.value
            )
        }

        override suspend fun getTransactions(
            pageSize: Int,
            cursor: WalletTransactionCursor?
        ): WalletTransactionPage =
            if (cursor == null) {
                WalletTransactionPage(
                    transactions = listOf(transaction("daily_1")),
                    nextCursor = WalletTransactionCursor(10, "daily_1")
                )
            } else {
                WalletTransactionPage(
                    transactions = listOf(transaction("daily_2")),
                    nextCursor = null
                )
            }

        private fun account(coinsBalance: Int) = ArenaAccountSnapshot(
            wallet = WalletSummary(coinsBalance, coinsBalance, 0, 1, 1),
            dailyReward = DailyRewardSummary(null, 0, true, 50, 100, "UTC"),
            missions = emptyList(),
            progression = ArenaProgression(0, 1, 1_000),
            achievements = emptyList()
        )

        private fun transaction(id: String) = WalletTransaction(
            transactionId = id,
            type = "DAILY_REWARD",
            amount = 50,
            balanceBefore = 0,
            balanceAfter = 50,
            source = "daily_login",
            timestampMillis = 1,
            referenceId = id,
            status = "COMMITTED"
        )
    }
}
