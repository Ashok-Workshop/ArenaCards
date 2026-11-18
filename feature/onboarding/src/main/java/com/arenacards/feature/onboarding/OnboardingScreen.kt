package com.arenacards.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaCardTile
import com.arenacards.designsystem.ArenaCardColor
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        eyebrow = "THE TABLE IS YOURS",
        title = "Every turn tells a story.",
        description = "Match bold colors and clever plays in a fresh, fast-paced card game made for one more round."
    ),
    OnboardingPage(
        eyebrow = "BETTER TOGETHER",
        title = "Bring your crew.",
        description = "Gather friends around the table, make a private room, and turn a quick match into a rivalry."
    ),
    OnboardingPage(
        eyebrow = "PLAY. PROGRESS. REPEAT.",
        title = "Rewards, just for fun.",
        description = "Collect virtual coins and celebrate your milestones. Coins are for play only and have no cash value."
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var pageIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                ArenaCardsLogoMark(Modifier.size(34.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("ARENA", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black)
                    Text("CARDS  •  PLAY TOGETHER", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            TextButton(
                onClick = onComplete,
                modifier = Modifier.semantics {
                    contentDescription = "Skip introduction"
                }
            ) {
                Text("Skip")
            }
        }

        AnimatedContent(targetState = pageIndex, label = "onboarding-page") { index ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingArtwork(index)
                Text(
                    text = pages[index].eyebrow,
                    modifier = Modifier.padding(top = 30.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = pages[index].title,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = pages[index].description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pageIndex) 22.dp else 8.dp, 8.dp)
                            .background(
                                color = if (index == pageIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                            .semantics {
                                contentDescription = "Page ${index + 1} of ${pages.size}"
                            }
                    )
                }
            }
            Text(
                text = "${pageIndex + 1} / ${pages.size}",
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            if (pageIndex == pages.lastIndex) {
                ArenaPrimaryButton(
                    text = "Get started",
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArenaSecondaryButton(
                        text = "Back",
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        modifier = Modifier.weight(1f),
                        enabled = pageIndex > 0
                    )
                    ArenaPrimaryButton(
                        text = "Next",
                        onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pages.lastIndex) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingArtwork(index: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ArenaPalette.Purple.copy(alpha = 0.26f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                ),
                shape = MaterialTheme.shapes.extraLarge
            ),
        contentAlignment = Alignment.Center
    ) {
        when (index) {
            0 -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArenaCardTile(
                        color = ArenaCardColor.RED,
                        value = 7,
                        width = 70.dp,
                        height = 108.dp,
                        modifier = Modifier.rotate(-14f)
                    )
                    ArenaCardTile(
                        color = ArenaCardColor.BLUE,
                        value = 3,
                        width = 78.dp,
                        height = 120.dp,
                        selected = true
                    )
                    ArenaCardTile(
                        color = ArenaCardColor.GREEN,
                        value = 7,
                        width = 70.dp,
                        height = 108.dp,
                        modifier = Modifier.rotate(14f)
                    )
                }
            }
            1 -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                        AvatarBadge("A", ArenaPalette.Purple)
                        AvatarBadge("J", ArenaPalette.Blue)
                    }
                    Text(
                        text = "FRIENDS AT THE TABLE",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                        AvatarBadge("M", ArenaPalette.Green)
                        AvatarBadge("K", ArenaPalette.Red)
                    }
                }
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .background(
                                Brush.linearGradient(listOf(ArenaPalette.Gold, Color(0xFFFFA62B))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = ArenaPalette.Navy,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "VIRTUAL COINS • NO CASH VALUE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = "Friend $label" },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
