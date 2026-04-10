package com.internshipuncle.feature_dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.Cloud
import com.internshipuncle.core.design.Graphite
import com.internshipuncle.core.design.InternshipUncleTheme
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
                CircularProgressIndicator()
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
                Button(onClick = viewModel::refresh) { Text("Retry") }
                OutlinedButton(onClick = onOpenJobs) { Text("Open jobs") }
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
        verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
    ) {
        Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
        DashboardHeroCard(uiState = uiState, onRefresh = onRefresh, onSignOut = onSignOut)
        DashboardActionRow(
            onOpenJobs = onOpenJobs,
            onOpenSavedJobs = onOpenSavedJobs,
            onOpenResumeLab = onOpenResumeLab,
            onOpenInterview = onOpenInterview
        )

        if (uiState.nextStepSuggestions.isNotEmpty()) {
            DashboardSectionCard(
                title = "Focus next",
                body = "A few practical steps to move the score.",
                content = {
                    uiState.nextStepSuggestions.take(3).forEach { suggestion ->
                        Text(
                            text = "- $suggestion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        MetricGrid(
            latestResumeScore = uiState.latestResumeScore,
            latestMockScore = uiState.latestMockScore,
            savedJobsCount = uiState.savedJobsCount,
            upcomingDeadlinesCount = uiState.upcomingDeadlines.size
        )

        DashboardSectionHeader(
            title = "Upcoming deadlines",
            body = if (uiState.upcomingDeadlines.isNotEmpty()) {
                "Deadlines pulled from your shortlist and active jobs."
            } else {
                "No deadlines yet. Save roles with deadlines to make this section useful."
            }
        )
        if (uiState.upcomingDeadlines.isNotEmpty()) {
            uiState.upcomingDeadlines.take(3).forEach { deadline ->
                DeadlineCard(item = deadline, onOpenJob = onOpenJob)
            }
        } else {
            EmptyInlineCard(
                title = "No upcoming deadlines",
                body = "Save internships with deadlines or open the jobs board to find the next role worth moving on."
            )
        }

        DashboardSectionHeader(
            title = "Recent activity",
            body = if (uiState.recentActivity.isNotEmpty()) {
                "The latest prep work that actually changed your readiness."
            } else {
                "Activity will appear after you roast a resume, practice a mock interview, or save a role."
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
            EmptyInlineCard(
                title = "No recent activity",
                body = "Use the quick actions above to generate a useful timeline here."
            )
        }

        uiState.signOutError?.let { error ->
            DashboardNoticeCard(
                title = "Sign out issue",
                body = error,
                accent = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
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
    PlaceholderScreen(
        eyebrow = "Dashboard",
        title = "Nothing to show yet",
        description = "This dashboard becomes useful after you connect it to a real target role.",
        sections = listOf(
            "Suggested next moves" to listOf(
                "Save one internship from Jobs.",
                "Upload or roast a resume to get a first score.",
                "Run one mock interview so you can compare progress over time."
            ).joinToString("\n"),
            "Why this matters" to "Readiness is computed from profile completion, latest scores, saved jobs, and deadlines."
        ),
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpenJobs, modifier = Modifier.fillMaxWidth()) { Text("Browse jobs") }
                OutlinedButton(onClick = onOpenSavedJobs, modifier = Modifier.fillMaxWidth()) { Text("Saved jobs") }
                OutlinedButton(onClick = onOpenResumeLab, modifier = Modifier.fillMaxWidth()) { Text("Resume lab") }
                OutlinedButton(onClick = onOpenInterview, modifier = Modifier.fillMaxWidth()) { Text("Interview prep") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                    OutlinedButton(onClick = onSignOut, enabled = !isSigningOut, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isSigningOut) "Signing out..." else "Sign out")
                    }
                }
                signOutError?.let { error ->
                    DashboardNoticeCard(
                        title = "Sign out issue",
                        body = error,
                        accent = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun DashboardHeroCard(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Graphite,
        contentColor = Cloud,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Readiness dashboard",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cloud.copy(alpha = 0.72f)
                    )
                    Text(
                        text = uiState.readinessScore?.let { "$it/100" } ?: "--/100",
                        style = MaterialTheme.typography.displayLarge,
                        color = Cloud,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onRefresh) { Text("Refresh", color = Cloud) }
                    TextButton(onClick = onSignOut, enabled = !uiState.isSigningOut) {
                        Text(if (uiState.isSigningOut) "Signing out..." else "Sign out", color = Cloud)
                    }
                }
            }

            Text(
                text = uiState.readinessSummary,
                style = MaterialTheme.typography.bodyLarge,
                color = Cloud.copy(alpha = 0.82f)
            )
            LinearProgressIndicator(
                progress = ((uiState.readinessScore ?: 0).coerceIn(0, 100) / 100f),
                modifier = Modifier.fillMaxWidth(),
                color = Cloud,
                trackColor = Cloud.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun DashboardActionRow(
    onOpenJobs: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenResumeLab: () -> Unit,
    onOpenInterview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenJobs) { Text("Browse jobs") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenSavedJobs) { Text("Saved jobs") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenResumeLab) { Text("Resume lab") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenInterview) { Text("Interview prep") }
    }
}

@Composable
private fun MetricGrid(
    latestResumeScore: Int?,
    latestMockScore: Int?,
    savedJobsCount: Int,
    upcomingDeadlinesCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            title = "Latest resume score",
            value = latestResumeScore?.toString() ?: "--",
            subtitle = if (latestResumeScore == null) "No roast yet" else "Latest feedback score"
        )
        MetricCard(
            title = "Latest mock score",
            value = latestMockScore?.toString() ?: "--",
            subtitle = if (latestMockScore == null) "No session yet" else "Latest interview score"
        )
        MetricCard(
            title = "Saved jobs",
            value = savedJobsCount.toString(),
            subtitle = if (savedJobsCount == 0) "Shortlist is empty" else "Roles tied to your prep"
        )
        MetricCard(
            title = "Upcoming deadlines",
            value = upcomingDeadlinesCount.toString(),
            subtitle = if (upcomingDeadlinesCount == 0) "None loaded yet" else "Deadlines worth watching"
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DashboardSectionCard(
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardSectionHeader(title = title, body = body)
            content()
        }
    }
}

@Composable
private fun DeadlineCard(
    item: DashboardDeadlineItem,
    onOpenJob: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    item.company,
                    item.location?.takeIf(String::isNotBlank),
                    item.workMode?.takeIf(String::isNotBlank)
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CardDefaults.shape) {
                Text(
                    text = formatDeadlineLabel(item.deadline),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(onClick = { onOpenJob(item.jobId) }) { Text("Open role") }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = item.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.score?.let {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CardDefaults.shape) {
                    Text(
                        text = "$it",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item.createdAt?.takeIf(String::isNotBlank)?.let { createdAt ->
                Text(text = formatActivityTime(createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun EmptyInlineCard(
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardNoticeCard(
    title: String,
    body: String,
    accent: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = accent)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
