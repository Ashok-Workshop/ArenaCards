package com.arenacards.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputValidationTest {
    @Test
    fun emailValidationAcceptsValidAddressAndRejectsMalformedInput() {
        assertTrue(AuthInputValidation.isValidEmail("player@example.com"))
        assertFalse(AuthInputValidation.isValidEmail("player@"))
    }

    @Test
    fun passwordRequiresLengthLetterAndNumber() {
        assertNull(AuthInputValidation.passwordError("arena123"))
        assertTrue(AuthInputValidation.passwordError("short1") != null)
        assertTrue(AuthInputValidation.passwordError("onlyletters") != null)
    }

    @Test
    fun phoneValidationRequiresInternationalE164Format() {
        assertTrue(AuthInputValidation.isValidPhoneNumber("+919876543210"))
        assertFalse(AuthInputValidation.isValidPhoneNumber("9876543210"))
        assertFalse(AuthInputValidation.isValidPhoneNumber("+0123456789"))
    }

    @Test
    fun otpValidationRequiresSixDigits() {
        assertTrue(AuthInputValidation.isValidOtp("123456"))
        assertFalse(AuthInputValidation.isValidOtp("12345"))
        assertFalse(AuthInputValidation.isValidOtp("12a456"))
    }

    @Test
    fun usernameValidationRestrictsLengthAndCharacters() {
        assertTrue(AuthInputValidation.isValidUsername("Arena_Player9"))
        assertFalse(AuthInputValidation.isValidUsername("ab"))
        assertFalse(AuthInputValidation.isValidUsername("player name"))
    }
}
