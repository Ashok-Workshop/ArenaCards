package com.arenacards.feature.profile

import kotlinx.coroutines.flow.Flow

interface PlayerProfileRepository {
    fun observeMyProfile(uid: String): Flow<PlayerProfile?>

    suspend fun isUsernameAvailable(username: String): Boolean

    suspend fun createProfile(
        username: String,
        displayName: String,
        avatarId: String
    ): PlayerProfile

    suspend fun updateProfile(
        username: String,
        displayName: String,
        avatarId: String,
        visibility: ProfileVisibility
    ): PlayerProfile

    suspend fun searchPublicPlayers(usernamePrefix: String): List<PublicPlayer>
}
