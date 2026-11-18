package com.arenacards.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import java.io.IOException
import kotlinx.coroutines.launch

@Composable
fun ArenaSettingsScreen(
    repository: ArenaSettingsRepository,
    onBack: () -> Unit,
    onOpenPracticeHistory: () -> Unit
) {
    val settings by repository.settings.collectAsState(initial = ArenaSettings())
    val scope = rememberCoroutineScope()
    var pendingVolume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun updateSettings(transform: (ArenaSettings) -> ArenaSettings) {
        scope.launch {
            try {
                repository.update(transform)
                saveError = null
            } catch (error: IOException) {
                saveError = "Settings could not be saved on this device."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080E1D), Color(0xFF14233D), Color(0xFF080E1D))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                ArenaCardsLogoMark(Modifier.size(40.dp))
                Column {
                    Text("ARENA / SETTINGS", color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge)
                    Text("Settings", style = MaterialTheme.typography.headlineLarge)
                }
            }
            ArenaSecondaryButton(text = "‹ Back", onClick = onBack)
        }
        Text(
            "Personalize your practice experience. Changes are saved on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        ArenaPanel(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            SettingToggle(
                title = "Sound effects",
                description = "Play subtle feedback tones during card-game actions.",
                checked = settings.soundEffectsEnabled,
                onCheckedChange = {
                    updateSettings { current -> current.copy(soundEffectsEnabled = it) }
                }
            )
            Text(
                "Effects volume",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = pendingVolume,
                onValueChange = { pendingVolume = it },
                onValueChangeFinished = {
                    val volume = pendingVolume
                    updateSettings { current -> current.copy(volume = volume) }
                },
                valueRange = 0f..1f,
                enabled = settings.soundEffectsEnabled,
                modifier = Modifier.semantics {
                    contentDescription = "Sound effects volume"
                }
            )
            SettingToggle(
                title = "Haptic feedback",
                description = "Use restrained vibration for important game actions.",
                checked = settings.hapticsEnabled,
                onCheckedChange = {
                    updateSettings { current -> current.copy(hapticsEnabled = it) }
                }
            )
            SettingToggle(
                title = "Reduce motion",
                description = "Use shorter, simpler visual transitions.",
                checked = settings.reducedMotionEnabled,
                onCheckedChange = {
                    updateSettings { current -> current.copy(reducedMotionEnabled = it) }
                }
            )
        }

        ArenaPanel(modifier = Modifier.fillMaxWidth()) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose a theme or follow your device setting.",
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArenaThemePreference.entries.forEach { option ->
                    val label = option.name.lowercase().replaceFirstChar(Char::uppercase)
                    val selected = option == settings.theme
                    val onSelect: () -> Unit = { updateSettings { it.copy(theme = option) } }
                    if (selected) {
                        ArenaPrimaryButton(
                            text = label,
                            onClick = onSelect,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ArenaSecondaryButton(
                            text = label,
                            onClick = onSelect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        ArenaPrimaryButton(
            text = "Practice history",
            onClick = onOpenPracticeHistory,
            modifier = Modifier.fillMaxWidth()
        )
        saveError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "Push notification preferences are available in Friends & Notifications.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = title
            }
        )
    }
}
