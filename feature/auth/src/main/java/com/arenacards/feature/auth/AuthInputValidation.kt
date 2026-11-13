package com.arenacards.feature.auth

object AuthInputValidation {
    private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    private val usernamePattern = Regex("^[A-Za-z0-9_]{3,20}$")
    private val e164Pattern = Regex("^\\+[1-9][0-9]{7,14}$")

    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())

    fun isValidUsername(username: String): Boolean = usernamePattern.matches(username.trim())

    fun passwordError(password: String): String? = when {
        password.length < 8 -> "Use at least 8 characters."
        password.none(Char::isLetter) || password.none(Char::isDigit) ->
            "Include at least one letter and one number."
        else -> null
    }

    fun isValidPhoneNumber(e164Number: String): Boolean =
        e164Pattern.matches(e164Number)

    fun isValidOtp(code: String): Boolean =
        code.length == OTP_LENGTH && code.all(Char::isDigit)

    const val OTP_LENGTH = 6
}
