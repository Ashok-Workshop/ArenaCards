package com.arenacards.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.arenacards.designsystem.ArenaCardsLogoMark
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPalette
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton
import com.arenacards.designsystem.PlayerAvatar
import java.text.DateFormat
import java.util.Date

private data class AvatarOption(
    val id: String
)

private val avatarOptions = listOf(
    AvatarOption("ember"),
    AvatarOption("tide"),
    AvatarOption("grove"),
    AvatarOption("sun"),
    AvatarOption("comet"),
    AvatarOption("crown"),
    AvatarOption("spark"),
    AvatarOption("wild")
)

@Composable
fun PlayerProfileScreen(
    uid: String,
    authDisplayName: String?,
    viewModel: PlayerProfileViewModel,
    onBack: () -> Unit,
    onContinueHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile?.takeIf { state.uid == uid && it.uid == uid }
    var editing by remember(profile?.uid) { mutableStateOf(profile == null) }
    var username by remember(profile?.uid) { mutableStateOf(profile?.username.orEmpty()) }
    var displayName by remember(profile?.uid) {
        mutableStateOf(profile?.displayName ?: authDisplayName.orEmpty())
    }
    var avatarId by remember(profile?.uid) {
        mutableStateOf(profile?.avatarId ?: "ember")
    }
    var isPublic by remember(profile?.uid) {
        mutableStateOf(profile?.profileVisibility == ProfileVisibility.PUBLIC)
    }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(uid) {
        viewModel.loadProfile(uid)
    }
    LaunchedEffect(state.message) {
        if (state.message == "Your player profile has been saved.") editing = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080E1D), Color(0xFF14233D), Color(0xFF080E1D))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArenaCardsLogoMark(Modifier.size(42.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("ARENA IDENTITY", color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge)
                    Text("PLAYER PROFILE", style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
            TextButton(onClick = onBack) { Text("‹ Back") }
        }
        Text(
            text = if (profile == null) "Create your player identity" else "Your player card",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge
        )

        if (state.uid != uid || (state.isLoading && !state.hasLoaded)) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Loading player profile" }
                )
            }
        } else if (state.profileLoadFailed) {
            ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                ArenaSecondaryButton(
                    text = "Retry",
                    onClick = { viewModel.loadProfile(uid) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        } else if (profile == null || editing) {
            ProfileEditor(
                isCreating = profile == null,
                username = username,
                onUsernameChange = {
                    username = it
                    viewModel.clearMessages()
                },
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                avatarId = avatarId,
                onAvatarSelected = { avatarId = it },
                isPublic = isPublic,
                onVisibilityChange = { isPublic = it },
                state = state,
                viewModel = viewModel,
                onSave = {
                    if (profile == null) {
                        viewModel.createProfile(username, displayName, avatarId)
                    } else {
                        viewModel.updateProfile(
                            username,
                            displayName,
                            avatarId,
                            if (isPublic) ProfileVisibility.PUBLIC else ProfileVisibility.PRIVATE
                        )
                    }
                }
            )
        } else {
            ProfileSummary(
                profile = profile,
                onCopyGamerId = {
                    clipboard.setText(AnnotatedString(profile.gamerId))
                    viewModel.clearMessages()
                },
                onEdit = { editing = true },
                onContinueHome = onContinueHome
            )
        }

        if (state.errorMessage != null && state.hasLoaded) {
            Text(
                text = state.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.message != null && profile != null && !editing) {
            Text(
                text = state.message.orEmpty(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PlayerProfileGateScreen(
    uid: String,
    viewModel: PlayerProfileViewModel,
    onProfileFound: () -> Unit,
    onProfileRequired: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(uid) {
        viewModel.loadProfile(uid)
    }
    LaunchedEffect(uid, state.uid, state.hasLoaded, state.profile, state.errorMessage) {
        if (state.uid == uid && state.hasLoaded && state.errorMessage == null) {
            if (state.profile == null) onProfileRequired() else onProfileFound()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.uid != uid || state.errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = "Checking player profile" }
            )
            Text(
                text = "Checking your player identity...",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            ArenaSecondaryButton(
                text = "Retry",
                onClick = { viewModel.loadProfile(uid) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ProfileEditor(
    isCreating: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    avatarId: String,
    onAvatarSelected: (String) -> Unit,
    isPublic: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    state: PlayerProfileUiState,
    viewModel: PlayerProfileViewModel,
    onSave: () -> Unit
) {
    ArenaPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isCreating) {
                "Choose a unique username. Your permanent Gamer ID is assigned securely."
            } else {
                "Update your public player identity. Your Gamer ID and match statistics cannot be edited."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).semantics {
                contentDescription = "Unique username"
            },
            label = { Text("Username") },
            supportingText = {
                Text(
                    when (state.usernameAvailable) {
                        true -> "Available"
                        false -> "Unavailable, reserved, or already taken"
                        null -> "3-20 letters, numbers, or underscores"
                    }
                )
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            singleLine = true,
            isError = state.usernameAvailable == false
        )
        ArenaSecondaryButton(
            text = if (state.isCheckingUsername) "Checking..." else "Check availability",
            onClick = { viewModel.checkUsername(username) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !state.isCheckingUsername && !state.isSaving
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics {
                contentDescription = "Display name"
            },
            label = { Text("Display name") },
            supportingText = { Text("1-30 characters") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            singleLine = true
        )
        Text(
            text = "Choose an avatar",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleMedium
        )
        avatarOptions.chunked(4).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = avatarId == option.id,
                        onClick = { onAvatarSelected(option.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlayerAvatar(
                                    initials = "",
                                    name = option.id,
                                    size = 34.dp,
                                    avatarId = option.id
                                )
                                Text(
                                    option.id.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "${option.id} avatar"
                        },
                        enabled = !state.isSaving,
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        }
        if (!isCreating) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Public profile", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "When enabled, approved player details can appear in lookup.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isPublic,
                    onCheckedChange = onVisibilityChange,
                    modifier = Modifier.semantics {
                        contentDescription = "Make player profile public"
                    }
                )
            }
        } else {
            Text(
                text = "Your profile starts private. You can change visibility after creation.",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        ArenaPrimaryButton(
            text = if (state.isSaving) "Saving..." else if (isCreating) "Create player profile" else "Save changes",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            enabled = !state.isSaving && !state.isCheckingUsername
        )
    }
}

@Composable
private fun ProfileSummary(
    profile: PlayerProfile,
    onCopyGamerId: () -> Unit,
    onEdit: () -> Unit,
    onContinueHome: () -> Unit
) {
    ArenaPanel(
        modifier = Modifier.fillMaxWidth(),
        backgroundBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(ArenaPalette.Purple.copy(alpha = 0.26f), ArenaPalette.Blue.copy(alpha = 0.18f))
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlayerAvatar(
                initials = "",
                name = profile.displayName,
                size = 68.dp,
                backgroundColor = ArenaPalette.Purple,
                avatarId = profile.avatarId
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "@${profile.username}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        TextButton(
            onClick = onCopyGamerId,
            modifier = Modifier.padding(top = 8.dp).semantics {
                contentDescription = "Copy Gamer ID ${profile.gamerId}"
            }
        ) {
            Text("Gamer ID  ${profile.gamerId}  •  Copy")
        }
        Text(
            text = "Level ${profile.playerLevel}  ·  ${profile.experiencePoints} XP",
            modifier = Modifier.padding(top = 4.dp),
            color = ArenaPalette.Gold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Joined ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.createdAtMillis))}",
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Text("Match record", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileStatCard("Matches", profile.totalMatches, Modifier.weight(1f))
        ProfileStatCard("Wins", profile.wins, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileStatCard("Losses", profile.losses, Modifier.weight(1f))
        ProfileStatCard("Draws", profile.draws, Modifier.weight(1f))
    }
    ArenaSecondaryButton(
        text = "Edit profile",
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth()
    )
    ArenaPrimaryButton(
        text = "Continue to ArenaCards",
        onClick = onContinueHome,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProfileStatCard(
    label: String,
    value: Long,
    modifier: Modifier = Modifier
) {
    ArenaPanel(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
