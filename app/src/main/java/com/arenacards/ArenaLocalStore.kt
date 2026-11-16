package com.arenacards

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arenacards.feature.game.CpuDifficulty
import com.arenacards.feature.game.PracticeMatchRecord
import com.arenacards.feature.game.PracticeResult
import com.arenacards.feature.game.PracticeStatistics
import com.arenacards.feature.game.PracticeStatisticsRepository
import com.arenacards.feature.settings.ArenaSettings
import com.arenacards.feature.settings.ArenaSettingsRepository
import com.arenacards.feature.settings.ArenaThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.arenaDataStore by preferencesDataStore(name = "arena_cards_local")

class ArenaLocalStore internal constructor(private val store: DataStore<Preferences>) :
    ArenaSettingsRepository,
    PracticeStatisticsRepository {
    constructor(context: Context) : this(context.applicationContext.arenaDataStore)

    override val settings: Flow<ArenaSettings> = store.data
        .map(::toSettings)
        .distinctUntilChanged()

    override val statistics: Flow<PracticeStatistics> = store.data
        .map(::toStatistics)
        .distinctUntilChanged()

    override suspend fun update(transform: (ArenaSettings) -> ArenaSettings) {
        store.edit { preferences ->
            val settings = transform(toSettings(preferences))
            require(settings.volume.isFinite() && settings.volume in 0f..1f) {
                "Sound volume must be between zero and one."
            }
            preferences[SOUND_ENABLED] = settings.soundEffectsEnabled
            preferences[HAPTICS_ENABLED] = settings.hapticsEnabled
            preferences[REDUCED_MOTION] = settings.reducedMotionEnabled
            preferences[VOLUME] = settings.volume
            preferences[THEME] = settings.theme.name
        }
    }

    override suspend fun recordMatch(record: PracticeMatchRecord) {
        require(record.matchId.isNotBlank())
        require(record.durationMillis >= 0L)
        require(record.finalHandScore >= 0)
        store.edit { preferences ->
            val recent = decodeMatches(preferences[RECENT_MATCHES].orEmpty())
            if (recent.any { it.matchId == record.matchId }) return@edit
            preferences[GAMES_PLAYED] = (preferences[GAMES_PLAYED] ?: 0) + 1
            when (record.result) {
                PracticeResult.WIN -> preferences[WINS] = (preferences[WINS] ?: 0) + 1
                PracticeResult.LOSS -> preferences[LOSSES] = (preferences[LOSSES] ?: 0) + 1
                PracticeResult.DRAW -> preferences[DRAWS] = (preferences[DRAWS] ?: 0) + 1
            }
            preferences[RECENT_MATCHES] = encodeMatches((listOf(record) + recent).take(MAX_RECENT_MATCHES))
        }
    }

    private fun toSettings(preferences: Preferences): ArenaSettings {
        val themeName = preferences[THEME] ?: ArenaThemePreference.DARK.name
        val theme = ArenaThemePreference.entries.firstOrNull { it.name == themeName }
            ?: throw IllegalStateException("Saved ArenaCards theme preference is invalid.")
        val volume = (preferences[VOLUME] ?: ArenaSettings().volume).also {
            check(it.isFinite() && it in 0f..1f) {
                "Saved ArenaCards sound volume is invalid."
            }
        }
        return ArenaSettings(
            soundEffectsEnabled = preferences[SOUND_ENABLED] ?: true,
            hapticsEnabled = preferences[HAPTICS_ENABLED] ?: true,
            reducedMotionEnabled = preferences[REDUCED_MOTION] ?: false,
            volume = volume,
            theme = theme
        )
    }

    private fun toStatistics(preferences: Preferences): PracticeStatistics {
        val recent = decodeMatches(preferences[RECENT_MATCHES].orEmpty())
        return PracticeStatistics(
            gamesPlayed = preferences[GAMES_PLAYED] ?: 0,
            wins = preferences[WINS] ?: 0,
            losses = preferences[LOSSES] ?: 0,
            draws = preferences[DRAWS] ?: 0,
            recentMatches = recent
        )
    }

    private fun encodeMatches(matches: List<PracticeMatchRecord>): String =
        matches.joinToString("\n") { match ->
            listOf(
                match.matchId,
                match.result.name,
                match.difficulty.name,
                match.durationMillis.toString(),
                match.finalHandScore.toString(),
                match.completedAtMillis.toString()
            ).joinToString("\t")
        }

    private fun decodeMatches(encoded: String): List<PracticeMatchRecord> {
        if (encoded.isEmpty()) return emptyList()
        return encoded.lines().map { line ->
            val fields = line.split('\t')
            check(fields.size == MATCH_FIELD_COUNT) {
                "Saved ArenaCards practice history is invalid."
            }
            PracticeMatchRecord(
                matchId = fields[0],
                result = enumValueOf(fields[1]),
                difficulty = enumValueOf<CpuDifficulty>(fields[2]),
                durationMillis = fields[3].toLongOrNull()
                    ?: throw IllegalStateException("Saved match duration is invalid."),
                finalHandScore = fields[4].toIntOrNull()
                    ?: throw IllegalStateException("Saved match score is invalid."),
                completedAtMillis = fields[5].toLongOrNull()
                    ?: throw IllegalStateException("Saved match date is invalid.")
            )
        }
    }

    private companion object {
        const val MAX_RECENT_MATCHES = 30
        const val MATCH_FIELD_COUNT = 6
        val SOUND_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion_enabled")
        val VOLUME = floatPreferencesKey("sound_effects_volume")
        val THEME = stringPreferencesKey("theme")
        val GAMES_PLAYED = intPreferencesKey("practice_games_played")
        val WINS = intPreferencesKey("practice_wins")
        val LOSSES = intPreferencesKey("practice_losses")
        val DRAWS = intPreferencesKey("practice_draws")
        val RECENT_MATCHES = stringPreferencesKey("practice_recent_matches_v1")
    }
}
