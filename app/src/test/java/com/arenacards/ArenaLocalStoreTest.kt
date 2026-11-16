package com.arenacards

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.arenacards.feature.game.CpuDifficulty
import com.arenacards.feature.game.PracticeMatchRecord
import com.arenacards.feature.game.PracticeStatistics
import com.arenacards.feature.game.PracticeResult
import com.arenacards.feature.settings.ArenaSettings
import com.arenacards.feature.settings.ArenaThemePreference
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class ArenaLocalStoreTest {
    @Test
    fun newInstallDefaultsToDarkArenaTheme() = runBlocking {
        val store = ArenaLocalStore(InMemoryPreferencesDataStore())

        assertEquals(ArenaThemePreference.DARK, store.settings.first().theme)
    }

    @Test
    fun settingsAreStoredByPreferencesDataStore() = runBlocking {
        val directory = Files.createTempDirectory("arena-local-store").toFile()
        val file = directory.resolve("settings.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            val store = ArenaLocalStore(dataStore)
            val expectedSettings = ArenaSettings(
                soundEffectsEnabled = false,
                hapticsEnabled = false,
                reducedMotionEnabled = true,
                volume = 0.35f,
                theme = ArenaThemePreference.DARK
            )
            store.update { expectedSettings }
            assertEquals(expectedSettings, store.settings.first())
        } finally {
            scope.cancel()
            file.delete()
            directory.delete()
        }
    }

    @Test
    fun duplicatePracticeMatchRecordsDoNotChangeStatistics() = runBlocking {
        val store = ArenaLocalStore(InMemoryPreferencesDataStore())
        val record = PracticeMatchRecord(
            matchId = "match-1",
            result = PracticeResult.WIN,
            difficulty = CpuDifficulty.HARD,
            durationMillis = 90_000L,
            finalHandScore = 0,
            completedAtMillis = 1_700_000_000_000L
        )

        store.recordMatch(record)
        store.recordMatch(record)

        assertEquals(
            PracticeStatistics(
                gamesPlayed = 1,
                wins = 1,
                recentMatches = listOf(record)
            ),
            store.statistics.first()
        )
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val preferences = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = preferences

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences = mutex.withLock {
            transform(preferences.value).also { preferences.value = it }
        }
    }
}
