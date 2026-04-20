package com.internshipuncle.feature_profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.CanvasWhite
import com.internshipuncle.core.design.CharcoalDark
import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RedNegative
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.SurfaceGray
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "Internship Uncle",
    val email: String = "",
    val college: String = "",
    val degree: String = "",
    val graduationYear: Int? = null,
    val targetRoles: List<String> = emptyList(),
    val isSigningOut: Boolean = false,
    val signOutError: String? = null
) {
    val initials: String
        get() = name
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "IU" }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val actionState = MutableStateFlow(
        ProfileUiState(isSigningOut = false, signOutError = null)
    )

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.session(),
        actionState
    ) { session, actionUiState ->
            val profile = session.profile
            actionUiState.copy(
                name = profile?.name?.takeIf { it.isNotBlank() } ?: "Internship Uncle",
                email = profile?.email ?: session.email.orEmpty(),
                college = profile?.college.orEmpty(),
                degree = profile?.degree.orEmpty(),
                graduationYear = profile?.graduationYear,
                targetRoles = profile?.targetRoles ?: emptyList()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState()
        )

    fun signOut() {
        if (actionState.value.isSigningOut) return
        viewModelScope.launch {
            actionState.update { it.copy(isSigningOut = true, signOutError = null) }
            val result = authRepository.signOut()
            actionState.update { it.copy(isSigningOut = false, signOutError = result.toErrorMessage()) }
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.displaySmall,
                color = InkBlack
            )

            AccountCard(uiState = uiState)

            SettingsCard()

            Button(
                onClick = viewModel::signOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CanvasWhite,
                    contentColor = RedNegative
                ),
                border = BorderStroke(1.dp, RedNegative.copy(alpha = 0.45f)),
                enabled = !uiState.isSigningOut
            ) {
                Text(
                    text = if (uiState.isSigningOut) "LOGGING OUT..." else "LOGOUT",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            uiState.signOutError?.let { error ->
                Text(
                    text = error,
                    color = RedNegative,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AccountCard(uiState: ProfileUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .background(CharcoalDark, CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = uiState.initials,
                        color = PureWhite,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Column {
                    Text(
                        text = uiState.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkBlack
                    )
                    if (uiState.email.isNotBlank()) {
                        Text(
                            text = uiState.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray
                        )
                    }
                    val summary = buildList {
                        if (uiState.degree.isNotBlank()) add(uiState.degree)
                        if (uiState.college.isNotBlank()) add(uiState.college)
                        uiState.graduationYear?.let { add(it.toString()) }
                    }.joinToString(" · ")
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CharcoalDark),
                color = PureWhite,
                modifier = Modifier.clickable { }
            ) {
                Text(
                    text = "EDIT",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = CharcoalDark,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SettingsCard() {
    val rows = listOf(
        "Edit Profile",
        "Notification Settings",
        "About App",
        "Privacy Policy",
        "Terms of Service",
        "Send Feedback"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            rows.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkBlack
                    )
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SlateGray)
                }
                if (index < rows.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 18.dp)
                            .background(DividerGray)
                    )
                }
            }
        }
    }
}

private fun RepositoryStatus.toErrorMessage(): String? {
    return when (this) {
        RepositoryStatus.Success -> null
        RepositoryStatus.NotConfigured -> "App configuration is incomplete. Check your Supabase setup."
        RepositoryStatus.BackendNotReady -> "Profile backend is not ready yet."
        is RepositoryStatus.Failure -> message
    }
}
