package com.arenacards.feature.profile

data class PlayerProfile(
    val uid: String,
    val gamerId: String,
    val username: String,
    val usernameNormalized: String,
    val displayName: String,
    val avatarId: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val accountStatus: String,
    val profileVisibility: ProfileVisibility,
    val totalMatches: Long,
    val wins: Long,
    val losses: Long,
    val draws: Long,
    val experiencePoints: Long,
    val playerLevel: Int
) {
    companion object {
        fun fromMap(uid: String, data: Map<String, Any?>): PlayerProfile {
            val visibility = data["profileVisibility"] as? String
                ?: error("Profile visibility is missing.")
            return PlayerProfile(
                uid = uid,
                gamerId = data.requiredString("gamerId"),
                username = data.requiredString("username"),
                usernameNormalized = data.requiredString("usernameNormalized"),
                displayName = data.requiredString("displayName"),
                avatarId = data.requiredString("avatarId"),
                createdAtMillis = data.requiredLong("createdAtMillis"),
                updatedAtMillis = data.requiredLong("updatedAtMillis"),
                accountStatus = data.requiredString("accountStatus"),
                profileVisibility = ProfileVisibility.fromWireValue(visibility),
                totalMatches = data.requiredLong("totalMatches"),
                wins = data.requiredLong("wins"),
                losses = data.requiredLong("losses"),
                draws = data.requiredLong("draws"),
                experiencePoints = data.requiredLong("experiencePoints"),
                playerLevel = data.requiredLong("playerLevel").toInt()
            )
        }

        private fun Map<String, Any?>.requiredString(key: String): String =
            this[key] as? String ?: error("Profile field '$key' is missing or invalid.")

        private fun Map<String, Any?>.requiredLong(key: String): Long =
            (this[key] as? Number)?.toLong()
                ?: error("Profile field '$key' is missing or invalid.")
    }
}

enum class ProfileVisibility(val wireValue: String) {
    PUBLIC("public"),
    PRIVATE("private");

    companion object {
        fun fromWireValue(value: String): ProfileVisibility =
            entries.firstOrNull { it.wireValue == value }
                ?: error("Profile visibility value is invalid.")
    }
}

data class PublicPlayer(
    val uid: String,
    val gamerId: String,
    val username: String,
    val displayName: String,
    val avatarId: String,
    val playerLevel: Int
)
