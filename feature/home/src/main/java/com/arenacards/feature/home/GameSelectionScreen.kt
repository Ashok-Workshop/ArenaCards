package com.arenacards.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

@Composable
fun GameSelectionScreen(
    online: Boolean,
    onBack: () -> Unit,
    onStartOfflineGame: (playerCount: Int) -> Unit,
    onStartOnlineGame: () -> Unit
) {
    var playerCount by remember { mutableIntStateOf(2) }
    val title = if (online) "Find your table" else "Choose your table"
    val accent = if (online) ArenaPalette.Blue else ArenaPalette.Green
    val opponentCount = playerCount - 1
    val opponentWord = if (opponentCount == 1) "opponent" else "opponents"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070D1B), Color(0xFF111C32), Color(0xFF0A1424))))
    ) {
        val wideLayout = maxWidth >= 600.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideLayout) 32.dp else 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ArenaSecondaryButton(text = "‹  Back", onClick = onBack)
                Surface(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Text(
                        "ARENA  •  " + if (online) "LIVE" else "PRACTICE",
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF202A4A), Color(0xFF173B48), Color(0xFF17243A))))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 22.dp, vertical = 24.dp)
            ) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)
                        .size(112.dp)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)), CircleShape)
                )
                Column {
                    Text(
                        if (online) "THE ARENA IS WAITING" else "YOUR NEXT ROUND STARTS HERE",
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (online) "Meet real players, create a private room, and take your seat."
                        else "Set the table, sharpen your strategy, and take on the CPU.",
                        modifier = Modifier.padding(top = 8.dp, end = 28.dp),
                        color = Color(0xFFD0D9EA),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).background(accent, CircleShape))
                        Text(
                            if (online) "REAL-TIME MULTIPLAYER" else "LOCAL MATCH  •  NO CONNECTION NEEDED",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (online) {
                LobbyPanel(
                    eyebrow = "ENTER THE MULTIPLAYER LOBBY",
                    heading = "Play your next match",
                    description = "Join matchmaking, enter a private room, or invite friends from the multiplayer lobby.",
                    buttonText = "Enter multiplayer  →",
                    accent = ArenaPalette.Blue,
                    onClick = onStartOnlineGame
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF151F35))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                        .padding(20.dp)
                ) {
                    Text("BUILD YOUR TABLE", color = ArenaPalette.Green, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "How many players?",
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "You + $opponentCount CPU $opponentWord",
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        color = Color(0xFFBFC9DC),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (2..4).forEach { count ->
                            PlayerCountOption(count, count == playerCount, Modifier.weight(1f)) { playerCount = count }
                        }
                    }
                    Text(
                        "Practice is local and does not award competitive coins, rank, or online mission progress.",
                        modifier = Modifier.padding(top = 14.dp),
                        color = Color(0xFF98A7C0),
                        style = MaterialTheme.typography.bodySmall
                    )
                    ArenaPrimaryButton(
                        text = "Deal me in  →",
                        onClick = { onStartOfflineGame(playerCount) },
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LobbyPanel(
    eyebrow: String,
    heading: String,
    description: String,
    buttonText: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF151F35))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Text(eyebrow, color = accent, style = MaterialTheme.typography.labelLarge)
        Text(heading, modifier = Modifier.padding(top = 8.dp), color = Color.White,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, modifier = Modifier.padding(top = 6.dp), color = Color(0xFFBFC9DC),
            style = MaterialTheme.typography.bodyMedium)
        ArenaPrimaryButton(text = buttonText, onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
    }
}

@Composable
private fun PlayerCountOption(
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.height(76.dp).clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(Color(0xFF246D5A), Color(0xFF174D4C)))
                else Brush.linearGradient(listOf(Color(0xFF202B43), Color(0xFF1A253A)))
            )
            .border(if (selected) 1.5.dp else 1.dp,
                if (selected) ArenaPalette.Green else Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$count players"; this.selected = selected }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("$count", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            if (selected) "SELECTED" else "PLAYERS",
            color = if (selected) ArenaPalette.Green else Color(0xFF9AA8C0),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
