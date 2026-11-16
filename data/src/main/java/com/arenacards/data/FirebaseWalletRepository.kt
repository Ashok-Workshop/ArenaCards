package com.arenacards.data

import com.arenacards.feature.wallet.ArenaAccountSnapshot
import com.arenacards.feature.wallet.ArenaAchievement
import com.arenacards.feature.wallet.ArenaAchievementUnlock
import com.arenacards.feature.wallet.ArenaMission
import com.arenacards.feature.wallet.ArenaProgression
import com.arenacards.feature.wallet.DailyRewardSummary
import com.arenacards.feature.wallet.RewardClaimResult
import com.arenacards.feature.wallet.WalletRepository
import com.arenacards.feature.wallet.WalletSummary
import com.arenacards.feature.wallet.WalletTransaction
import com.arenacards.feature.wallet.WalletTransactionCursor
import com.arenacards.feature.wallet.WalletTransactionPage
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

class FirebaseWalletRepository : WalletRepository {
    private val app = FirebaseApp.getInstance()
    private val auth = FirebaseAuth.getInstance(app)
    private val functions = FirebaseFunctions.getInstance(
        app,
        FirebaseBackendConfig.FUNCTIONS_REGION
    )

    override suspend fun getAccount(): ArenaAccountSnapshot {
        requireAuthenticatedUid()
        val response = call("getArenaAccount", emptyMap<String, Any>()).asMap("account")
        val wallet = response.requiredMap("wallet")
        val daily = response.requiredMap("dailyReward")
        val progression = response.requiredMap("progression")
        return ArenaAccountSnapshot(
            wallet = WalletSummary(
                coinsBalance = wallet.requiredInt("coinsBalance"),
                totalCoinsEarned = wallet.requiredInt("totalCoinsEarned"),
                totalCoinsSpent = wallet.requiredInt("totalCoinsSpent"),
                createdAtMillis = wallet.optionalLong("createdAtMillis"),
                updatedAtMillis = wallet.optionalLong("updatedAtMillis")
            ),
            dailyReward = DailyRewardSummary(
                lastClaimDate = daily.optionalString("lastClaimDate"),
                streakDays = daily.requiredInt("streakDays"),
                claimAvailable = daily.requiredBoolean("claimAvailable"),
                nextRewardCoins = daily.requiredInt("nextRewardCoins"),
                nextClaimAtMillis = daily.requiredLong("nextClaimAtMillis"),
                resetTimezone = daily.requiredString("resetTimezone")
            ),
            missions = response.requiredList("missions").map { item ->
                val mission = item.asMap("mission")
                ArenaMission(
                    id = mission.requiredString("id"),
                    title = mission.requiredString("title"),
                    description = mission.requiredString("description"),
                    type = mission.requiredString("type"),
                    targetValue = mission.requiredInt("targetValue"),
                    currentProgress = mission.requiredInt("currentProgress"),
                    rewardAmount = mission.requiredInt("rewardAmount"),
                    rewardXp = mission.requiredInt("rewardXp"),
                    startTimeMillis = mission.requiredLong("startTimeMillis"),
                    expiryTimeMillis = mission.requiredLong("expiryTimeMillis"),
                    completed = mission.requiredBoolean("completed"),
                    claimed = mission.requiredBoolean("claimed")
                )
            },
            progression = ArenaProgression(
                experiencePoints = progression.requiredInt("experiencePoints"),
                level = progression.requiredInt("level"),
                xpToNextLevel = progression.requiredInt("xpToNextLevel")
            ),
            achievements = response.requiredList("achievements").map { item ->
                val achievement = item.asMap("achievement")
                ArenaAchievement(
                    id = achievement.requiredString("id"),
                    title = achievement.requiredString("title"),
                    description = achievement.requiredString("description"),
                    progress = achievement.requiredInt("progress"),
                    targetValue = achievement.requiredInt("targetValue"),
                    unlocked = achievement.requiredBoolean("unlocked"),
                    unlockedAtMillis = achievement.optionalLong("unlockedAtMillis")
                )
            },
            achievementUnlockHistory = response.requiredList("achievementUnlockHistory").map { item ->
                val unlock = item.asMap("achievement unlock history")
                ArenaAchievementUnlock(
                    id = unlock.requiredString("id"),
                    title = unlock.requiredString("title"),
                    unlockedAtMillis = unlock.optionalLong("unlockedAtMillis")
                )
            }
        )
    }

    override suspend fun claimDailyReward(requestId: String): RewardClaimResult {
        requireAuthenticatedUid()
        return call(
            "claimDailyReward",
            mapOf("requestId" to requestId)
        ).asMap("daily reward").toRewardClaimResult(daily = true)
    }

    override suspend fun claimMission(
        missionId: String,
        requestId: String
    ): RewardClaimResult {
        requireAuthenticatedUid()
        return call(
            "claimMissionReward",
            mapOf("missionId" to missionId, "requestId" to requestId)
        ).asMap("mission reward").toRewardClaimResult(daily = false)
    }

    override suspend fun getTransactions(
        pageSize: Int,
        cursor: WalletTransactionCursor?
    ): WalletTransactionPage {
        requireAuthenticatedUid()
        require(pageSize in 1..MAX_PAGE_SIZE) { "Transaction page size is invalid." }
        val request = buildMap<String, Any> {
            put("pageSize", pageSize)
            cursor?.let {
                put("beforeCreatedAtMillis", it.beforeCreatedAtMillis)
                put("beforeTransactionId", it.beforeTransactionId)
            }
        }
        val response = call("getWalletTransactions", request).asMap("transaction history")
        val nextCursor = response["nextCursor"]?.let { value ->
            val map = value.asMap("transaction cursor")
            WalletTransactionCursor(
                beforeCreatedAtMillis = map.requiredLong("beforeCreatedAtMillis"),
                beforeTransactionId = map.requiredString("beforeTransactionId")
            )
        }
        return WalletTransactionPage(
            transactions = response.requiredList("transactions").map { item ->
                val transaction = item.asMap("transaction")
                WalletTransaction(
                    transactionId = transaction.requiredString("transactionId"),
                    type = transaction.requiredString("type"),
                    amount = transaction.requiredInt("amount"),
                    balanceBefore = transaction.requiredInt("balanceBefore"),
                    balanceAfter = transaction.requiredInt("balanceAfter"),
                    source = transaction.requiredString("source"),
                    timestampMillis = transaction.optionalLong("timestampMillis"),
                    referenceId = transaction.requiredString("referenceId"),
                    status = transaction.requiredString("status")
                )
            },
            nextCursor = nextCursor
        )
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Any? {
        requireAuthenticatedUid()
        return try {
            functions.getHttpsCallable(name).call(payload).await().getData()
        } catch (error: FirebaseFunctionsException) {
            val message = when (error.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    "Sign in again to continue."
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    "This account is not allowed to perform that action."
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    "Please wait before trying again."
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    "The ArenaCards service is temporarily unavailable. Try again."
                else -> error.message ?: "The ArenaCards request failed."
            }
            throw IllegalStateException(message, error)
        }
    }

    private fun requireAuthenticatedUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Sign in to access your Arena account.")

    private fun Any?.asMap(description: String): Map<String, Any?> =
        (this as? Map<*, *>)
            ?.entries
            ?.associate { (key, value) ->
                val stringKey = key as? String
                    ?: throw IllegalStateException("$description response has an invalid field.")
                stringKey to value
            }
            ?: throw IllegalStateException("$description response was invalid.")

    private fun Map<String, Any?>.requiredMap(key: String): Map<String, Any?> =
        this[key].asMap(key)

    private fun Map<String, Any?>.requiredList(key: String): List<*> =
        this[key] as? List<*> ?: throw IllegalStateException("$key response was invalid.")

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: throw IllegalStateException("$key response was invalid.")

    private fun Map<String, Any?>.optionalString(key: String): String? =
        this[key]?.let { it as? String ?: throw IllegalStateException("$key response was invalid.") }

    private fun Map<String, Any?>.requiredBoolean(key: String): Boolean =
        this[key] as? Boolean ?: throw IllegalStateException("$key response was invalid.")

    private fun Map<String, Any?>.requiredLong(key: String): Long =
        this[key].toLongValue(key)

    private fun Map<String, Any?>.optionalLong(key: String): Long? =
        this[key]?.toLongValue(key)

    private fun Map<String, Any?>.requiredInt(key: String): Int {
        val value = requiredLong(key)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw IllegalStateException("$key response was outside the supported range.")
        }
        return value.toInt()
    }

    private fun Any?.toLongValue(field: String): Long {
        val number = this as? Number
            ?: throw IllegalStateException("$field response was invalid.")
        val value = number.toLong()
        if (!number.toDouble().isFinite() || number.toDouble() != value.toDouble()) {
            throw IllegalStateException("$field response was invalid.")
        }
        return value
    }

    private fun Map<String, Any?>.toRewardClaimResult(daily: Boolean): RewardClaimResult =
        RewardClaimResult(
            claimed = requiredBoolean("claimed"),
            alreadyClaimed = requiredBoolean("alreadyClaimed"),
            rewardAmount = if (daily) requiredInt("rewardCoins") else requiredInt("rewardAmount"),
            rewardXp = optionalLong("rewardXp")?.toInt()
                ?: if (daily) 0 else requiredInt("rewardXp"),
            coinsBalance = requiredInt("coinsBalance"),
            streakDays = optionalLong("streakDays")?.toInt(),
            level = optionalLong("level")?.toInt()
        )

    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
