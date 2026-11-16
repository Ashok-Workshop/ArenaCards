package com.arenacards

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.compositionLocalOf
import com.arenacards.feature.game.GameFeedback
import com.arenacards.feature.settings.ArenaSettings

class ArenaFeedbackController(initialSettings: ArenaSettings) {
    private var settings = initialSettings
    private var toneGenerator: ToneGenerator? = createToneGenerator(initialSettings)

    @Synchronized
    fun updateSettings(updated: ArenaSettings) {
        if (settings.soundEffectsEnabled != updated.soundEffectsEnabled ||
            settings.volume != updated.volume
        ) {
            toneGenerator?.release()
            toneGenerator = createToneGenerator(updated)
        }
        settings = updated
    }

    @Synchronized
    fun play(event: GameFeedback) {
        val tone = when (event) {
            GameFeedback.CARD_SELECTED -> ToneGenerator.TONE_PROP_BEEP2 to 45
            GameFeedback.CARD_PLAYED -> ToneGenerator.TONE_PROP_ACK to 70
            GameFeedback.CARD_DRAWN -> ToneGenerator.TONE_PROP_BEEP to 55
            GameFeedback.SPECIAL_CARD -> ToneGenerator.TONE_PROP_ACK to 110
            GameFeedback.CHALLENGE_SUCCESS -> ToneGenerator.TONE_PROP_PROMPT to 130
            GameFeedback.CHALLENGE_FAILED -> ToneGenerator.TONE_PROP_NACK to 130
            GameFeedback.TURN_CHANGED -> ToneGenerator.TONE_PROP_BEEP2 to 70
            GameFeedback.MATCH_STARTED -> ToneGenerator.TONE_PROP_BEEP to 90
            GameFeedback.VICTORY -> ToneGenerator.TONE_PROP_PROMPT to 180
            GameFeedback.DEFEAT -> ToneGenerator.TONE_PROP_NACK to 160
        }
        toneGenerator?.startTone(tone.first, tone.second)
    }

    @Synchronized
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun createToneGenerator(settings: ArenaSettings): ToneGenerator? =
        if (settings.soundEffectsEnabled) {
            ToneGenerator(AudioManager.STREAM_MUSIC, (settings.volume * 100).toInt())
        } else {
            null
        }
}

val LocalArenaFeedback = compositionLocalOf<ArenaFeedbackController?> { null }
