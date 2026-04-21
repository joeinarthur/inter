package com.internshipuncle.feature_auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.CanvasWhite
import com.internshipuncle.core.design.SurfaceGray
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.core.ui.PlaceholderScreen
import com.internshipuncle.data.repository.AuthRepository
import com.internshipuncle.data.repository.AuthSession
import com.internshipuncle.domain.model.OnboardingProfileInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MinPasswordLength = 8

data class LoginUiState(
    val headline: String = "Stop applying blind.",
    val subhead: String = "Sign in to keep your target role, saved jobs, resume versions, and interview prep anchored in one workflow.",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isConfigured: Boolean = true
) {
    val emailError: String?
        get() = when {
            email.isBlank() -> "Email is required."
            !email.isLikelyEmail() -> "Enter a valid email address."
            else -> null
        }

    val passwordError: String?
        get() = when {
            password.isBlank() -> "Password is required."
            password.length < MinPasswordLength -> "Use at least $MinPasswordLength characters."
            else -> null
        }

    val canSubmit: Boolean
        get() = isConfigured && !isSubmitting && emailError == null && passwordError == null
}

data class SignupUiState(
    val headline: String = "Build a role-first prep loop.",
    val subhead: String = "Create your account, then finish onboarding with the profile fields the product needs for role-aware prep.",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isConfigured: Boolean = true
) {
    val emailError: String?
        get() = when {
            email.isBlank() -> "Email is required."
            !email.isLikelyEmail() -> "Enter a valid email address."
            else -> null
        }

    val passwordError: String?
        get() = when {
            password.isBlank() -> "Password is required."
            password.length < MinPasswordLength -> "Use at least $MinPasswordLength characters."
            else -> null
        }

    val canSubmit: Boolean
        get() = isConfigured && !isSubmitting && emailError == null && passwordError == null
}

data class OnboardingUiState(
    val headline: String = "Everything revolves around your target role.",
    val subhead: String = "Finish your profile so the app can keep discovery, resume work, interview prep, and readiness tied to the roles you actually want.",
    val name: String = "",
    val college: String = "",
    val degree: String = "",
    val graduationYear: String = "",
    val selectedRoles: Set<String> = emptySet(),
    val customRoleInput: String = "",
    val isSubmitting: Boolean = false,
    val isSigningOut: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isConfigured: Boolean = true,
    val isAuthenticated: Boolean = false,
    val existingEmail: String? = null
) {
    val targetRoles: List<String>
        get() = (selectedRoles + parseTargetRoles(customRoleInput)).toList()

    val nameError: String?
        get() = if (name.isBlank()) "Name is required." else null

    val collegeError: String?
        get() = if (college.isBlank()) "College is required." else null

    val degreeError: String?
        get() = if (degree.isBlank()) "Degree is required." else null

    val graduationYearValue: Int?
        get() = graduationYear.trim().toIntOrNull()

    val graduationYearError: String?
        get() = when {
            graduationYear.isBlank() -> "Graduation year is required."
            graduationYearValue == null -> "Enter a valid year."
            graduationYearValue !in 2000..2100 -> "Use a year between 2000 and 2100."
            else -> null
        }

    val targetRolesError: String?
        get() = if (targetRoles.isEmpty()) "Add at least one target role." else null

    val canSubmit: Boolean
        get() = isConfigured &&
            isAuthenticated &&
            !isSubmitting &&
            !isSigningOut &&
            nameError == null &&
            collegeError == null &&
            degreeError == null &&
            graduationYearError == null &&
            targetRolesError == null
}

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    authRepository: AuthRepository
) : ViewModel() {
    val session: StateFlow<AuthSession> = authRepository.session().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthSession()
    )
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val state = MutableStateFlow(LoginUiState())

    init {
        viewModelScope.launch {
            authRepository.session().collect { session ->
                state.update {
                    it.copy(isConfigured = session.isConfigured)
                }
            }
        }
    }

    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

    fun onEmailChanged(value: String) {
        state.update {
            it.copy(
                email = value,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        state.update {
            it.copy(
                password = value,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun signIn() {
        if (!state.value.canSubmit) {
            return
        }

        viewModelScope.launch {
            state.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }

            val result = authRepository.signIn(
                email = state.value.email.trim(),
                password = state.value.password
            )

            state.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = result.toAuthErrorMessage(),
                    infoMessage = null
                )
            }
        }
    }
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val state = MutableStateFlow(SignupUiState())

    init {
        viewModelScope.launch {
            authRepository.session().collect { session ->
                state.update {
                    it.copy(isConfigured = session.isConfigured)
                }
            }
        }
    }

    val uiState: StateFlow<SignupUiState> = state.asStateFlow()

    fun onEmailChanged(value: String) {
        state.update {
            it.copy(
                email = value,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        state.update {
            it.copy(
                password = value,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun signUp() {
        if (!state.value.canSubmit) {
            return
        }

        viewModelScope.launch {
            state.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }

            val result = authRepository.signUp(
                email = state.value.email.trim(),
                password = state.value.password
            )

            state.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = if (result.isUserAlreadyExists()) null else result.toAuthErrorMessage(),
                    infoMessage = when {
                        result.isUserAlreadyExists() ->
                            "An account already exists for this email. Please log in instead."
                        result is RepositoryStatus.Success ->
                            "Account created. If email confirmation is enabled in Supabase, verify the inbox before signing in."
                        else -> null
                    }
                )
            }
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val state = MutableStateFlow(OnboardingUiState())
    private var hasHydratedProfile = false

    init {
        viewModelScope.launch {
            authRepository.session().collect { session ->
                state.update { current ->
                    val baseState = current.copy(
                        isConfigured = session.isConfigured,
                        isAuthenticated = session.isLoggedIn,
                        existingEmail = session.email
                    )

                    if (!hasHydratedProfile && session.isLoggedIn) {
                        hasHydratedProfile = true
                        val profile = session.profile
                        baseState.copy(
                            name = profile?.name?.takeIf(String::isNotBlank)
                                ?: session.email?.substringBefore("@").orEmpty(),
                            college = profile?.college.orEmpty(),
                            degree = profile?.degree.orEmpty(),
                            graduationYear = profile?.graduationYear?.toString().orEmpty(),
                            selectedRoles = profile?.targetRoles?.toSet().orEmpty()
                        )
                    } else {
                        baseState
                    }
                }
            }
        }
    }

    val popularRoles = listOf(
        "Software Engineer", "Frontend Developer", "Backend Developer",
        "Product Manager", "Data Analyst", "UI/UX Designer",
        "Mobile Developer", "Cloud Architect", "Security Analyst"
    )

    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    fun onNameChanged(value: String) {
        state.update { it.copy(name = value, errorMessage = null, infoMessage = null) }
    }

    fun onCollegeChanged(value: String) {
        state.update { it.copy(college = value, errorMessage = null, infoMessage = null) }
    }

    fun onDegreeChanged(value: String) {
        state.update { it.copy(degree = value, errorMessage = null, infoMessage = null) }
    }

    fun onGraduationYearChanged(value: String) {
        state.update {
            it.copy(
                graduationYear = value.filter(Char::isDigit).take(4),
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onTargetRolesChanged(value: String) {
        state.update { it.copy(customRoleInput = value, errorMessage = null, infoMessage = null) }
    }

    fun toggleRole(role: String) {
        state.update { current ->
            val newSelected = if (role in current.selectedRoles) {
                current.selectedRoles - role
            } else {
                current.selectedRoles + role
            }
            current.copy(selectedRoles = newSelected, errorMessage = null)
        }
    }

    fun submit() {
        val currentState = state.value
        if (!currentState.canSubmit) {
            return
        }

        val graduationYear = currentState.graduationYearValue ?: return

        viewModelScope.launch {
            state.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }

            val result = authRepository.submitOnboarding(
                OnboardingProfileInput(
                    name = currentState.name.trim(),
                    college = currentState.college.trim(),
                    degree = currentState.degree.trim(),
                    graduationYear = graduationYear,
                    targetRoles = currentState.targetRoles
                )
            )

            state.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = result.toOnboardingErrorMessage(),
                    infoMessage = if (result is RepositoryStatus.Success) {
                        "Profile saved. Redirecting to curated jobs."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun signOut() {
        if (state.value.isSigningOut) {
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSigningOut = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.signOut()
            state.update {
                it.copy(
                    isSigningOut = false,
                    errorMessage = result.toAuthErrorMessage(),
                    infoMessage = null
                )
            }
        }
    }
}

@Composable
fun SplashScreen(
    session: AuthSession
) {
    PlaceholderScreen(
        eyebrow = "Internship Uncle",
        title = "Target the role.\nFix the gaps.\nApply sharper.",
        description = "The app restores your Supabase session first, then routes you into login, onboarding, or curated jobs based on real auth and profile state.",
        sections = listOf(
            "Session restore" to when {
                session.isRestoring -> "Checking stored session and profile state."
                session.isLoggedIn && session.needsOnboarding -> "Signed in, but profile onboarding is not complete."
                session.isLoggedIn && !session.profileBackendReady -> "Signed in. Profile sync is unavailable, so the app will continue without onboarding."
                session.isLoggedIn -> "Signed in and ready to continue to jobs."
                session.isConfigured -> "No active session found."
                else -> "Supabase client config is missing, so auth actions are disabled."
            },
            "Guardrail" to "AI logic stays server-side. Android handles auth, screen state, and structured data display only."
        ),
        actions = {
            CircularProgressIndicator(color = InkBlack)
        }
    )
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onSignup: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AuthStageScaffold(
        eyebrow = "Login",
        title = uiState.headline,
        description = uiState.subhead,
        footerActionLabel = "Create account",
        onFooterAction = onSignup
    ) {
        AuthMessageBanner(
            message = uiState.errorMessage ?: uiState.infoMessage,
            isError = uiState.errorMessage != null
        )
        if (!uiState.isConfigured) {
            AuthMessageBanner(
                message = "Add `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` or `SUPABASE_ANON_KEY` before using auth.",
                isError = true
            )
        }
        AppTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = "Email",
            enabled = !uiState.isSubmitting,
            isError = uiState.email.isNotEmpty() && uiState.emailError != null,
            supportingText = uiState.emailError,
            keyboardType = KeyboardType.Email
        )
        AppTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = "Password",
            enabled = !uiState.isSubmitting,
            isError = uiState.password.isNotEmpty() && uiState.passwordError != null,
            supportingText = uiState.passwordError,
            keyboardType = KeyboardType.Password,
            isPassword = true
        )
        PillButton(
            onClick = viewModel::signIn,
            enabled = uiState.canSubmit,
            isLoading = uiState.isSubmitting,
            label = "Sign in"
        )
    }
}

@Composable
fun SignupScreen(
    viewModel: SignupViewModel = hiltViewModel(),
    onLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AuthStageScaffold(
        eyebrow = "Signup",
        title = uiState.headline,
        description = uiState.subhead,
        footerActionLabel = "Back to login",
        onFooterAction = onLogin
    ) {
        AuthMessageBanner(
            message = uiState.errorMessage ?: uiState.infoMessage,
            isError = uiState.errorMessage != null
        )
        if (!uiState.isConfigured) {
            AuthMessageBanner(
                message = "Add `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` or `SUPABASE_ANON_KEY` before creating an account.",
                isError = true
            )
        }
        AppTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = "Email",
            enabled = !uiState.isSubmitting,
            isError = uiState.email.isNotEmpty() && uiState.emailError != null,
            supportingText = uiState.emailError,
            keyboardType = KeyboardType.Email
        )
        AppTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = "Password",
            enabled = !uiState.isSubmitting,
            isError = uiState.password.isNotEmpty() && uiState.passwordError != null,
            supportingText = uiState.passwordError,
            keyboardType = KeyboardType.Password,
            isPassword = true
        )
        PillButton(
            onClick = viewModel::signUp,
            enabled = uiState.canSubmit,
            isLoading = uiState.isSubmitting,
            label = "Create account"
        )
    }
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AuthStageScaffold(
        eyebrow = "Onboarding",
        title = uiState.headline,
        description = uiState.subhead
    ) {
        AuthMessageBanner(
            message = uiState.errorMessage ?: uiState.infoMessage,
            isError = uiState.errorMessage != null
        )
        
        // Profile Grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                label = "Full Name",
                enabled = !uiState.isSubmitting && !uiState.isSigningOut,
                isError = uiState.name.isNotEmpty() && uiState.nameError != null,
                supportingText = uiState.nameError
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1.5f)) {
                    AppTextField(
                        value = uiState.college,
                        onValueChange = viewModel::onCollegeChanged,
                        label = "College/University",
                        enabled = !uiState.isSubmitting && !uiState.isSigningOut,
                        isError = uiState.college.isNotEmpty() && uiState.collegeError != null,
                        supportingText = uiState.collegeError
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppTextField(
                        value = uiState.graduationYear,
                        onValueChange = viewModel::onGraduationYearChanged,
                        label = "Class of",
                        enabled = !uiState.isSubmitting && !uiState.isSigningOut,
                        isError = uiState.graduationYear.isNotEmpty() && uiState.graduationYearError != null,
                        supportingText = uiState.graduationYearError,
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            AppTextField(
                value = uiState.degree,
                onValueChange = viewModel::onDegreeChanged,
                label = "Degree / Specialization",
                enabled = !uiState.isSubmitting && !uiState.isSigningOut,
                isError = uiState.degree.isNotEmpty() && uiState.degreeError != null,
                supportingText = uiState.degreeError
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DividerGray)
        Spacer(Modifier.height(8.dp))

        // Role Selection
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Pick your target roles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.popularRoles.forEach { role ->
                    val isSelected = role in uiState.selectedRoles
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleRole(role) },
                        label = { Text(role) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkBlack,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceGray,
                            labelColor = SlateGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) InkBlack else DividerGray,
                            selectedBorderColor = InkBlack
                        )
                    )
                }
            }

            AppTextField(
                value = uiState.customRoleInput,
                onValueChange = viewModel::onTargetRolesChanged,
                label = "Other roles (comma separated)",
                enabled = !uiState.isSubmitting && !uiState.isSigningOut,
                isError = uiState.customRoleInput.isNotEmpty() && uiState.targetRolesError != null,
                supportingText = uiState.targetRolesError
            )
        }

        Spacer(Modifier.height(16.dp))

        PillButton(
            onClick = viewModel::submit,
            enabled = uiState.canSubmit,
            isLoading = uiState.isSubmitting,
            label = "Finish onboarding"
        )
        TextButton(
            onClick = viewModel::signOut,
            enabled = !uiState.isSubmitting && !uiState.isSigningOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            LoadingLabel(
                isLoading = uiState.isSigningOut,
                idleText = "Sign out"
            )
        }
    }
}

// ── Redesigned Auth Scaffold ──────────────────────────────────────────

@Composable
private fun AuthStageScaffold(
    eyebrow: String,
    title: String,
    description: String,
    footerActionLabel: String? = null,
    onFooterAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 20.dp,
                vertical = 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = SlateGray,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = InkBlack,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateGray
            )
        }

        // Form container (Flat inputs on canvas)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }

        if (footerActionLabel != null && onFooterAction != null) {
            TextButton(
                onClick = onFooterAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = footerActionLabel,
                    color = SlateGray,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(Modifier.height(48.dp))
    }
}

// ── Pill Button (design.md: 980 radius CTA) ──────────────────────────

@Composable
private fun PillButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    label: String
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = InkBlack,
            contentColor = PureWhite,
            disabledContainerColor = InkBlack.copy(alpha = 0.4f),
            disabledContentColor = SurfaceGray
        )
    ) {
        LoadingLabel(isLoading = isLoading, idleText = label)
    }
}

// ── Styled Text Field ────────────────────────────────────────────────

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = SlateGray) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = isError,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = InkBlack,
            unfocusedBorderColor = DividerGray,
            focusedLabelColor = InkBlack,
            cursorColor = InkBlack
        ),
        supportingText = {
            supportingText?.takeIf(String::isNotBlank)?.let { text ->
                Text(text)
            }
        }
    )
}

// ── Message Banner ───────────────────────────────────────────────────

@Composable
private fun AuthMessageBanner(
    message: String?,
    isError: Boolean
) {
    val safeMessage = message ?: return
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            SurfaceGray
        },
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = safeMessage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                InkBlack
            }
        )
    }
}

@Composable
private fun ReadOnlyProfileRow(
    label: String,
    value: String
) {
    Surface(
        color = SurfaceGray,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = SlateGray,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = InkBlack
            )
        }
    }
}

@Composable
private fun LoadingLabel(
    isLoading: Boolean,
    idleText: String
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.padding(vertical = 2.dp),
            strokeWidth = 2.dp,
            color = PureWhite
        )
    } else {
        Text(idleText, fontWeight = FontWeight.SemiBold)
    }
}

private fun RepositoryStatus.toAuthErrorMessage(): String? {
    return when (this) {
        RepositoryStatus.Success -> null
        RepositoryStatus.NotConfigured -> "Supabase client config is missing."
        RepositoryStatus.BackendNotReady -> "The auth backend is not fully ready yet."
        is RepositoryStatus.Failure -> message.toFriendlyAuthMessage()
    }
}

private fun RepositoryStatus.toOnboardingErrorMessage(): String? {
    return when (this) {
        RepositoryStatus.Success -> null
        RepositoryStatus.NotConfigured -> "Supabase client config is missing."
        RepositoryStatus.BackendNotReady -> "The `profiles` table or related RLS setup is not ready yet."
        is RepositoryStatus.Failure -> message
    }
}

private fun RepositoryStatus.isUserAlreadyExists(): Boolean {
    val message = when (this) {
        is RepositoryStatus.Failure -> message
        else -> null
    }.orEmpty()

    return message.contains("user_already_exists", ignoreCase = true) ||
        message.contains("already registered", ignoreCase = true) ||
        message.contains("already exists", ignoreCase = true)
}

private fun String.toFriendlyAuthMessage(): String {
    return when {
        contains("invalid login credentials", ignoreCase = true) ->
            "Incorrect email or password."
        contains("user_already_exists", ignoreCase = true) ||
            contains("already registered", ignoreCase = true) ||
            contains("already exists", ignoreCase = true) ->
            "An account already exists for this email. Please log in instead."
        contains("email not confirmed", ignoreCase = true) ->
            "Please confirm your email before signing in."
        else -> this
    }
}

private fun String.isLikelyEmail(): Boolean {
    return matches(Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE))
}

private fun parseTargetRoles(input: String): List<String> {
    return input
        .split(",", "\n")
        .map(String::trim)
        .filter(String::isNotBlank)
}
