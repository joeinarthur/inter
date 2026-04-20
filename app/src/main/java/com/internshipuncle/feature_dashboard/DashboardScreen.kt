package com.internshipuncle.feature_dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.internshipuncle.core.design.CanvasWhite
import com.internshipuncle.core.design.CharcoalDark
import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.GreenPositive
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RedNegative
import com.internshipuncle.core.design.RedNegative
import com.internshipuncle.core.design.SilverMist
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.SurfaceGray
import com.internshipuncle.core.design.SurfaceLight
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

// ── State & ViewModel (unchanged logic) ────────────────────────────────

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
    val errorMessage: String? = null,
    val userName: String = "Internship Uncle",
    val userInitials: String = "IU"
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
        authRepository.session(),
        signOutState
    ) { snapshotResult, session, signOutUiState ->
        val profile = session.profile
        snapshotResult.toDashboardUiState().copy(
            isSigningOut = signOutUiState.isSigningOut,
            signOutError = signOutUiState.signOutError,
            userName = profile?.name ?: "Internship Uncle",
            userInitials = profile?.name?.toInitials() ?: "IU"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun refresh() { dashboardRepository.refresh() }

    fun signOut() {
        if (uiState.value.isSigningOut) return
        viewModelScope.launch {
            signOutState.update { it.copy(isSigningOut = true, signOutError = null) }
            val result = authRepository.signOut()
            signOutState.update {
                it.copy(isSigningOut = false, signOutError = result.toSignOutMessage())
            }
        }
    }
}

// ── Entry point ────────────────────────────────────────────────────────

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
            eyebrow     = "Dashboard",
            title       = "Refining your workstation",
            description = "Pulling scores, deadlines and activity from Supabase.",
            actions     = {
                CircularProgressIndicator(
                    modifier    = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp,
                    color       = InkBlack
                )
            }
        )

        uiState.errorMessage != null -> PlaceholderScreen(
            eyebrow     = "Dashboard",
            title       = "System pause",
            description = uiState.errorMessage ?: "The dashboard could not be loaded.",
            sections    = listOf(
                "Status" to if (uiState.isConfigured) "Configured but unavailable." else "Supabase config is missing."
            ),
            actions     = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FintechPillButton(onClick = viewModel::refresh, label = "Retry")
                }
            }
        )

        else -> DashboardContent(
            uiState          = uiState,
            onOpenJobs       = onOpenJobs,
            onOpenSavedJobs  = onOpenSavedJobs,
            onOpenResumeLab  = onOpenResumeLab,
            onOpenInterview  = onOpenInterview,
            onOpenJob        = onOpenJob,
            onRefresh        = viewModel::refresh,
            onSignOut        = viewModel::signOut
        )
    }
}

// ── Main Dashboard Content ────────────────────────────────────────────

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
            .background(CanvasWhite)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── 1. Compact Header
        WelcomeHeader(
            userName = uiState.userName,
            userInitials = uiState.userInitials,
            onSignOut = onSignOut
        )

        Spacer(Modifier.height(20.dp))

        // ── 2. Momentum Hub (Hero Card)
        MomentumHub(uiState = uiState, onOpenInterview = onOpenInterview)

        Spacer(Modifier.height(32.dp))

        // ── 3. Simulation Toolbox (Gallery Cards)
        ToolboxGallery(
            onOpenInterview = onOpenInterview,
            onOpenResumeLab = onOpenResumeLab,
            onOpenJobs = onOpenJobs
        )

        Spacer(Modifier.height(32.dp))

        // ── 4. Quick Stats & Shortcuts
        QuickInsightsRow(
            savedJobsCount = uiState.savedJobsCount,
            onOpenSavedJobs = onOpenSavedJobs
        )

        Spacer(Modifier.height(32.dp))

        // ── 5. Activity Timeline
        RecentActivitySection(
            activities      = uiState.recentActivity,
            onOpenJob       = onOpenJob,
            onOpenResumeLab = onOpenResumeLab,
            onOpenInterview = onOpenInterview
        )

        Spacer(Modifier.height(32.dp))

        // ── 6. Deadlines
        if (uiState.upcomingDeadlines.isNotEmpty()) {
            DeadlinesSection(
                deadlines  = uiState.upcomingDeadlines,
                onOpenJob  = onOpenJob
            )
            Spacer(Modifier.height(32.dp))
        }

        // Sign-out error notice
        uiState.signOutError?.let { error ->
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                ErrorNotice(message = error)
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(96.dp))
    }
}

// ── 1. Welcome Header ─────────────────────────────────────────────────
// Left: circle avatar with initials + "Welcome back, / User"
// Right: notification bell + settings gear (outlined circles)

@Composable
private fun WelcomeHeader(
    userName: String,
    userInitials: String,
    onSignOut: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Avatar + greeting (clickable for dropdown)
        Row(
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { isExpanded = true }
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Initials avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(InkBlack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = userInitials,
                    color      = PureWhite,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Column {
                Text(
                    text  = "Welcome back,",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray
                )
                Text(
                    text       = userName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = InkBlack
                )
            }

            // Dropdown Menu Pop-out
            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
                modifier = Modifier.background(PureWhite, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Settings", style = MaterialTheme.typography.bodyMedium) },
                    onClick = { isExpanded = false },
                    leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Log Out", color = RedNegative, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        isExpanded = false
                        onSignOut()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp), tint = RedNegative) }
                )
            }
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(SurfaceGray)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = InkBlack,
            modifier           = Modifier.size(20.dp)
        )
    }
}

// ── 2. Momentum Hub ───────────────────────────────────────────────────

@Composable
private fun MomentumHub(uiState: DashboardUiState, onOpenInterview: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = InkBlack,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "Good to see you, \nUncle.",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                        color = PureWhite,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Uncle has been waiting.\nLet's see what you've been\nworking on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite.copy(alpha = 0.8f)
                    )
                }
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.internshipuncle.R.drawable.favicon_removebg_preview),
                    contentDescription = "Uncle Logo",
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White)
                )
            }
            
            Button(
                onClick = onOpenInterview,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = RedNegative,
                    contentColor = PureWhite
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.MicNone, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("UNCLE WILL GRILL YOU NOW", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun ScoreIndicator(label: String, score: Int?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateGray)
        Text(
            text = score?.let { "$it%" } ?: "Pending",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (score != null) InkBlack else SilverMist
        )
    }
}

// ── 3. Toolbox Gallery ────────────────────────────────────────────────

@Composable
private fun ToolboxGallery(
    onOpenInterview: () -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenJobs: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "SIMULATION WORKSTATION",
            style = MaterialTheme.typography.labelSmall,
            color = SlateGray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToolCard(
                modifier = Modifier.weight(1f),
                title = "Interview\nLab",
                description = "Custom behavioral & technical mocks.",
                icon = Icons.Outlined.MicNone,
                onClick = onOpenInterview
            )
            ToolCard(
                modifier = Modifier.weight(1f),
                title = "Resume\nRoast",
                description = "JD-specific score & experience audit.",
                icon = Icons.Outlined.Description,
                onClick = onOpenResumeLab
            )
        }
    }
}

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = InkBlack,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PureWhite.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = PureWhite, modifier = Modifier.size(20.dp))
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, color = PureWhite.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── 4. Quick Insights Row ─────────────────────────────────────────────

@Composable
private fun QuickInsightsRow(
    savedJobsCount: Int,
    onOpenSavedJobs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceGray,
            onClick = onOpenSavedJobs
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.BookmarkBorder, null, tint = InkBlack)
                Column {
                    Text("$savedJobsCount Saved", fontWeight = FontWeight.Bold, color = InkBlack)
                    Text("Targeting roles", style = MaterialTheme.typography.labelSmall, color = SlateGray)
                }
            }
        }
        
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceGray,
            onClick = { /* Could be Settings or Stats */ }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.GridView, null, tint = InkBlack)
            }
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    detail: String,
    positive: Boolean?
) {
    Surface(
        modifier        = modifier,
        shape           = RoundedCornerShape(18.dp),
        color           = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier            = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray
            )
            Text(
                text       = value,
                style      = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color      = when (positive) {
                    true  -> InkBlack
                    false -> SlateGray
                    null  -> InkBlack
                }
            )
            Text(
                text  = detail,
                style = MaterialTheme.typography.bodySmall,
                color = SilverMist
            )
        }
    }
}

// ── 6. Recent Activity — transaction list ─────────────────────────────

@Composable
private fun RecentActivitySection(
    activities: List<DashboardActivityItem>,
    onOpenJob: (String) -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Section header (matches "Recent transactions  See all")
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Recent activity",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = InkBlack
            )
            Text(
                text  = "See all",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(16.dp))

        if (activities.isEmpty()) {
            EmptyActivityHint()
        } else {
            // Transaction-list style items with dividers
            Surface(
                shape           = RoundedCornerShape(18.dp),
                color           = SurfaceGray,
                shadowElevation = 0.dp,
                modifier        = Modifier.fillMaxWidth()
            ) {
                Column {
                    activities.take(5).forEachIndexed { index, activity ->
                        ActivityListItem(
                            item            = activity,
                            onOpenJob       = onOpenJob,
                            onOpenResumeLab = onOpenResumeLab,
                            onOpenInterview = onOpenInterview
                        )
                        if (index < minOf(activities.size - 1, 4)) {
                            HorizontalDivider(
                                modifier  = Modifier.padding(start = 68.dp),
                                color     = DividerGray,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityListItem(
    item: DashboardActivityItem,
    onOpenJob: (String) -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit
) {
    val (icon, bgColor) = when (item.type) {
        DashboardActivityType.SavedJob       -> Pair(Icons.Outlined.WorkOutline,      CharcoalDark)
        DashboardActivityType.ResumeRoast    -> Pair(Icons.Outlined.Description,      InkBlack)
        DashboardActivityType.MockInterview  -> Pair(Icons.Outlined.MicNone,          SlateGray)
    }

    val onAction: (() -> Unit)? = when (item.type) {
        DashboardActivityType.SavedJob       -> item.jobId?.let { { onOpenJob(it) } }
        DashboardActivityType.ResumeRoast    -> onOpenResumeLab
        DashboardActivityType.MockInterview  -> onOpenInterview
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled           = onAction != null,
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = { onAction?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Circle icon (like transaction avatars in screenshot)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = PureWhite,
                modifier           = Modifier.size(20.dp)
            )
        }

        // Title + timestamp
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text      = item.title,
                style     = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color     = InkBlack,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
            Text(
                text  = item.details.take(45),
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right: score or amount (like ₦10,000 in screenshot)
        Column(horizontalAlignment = Alignment.End) {
            item.score?.let { score ->
                Text(
                    text       = "$score/100",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (score >= 70) GreenPositive else InkBlack
                )
            }
            item.createdAt?.takeIf(String::isNotBlank)?.let { ts ->
                Text(
                    text  = formatActivityTime(ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMist
                )
            }
        }
    }
}

@Composable
private fun EmptyActivityHint() {
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceGray,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text  = "No activity yet",
                style = MaterialTheme.typography.titleSmall,
                color = InkBlack,
                fontWeight = FontWeight.Medium
            )
            Text(
                text  = "Roast a resume, run a mock interview or save a role to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray
            )
        }
    }
}

// ── 7. Deadlines Section ──────────────────────────────────────────────

@Composable
private fun DeadlinesSection(
    deadlines: List<DashboardDeadlineItem>,
    onOpenJob: (String) -> Unit
) {
    Column(
        modifier            = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Upcoming deadlines",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = InkBlack
            )
        }

        Surface(
            shape  = RoundedCornerShape(18.dp),
            color  = SurfaceGray,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                deadlines.take(3).forEachIndexed { index, deadline ->
                    DeadlineListItem(item = deadline, onOpenJob = onOpenJob)
                    if (index < minOf(deadlines.size - 1, 2)) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 68.dp),
                            color     = DividerGray,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadlineListItem(
    item: DashboardDeadlineItem,
    onOpenJob: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onOpenJob(item.jobId) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(InkBlack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Outlined.WorkOutline,
                contentDescription = null,
                tint               = PureWhite,
                modifier           = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text      = item.title,
                style     = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color     = InkBlack,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
            Text(
                text  = item.company ?: "Unknown company",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray
            )
        }

        Text(
            text       = formatDeadlineLabel(item.deadline),
            style      = MaterialTheme.typography.labelSmall,
            color      = RedNegative,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── 8. Next Steps Section ─────────────────────────────────────────────

@Composable
private fun NextStepsSection(suggestions: List<String>) {
    Column(
        modifier            = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text       = "Focus next",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = InkBlack
        )

        Surface(
            shape  = RoundedCornerShape(18.dp),
            color  = SurfaceGray,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                suggestions.take(3).forEachIndexed { index, suggestion ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(InkBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = "${index + 1}",
                                color = PureWhite,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text  = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateGray
                        )
                    }
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────

@Composable
private fun ErrorNotice(message: String) {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = RedNegative.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text       = "Notice",
                style      = MaterialTheme.typography.labelMedium,
                color      = RedNegative,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = message,
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray
            )
        }
    }
}

@Composable
private fun FintechPillButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(RedNegative)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = PureWhite,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 28.dp)
        )
    }
}

// ── Mapping helpers ───────────────────────────────────────────────────

private fun QueryResult<DashboardSnapshot>.toDashboardUiState(): DashboardUiState {
    return when (this) {
        QueryResult.Loading         -> DashboardUiState(isLoading = true)
        QueryResult.NotConfigured   -> DashboardUiState(
            isLoading      = false,
            isConfigured   = false,
            errorMessage   = "Add `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` or `SUPABASE_ANON_KEY` to load the dashboard."
        )
        QueryResult.BackendNotReady -> DashboardUiState(
            isLoading    = false,
            errorMessage = "The dashboard backend tables or functions are not ready yet."
        )
        is QueryResult.Failure      -> DashboardUiState(isLoading = false, errorMessage = message)
        is QueryResult.Success      -> data.toDashboardUiState()
    }
}

private fun DashboardSnapshot.toDashboardUiState(): DashboardUiState {
    return DashboardUiState(
        isLoading            = false,
        readinessScore       = readinessScore,
        readinessSummary     = buildReadinessSummary(),
        latestResumeScore    = latestResumeScore,
        latestMockScore      = latestMockScore,
        savedJobsCount       = savedJobsCount,
        upcomingDeadlines    = upcomingDeadlines,
        recentActivity       = recentActivity,
        nextStepSuggestions  = buildNextStepSuggestions(),
        isConfigured         = isConfigured
    )
}

private fun DashboardSnapshot.buildReadinessSummary(): String {
    val readiness = readinessScore ?: 0
    return when {
        !isConfigured                                                        -> "Connect Supabase before the dashboard can calculate readiness."
        latestResumeScore == null && latestMockScore == null && savedJobsCount == 0 ->
            "Start by saving one internship, then roast a resume and run a mock interview."
        readiness >= 80 -> "Your prep loop is strong. Keep tightening the role fit."
        readiness >= 50 -> "You have momentum. A cleaner resume score will move you forward."
        else            -> "You need more proof points. Save a role and add a resume or interview score."
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

private fun formatDeadlineLabel(deadline: String): String = "Due ${parseDateLabel(deadline)}"

private fun formatActivityTime(createdAt: String): String {
    val parsed = parseDate(createdAt) ?: return createdAt.substringBefore("T")
    val elapsedMillis = System.currentTimeMillis() - parsed.time
    val minutes = (elapsedMillis / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1          -> "Just now"
        minutes < 60         -> "${minutes}m ago"
        minutes < 60 * 24    -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else                 -> parseDateLabel(createdAt)
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
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX",   Locale.US),
        SimpleDateFormat("yyyy-MM-dd",                Locale.US)
    )
    return formats.firstNotNullOfOrNull { format ->
        runCatching { format.parse(value) }.getOrNull()
    }
}

private fun RepositoryStatus.toSignOutMessage(): String? {
    return when (this) {
        RepositoryStatus.Success          -> null
        RepositoryStatus.NotConfigured    -> "Supabase client config is missing."
        RepositoryStatus.BackendNotReady  -> "The auth backend is not fully ready yet."
        is RepositoryStatus.Failure       -> message
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun String.toInitials(): String {
    return this.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
}
