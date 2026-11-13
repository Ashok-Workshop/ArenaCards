package com.arenacards.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileInputValidationTest {
    @Test
    fun usernameNormalizationIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals("arena_player", ProfileInputValidation.normalizeUsername(" Arena_Player "))
    }

    @Test
    fun usernameValidationEnforcesLengthCharactersAndReservedNames() {
        assertNull(ProfileInputValidation.usernameError("Arena_Player9"))
        assertTrue(ProfileInputValidation.usernameError("ab") != null)
        assertTrue(ProfileInputValidation.usernameError("player name") != null)
        assertTrue(ProfileInputValidation.usernameError("ADMIN") != null)
    }

    @Test
    fun gamerIdRequiresPermanentArenaFormat() {
        assertTrue(ProfileInputValidation.isValidGamerId("AR-827491"))
        assertFalse(ProfileInputValidation.isValidGamerId("827491"))
        assertFalse(ProfileInputValidation.isValidGamerId("AR-12345"))
    }

    @Test
    fun displayNameAndAvatarAreValidated() {
        assertNull(ProfileInputValidation.displayNameError("Player One"))
        assertTrue(ProfileInputValidation.displayNameError(" ") != null)
        assertTrue(ProfileInputValidation.displayNameError("x".repeat(31)) != null)
        assertTrue(ProfileInputValidation.isValidAvatarId("ember"))
        assertFalse(ProfileInputValidation.isValidAvatarId("unknown"))
    }
}
