package com.arenacards.feature.settings

import kotlinx.coroutines.flow.Flow

enum class ArenaThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

data class ArenaSettings(
    val soundEffectsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotionEnabled: Boolean = false,
    val volume: Float = 0.65f,
    val theme: ArenaThemePreference = ArenaThemePreference.DARK
)

interface ArenaSettingsRepository {
    val settings: Flow<ArenaSettings>

    suspend fun update(transform: (ArenaSettings) -> ArenaSettings)
}
