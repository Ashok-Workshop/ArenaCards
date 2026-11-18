package com.arenacards.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arenacards.designsystem.ArenaPanel
import com.arenacards.designsystem.ArenaPrimaryButton
import com.arenacards.designsystem.ArenaSecondaryButton

enum class AuthMode {
    WELCOME,
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    VERIFY_EMAIL,
    PHONE,
    LINK_PHONE,
    ACCOUNT_LINKS
}

private data class PhoneCountry(val name: String, val dialCode: String)

private val phoneCountries = listOf(
    PhoneCountry("India", "+91"),
    PhoneCountry("United States", "+1"),
    PhoneCountry("United Kingdom", "+44"),
    PhoneCountry("Canada", "+1"),
    PhoneCountry("Australia", "+61"),
    PhoneCountry("New Zealand", "+64"),
    PhoneCountry("Ireland", "+353"),
    PhoneCountry("Germany", "+49"),
    PhoneCountry("France", "+33"),
    PhoneCountry("Spain", "+34"),
    PhoneCountry("Italy", "+39"),
    PhoneCountry("Netherlands", "+31"),
    PhoneCountry("Switzerland", "+41"),
    PhoneCountry("Sweden", "+46"),
    PhoneCountry("Norway", "+47"),
    PhoneCountry("Denmark", "+45"),
    PhoneCountry("Poland", "+48"),
    PhoneCountry("Portugal", "+351"),
    PhoneCountry("United Arab Emirates", "+971"),
    PhoneCountry("Saudi Arabia", "+966"),
    PhoneCountry("Pakistan", "+92"),
    PhoneCountry("Bangladesh", "+880"),
    PhoneCountry("Sri Lanka", "+94"),
    PhoneCountry("Nepal", "+977"),
    PhoneCountry("Singapore", "+65"),
    PhoneCountry("Malaysia", "+60"),
    PhoneCountry("Indonesia", "+62"),
    PhoneCountry("Philippines", "+63"),
    PhoneCountry("Japan", "+81"),
    PhoneCountry("South Korea", "+82"),
    PhoneCountry("China", "+86"),
    PhoneCountry("Brazil", "+55"),
    PhoneCountry("Mexico", "+52"),
    PhoneCountry("South Africa", "+27"),
    PhoneCountry("Nigeria", "+234"),
    PhoneCountry("Kenya", "+254"),
    PhoneCountry("Egypt", "+20")
)

@Composable
fun AuthScreen(
    mode: AuthMode,
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onOpenWelcome: () -> Unit,
    onOpenVerifyEmail: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenForgotPassword: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenPhoneLink: () -> Unit,
    onAuthenticated: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(phoneCountries.first()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val webClientId = remember(context) {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        resourceId.takeIf { it != 0 }?.let(context::getString)
    }

    LaunchedEffect(state.isAuthenticated, mode) {
        if (state.isAuthenticated && mode !in setOf(AuthMode.ACCOUNT_LINKS, AuthMode.LINK_PHONE)) {
            onAuthenticated()
        }
    }

    val title = when (mode) {
        AuthMode.WELCOME -> "Your next great game starts here"
        AuthMode.LOGIN -> "Welcome back"
        AuthMode.REGISTER -> "Create your player account"
        AuthMode.FORGOT_PASSWORD -> "Reset your password"
        AuthMode.VERIFY_EMAIL -> "Verify your email"
        AuthMode.PHONE -> if (state.phoneVerificationId == null) "Continue with phone" else "Enter your code"
        AuthMode.LINK_PHONE -> if (state.phoneVerificationId == null) "Link a phone number" else "Verify your number"
        AuthMode.ACCOUNT_LINKS -> "Connected sign-in methods"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹  Back") }
        Text(
            text = "ARENA  /  ACCOUNT",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = when (mode) {
                AuthMode.WELCOME -> "Choose a secure way to continue. ArenaCards uses Firebase Authentication; no credentials are stored by the app."
                AuthMode.VERIFY_EMAIL -> "Open the verification link in your inbox, then return here to continue."
                AuthMode.PHONE, AuthMode.LINK_PHONE ->
                    "Firebase sends and verifies the code. Carrier delivery and regional SMS limits may apply."
                else -> "Your Firebase account keeps your player identity and sign-in methods together."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        when (mode) {
            AuthMode.WELCOME -> {
                ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                    if (state.needsEmailVerification) {
                        Text(
                            text = "Your email account must be verified before continuing with another sign-in method.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ArenaPrimaryButton(
                            text = "Continue email verification",
                            onClick = onOpenVerifyEmail,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        TextButton(
                            onClick = viewModel::signOut,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign out to use another account")
                        }
                    } else {
                        GoogleSignInButton(
                            onClick = { activity?.let { viewModel.signInWithGoogle(it, webClientId) } },
                            enabled = !state.isLoading && activity != null,
                            isConfigured = webClientId != null
                        )
                        ArenaPrimaryButton(
                            text = "Continue with email",
                            onClick = onOpenLogin,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        ArenaSecondaryButton(
                            text = "Continue with phone",
                            onClick = onOpenPhone,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                        TextButton(onClick = onOpenRegister, modifier = Modifier.fillMaxWidth()) {
                            Text("Create a new email account")
                        }
                    }
                }
            }

            AuthMode.LOGIN, AuthMode.REGISTER, AuthMode.FORGOT_PASSWORD -> {
                ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                    if (mode == AuthMode.REGISTER) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "Username"
                            },
                            label = { Text("Username") },
                            supportingText = { Text("3-20 letters, numbers, or underscores") },
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Email address"
                        },
                        label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )
                    if (mode != AuthMode.FORGOT_PASSWORD) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics {
                                contentDescription = "Password"
                            },
                            label = { Text("Password") },
                            supportingText = if (mode == AuthMode.REGISTER) {
                                { Text("At least 8 characters with a letter and a number") }
                            } else null,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )
                    }
                    if (mode == AuthMode.REGISTER) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics {
                                contentDescription = "Confirm password"
                            },
                            label = { Text("Confirm password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )
                    }
                }

                if (state.needsEmailVerification && mode != AuthMode.FORGOT_PASSWORD) {
                    ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                        Text("Verify your email to continue", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.user?.email.orEmpty(),
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ArenaSecondaryButton(
                            text = "Check verification",
                            onClick = viewModel::refreshVerificationStatus,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            enabled = !state.isLoading
                        )
                        TextButton(onClick = viewModel::resendVerification, enabled = !state.isLoading) {
                            Text("Resend verification email")
                        }
                    }
                }

                when (mode) {
                    AuthMode.LOGIN -> ArenaPrimaryButton(
                        text = "Sign in",
                        onClick = { viewModel.signIn(email, password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )
                    AuthMode.REGISTER -> ArenaPrimaryButton(
                        text = "Create account",
                        onClick = {
                            viewModel.register(email, username, password, confirmPassword)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )
                    AuthMode.FORGOT_PASSWORD -> ArenaPrimaryButton(
                        text = "Send reset instructions",
                        onClick = { viewModel.sendPasswordReset(email) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )
                    else -> Unit
                }
            }

            AuthMode.PHONE, AuthMode.LINK_PHONE -> {
                ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                    if (state.phoneVerificationId == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column {
                                OutlinedButton(
                                    onClick = { countryMenuExpanded = true },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Country calling code, ${country.dialCode}"
                                    }
                                ) { Text(country.dialCode) }
                                DropdownMenu(
                                    expanded = countryMenuExpanded,
                                    onDismissRequest = { countryMenuExpanded = false }
                                ) {
                                    phoneCountries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text("${option.name} (${option.dialCode})") },
                                            onClick = {
                                                country = option
                                                countryMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = phoneDigits,
                                onValueChange = { value ->
                                    phoneDigits = value.filter(Char::isDigit).take(15)
                                },
                                modifier = Modifier.weight(1f).semantics {
                                    contentDescription = "Phone number"
                                },
                                label = { Text("Phone number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )
                        }
                        ArenaPrimaryButton(
                            text = "Send verification code",
                            onClick = {
                                activity?.let {
                                    viewModel.sendPhoneCode(it, country.dialCode + phoneDigits)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            enabled = !state.isLoading && activity != null
                        )
                    } else {
                        Text(
                            text = "Code sent to ${state.phoneNumber}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { value -> otp = value.filter(Char::isDigit).take(6) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics {
                                contentDescription = "Six-digit verification code"
                            },
                            label = { Text("6-digit code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                        ArenaPrimaryButton(
                            text = "Verify code",
                            onClick = { viewModel.verifyPhoneCode(otp) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            enabled = !state.isLoading
                        )
                        ArenaSecondaryButton(
                            text = if (state.resendCooldownSeconds > 0) {
                                "Resend in ${state.resendCooldownSeconds}s"
                            } else {
                                "Resend code"
                            },
                            onClick = {
                                activity?.let {
                                    state.phoneNumber?.let { number ->
                                        viewModel.sendPhoneCode(it, number, resend = true)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            enabled = !state.isLoading && state.resendCooldownSeconds == 0
                        )
                    }
                }
            }

            AuthMode.VERIFY_EMAIL -> ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text(state.user?.email ?: "No signed-in account", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Email verification is required before this account can enter the arena.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ArenaPrimaryButton(
                    text = "I've verified my email",
                    onClick = viewModel::refreshVerificationStatus,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    enabled = !state.isLoading
                )
                ArenaSecondaryButton(
                    text = "Resend verification email",
                    onClick = viewModel::resendVerification,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !state.isLoading
                )
                TextButton(onClick = {
                    viewModel.signOut()
                    onOpenWelcome()
                }) { Text("Sign out") }
            }

            AuthMode.ACCOUNT_LINKS -> ArenaPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Linked methods: ${state.user?.providerIds.orEmpty().joinToString().ifBlank { "None" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                GoogleSignInButton(
                    onClick = { activity?.let { viewModel.linkGoogleToCurrentUser(it, webClientId) } },
                    enabled = !state.isLoading && activity != null && webClientId != null,
                    isConfigured = webClientId != null
                )
                ArenaSecondaryButton(
                    text = "Link a phone number",
                    onClick = onOpenPhoneLink,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    enabled = !state.isLoading
                )
            }
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.message != null) {
            Text(
                text = state.message.orEmpty(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .semantics { contentDescription = "Authentication request in progress" }
            )
        }

        when (mode) {
            AuthMode.LOGIN -> Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenForgotPassword) { Text("Forgot password?") }
                TextButton(onClick = onOpenRegister) { Text("Create account") }
            }
            AuthMode.REGISTER -> Row(horizontalArrangement = Arrangement.Center) {
                Text("Already have an account?")
                TextButton(onClick = onOpenLogin) { Text("Sign in") }
            }
            AuthMode.FORGOT_PASSWORD -> Row(horizontalArrangement = Arrangement.Center) {
                Text("Remembered it?")
                TextButton(onClick = onOpenLogin) { Text("Back to sign in") }
            }
            AuthMode.PHONE, AuthMode.LINK_PHONE -> if (state.phoneVerificationId != null) {
                TextButton(onClick = viewModel::cancelPhoneVerification) { Text("Use a different number") }
            }
            AuthMode.ACCOUNT_LINKS -> TextButton(onClick = onBack) { Text("Done") }
            else -> Unit
        }
    }
}

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isConfigured: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = if (isConfigured) {
                    "Continue with Google"
                } else {
                    "Google sign-in requires Firebase provider setup"
                }
            },
            enabled = enabled
        ) {
            Text("G   Continue with Google")
        }
        Text(
            text = if (isConfigured) {
                "Continue with your Google account."
            } else {
                "Google sign-in needs Firebase Google provider setup and an updated google-services.json."
            },
            color = if (isConfigured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
