package com.arenacards.navigation

object ArenaCardsDestinations {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val PROFILE_GATE = "profile-gate"
    const val SETTINGS = "settings"
    const val PRACTICE_HISTORY = "practice-history"
    const val WALLET = "wallet"
    const val LEADERBOARD = "leaderboard"
    const val MULTIPLAYER = "multiplayer"
    const val FRIENDS = "friends"
    const val CHAT_PATTERN = "chat/{friendUid}"
    const val ROOM_CHAT_PATTERN = "room-chat/{roomId}"
    const val NOTIFICATIONS = "notifications"
    const val AUTH_PATTERN = "auth/{mode}"
    const val GAME_SELECTION_PATTERN = "game-selection/{mode}"
    const val GAME_PATTERN = "game/{players}"

    fun auth(mode: String): String = "auth/$mode"
    fun gameSelection(mode: String): String = "game-selection/$mode"
    fun game(players: Int): String = "game/$players"
    fun chat(friendUid: String): String = "chat/$friendUid"
    fun roomChat(roomId: String): String = "room-chat/$roomId"
}
