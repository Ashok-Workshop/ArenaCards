package com.arenacards.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerProfileMappingTest {
    @Test
    fun mapsTheCanonicalProfileFieldsWithoutPrivateAuthenticationData() {
        val profile = PlayerProfile.fromMap(
            "firebase-uid",
            mapOf(
                "gamerId" to "AR-827491",
                "username" to "ArenaOne",
                "usernameNormalized" to "arenaone",
                "displayName" to "Arena One",
                "avatarId" to "ember",
                "createdAtMillis" to 10_000L,
                "updatedAtMillis" to 20_000L,
                "accountStatus" to "active",
                "profileVisibility" to "private",
                "totalMatches" to 12L,
                "wins" to 7L,
                "losses" to 3L,
                "draws" to 2L,
                "experiencePoints" to 700L,
                "playerLevel" to 1L
            )
        )

        assertEquals("firebase-uid", profile.uid)
        assertEquals("AR-827491", profile.gamerId)
        assertEquals("arenaone", profile.usernameNormalized)
        assertEquals(ProfileVisibility.PRIVATE, profile.profileVisibility)
        assertEquals(12L, profile.totalMatches)
        assertEquals(10_000L, profile.createdAtMillis)
    }
}
