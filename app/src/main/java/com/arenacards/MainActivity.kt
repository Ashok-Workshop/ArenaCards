package com.arenacards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arenacards.designsystem.ArenaCardColor
import com.arenacards.designsystem.ArenaCardTile
import com.arenacards.designsystem.ArenaCardsTheme
import com.arenacards.designsystem.ArenaInfoDialog
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.feature.auth.AuthMode
import com.arenacards.feature.auth.AuthScreen
import com.arenacards.feature.auth.AuthViewModel
import com.arenacards.feature.auth.AuthViewModelFactory
import com.arenacards.data.FirebaseAuthRepository
import com.arenacards.data.FirebasePlayerProfileRepository
import com.arenacards.feature.game.ArenaCardsGameScreen
import com.arenacards.feature.home.GameSelectionScreen
import com.arenacards.feature.home.HomeScreen
import com.arenacards.feature.onboarding.OnboardingScreen
import com.arenacards.feature.profile.PlayerProfileGateScreen
import com.arenacards.feature.profile.PlayerProfileScreen
import com.arenacards.feature.profile.PlayerProfileViewModel
import com.arenacards.feature.profile.PlayerProfileViewModelFactory
import com.arenacards.navigation.ArenaCardsDestinations
import kotlinx.coroutines.delay

private const val PREFERENCES_NAME = "arena_cards_preferences"
private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"

private data class InfoMessage(val title: String, val body: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArenaCardsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArenaCardsApp()
                }
            }
        }
    }
}

@Composable
private fun ArenaCardsApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val applicationContext = context.applicationContext
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(applicationContext) {
            AuthViewModelFactory(FirebaseAuthRepository(applicationContext))
        }
    )
    val authState by authViewModel.uiState.collectAsState()
    val profileViewModel: PlayerProfileViewModel = viewModel(
        factory = remember {
            PlayerProfileViewModelFactory(FirebasePlayerProfileRepository())
        }
    )
    val profileState by profileViewModel.uiState.collectAsState()
    val activeProfile = profileState.profile?.takeIf {
        authState.isAuthenticated && it.uid == authState.user?.uid
    }
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }
    var infoMessage by remember { mutableStateOf<InfoMessage?>(null) }

    NavHost(
        navController = navController,
        startDestination = ArenaCardsDestinations.SPLASH,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(ArenaCardsDestinations.SPLASH) {
            SplashScreen()
            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS)
                val destination = if (preferences.getBoolean(ONBOARDING_COMPLETE_KEY, false)) {
                    when {
                        authState.isAuthenticated -> ArenaCardsDestinations.PROFILE_GATE
                        authState.needsEmailVerification -> ArenaCardsDestinations.auth("verify-email")
                        else -> ArenaCardsDestinations.HOME
                    }
                } else {
                    ArenaCardsDestinations.ONBOARDING
                }
                navController.navigate(destination) {
                    popUpTo(ArenaCardsDestinations.SPLASH) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable(ArenaCardsDestinations.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    preferences.edit().putBoolean(ONBOARDING_COMPLETE_KEY, true).apply()
                    navController.navigate(
                        if (authState.isAuthenticated) {
                            ArenaCardsDestinations.PROFILE_GATE
                        } else {
                            ArenaCardsDestinations.HOME
                        }
                    ) {
                        popUpTo(ArenaCardsDestinations.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ArenaCardsDestinations.HOME) {
            HomeScreen(
                onPlayOnline = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.gameSelection("online"))
                    } else {
                        navController.navigate(
                            ArenaCardsDestinations.auth(
                                if (authState.needsEmailVerification) "verify-email" else "welcome"
                            )
                        )
                    }
                },
                onPlayOffline = {
                    navController.navigate(ArenaCardsDestinations.gameSelection("offline"))
                },
                onFriends = {
                    infoMessage = InfoMessage(
                        "Friends are coming later",
                        "Friend lists, invitations, and presence are not connected in Phase 2A."
                    )
                },
                onDailyRewards = {
                    infoMessage = InfoMessage(
                        "Rewards are a preview",
                        "The balance and reward tiles are placeholders. No coins are granted or stored by this UI."
                    )
                },
                onLeaderboard = {
                    infoMessage = InfoMessage(
                        "Leaderboard unavailable",
                        "Player rankings require account and trusted backend services that are not part of this phase."
                    )
                },
                onProfile = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.PROFILE)
                    } else {
                        navController.navigate(ArenaCardsDestinations.auth("welcome"))
                    }
                },
                onSettings = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.auth("account-links"))
                    } else {
                        infoMessage = InfoMessage(
                            "Settings are coming later",
                            "Sound, accessibility, and account settings will be wired in a future phase."
                        )
                    }
                },
                onHelpSupport = {
                    infoMessage = InfoMessage(
                        "Help and support",
                        "Support content and contact options will be added in a future update."
                    )
                },
                onSignIn = {
                    navController.navigate(ArenaCardsDestinations.auth("welcome"))
                },
                onSignOut = authViewModel::signOut,
                playerName = activeProfile?.displayName ?: authState.user?.displayName,
                playerUsername = activeProfile?.username,
                playerGamerId = activeProfile?.gamerId,
                playerAvatarId = activeProfile?.avatarId,
                isAuthenticated = authState.isAuthenticated
            )
        }
        composable(ArenaCardsDestinations.PROFILE_GATE) {
            val uid = authState.user?.uid
            if (authState.isAuthenticated && uid != null) {
                PlayerProfileGateScreen(
                    uid = uid,
                    viewModel = profileViewModel,
                    onProfileFound = {
                        navController.navigate(ArenaCardsDestinations.HOME) {
                            popUpTo(ArenaCardsDestinations.PROFILE_GATE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onProfileRequired = {
                        navController.navigate(ArenaCardsDestinations.PROFILE) {
                            popUpTo(ArenaCardsDestinations.PROFILE_GATE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.HOME) {
                        popUpTo(ArenaCardsDestinations.PROFILE_GATE) { inclusive = true }
                    }
                }
            }
        }
        composable(ArenaCardsDestinations.PROFILE) {
            val uid = authState.user?.uid
            if (authState.isAuthenticated && uid != null) {
                PlayerProfileScreen(
                    uid = uid,
                    authDisplayName = authState.user?.displayName,
                    viewModel = profileViewModel,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(ArenaCardsDestinations.HOME) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onContinueHome = {
                        navController.navigate(ArenaCardsDestinations.HOME) {
                            popUpTo(ArenaCardsDestinations.PROFILE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                        popUpTo(ArenaCardsDestinations.PROFILE) { inclusive = true }
                    }
                }
            }
        }
        composable(
            route = ArenaCardsDestinations.GAME_SELECTION_PATTERN,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            val online = entry.arguments?.getString("mode") == "online"
            GameSelectionScreen(
                online = online,
                onBack = { navController.popBackStack() },
                onStartOfflineGame = { players ->
                    navController.navigate(ArenaCardsDestinations.game(players))
                }
            )
        }
        composable(
            route = ArenaCardsDestinations.AUTH_PATTERN,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            val mode = when (entry.arguments?.getString("mode")) {
                "welcome" -> AuthMode.WELCOME
                "register" -> AuthMode.REGISTER
                "forgot-password" -> AuthMode.FORGOT_PASSWORD
                "verify-email" -> AuthMode.VERIFY_EMAIL
                "phone" -> AuthMode.PHONE
                "link-phone" -> AuthMode.LINK_PHONE
                "account-links" -> AuthMode.ACCOUNT_LINKS
                else -> AuthMode.LOGIN
            }
            AuthScreen(
                mode = mode,
                viewModel = authViewModel,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(ArenaCardsDestinations.HOME) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenWelcome = { navController.navigate(ArenaCardsDestinations.auth("welcome")) },
                onOpenVerifyEmail = {
                    navController.navigate(ArenaCardsDestinations.auth("verify-email"))
                },
                onOpenLogin = { navController.navigate(ArenaCardsDestinations.auth("login")) },
                onOpenRegister = { navController.navigate(ArenaCardsDestinations.auth("register")) },
                onOpenForgotPassword = {
                    navController.navigate(ArenaCardsDestinations.auth("forgot-password"))
                },
                onOpenPhone = { navController.navigate(ArenaCardsDestinations.auth("phone")) },
                onOpenPhoneLink = {
                    navController.navigate(ArenaCardsDestinations.auth("link-phone"))
                },
                onAuthenticated = {
                    navController.navigate(ArenaCardsDestinations.PROFILE_GATE) {
                        navController.currentDestination?.id?.let { destinationId ->
                            popUpTo(destinationId) { inclusive = true }
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = ArenaCardsDestinations.GAME_PATTERN,
            arguments = listOf(navArgument("players") { type = NavType.IntType })
        ) { entry ->
            val players = entry.arguments?.getInt("players") ?: 2
            ArenaCardsGameScreen(
                playerCount = players,
                onExit = { navController.popBackStack() }
            )
        }
    }

    infoMessage?.let { message ->
        ArenaInfoDialog(
            title = message.title,
            message = message.body,
            onDismiss = { infoMessage = null }
        )
    }
}

@Composable
private fun SplashScreen() {
    var animationStarted by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.72f,
        animationSpec = tween(durationMillis = 800),
        label = "splash-logo-scale"
    )
    LaunchedEffect(Unit) {
        animationStarted = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ArenaPalette.Navy, Color(0xFF171E3A), ArenaPalette.Navy)
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RowOfCards(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        Text(
            text = "ARENACARDS",
            modifier = Modifier.padding(top = 28.dp),
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "A seat at every table.",
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun RowOfCards(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArenaCardTile(
            color = ArenaCardColor.RED,
            value = 4,
            width = 64.dp,
            height = 94.dp,
            modifier = Modifier.graphicsLayer { rotationZ = -16f }
        )
        ArenaCardTile(
            color = ArenaCardColor.WILD,
            value = 13,
            width = 70.dp,
            height = 104.dp,
            selected = true
        )
        ArenaCardTile(
            color = ArenaCardColor.BLUE,
            value = 8,
            width = 64.dp,
            height = 94.dp,
            modifier = Modifier.graphicsLayer { rotationZ = 16f }
        )
    }
}

private const val SPLASH_DURATION_MS = 1_500L
