package com.internshipuncle.feature_dashboard

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import com.internshipuncle.core.design.SkyBlueLight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.CoolGray
import com.internshipuncle.core.design.DeepNavy
import com.internshipuncle.core.design.Cloud
import com.internshipuncle.core.design.Graphite
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.PaleBlue
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RoyalBlue
import com.internshipuncle.core.design.SoftBlue
import com.internshipuncle.core.design.SkyBlueMedium
import com.internshipuncle.core.ui.PlaceholderScreen
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.data.repository.AuthRepository
import com.internshipuncle.data.repository.DashboardRepository
import com.internshipuncle.domain.model.DashboardActivityItem
import com.internshipuncle.domain.model.DashboardActivityType
import com.internshipuncle.domain.model.DashboardDeadlineItem
import com.internshipuncle.domain.model.DashboardSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val readinessScore: Int? = null,
    val readinessSummary: String = "Your readiness dashboard will populate once Supabase returns real prep data.",
    val latestResumeScore: Int? = null,
    val latestMockScore: Int? = null,
    val savedJobsCount: Int = 0,
    val upcomingDeadlines: List<DashboardDeadlineItem> = emptyList(),
    val recentActivity: List<DashboardActivityItem> = emptyList(),
    val nextStepSuggestions: List<String> = emptyList(),
    val isConfigured: Boolean = true,
    val isSigningOut: Boolean = false,
    val signOutError: String? = null,
    val errorMessage: String? = null
) {
    val hasContent: Boolean
        get() = (readinessScore ?: 0) > 0 ||
            latestResumeScore != null ||
            latestMockScore != null ||
            savedJobsCount > 0 ||
            upcomingDeadlines.isNotEmpty() ||
            recentActivity.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && !hasContent
}

private data class DashboardActionState(
    val isSigningOut: Boolean = false,
    val signOutError: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val signOutState = MutableStateFlow(DashboardActionState())

    val uiState: StateFlow<DashboardUiState> = combine(
        dashboardRepository.snapshot(),
        signOutState
    ) { snapshotResult, signOutUiState ->
        snapshotResult.toDashboardUiState().copy(
            isSigningOut = signOutUiState.isSigningOut,
            signOutError = signOutUiState.signOutError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun refresh() {
        dashboardRepository.refresh()
    }

    fun signOut() {
        if (uiState.value.isSigningOut) return

        viewModelScope.launch {
            signOutState.update { it.copy(isSigningOut = true, signOutError = null) }
            val result = authRepository.signOut()
            signOutState.update {
                it.copy(
                    isSigningOut = false,
                    signOutError = result.toSignOutMessage()
                )
            }
        }
    }
}

// ── Shared Generic Sub-components ──────────────────────────────────

@Composable
private fun PillButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    label: String
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RoyalBlue,
            contentColor = PureWhite,
            disabledContainerColor = RoyalBlue.copy(alpha = 0.4f),
            disabledContentColor = PureWhite.copy(alpha = 0.6f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp, color = PureWhite)
        } else {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OutlinedPillButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = if (enabled) RoyalBlue else CoolGray)
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenJobs: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit,
    onOpenJob: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> PlaceholderScreen(
            eyebrow = "Dashboard",
            title = "Loading readiness dashboard",
            description = "Pulling scores, deadlines, and activity from Supabase.",
            sections = listOf(
                "Readiness" to "Waiting on your real prep data.",
                "Source of truth" to "Saved jobs, resume feedback, mock interviews, and active deadlines."
            ),
            actions = {
                CircularProgressIndicator(color = RoyalBlue)
            }
        )

        uiState.errorMessage != null -> PlaceholderScreen(
            eyebrow = "Dashboard",
            title = "Dashboard unavailable",
            description = uiState.errorMessage ?: "The dashboard could not be loaded.",
            sections = listOf(
                "Backend state" to if (uiState.isConfigured) "Configured but unavailable." else "Supabase config is missing."
            ),
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)){
                    PillButton(
                        onClick = viewModel::refresh,
                        label = "Retry"
                    )
                    OutlinedPillButton(onClick = onOpenJobs, label = "Open jobs")
                }
            }
        )

        uiState.isEmpty -> DashboardEmptyContent(
            onOpenJobs = onOpenJobs,
            onOpenSavedJobs = onOpenSavedJobs,
            onOpenResumeLab = onOpenResumeLab,
            onOpenInterview = onOpenInterview,
            onRefresh = viewModel::refresh,
            onSignOut = viewModel::signOut,
            isSigningOut = uiState.isSigningOut,
            signOutError = uiState.signOutError
        )

        else -> DashboardContent(
            uiState = uiState,
            onOpenJobs = onOpenJobs,
            onOpenSavedJobs = onOpenSavedJobs,
            onOpenResumeLab = onOpenResumeLab,
            onOpenInterview = onOpenInterview,
            onOpenJob = onOpenJob,
            onRefresh = viewModel::refresh,
            onSignOut = viewModel::signOut
        )
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onOpenJobs: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit,
    onOpenJob: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = InternshipUncleTheme.spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Welcome section
        WelcomeHeader(onRefresh = onRefresh, onSignOut = onSignOut, isSigningOut = uiState.isSigningOut)

        // Hero readiness card
        ReadinessHero(uiState = uiState)

        // Quick action icons row (matching inspiration image)
        QuickActionGrid(
            onOpenJobs = onOpenJobs,
            onOpenSavedJobs = onOpenSavedJobs,
            onOpenResumeLab = onOpenResumeLab,
            onOpenInterview = onOpenInterview
        )

        // Focus next suggestions
        if (uiState.nextStepSuggestions.isNotEmpty()) {
            GlassCard(title = "Focus Next") {
                uiState.nextStepSuggestions.take(3).forEachIndexed { index, suggestion ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoyalBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Metrics grid (2-column)
        MetricGrid(
            latestResumeScore = uiState.latestResumeScore,
            latestMockScore = uiState.latestMockScore,
            savedJobsCount = uiState.savedJobsCount,
            upcomingDeadlinesCount = uiState.upcomingDeadlines.size
        )

        // Deadlines
        SectionLabel(
            title = "Upcoming Deadlines",
            subtitle = if (uiState.upcomingDeadlines.isNotEmpty()) {
                "Deadlines from your shortlist."
            } else {
                "Save roles with deadlines to populate."
            }
        )
        if (uiState.upcomingDeadlines.isNotEmpty()) {
            uiState.upcomingDeadlines.take(3).forEach { deadline ->
                DeadlineCard(item = deadline, onOpenJob = onOpenJob)
            }
        } else {
            EmptyStateCard(
                title = "No upcoming deadlines",
                body = "Save internships with deadlines or open the jobs board."
            )
        }

        // Activity
        SectionLabel(
            title = "Recent Activity",
            subtitle = if (uiState.recentActivity.isNotEmpty()) {
                "Latest prep work that changed your readiness."
            } else {
                "Activity appears after roasting a resume, an interview, or saving a role."
            }
        )
        if (uiState.recentActivity.isNotEmpty()) {
            uiState.recentActivity.take(4).forEach { activity ->
                ActivityCard(
                    item = activity,
                    onOpenJob = onOpenJob,
                    onOpenResumeLab = onOpenResumeLab,
                    onOpenInterview = onOpenInterview
                )
            }
        } else {
            EmptyStateCard(
                title = "No recent activity",
                body = "Use the quick actions above to generate a timeline."
            )
        }

        uiState.signOutError?.let { error ->
            NoticeCard(
                title = "Sign out issue",
                body = error,
                accent = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun DashboardEmptyContent(
    onOpenJobs: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    isSigningOut: Boolean,
    signOutError: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = InternshipUncleTheme.spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Welcome section
        WelcomeHeader(onRefresh = onRefresh, onSignOut = onSignOut, isSigningOut = isSigningOut)

        // Empty Hero readiness card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = PureWhite.copy(alpha = 0.85f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CalendarToday, null, tint = CoolGray, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = java.text.SimpleDateFormat("dd MMMM", java.util.Locale.getDefault()).format(java.util.Date()),
                                style = MaterialTheme.typography.labelMedium,
                                color = Graphite
                            )
                            Text(
                                text = "Readiness Score",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoolGray
                            )
                        }
                    }
                    Icon(Icons.Outlined.MoreHoriz, null, tint = CoolGray)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Dashboard Readiness\nConnect Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Graphite
                    )
                    Text(
                        text = "This dashboard becomes useful after you connect it to a real target role.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoolGray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Graphite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(onClick = onOpenJobs)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Find Interships To Start",
                            color = PureWhite,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick action icons row (matching inspiration image)
        QuickActionGrid(
            onOpenJobs = onOpenJobs,
            onOpenSavedJobs = onOpenSavedJobs,
            onOpenResumeLab = onOpenResumeLab,
            onOpenInterview = onOpenInterview
        )

        // Suggestions
        GlassCard(title = "Suggested Next Moves") {
            val steps = listOf(
                "Save one internship from Jobs.",
                "Upload or roast a resume for a first score.",
                "Run one mock interview to compare progress."
            )
            steps.forEachIndexed { index, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, color = RoyalBlue, fontWeight = FontWeight.SemiBold)
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedPillButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                label = "Retry"
            )
            OutlinedPillButton(
                onClick = onSignOut,
                enabled = !isSigningOut,
                modifier = Modifier.fillMaxWidth(),
                label = if (isSigningOut) "Signing out..." else "Sign out"
            )
        }

        signOutError?.let { error ->
            NoticeCard(title = "Sign out issue", body = error, accent = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

// ── Welcome Header (like inspiration image) ───────────────────────────

@Composable
private fun WelcomeHeader(
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    isSigningOut: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle avatar
                Surface(
                    shape = CircleShape,
                    color = SkyBlueMedium,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = PureWhite)
                    }
                }
                Column {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoolGray
                    )
                    Text(
                        text = "Internship Uncle",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Graphite
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = PureWhite,
                shadowElevation = 4.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable(onClick = onRefresh)) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = Graphite
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Empower Your\nWorkflow",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Graphite
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PureWhite,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onSignOut)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSigningOut) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Graphite)
                    } else {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = Graphite)
                        Text("Chat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Graphite)
                    }
                }
            }
        }
    }
}

// ── Readiness Hero ────────────────────────────────────────────────────

@Composable
private fun ReadinessHero(uiState: DashboardUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CalendarToday, null, tint = CoolGray, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            text = java.text.SimpleDateFormat("dd MMMM", java.util.Locale.getDefault()).format(java.util.Date()),
                            style = MaterialTheme.typography.labelMedium,
                            color = Graphite
                        )
                        Text(
                            text = "Readiness Score",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolGray
                        )
                    }
                }
                Icon(Icons.Outlined.MoreHoriz, null, tint = CoolGray)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Dashboard Readiness\nSuccess Update",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Graphite
                )
                Text(
                    text = uiState.readinessScore?.let { "$it/100 Score Achieved" } ?: uiState.readinessSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolGray
                )
            }

            LinearProgressIndicator(
                progress = { ((uiState.readinessScore ?: 0).coerceIn(0, 100) / 100f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = RoyalBlue,
                trackColor = SkyBlueLight,
                strokeCap = StrokeCap.Round
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Graphite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Apply Now",
                        color = PureWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Quick Action Grid (from inspiration image: icons in circles) ──────

@Composable
private fun QuickActionGrid(
    onOpenJobs: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Announcements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Graphite
            )
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium,
                color = CoolGray,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickActionItem(icon = Icons.Outlined.WorkOutline, label = "Time Tracking\nJobs Board", onClick = onOpenJobs)
            QuickActionItem(icon = Icons.Outlined.Analytics, label = "Org Chart\nSaved Roles", onClick = onOpenSavedJobs)
            QuickActionItem(icon = Icons.Outlined.Description, label = "Expenses\nResume Lab", onClick = onOpenResumeLab)
            QuickActionItem(icon = Icons.Outlined.RecordVoiceOver, label = "Check-ins\nMock Interview", onClick = onOpenInterview)
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = PureWhite.copy(alpha = 0.90f),
        shadowElevation = 4.dp,
        modifier = Modifier
            .width(140.dp)
            .height(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Graphite,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Graphite,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Metric Grid (2 columns) ──────────────────────────────────────────

@Composable
private fun MetricGrid(
    latestResumeScore: Int?,
    latestMockScore: Int?,
    savedJobsCount: Int,
    upcomingDeadlinesCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                title = "Resume",
                value = latestResumeScore?.toString() ?: "--",
                subtitle = if (latestResumeScore == null) "No roast yet" else "Latest score"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                title = "Interview",
                value = latestMockScore?.toString() ?: "--",
                subtitle = if (latestMockScore == null) "No session yet" else "Latest score"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                title = "Saved Jobs",
                value = savedJobsCount.toString(),
                subtitle = if (savedJobsCount == 0) "Empty" else "In shortlist"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                title = "Deadlines",
                value = upcomingDeadlinesCount.toString(),
                subtitle = if (upcomingDeadlinesCount == 0) "None" else "Watching"
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = CoolGray)
            Text(text = value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Graphite)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CoolGray)
        }
    }
}

// ── Reusable Components ──────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CoolGray)
    }
}

@Composable
private fun GlassCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.82f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DeadlineCard(
    item: DashboardDeadlineItem,
    onOpenJob: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PureWhite.copy(alpha = 0.82f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(item.company, item.location?.takeIf(String::isNotBlank), item.workMode?.takeIf(String::isNotBlank)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = CoolGray
            )
            Surface(color = PaleBlue, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = formatDeadlineLabel(item.deadline),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedPillButton(modifier = Modifier.fillMaxWidth(), onClick = { onOpenJob(item.jobId) }, label = "Open role")
        }
    }
}

@Composable
private fun ActivityCard(
    item: DashboardActivityItem,
    onOpenJob: (String) -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PureWhite.copy(alpha = 0.82f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = item.details, style = MaterialTheme.typography.bodySmall, color = CoolGray)
            item.score?.let {
                Surface(color = RoyalBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "$it",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = RoyalBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item.createdAt?.takeIf(String::isNotBlank)?.let { createdAt ->
                Text(text = formatActivityTime(createdAt), style = MaterialTheme.typography.labelSmall, color = CoolGray)
            }
            val actionLabel = when (item.type) {
                DashboardActivityType.SavedJob -> item.jobId?.let { "Open role" }
                DashboardActivityType.ResumeRoast -> "Open resume lab"
                DashboardActivityType.MockInterview -> "Practice again"
            }
            val onAction = when (item.type) {
                DashboardActivityType.SavedJob -> item.jobId?.let { { onOpenJob(it) } }
                DashboardActivityType.ResumeRoast -> onOpenResumeLab
                DashboardActivityType.MockInterview -> onOpenInterview
            }
            if (actionLabel != null && onAction != null) {
                OutlinedPillButton(modifier = Modifier.fillMaxWidth(), onClick = onAction, label = actionLabel)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PureWhite.copy(alpha = 0.7f),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = CoolGray)
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Mapping helpers ──────────────────────────────────────────────────

private fun QueryResult<DashboardSnapshot>.toDashboardUiState(): DashboardUiState {
    return when (this) {
        QueryResult.Loading -> DashboardUiState(isLoading = true)
        QueryResult.NotConfigured -> DashboardUiState(
            isLoading = false,
            isConfigured = false,
            errorMessage = "Add `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` or `SUPABASE_ANON_KEY` to load the dashboard."
        )
        QueryResult.BackendNotReady -> DashboardUiState(
            isLoading = false,
            errorMessage = "The dashboard backend tables or functions are not ready yet."
        )
        is QueryResult.Failure -> DashboardUiState(
            isLoading = false,
            errorMessage = message
        )
        is QueryResult.Success -> data.toDashboardUiState()
    }
}

private fun DashboardSnapshot.toDashboardUiState(): DashboardUiState {
    return DashboardUiState(
        isLoading = false,
        readinessScore = readinessScore,
        readinessSummary = buildReadinessSummary(),
        latestResumeScore = latestResumeScore,
        latestMockScore = latestMockScore,
        savedJobsCount = savedJobsCount,
        upcomingDeadlines = upcomingDeadlines,
        recentActivity = recentActivity,
        nextStepSuggestions = buildNextStepSuggestions(),
        isConfigured = isConfigured
    )
}

private fun DashboardSnapshot.buildReadinessSummary(): String {
    val readiness = readinessScore ?: 0
    return when {
        !isConfigured -> "Connect Supabase before the dashboard can calculate readiness."
        latestResumeScore == null && latestMockScore == null && savedJobsCount == 0 -> {
            "Start by saving one internship, then roast a resume and run a mock interview to build your first score."
        }
        readiness >= 80 -> "Your prep loop is strong. Keep tightening the role fit and deadlines."
        readiness >= 50 -> "You have momentum. A cleaner resume score or another mock run will move you forward."
        else -> "You need more proof points. Finish onboarding, save a role, and add a resume or interview score."
    }
}

private fun DashboardSnapshot.buildNextStepSuggestions(): List<String> {
    return buildList {
        if (savedJobsCount == 0) add("Save one internship so the dashboard can track a real target.")
        if (latestResumeScore == null) add("Upload or roast a resume to get your first resume score.")
        if (latestMockScore == null) add("Run one mock interview so you can compare progress over time.")
        if (upcomingDeadlines.isEmpty()) add("Pick a role with a deadline so prep has a clear finish line.")
    }.take(3)
}

private fun formatDeadlineLabel(deadline: String): String {
    return "Deadline ${parseDateLabel(deadline)}"
}

private fun formatActivityTime(createdAt: String): String {
    val parsed = parseDate(createdAt) ?: return createdAt.substringBefore("T")
    val elapsedMillis = System.currentTimeMillis() - parsed.time
    val minutes = (elapsedMillis / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> parseDateLabel(createdAt)
    }
}

private fun parseDateLabel(value: String): String {
    return parseDate(value)?.let { date ->
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
    } ?: value.substringBefore("T")
}

private fun parseDate(value: String): Date? {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )
    return formats.firstNotNullOfOrNull { format ->
        runCatching { format.parse(value) }.getOrNull()
    }
}

private fun RepositoryStatus.toSignOutMessage(): String? {
    return when (this) {
        RepositoryStatus.Success -> null
        RepositoryStatus.NotConfigured -> "Supabase client config is missing."
        RepositoryStatus.BackendNotReady -> "The auth backend is not fully ready yet."
        is RepositoryStatus.Failure -> message
    }
}
