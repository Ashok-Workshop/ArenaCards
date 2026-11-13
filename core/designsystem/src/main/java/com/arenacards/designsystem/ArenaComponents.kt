package com.arenacards.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ArenaCardColor { RED, BLUE, GREEN, YELLOW, WILD }

@Composable
fun ArenaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ArenaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ArenaPanel(
    modifier: Modifier = Modifier,
    backgroundBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (backgroundBrush == null) {
                MaterialTheme.colorScheme.surface
            } else {
                Color.Transparent
            }
        )
    ) {
        if (backgroundBrush == null) {
            Column(
                modifier = Modifier.padding(18.dp),
                content = content
            )
        } else {
            Box(
                modifier = Modifier
                    .background(backgroundBrush, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(content = content)
            }
        }
    }
}

@Composable
fun PlayerAvatar(
    initials: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    isActive: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "$name avatar${if (isActive) ", current turn" else ""}"
                selected = isActive
            },
        shape = CircleShape,
        color = if (isActive) ArenaPalette.Gold else backgroundColor,
        tonalElevation = if (isActive) 6.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials.take(2).uppercase(),
                color = if (isActive) ArenaPalette.Navy else Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CoinBalance(
    amount: String,
    modifier: Modifier = Modifier,
    label: String = "Virtual coins"
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "$label: $amount"
        },
        shape = RoundedCornerShape(50),
        color = ArenaPalette.Gold.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ArenaPalette.Gold, Color(0xFFFFA62B)))),
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = ArenaPalette.Navy, style = MaterialTheme.typography.labelSmall)
            }
            Text(amount, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ArenaCardTile(
    color: ArenaCardColor,
    value: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    width: Dp = 74.dp,
    height: Dp = 112.dp
) {
    val lift by animateDpAsState(if (selected) 12.dp else 0.dp, label = "card-selection-lift")
    val palette = cardPalette(color)
    val brush = if (color == ArenaCardColor.WILD) {
        Brush.linearGradient(
            listOf(ArenaPalette.Red, ArenaPalette.Yellow, ArenaPalette.Green, ArenaPalette.Blue),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(width.value, height.value)
        )
    } else {
        Brush.linearGradient(listOf(palette, palette.copy(alpha = 0.78f)))
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer { translationY = -lift.toPx() }
            .clip(RoundedCornerShape(17.dp))
            .background(brush)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = "${cardValueLabel(value)} ${color.name.lowercase()} card"
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(7.dp)
                .matchParentSize()
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.12f))
        )
        Text(
            text = cardValueLabel(value),
            modifier = Modifier.padding(4.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = color.name.take(1),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ArenaLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String = "Loading"
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ArenaInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "Got it"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun cardPalette(color: ArenaCardColor): Color = when (color) {
    ArenaCardColor.RED -> ArenaPalette.Red
    ArenaCardColor.BLUE -> ArenaPalette.Blue
    ArenaCardColor.GREEN -> ArenaPalette.Green
    ArenaCardColor.YELLOW -> ArenaPalette.Yellow
    ArenaCardColor.WILD -> ArenaPalette.Purple
}

private fun cardValueLabel(value: Int): String = when (value) {
    10 -> "SKIP"
    11 -> "REV"
    12 -> "+2"
    13 -> "WILD"
    14 -> "+4"
    else -> value.toString()
}
