package com.arenacards.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.CoinBalance
import com.arenacards.designsystem.PlayerAvatar

private val Night = Color(0xFF080D20)
private val Panel = Color(0xFF141D39)
private val PanelRaised = Color(0xFF202C52)
private val Violet = Color(0xFF9B72FF)
private val Cyan = Color(0xFF55D9F5)
private val Gold = Color(0xFFFFD36A)
private val Muted = Color(0xFFAAB7D5)

@Composable
fun HomeScreen(
    onPlayOnline: () -> Unit,
    onPlayOffline: () -> Unit,
    onFriends: () -> Unit,
    onNotifications: () -> Unit,
    onDailyRewards: () -> Unit,
    onWallet: () -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onHelpSupport: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    playerName: String?,
    playerUsername: String?,
    playerGamerId: String?,
    playerAvatarId: String?,
    isAuthenticated: Boolean,
    walletBalance: Int?,
    walletLoading: Boolean,
    playerLevel: Int?,
    experiencePoints: Long?,
    xpToNextLevel: Int?,
    dailyRewardCoins: Int?,
    dailyRewardAvailable: Boolean?,
    readyMissionCount: Int?
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Night, Color(0xFF111B39), Night))
        )
    ) {
        val wide = maxWidth >= 640.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArenaCardsLogoMark(Modifier.size(42.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("ARENA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("CARDS  •  MULTIPLAYER", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoinBalance(
                        amount = walletBalance?.toString() ?: "—",
                        label = when {
                            !isAuthenticated -> "Sign in for wallet"
                            walletBalance != null -> "Server wallet balance"
                            walletLoading -> "Wallet balance loading"
                            else -> "Wallet balance unavailable"
                        },
                        onClick = onWallet
                    )
                    Surface(
                        Modifier.size(44.dp).clickable(role = Role.Button, onClick = onProfile)
                            .semantics { contentDescription = "Open player profile" },
                        shape = CircleShape, color = PanelRaised,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("☺", color = Violet, fontSize = 23.sp) }
                    }
                }
            }

            // Player identity and progression hero
            Box(
                Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF29366D), Color(0xFF35265F), Color(0xFF142C50))))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(28.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(
                            initials = playerAvatarId?.take(1) ?: "G",
                            name = playerName ?: "Guest player",
                            size = 58.dp,
                            backgroundColor = Violet,
                            avatarId = playerAvatarId
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (isAuthenticated) "WELCOME BACK" else "WELCOME TO THE ARENA", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            Text(playerName ?: "Guest player", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    playerUsername != null && playerGamerId != null -> "@$playerUsername  •  $playerGamerId"
                                    playerUsername != null -> "@$playerUsername"
                                    isAuthenticated -> "Your player profile is ready"
                                    else -> "Jump in and claim your seat"
                                },
                                color = Color(0xFFD4DDF5), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = if (isAuthenticated) onProfile else onSignIn) {
                            Text(if (isAuthenticated) "PROFILE" else "JOIN", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("READY WHEN YOU ARE", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Pick a mode. Make your move. Own the table.", color = Color(0xFFD4DDF5), fontSize = 11.sp)
                        }
                        CardFan()
                    }
                    if (playerLevel != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("LEVEL $playerLevel", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text((experiencePoints ?: 0L).toString() + " XP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (experiencePoints != null && xpToNextLevel != null) {
                            val progress = ((1_000 - xpToNextLevel).coerceIn(0, 1_000)) / 1_000f
                            LinearProgressIndicator(
                                progress = { progress },
                                Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = Gold, trackColor = Color.White.copy(alpha = .18f)
                            )
                            Text("$xpToNextLevel XP TO NEXT LEVEL", color = Color(0xFFD4DDF5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isAuthenticated) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onSignOut) { Text("SIGN OUT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            Text("CHOOSE YOUR BATTLE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)

            // Featured game mode
            Box(
                Modifier.fillMaxWidth().heightIn(min = 178.dp).shadow(16.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF46295F), Color(0xFF222B58), Color(0xFF132C4B))))
                    .border(1.dp, Color(0xFFFF6684).copy(alpha = .48f), RoundedCornerShape(26.dp))
                    .clickable(role = Role.Button, onClick = onPlayOffline).padding(19.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Surface(shape = CircleShape, color = Color(0xFFFF6684).copy(alpha = .18f)) {
                            Text("MOST PLAYED", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color(0xFFFF91A5), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                        Text("CLASSIC", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("The original card battle", color = Color(0xFFD1D9EF), fontSize = 12.sp)
                        Surface(shape = CircleShape, color = Color(0xFFFF6684)) {
                            Text("PLAY NOW   →", Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = Night, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Box(Modifier.width(118.dp).height(126.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF6684).copy(alpha = .38f), Color.Transparent))))
                        MiniCard("7", Color(0xFFFF5474), Modifier.graphicsLayer { rotationZ = -17f }.padding(end = 46.dp, top = 14.dp))
                        MiniCard("+2", Color(0xFF4B9EFF), Modifier.graphicsLayer { rotationZ = 13f }.padding(start = 43.dp, bottom = 12.dp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameModeTile("ONLINE ARENA", "Play with the world", "◎", Cyan, Modifier.weight(1f), onPlayOnline)
                GameModeTile("PRACTICE", "Train vs CPU", "✦", Violet, Modifier.weight(1f), onPlayOffline)
            }

            Text("YOUR ARENA", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutTile("FRIENDS", "Crew & invites", "♟", Cyan, Modifier.weight(1f), onFriends)
                ShortcutTile("REWARDS", rewardSummary(isAuthenticated, dailyRewardCoins, dailyRewardAvailable, readyMissionCount), "✦", Gold, Modifier.weight(1f), onDailyRewards)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutTile("LEADERBOARD", "Ranks & events", "↗", Violet, Modifier.weight(1f), onLeaderboard)
                ShortcutTile("NOTIFICATIONS", "Invites & updates", "◌", Cyan, Modifier.weight(1f), onNotifications)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutTile("SETTINGS", "Audio & display", "⚙", Muted, Modifier.weight(1f), onSettings)
                ShortcutTile("HELP & SUPPORT", "Get assistance", "?", Violet, Modifier.weight(1f), onHelpSupport)
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Panel) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isAuthenticated) "YOUR ARENA ID" else "SAVE YOUR PROGRESS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text(
                            if (isAuthenticated) playerGamerId ?: playerUsername?.let { "@$it" } ?: "Complete your profile"
                            else "Sign in to keep your stats, friends and rewards.",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = if (isAuthenticated) onProfile else onSignIn) {
                        Text(if (isAuthenticated) "PROFILE" else "SIGN IN", color = Cyan, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CardFan() {
    Row(Modifier.width(116.dp).height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
        MiniCard("4", Color(0xFFFF5474), Modifier.graphicsLayer { rotationZ = -13f }.padding(top = 5.dp))
        MiniCard("6", Color(0xFFFFC84D), Modifier.padding(bottom = 5.dp))
        MiniCard("8", Color(0xFF48BDF5), Modifier.graphicsLayer { rotationZ = 13f }.padding(top = 5.dp))
    }
}

@Composable
private fun MiniCard(value: String, color: Color, modifier: Modifier = Modifier) {
    // Lightweight 3D-inspired idle motion: perspective tilt and a slow floating lift.
    val motion = rememberInfiniteTransition(label = "arena-card-$value")
    val tilt by motion.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardTilt"
    )
    val lift by motion.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardLift"
    )
    Box(
        modifier
            .graphicsLayer {
                rotationX = tilt
                rotationY = -tilt * .7f
                translationY = lift * density
                cameraDistance = 18f * density
            }
            .size(39.dp, 59.dp)
            .shadow(9.dp, RoundedCornerShape(9.dp))
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = .78f))))
            .border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .22f), Color.Transparent, Color.Black.copy(alpha = .08f))
                )
            )
        )
        Text(value, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun GameModeTile(title: String, subtitle: String, symbol: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.heightIn(min = 116.dp).clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel), elevation = CardDefaults.cardElevation(5.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(symbol, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ShortcutTile(title: String, subtitle: String, symbol: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier.heightIn(min = 88.dp).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(18.dp), color = Panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .05f))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(symbol, color = accent, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun rewardSummary(isAuthenticated: Boolean, dailyRewardCoins: Int?, dailyRewardAvailable: Boolean?, readyMissionCount: Int?): String = when {
    !isAuthenticated -> "Sign in to unlock"
    readyMissionCount != null && readyMissionCount > 0 -> readyMissionCount.toString() + if (readyMissionCount == 1) " mission ready" else " missions ready"
    dailyRewardAvailable == true && dailyRewardCoins != null -> "Claim " + dailyRewardCoins + " coins"
    dailyRewardAvailable == true -> "Daily reward ready"
    else -> "Missions & achievements"
}
