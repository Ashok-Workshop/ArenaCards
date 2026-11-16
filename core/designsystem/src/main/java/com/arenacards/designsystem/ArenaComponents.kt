package com.arenacards.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor

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
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(ArenaShapeTokens.Button),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 5.dp,
            pressedElevation = 1.dp,
            disabledElevation = 0.dp
        ),
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
        modifier = modifier.defaultMinSize(minHeight = 54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(ArenaShapeTokens.Button)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ArenaPanel(
    modifier: Modifier = Modifier,
    backgroundBrush: Brush? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(ArenaShapeTokens.Panel))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        ArenaPalette.Gold.copy(alpha = 0.10f)
                    )
                ),
                shape = RoundedCornerShape(ArenaShapeTokens.Panel)
            ),
        shape = RoundedCornerShape(ArenaShapeTokens.Panel),
        colors = CardDefaults.cardColors(
            containerColor = if (backgroundBrush == null) {
                MaterialTheme.colorScheme.surface
            } else {
                Color.Transparent
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp, pressedElevation = 3.dp)
    ) {
        if (backgroundBrush == null) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    content = content
                )
            }
        } else {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Box(
                    modifier = Modifier
                        .background(backgroundBrush, RoundedCornerShape(ArenaShapeTokens.Panel))
                        .padding(18.dp)
                ) {
                    Column(content = content)
                }
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
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    avatarId: String? = null
) {
    val variant = avatarId ?: name.ifBlank { initials }
    val colors = avatarPalette(variant, backgroundColor)
    Surface(
        modifier = modifier
            .size(size)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) ArenaPalette.Gold else Color.White.copy(alpha = 0.28f),
                shape = CircleShape
            )
            .semantics {
                contentDescription = "$name avatar${if (isActive) ", current turn" else ""}"
                selected = isActive
            },
        shape = CircleShape,
        color = backgroundColor,
        tonalElevation = if (isActive) 4.dp else 0.dp
    ) {
        Canvas(Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.first, colors.second),
                    center = Offset(this.size.width * 0.32f, this.size.height * 0.24f),
                    radius = radius * 1.55f
                )
            )
            drawCircle(color = Color.White.copy(alpha = 0.15f), radius = radius * 0.78f, style = Stroke(width = radius * 0.035f))
            drawAvatarEmblem(variant.lowercase(), center, radius)
        }
    }
}

@Composable
fun CoinBalance(
    amount: String,
    modifier: Modifier = Modifier,
    label: String = "Virtual coins",
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = "$label: $amount" },
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
    height: Dp = 112.dp,
    reducedMotion: Boolean = false,
    dropTargetBounds: Rect? = null,
    onDrop: (() -> Unit)? = null,
    onDragStateChanged: ((Boolean) -> Unit)? = null
) {
    val lift by animateDpAsState(
        if (selected && !reducedMotion) 9.dp else 0.dp,
        label = "card-selection-lift"
    )
    val tilt by animateFloatAsState(
        if (selected && !reducedMotion) -4f else 0f,
        label = "card-selection-tilt"
    )
    val twist by animateFloatAsState(
        if (selected && !reducedMotion) -1.5f else 0f,
        label = "card-selection-twist"
    )
    val scale by animateFloatAsState(
        if (selected && !reducedMotion) 1.025f else 1f,
        label = "card-selection-scale"
    )
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragPositionInRoot by remember { mutableStateOf<Offset?>(null) }
    val draggingScale by animateFloatAsState(
        if (isDragging && !reducedMotion) 1.1f else scale,
        label = "card-drag-scale"
    )
    val latestOnDrop by rememberUpdatedState(onDrop)
    val latestOnDragStateChanged by rememberUpdatedState(onDragStateChanged)
    val latestDropTargetBounds by rememberUpdatedState(dropTargetBounds)
    val palette = cardPalette(color)
    val ink = if (color == ArenaCardColor.YELLOW || color == ArenaCardColor.GREEN) {
        ArenaPalette.Navy
    } else {
        Color.White
    }
    // A porcelain face with a saturated original ArenaCards suit panel reads
    // clearly on both the felt table and a compact hand carousel.
    val brush = Brush.linearGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFE8EDF6), Color(0xFFDCE4F1)),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(width.value, height.value)
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .onGloballyPositioned { layoutCoordinates = it }
            .pointerInput(enabled, onDrop != null) {
                if (onDrop != null && enabled) {
                    detectDragGestures(
                        onDragStart = { position ->
                            isDragging = true
                            dragPositionInRoot = layoutCoordinates?.localToRoot(position)
                            latestOnDragStateChanged?.invoke(true)
                        },
                        onDragEnd = {
                            val dropPosition = dragPositionInRoot
                            if (dropPosition != null && latestDropTargetBounds?.contains(dropPosition) == true) {
                                latestOnDrop?.invoke()
                            }
                            isDragging = false
                            dragOffset = Offset.Zero
                            dragPositionInRoot = null
                            latestOnDragStateChanged?.invoke(false)
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = Offset.Zero
                            dragPositionInRoot = null
                            latestOnDragStateChanged?.invoke(false)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            dragPositionInRoot = layoutCoordinates?.localToRoot(change.position)
                        }
                    )
                }
            }
            .graphicsLayer {
                translationY = -lift.toPx()
                translationX = dragOffset.x
                translationY += dragOffset.y
                alpha = if (enabled) 1f else 0.62f
                // Live 3D response while the player lifts and moves a card.
                // The effect is bounded so the face remains readable on small phones.
                rotationX = if (reducedMotion) 0f else
                    (-(dragOffset.y / height.toPx().coerceAtLeast(1f)) * 18f).coerceIn(-10f, 10f)
                rotationY = if (reducedMotion) 0f else
                    (tilt + (dragOffset.x / width.toPx().coerceAtLeast(1f)) * 18f).coerceIn(-14f, 14f)
                rotationZ = twist
                scaleX = draggingScale
                scaleY = draggingScale
                shadowElevation = if (isDragging && !reducedMotion) 26.dp.toPx() else if (selected) 16.dp.toPx() else 6.dp.toPx()
                cameraDistance = 22 * density
            }
            .shadow(if (selected) 12.dp else 5.dp, RoundedCornerShape(ArenaShapeTokens.Card))
            .clip(RoundedCornerShape(ArenaShapeTokens.Card))
            .background(brush)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    selected -> ArenaPalette.Gold
                    enabled -> Color.White.copy(alpha = 0.94f)
                    else -> Color.White.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(ArenaShapeTokens.Card)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = buildString {
                    append("${cardSpokenLabel(value)} ${color.name.lowercase()} card")
                    if (onClick != null && enabled) append(", playable")
                    if (selected) append(", selected")
                }
                this.selected = selected
                if (!enabled && onClick != null) disabled()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val inset = size.minDimension * 0.075f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.44f
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.13f)
            )
        }
        Box(
            modifier = Modifier
                .width(width * 0.66f)
                .height(height * 0.58f)
                .rotate(-18f)
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(palette, palette.copy(alpha = 0.78f))))
        )
        if (color == ArenaCardColor.WILD) {
            ArenaWildGlyph()
        }
        Text(
            text = cardCenterGlyph(value),
            modifier = Modifier.padding(horizontal = 5.dp),
            color = ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = cardValueLabel(value),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 7.dp, top = 5.dp),
            color = ink,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = cardValueLabel(value),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 7.dp, bottom = 5.dp)
                .graphicsLayer { rotationZ = 180f },
            color = ink,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ArenaCardReveal(
    color: ArenaCardColor,
    value: Int,
    eventId: String,
    modifier: Modifier = Modifier,
    width: Dp = 74.dp,
    height: Dp = 112.dp,
    reducedMotion: Boolean = false
) {
    val rotation = remember { Animatable(0f) }
    var previousEventId by remember { mutableStateOf(eventId) }
    LaunchedEffect(eventId, reducedMotion) {
        if (reducedMotion) {
            previousEventId = eventId
            rotation.snapTo(360f)
        } else if (eventId != previousEventId) {
            previousEventId = eventId
            rotation.snapTo(180f)
            rotation.animateTo(360f, animationSpec = tween(durationMillis = 420))
        }
    }
    val isBack = rotation.value % 360f in 90f..270f
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 32 * density
            },
        contentAlignment = Alignment.Center
    ) {
        if (isBack) {
            Box(
                modifier = Modifier.graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center
            ) {
                ArenaCardBack(width = width, height = height)
            }
        } else {
            Box(modifier = Modifier.graphicsLayer { rotationY = 0f }) {
                ArenaCardTile(
                    color = color,
                    value = value,
                    width = width,
                    height = height,
                    reducedMotion = true
                )
            }
        }
    }
}

@Composable
fun ArenaTableBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Static procedural lighting gives the table depth without a continuous
    // animation loop, keeping rendering friendly to lower-powered devices.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val outer = if (isDark) Color(0xFF070D1B) else Color(0xFF122A28)
    val mid = if (isDark) Color(0xFF101C32) else Color(0xFF1A5143)
    val felt = if (isDark) Color(0xFF123B3B) else Color(0xFF26745C)
    val gold = Color(0xFFE8BC67)
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to outer,
                        0.34f to mid,
                        0.58f to felt,
                        0.82f to mid,
                        1f to outer
                    )
                )
            )
            .drawBehind {
                val center = Offset(size.width * 0.5f, size.height * 0.43f)
                val maxRadius = size.maxDimension * 0.78f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4CD6B0).copy(alpha = if (isDark) 0.17f else 0.12f),
                            Color(0xFF1B8270).copy(alpha = 0.07f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                val left = size.width * 0.035f
                val top = size.height * 0.13f
                val tableWidth = size.width * 0.93f
                val tableHeight = size.height * 0.63f
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(gold.copy(alpha = 0.30f), Color(0xFF68D7BB).copy(alpha = 0.10f), gold.copy(alpha = 0.16f)),
                        startY = top,
                        endY = top + tableHeight
                    ),
                    topLeft = Offset(left, top),
                    size = Size(tableWidth, tableHeight),
                    style = Stroke(width = 7.dp.toPx())
                )
                val inset = 9.dp.toPx()
                drawOval(
                    color = Color.White.copy(alpha = 0.13f),
                    topLeft = Offset(left + inset, top + inset),
                    size = Size(tableWidth - inset * 2f, tableHeight - inset * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )

                val markerY = size.height * 0.445f
                listOf(0.18f, 0.27f, 0.73f, 0.82f).forEach { fraction ->
                    drawCircle(
                        color = gold.copy(alpha = 0.32f),
                        radius = 2.dp.toPx(),
                        center = Offset(size.width * fraction, markerY)
                    )
                }

                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(outer.copy(alpha = 0.72f), Color.Transparent, Color.Transparent, outer.copy(alpha = 0.72f))
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(outer.copy(alpha = 0.48f), Color.Transparent, Color.Transparent, outer.copy(alpha = 0.62f))
                    )
                )
            },
        content = content
    )
}

@Composable
private fun ArenaWildGlyph() {
    Canvas(modifier = Modifier.size(42.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.44f
        val colors = listOf(ArenaPalette.Purple, ArenaPalette.Blue, ArenaPalette.Green, ArenaPalette.Gold)
        colors.forEachIndexed { index, color ->
            val angle = Math.toRadians(index * 90.0 - 45.0)
            val tipX = centerX + kotlin.math.cos(angle).toFloat() * radius
            val tipY = centerY + kotlin.math.sin(angle).toFloat() * radius
            val sideX = kotlin.math.cos(angle + Math.PI / 2).toFloat() * radius * 0.42f
            val sideY = kotlin.math.sin(angle + Math.PI / 2).toFloat() * radius * 0.42f
            val path = Path().apply {
                moveTo(centerX, centerY)
                lineTo(tipX + sideX, tipY + sideY)
                lineTo(tipX, tipY)
                lineTo(tipX - sideX, tipY - sideY)
                close()
            }
            drawPath(path, color)
        }
        drawCircle(Color.White, radius = size.minDimension * 0.1f)
    }
}

@Composable
fun ArenaCardBack(
    modifier: Modifier = Modifier,
    width: Dp = 74.dp,
    height: Dp = 112.dp
) {
    val shape = RoundedCornerShape(ArenaShapeTokens.Card)
    Box(
        modifier = modifier
            .size(width, height)
            .shadow(9.dp, shape)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF263D78), Color(0xFF111B36), Color(0xFF202E59))
                )
            )
            .border(
                width = 1.4.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFE4A1), Color.White.copy(alpha = 0.72f), Color(0xFF8DABEF))
                ),
                shape = shape
            )
            .drawBehind {
                // Fine radial glints and inset frame make the reverse side
                // recognisable without adding a bitmap or expensive effects.
                val inset = size.minDimension * 0.085f
                drawRoundRect(
                    color = Color(0xFFFFD477).copy(alpha = 0.72f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f),
                    style = Stroke(width = 1.dp.toPx())
                )
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF8DABEF).copy(alpha = 0.24f), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.62f
                    ),
                    radius = size.minDimension * 0.62f,
                    center = center
                )
                for (index in 0 until 4) {
                    val x = size.width * (0.22f + index * 0.19f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.48f),
                        radius = 1.dp.toPx(),
                        center = Offset(x, size.height * 0.17f)
                    )
                    drawCircle(
                        color = Color(0xFFFFD477).copy(alpha = 0.55f),
                        radius = 1.dp.toPx(),
                        center = Offset(x, size.height * 0.83f)
                    )
                }
            }
            .semantics { contentDescription = "ArenaCards premium card back" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .padding(7.dp)
                .matchParentSize()
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(10.dp)
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ArenaCardsLogoMark(Modifier.size(width * 0.58f))
            Text(
                text = "ARENA",
                color = Color(0xFFFFE5A8),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
fun ArenaCardsLogoMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "ArenaCards original four-shard emblem"
) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        }
    ) {
        val inset = size.minDimension * 0.06f
        val corner = size.minDimension * 0.24f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF273D7B), Color(0xFF101A35))),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = Stroke(width = size.minDimension * 0.055f)
        )
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.36f
        val colors = listOf(ArenaPalette.Purple, ArenaPalette.Blue, ArenaPalette.Green, ArenaPalette.Gold)
        colors.forEachIndexed { index, color ->
            val angle = Math.toRadians(index * 90.0 - 45.0)
            val tipX = centerX + kotlin.math.cos(angle).toFloat() * radius
            val tipY = centerY + kotlin.math.sin(angle).toFloat() * radius
            val sideX = kotlin.math.cos(angle + Math.PI / 2).toFloat() * radius * 0.34f
            val sideY = kotlin.math.sin(angle + Math.PI / 2).toFloat() * radius * 0.34f
            val path = Path().apply {
                moveTo(centerX, centerY)
                lineTo(tipX + sideX, tipY + sideY)
                lineTo(tipX, tipY)
                lineTo(tipX - sideX, tipY - sideY)
                close()
            }
            drawPath(path, color)
        }
        drawCircle(Color.White, radius = size.minDimension * 0.045f)
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

/**
 * Original ArenaCards collectible card face. Pointer tilt is bounded and
 * springs back to neutral; vector artwork stays crisp at every screen density.
 */
@Composable
fun ArenaPremiumCard(
    color: ArenaCardColor,
    value: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    reducedMotion: Boolean = false,
    width: Dp = 104.dp,
    height: Dp = 154.dp
) {
    var pointerTiltX by remember { mutableStateOf(0f) }
    var pointerTiltY by remember { mutableStateOf(0f) }
    val tiltX by animateFloatAsState(if (reducedMotion) 0f else pointerTiltX, animationSpec = tween(160), label = "premium-card-tilt-x")
    val tiltY by animateFloatAsState(if (reducedMotion) 0f else pointerTiltY, animationSpec = tween(160), label = "premium-card-tilt-y")
    val lift by animateDpAsState(if (selected && !reducedMotion) 10.dp else 0.dp, label = "premium-card-lift")
    val accent = cardPalette(color)
    val shape = RoundedCornerShape(18.dp)
    val label = cardValueLabel(value)
    val glyph = cardCenterGlyph(value)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                translationY = -lift.toPx()
                cameraDistance = 26f * density
                shadowElevation = if (selected) 22.dp.toPx() else 12.dp.toPx()
                this.shape = shape
                clip = true
            }
            .pointerInput(reducedMotion) {
                if (!reducedMotion) {
                    detectDragGestures(
                        onDragEnd = { pointerTiltX = 0f; pointerTiltY = 0f },
                        onDragCancel = { pointerTiltX = 0f; pointerTiltY = 0f }
                    ) { change, drag ->
                        change.consume()
                        pointerTiltY = (pointerTiltY + drag.x * 0.22f).coerceIn(-13f, 13f)
                        pointerTiltX = (pointerTiltX - drag.y * 0.22f).coerceIn(-13f, 13f)
                    }
                }
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFCFDFF), Color(0xFFE8EDF7))))
            .border(
                width = if (selected) 2.dp else 1.dp,
                brush = Brush.linearGradient(listOf(Color.White, accent.copy(alpha = 0.72f), Color(0xFF9BA9C1))),
                shape = shape
            )
            .semantics {
                contentDescription = "${cardSpokenLabel(value)} ${color.name.lowercase()} ArenaCards card"
                this.selected = selected
            }
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.62f), Color.Transparent, accent.copy(alpha = 0.10f)))
            )
        )
        Box(
            Modifier.align(Alignment.Center).size(width * 0.78f, height * 0.66f)
                .graphicsLayer { rotationZ = -24f }
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.82f))))
        )
        Text(
            label,
            modifier = Modifier.align(Alignment.Center),
            color = if (color == ArenaCardColor.YELLOW || color == ArenaCardColor.GREEN) Color(0xFF10182A) else Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(label, modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            color = Color(0xFF172238), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
        Text(glyph, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            color = Color(0xFF172238), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        Box(
            Modifier.fillMaxWidth().height(20.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.58f), Color.Transparent)))
        )
    }
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

private fun cardSpokenLabel(value: Int): String = when (value) {
    10 -> "Skip"
    11 -> "Reverse"
    12 -> "Draw Two"
    13 -> "Wild"
    14 -> "Wild Draw Four"
    else -> value.toString()
}

private fun cardCenterGlyph(value: Int): String = when (value) {
    10 -> "⊘"
    11 -> "↻"
    12 -> "+2"
    13 -> "A"
    14 -> "+4"
    else -> value.toString()
}

private fun avatarPalette(avatarId: String, fallback: Color): Pair<Color, Color> = when {
    avatarId.contains("ember") -> Color(0xFFE9524A) to Color(0xFF7F2035)
    avatarId.contains("tide") -> Color(0xFF43B8E8) to Color(0xFF164C91)
    avatarId.contains("grove") -> Color(0xFF53C98A) to Color(0xFF145C54)
    avatarId.contains("sun") -> Color(0xFFFFCF5A) to Color(0xFFB6602D)
    avatarId.contains("comet") -> Color(0xFFB8A0FF) to Color(0xFF3A3C9C)
    avatarId.contains("crown") -> Color(0xFFFFD166) to Color(0xFF775032)
    avatarId.contains("spark") -> Color(0xFFFA8FC0) to Color(0xFF6D387F)
    avatarId.contains("wild") -> Color(0xFF8F56E8) to Color(0xFF185F86)
    else -> fallback to ArenaPalette.Navy
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAvatarEmblem(
    avatarId: String,
    center: Offset,
    radius: Float
) {
    val accent = Color.White.copy(alpha = 0.92f)
    val emblemRadius = radius * 0.43f
    when {
        avatarId.contains("ember") -> {
            val flame = Path().apply {
                moveTo(center.x, center.y - emblemRadius)
                cubicTo(center.x + emblemRadius * 0.1f, center.y - emblemRadius * 0.32f, center.x + emblemRadius, center.y - emblemRadius * 0.1f, center.x + emblemRadius * 0.58f, center.y + emblemRadius * 0.72f)
                cubicTo(center.x + emblemRadius * 0.22f, center.y + emblemRadius, center.x - emblemRadius * 0.64f, center.y + emblemRadius * 0.62f, center.x - emblemRadius * 0.44f, center.y - emblemRadius * 0.08f)
                cubicTo(center.x - emblemRadius * 0.26f, center.y + emblemRadius * 0.08f, center.x - emblemRadius * 0.1f, center.y - emblemRadius * 0.12f, center.x, center.y - emblemRadius)
            }
            drawPath(flame, accent)
        }
        avatarId.contains("tide") -> {
            val wave = Path().apply {
                moveTo(center.x - emblemRadius, center.y - emblemRadius * 0.18f)
                cubicTo(center.x - emblemRadius * 0.34f, center.y - emblemRadius * 0.9f, center.x + emblemRadius * 0.15f, center.y + emblemRadius * 0.58f, center.x + emblemRadius, center.y - emblemRadius * 0.18f)
                lineTo(center.x + emblemRadius, center.y + emblemRadius * 0.47f)
                cubicTo(center.x + emblemRadius * 0.15f, center.y + emblemRadius, center.x - emblemRadius * 0.32f, center.y - emblemRadius * 0.34f, center.x - emblemRadius, center.y + emblemRadius * 0.47f)
                close()
            }
            drawPath(wave, accent)
        }
        avatarId.contains("grove") -> {
            drawLine(accent, Offset(center.x, center.y + emblemRadius), Offset(center.x, center.y - emblemRadius * 0.42f), radius * 0.09f)
            drawOval(accent, Offset(center.x - emblemRadius, center.y - emblemRadius * 0.62f), Size(emblemRadius * 0.9f, emblemRadius * 0.48f))
            drawOval(accent, Offset(center.x + emblemRadius * 0.1f, center.y - emblemRadius * 0.12f), Size(emblemRadius * 0.9f, emblemRadius * 0.48f))
        }
        avatarId.contains("sun") -> {
            for (index in 0 until 8) {
                val angle = Math.PI * index / 4.0
                val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * emblemRadius * 0.72f, center.y + kotlin.math.sin(angle).toFloat() * emblemRadius * 0.72f)
                val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * emblemRadius, center.y + kotlin.math.sin(angle).toFloat() * emblemRadius)
                drawLine(accent, start, end, radius * 0.07f)
            }
            drawCircle(accent, emblemRadius * 0.48f, center)
        }
        avatarId.contains("comet") -> {
            drawLine(accent.copy(alpha = 0.65f), Offset(center.x - emblemRadius, center.y + emblemRadius * 0.7f), Offset(center.x + emblemRadius * 0.48f, center.y - emblemRadius * 0.62f), radius * 0.12f)
            drawCircle(accent, emblemRadius * 0.31f, Offset(center.x + emblemRadius * 0.43f, center.y - emblemRadius * 0.42f))
        }
        avatarId.contains("crown") -> {
            val crown = Path().apply {
                moveTo(center.x - emblemRadius, center.y + emblemRadius * 0.6f)
                lineTo(center.x - emblemRadius * 0.82f, center.y - emblemRadius * 0.36f)
                lineTo(center.x - emblemRadius * 0.24f, center.y + emblemRadius * 0.05f)
                lineTo(center.x, center.y - emblemRadius)
                lineTo(center.x + emblemRadius * 0.27f, center.y + emblemRadius * 0.05f)
                lineTo(center.x + emblemRadius * 0.84f, center.y - emblemRadius * 0.36f)
                lineTo(center.x + emblemRadius, center.y + emblemRadius * 0.6f)
                close()
            }
            drawPath(crown, accent)
        }
        avatarId.contains("spark") -> {
            val star = Path().apply {
                moveTo(center.x, center.y - emblemRadius)
                lineTo(center.x + emblemRadius * 0.22f, center.y - emblemRadius * 0.22f)
                lineTo(center.x + emblemRadius, center.y)
                lineTo(center.x + emblemRadius * 0.22f, center.y + emblemRadius * 0.22f)
                lineTo(center.x, center.y + emblemRadius)
                lineTo(center.x - emblemRadius * 0.22f, center.y + emblemRadius * 0.22f)
                lineTo(center.x - emblemRadius, center.y)
                lineTo(center.x - emblemRadius * 0.22f, center.y - emblemRadius * 0.22f)
                close()
            }
            drawPath(star, accent)
        }
        else -> {
            val colors = listOf(ArenaPalette.Purple, ArenaPalette.Blue, ArenaPalette.Green, ArenaPalette.Gold)
            colors.forEachIndexed { index, color ->
                val angle = Math.toRadians(index * 90.0 - 45.0)
                val tip = Offset(center.x + kotlin.math.cos(angle).toFloat() * emblemRadius, center.y + kotlin.math.sin(angle).toFloat() * emblemRadius)
                val side = Offset(kotlin.math.cos(angle + Math.PI / 2).toFloat() * emblemRadius * 0.34f, kotlin.math.sin(angle + Math.PI / 2).toFloat() * emblemRadius * 0.34f)
                drawPath(Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(tip.x + side.x, tip.y + side.y)
                    lineTo(tip.x, tip.y)
                    lineTo(tip.x - side.x, tip.y - side.y)
                    close()
                }, color)
            }
        }
    }
}
