package com.arenacards.feature.profile

object ProfileInputValidation {
    private val usernamePattern = Regex("^[A-Za-z0-9_]{3,20}$")
    private val gamerIdPattern = Regex("^AR-[0-9]{6}$")
    private val reservedUsernames = setOf(
        "admin",
        "administrator",
        "arenacards",
        "moderator",
        "null",
        "official",
        "root",
        "support",
        "system",
        "undefined"
    )

    fun normalizeUsername(username: String): String = username.trim().lowercase()

    fun usernameError(username: String): String? {
        val trimmed = username.trim()
        return when {
            !usernamePattern.matches(trimmed) ->
                "Username must be 3-20 characters using letters, numbers, or underscores."
            isReserved(normalizeUsername(trimmed)) ->
                "That username is reserved. Choose another."
            else -> null
        }
    }

    fun displayNameError(displayName: String): String? =
        if (displayName.trim().length !in MIN_DISPLAY_NAME_LENGTH..MAX_DISPLAY_NAME_LENGTH) {
            "Display name must be 1-30 characters."
        } else {
            null
        }

    fun isValidGamerId(gamerId: String): Boolean = gamerIdPattern.matches(gamerId)

    fun isValidAvatarId(avatarId: String): Boolean = avatarId in AVATAR_IDS

    private fun isReserved(username: String): Boolean =
        username in reservedUsernames ||
            listOf("admin", "arenacards", "moderator", "official", "support")
                .any(username::startsWith)

    val AVATAR_IDS = setOf(
        "ember",
        "tide",
        "grove",
        "sun",
        "comet",
        "crown",
        "spark",
        "wild"
    )

    private const val MIN_DISPLAY_NAME_LENGTH = 1
    private const val MAX_DISPLAY_NAME_LENGTH = 30
}
