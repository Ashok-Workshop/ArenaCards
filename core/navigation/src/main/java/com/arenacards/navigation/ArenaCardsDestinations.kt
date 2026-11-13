package com.arenacards.navigation

object ArenaCardsDestinations {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val PROFILE_GATE = "profile-gate"
    const val AUTH_PATTERN = "auth/{mode}"
    const val GAME_SELECTION_PATTERN = "game-selection/{mode}"
    const val GAME_PATTERN = "game/{players}"

    fun auth(mode: String): String = "auth/$mode"
    fun gameSelection(mode: String): String = "game-selection/$mode"
    fun game(players: Int): String = "game/$players"
}
