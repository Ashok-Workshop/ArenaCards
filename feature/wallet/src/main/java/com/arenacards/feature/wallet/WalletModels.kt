package com.arenacards.feature.wallet

data class WalletSummary(
    val coinsBalance: Int,
    val totalCoinsEarned: Int,
    val totalCoinsSpent: Int,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?
)

data class DailyRewardSummary(
    val lastClaimDate: String?,
    val streakDays: Int,
    val claimAvailable: Boolean,
    val nextRewardCoins: Int,
    val nextClaimAtMillis: Long,
    val resetTimezone: String
)

data class ArenaMission(
    val id: String,
    val title: String,
    val description: String,
    val type: String,
    val targetValue: Int,
    val currentProgress: Int,
    val rewardAmount: Int,
    val rewardXp: Int,
    val startTimeMillis: Long,
    val expiryTimeMillis: Long,
    val completed: Boolean,
    val claimed: Boolean
)

data class ArenaAchievement(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val targetValue: Int,
    val unlocked: Boolean,
    val unlockedAtMillis: Long?
)

data class ArenaAchievementUnlock(
    val id: String,
    val title: String,
    val unlockedAtMillis: Long?
)

data class ArenaProgression(
    val experiencePoints: Int,
    val level: Int,
    val xpToNextLevel: Int
)

data class ArenaAccountSnapshot(
    val wallet: WalletSummary,
    val dailyReward: DailyRewardSummary,
    val missions: List<ArenaMission>,
    val progression: ArenaProgression,
    val achievements: List<ArenaAchievement>,
    val achievementUnlockHistory: List<ArenaAchievementUnlock> = emptyList()
)

data class WalletTransaction(
    val transactionId: String,
    val type: String,
    val amount: Int,
    val balanceBefore: Int,
    val balanceAfter: Int,
    val source: String,
    val timestampMillis: Long?,
    val referenceId: String,
    val status: String
)

data class WalletTransactionCursor(
    val beforeCreatedAtMillis: Long,
    val beforeTransactionId: String
)

data class WalletTransactionPage(
    val transactions: List<WalletTransaction>,
    val nextCursor: WalletTransactionCursor?
)

data class RewardClaimResult(
    val claimed: Boolean,
    val alreadyClaimed: Boolean,
    val rewardAmount: Int,
    val rewardXp: Int,
    val coinsBalance: Int,
    val streakDays: Int? = null,
    val level: Int? = null
)

interface WalletRepository {
    suspend fun getAccount(): ArenaAccountSnapshot
    suspend fun claimDailyReward(requestId: String): RewardClaimResult
    suspend fun claimMission(missionId: String, requestId: String): RewardClaimResult
    suspend fun getTransactions(
        pageSize: Int,
        cursor: WalletTransactionCursor?
    ): WalletTransactionPage
}

object WalletUnavailableRepository : WalletRepository {
    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("The secure wallet backend is unavailable.")

    override suspend fun getAccount(): ArenaAccountSnapshot = unavailable()

    override suspend fun claimDailyReward(requestId: String): RewardClaimResult = unavailable()

    override suspend fun claimMission(
        missionId: String,
        requestId: String
    ): RewardClaimResult = unavailable()

    override suspend fun getTransactions(
        pageSize: Int,
        cursor: WalletTransactionCursor?
    ): WalletTransactionPage = unavailable()
}
