package com.arenacards

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import com.arenacards.data.FirebaseWalletRepository
import com.arenacards.data.FirebaseMultiplayerRepository
import com.arenacards.data.FirebaseSocialRepository
import com.arenacards.data.FirebaseCompetitiveRepository
import com.google.firebase.auth.FirebaseAuth
import com.arenacards.feature.game.ArenaCardsGameScreen
import com.arenacards.feature.game.GameFeedback
import com.arenacards.feature.game.PracticeHistoryScreen
import com.arenacards.feature.home.GameSelectionScreen
import com.arenacards.feature.home.HomeScreen
import com.arenacards.feature.onboarding.OnboardingScreen
import com.arenacards.feature.profile.PlayerProfileGateScreen
import com.arenacards.feature.profile.PlayerProfileScreen
import com.arenacards.feature.profile.PlayerProfileViewModel
import com.arenacards.feature.profile.PlayerProfileViewModelFactory
import com.arenacards.feature.settings.ArenaSettings
import com.arenacards.feature.settings.ArenaSettingsScreen
import com.arenacards.feature.settings.ArenaThemePreference
import com.arenacards.feature.wallet.WalletScreen
import com.arenacards.feature.wallet.WalletViewModel
import com.arenacards.feature.wallet.WalletViewModelFactory
import com.arenacards.feature.multiplayer.MultiplayerFeedback
import com.arenacards.feature.multiplayer.MultiplayerScreen
import com.arenacards.feature.multiplayer.MultiplayerViewModel
import com.arenacards.feature.multiplayer.MultiplayerViewModelFactory
import com.arenacards.feature.friends.FriendsScreen
import com.arenacards.feature.friends.FriendsViewModel
import com.arenacards.feature.friends.FriendsViewModelFactory
import com.arenacards.feature.chat.ChatScreen
import com.arenacards.feature.chat.ChatViewModel
import com.arenacards.feature.chat.ChatViewModelFactory
import com.arenacards.feature.notifications.NotificationsScreen
import com.arenacards.feature.notifications.NotificationsViewModel
import com.arenacards.feature.notifications.NotificationsViewModelFactory
import com.arenacards.feature.leaderboard.LeaderboardScreen
import com.arenacards.feature.leaderboard.LeaderboardViewModel
import com.arenacards.feature.leaderboard.LeaderboardViewModelFactory
import com.arenacards.navigation.ArenaCardsDestinations
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

private const val PREFERENCES_NAME = "arena_cards_preferences"
private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"

private data class InfoMessage(val title: String, val body: String)

class MainActivity : ComponentActivity() {
    private var shouldOpenNotifications by mutableStateOf(false)
    private val socialRepository by lazy { FirebaseSocialRepository() }
    private var presenceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shouldOpenNotifications = intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)
        val localStore = ArenaLocalStore(applicationContext)
        setContent {
            val settings by localStore.settings.collectAsStateWithLifecycle(initialValue = ArenaSettings())
            val feedback = remember { ArenaFeedbackController(settings) }
            SideEffect { feedback.updateSettings(settings) }
            DisposableEffect(feedback) {
                onDispose { feedback.release() }
            }
            val darkTheme = when (settings.theme) {
                ArenaThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ArenaThemePreference.LIGHT -> false
                ArenaThemePreference.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(LocalArenaFeedback provides feedback) {
                ArenaCardsTheme(darkTheme = darkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ArenaCardsApp(
                            localStore = localStore,
                            settings = settings,
                            openNotifications = shouldOpenNotifications,
                            onNotificationsOpened = { shouldOpenNotifications = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shouldOpenNotifications = intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)
    }

    override fun onStart() {
        super.onStart()
        presenceJob = lifecycleScope.launch {
            while (isActive) {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    try {
                        socialRepository.updatePresence(foreground = true)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e("ArenaCardsPresence", "Presence heartbeat failed.", error)
                    }
                }
                delay(PRESENCE_HEARTBEAT_MILLIS)
            }
        }
    }

    override fun onStop() {
        presenceJob?.cancel()
        presenceJob = null
        if (FirebaseAuth.getInstance().currentUser != null) {
            lifecycleScope.launch {
                try {
                    socialRepository.updatePresence(foreground = false)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("ArenaCardsPresence", "Presence background update failed.", error)
                }
            }
        }
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "com.arenacards.OPEN_NOTIFICATIONS"
        private const val PRESENCE_HEARTBEAT_MILLIS = 60_000L
    }
}

@Composable
private fun ArenaCardsApp(
    localStore: ArenaLocalStore,
    settings: ArenaSettings,
    openNotifications: Boolean,
    onNotificationsOpened: () -> Unit
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val feedback = LocalArenaFeedback.current
    val applicationContext = context.applicationContext
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(applicationContext) {
            AuthViewModelFactory(FirebaseAuthRepository(applicationContext))
        }
    )
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val walletRepository = remember { FirebaseWalletRepository() }
    val multiplayerRepository = remember { FirebaseMultiplayerRepository() }
    val socialRepository = remember { FirebaseSocialRepository() }
    val competitiveRepository = remember { FirebaseCompetitiveRepository() }
    val walletViewModel: WalletViewModel = viewModel(
        factory = remember(walletRepository) {
            WalletViewModelFactory(walletRepository)
        }
    )
    val walletState by walletViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(authState.user?.uid) {
        if (authState.isAuthenticated && authState.user?.uid != null) {
            walletViewModel.refresh()
        } else {
            walletViewModel.clearForSignOut()
        }
    }
    val profileViewModel: PlayerProfileViewModel = viewModel(
        factory = remember {
            PlayerProfileViewModelFactory(FirebasePlayerProfileRepository())
        }
    )
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val activeProfile = profileState.profile?.takeIf {
        authState.isAuthenticated && it.uid == authState.user?.uid
    }
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }
    var infoMessage by remember { mutableStateOf<InfoMessage?>(null) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun registerPushToken() {
        try {
            socialRepository.registerCurrentNotificationToken(applicationContext)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            infoMessage = InfoMessage(
                "Push registration failed",
                error.message ?: "This device could not register for push notifications."
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch { registerPushToken() }
        } else {
            infoMessage = InfoMessage(
                "Notifications are off",
                "You can still read ArenaCards activity in the in-app notification center."
            )
        }
    }

    LaunchedEffect(authState.user?.uid) {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (
            authState.isAuthenticated
            && permissionGranted
            && NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            registerPushToken()
        }
        if (authState.isAuthenticated) {
            try {
                socialRepository.updatePresence(foreground = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ArenaCardsPresence", "Initial presence update failed.", error)
            }
        }
    }

    LaunchedEffect(openNotifications, authState.isAuthenticated) {
        if (openNotifications && authState.isAuthenticated) {
            navController.navigate(ArenaCardsDestinations.NOTIFICATIONS) {
                launchSingleTop = true
            }
            onNotificationsOpened()
        }
    }

    NavHost(
        navController = navController,
        startDestination = ArenaCardsDestinations.SPLASH,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(ArenaCardsDestinations.SPLASH) {
            SplashScreen(reducedMotion = settings.reducedMotionEnabled)
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
                        navController.navigate(ArenaCardsDestinations.MULTIPLAYER)
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
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.FRIENDS)
                    } else {
                        navController.navigate(ArenaCardsDestinations.auth("welcome"))
                    }
                },
                onNotifications = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.NOTIFICATIONS) {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(ArenaCardsDestinations.auth("welcome"))
                    }
                },
                onDailyRewards = {
                    navController.navigate(ArenaCardsDestinations.WALLET) {
                        launchSingleTop = true
                    }
                },
                onWallet = {
                    navController.navigate(ArenaCardsDestinations.WALLET) {
                        launchSingleTop = true
                    }
                },
                onLeaderboard = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.LEADERBOARD) {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(ArenaCardsDestinations.auth("welcome"))
                    }
                },
                onProfile = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.PROFILE)
                    } else {
                        navController.navigate(ArenaCardsDestinations.auth("welcome"))
                    }
                },
                onSettings = {
                    navController.navigate(ArenaCardsDestinations.SETTINGS) {
                        launchSingleTop = true
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
                isAuthenticated = authState.isAuthenticated,
                walletBalance = walletState.account?.wallet?.coinsBalance,
                walletLoading = walletState.isLoading,
                playerLevel = walletState.account?.progression?.level
                    ?: activeProfile?.playerLevel,
                experiencePoints = walletState.account?.progression?.experiencePoints
                    ?.toLong() ?: activeProfile?.experiencePoints,
                xpToNextLevel = walletState.account?.progression?.xpToNextLevel,
                dailyRewardCoins = walletState.account?.dailyReward?.nextRewardCoins,
                dailyRewardAvailable = walletState.account?.dailyReward?.claimAvailable,
                readyMissionCount = walletState.account?.missions
                    ?.count { it.completed && !it.claimed },
                reducedMotion = settings.reducedMotionEnabled
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
        composable(ArenaCardsDestinations.SETTINGS) {
            ArenaSettingsScreen(
                repository = localStore,
                onBack = { navController.popBackStack() },
                onOpenPracticeHistory = {
                    navController.navigate(ArenaCardsDestinations.PRACTICE_HISTORY)
                }
            )
        }
        composable(ArenaCardsDestinations.PRACTICE_HISTORY) {
            PracticeHistoryScreen(
                repository = localStore,
                onBack = { navController.popBackStack() }
            )
        }
        composable(ArenaCardsDestinations.WALLET) {
            WalletScreen(
                isAuthenticated = authState.isAuthenticated,
                onSignIn = {
                    navController.navigate(ArenaCardsDestinations.auth("welcome"))
                },
                onBack = { navController.popBackStack() },
                viewModel = walletViewModel
            )
        }
        composable(ArenaCardsDestinations.LEADERBOARD) {
            if (authState.isAuthenticated) {
                val leaderboardViewModel: LeaderboardViewModel = viewModel(
                    factory = remember(competitiveRepository) {
                        LeaderboardViewModelFactory(competitiveRepository)
                    }
                )
                LeaderboardScreen(
                    viewModel = leaderboardViewModel,
                    reducedMotion = settings.reducedMotionEnabled,
                    onBack = { navController.popBackStack() },
                    onOpenOnlineMatch = { _ ->
                        navController.navigate(ArenaCardsDestinations.MULTIPLAYER) {
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                        popUpTo(ArenaCardsDestinations.LEADERBOARD) { inclusive = true }
                    }
                }
            }
        }
        composable(ArenaCardsDestinations.MULTIPLAYER) {
            if (authState.isAuthenticated) {
                val multiplayerViewModel: MultiplayerViewModel = viewModel(
                    factory = remember(multiplayerRepository) {
                        MultiplayerViewModelFactory(multiplayerRepository)
                    }
                )
                MultiplayerScreen(
                    viewModel = multiplayerViewModel,
                    reducedMotion = settings.reducedMotionEnabled,
                    onBack = { navController.popBackStack() },
                    socialRepository = socialRepository,
                    onOpenRoomChat = { roomId ->
                        navController.navigate(ArenaCardsDestinations.roomChat(roomId))
                    },
                    onFeedback = { event ->
                        feedback?.play(event.toGameFeedback())
                        if (
                            settings.hapticsEnabled
                            && event in setOf(
                                MultiplayerFeedback.YOUR_TURN,
                                MultiplayerFeedback.CARD_DRAWN,
                                MultiplayerFeedback.PENALTY,
                                MultiplayerFeedback.MATCH_WON,
                                MultiplayerFeedback.MATCH_LOST
                            )
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                        popUpTo(ArenaCardsDestinations.MULTIPLAYER) { inclusive = true }
                    }
                }
            }
        }
        composable(ArenaCardsDestinations.FRIENDS) {
            if (authState.isAuthenticated) {
                val friendsViewModel: FriendsViewModel = viewModel(
                    factory = remember(socialRepository) {
                        FriendsViewModelFactory(socialRepository)
                    }
                )
                FriendsScreen(
                    viewModel = friendsViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { uid ->
                        navController.navigate(ArenaCardsDestinations.chat(uid))
                    },
                    onOpenMessages = {
                        navController.navigate(ArenaCardsDestinations.chat("inbox"))
                    },
                    onOpenNotifications = {
                        navController.navigate(ArenaCardsDestinations.NOTIFICATIONS)
                    },
                    onOpenMultiplayer = {
                        navController.navigate(ArenaCardsDestinations.MULTIPLAYER)
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                        popUpTo(ArenaCardsDestinations.FRIENDS) { inclusive = true }
                    }
                }
            }
        }
        composable(
            route = ArenaCardsDestinations.CHAT_PATTERN,
            arguments = listOf(navArgument("friendUid") { type = NavType.StringType })
        ) { entry ->
            if (authState.isAuthenticated) {
                val friendUid = entry.arguments?.getString("friendUid")
                    ?.takeUnless { it == "inbox" }
                val chatViewModel: ChatViewModel = viewModel(
                    key = "chat-${friendUid ?: "inbox"}",
                    factory = remember(socialRepository, friendUid) {
                        ChatViewModelFactory(socialRepository, friendUid)
                    }
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                        popUpTo(ArenaCardsDestinations.CHAT_PATTERN) { inclusive = true }
                    }
                }
                composable(
                    route = ArenaCardsDestinations.ROOM_CHAT_PATTERN,
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) { entry ->
                    if (authState.isAuthenticated) {
                        val roomId = entry.arguments?.getString("roomId")
                        val chatViewModel: ChatViewModel = viewModel(
                            key = "room-chat-${roomId ?: "missing"}",
                            factory = remember(socialRepository, roomId) {
                                ChatViewModelFactory(socialRepository, initialFriendUid = null, initialRoomId = roomId)
                            }
                        )
                        ChatScreen(
                            viewModel = chatViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                                popUpTo(ArenaCardsDestinations.ROOM_CHAT_PATTERN) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
        composable(ArenaCardsDestinations.NOTIFICATIONS) {
            if (authState.isAuthenticated) {
                val notificationsViewModel: NotificationsViewModel = viewModel(
                    factory = remember(socialRepository) {
                        NotificationsViewModelFactory(socialRepository)
                    }
                )
                NotificationsScreen(
                    viewModel = notificationsViewModel,
                    onBack = { navController.popBackStack() },
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            coroutineScope.launch { registerPushToken() }
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(ArenaCardsDestinations.auth("welcome")) {
                        popUpTo(ArenaCardsDestinations.NOTIFICATIONS) { inclusive = true }
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
                },
                onStartOnlineGame = {
                    if (authState.isAuthenticated) {
                        navController.navigate(ArenaCardsDestinations.MULTIPLAYER) {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(ArenaCardsDestinations.auth("welcome"))
                    }
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
                onExit = { navController.popBackStack() },
                onReturnHome = {
                    navController.navigate(ArenaCardsDestinations.HOME) {
                        popUpTo(ArenaCardsDestinations.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(ArenaCardsDestinations.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                statisticsRepository = localStore,
                settings = settings,
                onFeedback = { event ->
                    feedback?.play(event)
                    if (settings.hapticsEnabled && event in HAPTIC_FEEDBACK_EVENTS) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
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
private fun SplashScreen(reducedMotion: Boolean) {
    var animationStarted by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (reducedMotion || animationStarted) 1f else 0.72f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 800),
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
private val HAPTIC_FEEDBACK_EVENTS = setOf(
    com.arenacards.feature.game.GameFeedback.CARD_PLAYED,
    com.arenacards.feature.game.GameFeedback.CARD_DRAWN,
    com.arenacards.feature.game.GameFeedback.SPECIAL_CARD,
    com.arenacards.feature.game.GameFeedback.CHALLENGE_SUCCESS,
    com.arenacards.feature.game.GameFeedback.CHALLENGE_FAILED,
    com.arenacards.feature.game.GameFeedback.VICTORY,
    com.arenacards.feature.game.GameFeedback.DEFEAT
)

private fun MultiplayerFeedback.toGameFeedback(): GameFeedback = when (this) {
    MultiplayerFeedback.MATCH_FOUND -> GameFeedback.MATCH_STARTED
    MultiplayerFeedback.PLAYER_JOINED -> GameFeedback.TURN_CHANGED
    MultiplayerFeedback.YOUR_TURN -> GameFeedback.TURN_CHANGED
    MultiplayerFeedback.OPPONENT_PLAYED -> GameFeedback.CARD_PLAYED
    MultiplayerFeedback.CARD_DRAWN -> GameFeedback.CARD_DRAWN
    MultiplayerFeedback.PENALTY -> GameFeedback.SPECIAL_CARD
    MultiplayerFeedback.MATCH_WON -> GameFeedback.VICTORY
    MultiplayerFeedback.MATCH_LOST -> GameFeedback.DEFEAT
}
