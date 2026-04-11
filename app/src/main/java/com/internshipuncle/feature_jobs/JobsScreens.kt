package com.internshipuncle.feature_jobs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.CoolGray
import com.internshipuncle.core.design.DeepNavy
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.PaleBlue
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RoyalBlue
import com.internshipuncle.core.design.SkyBlueLight
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.core.ui.PlaceholderScreen
import com.internshipuncle.data.repository.JobsRepository
import com.internshipuncle.domain.model.JobAnalysis
import com.internshipuncle.domain.model.JobCard
import com.internshipuncle.domain.model.JobDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobsUiState(
    val isLoading: Boolean = true,
    val featuredJobs: List<JobCard> = emptyList(),
    val latestJobs: List<JobCard> = emptyList(),
    val savedJobsCount: Int = 0,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && featuredJobs.isEmpty() && latestJobs.isEmpty()
}

data class SavedJobsUiState(
    val isLoading: Boolean = true,
    val jobs: List<JobCard> = emptyList(),
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && jobs.isEmpty()
}

data class JobDetailUiState(
    val isLoading: Boolean = true,
    val job: JobDetail? = null,
    val analysis: JobAnalysis? = null,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val analysisMessage: String? = null,
    val saveErrorMessage: String? = null
)

private data class JobDetailActionState(
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null
)

@HiltViewModel
class JobsViewModel @Inject constructor(
    jobsRepository: JobsRepository
) : ViewModel() {
    val uiState: StateFlow<JobsUiState> = combine(
        jobsRepository.featuredJobs(),
        jobsRepository.latestJobs(),
        jobsRepository.savedJobs()
    ) { featuredResult, latestResult, savedResult ->
        val featuredJobs = featuredResult.successData().orEmpty()
        val latestJobs = latestResult.successData().orEmpty()
        val featuredIds = featuredJobs.map(JobCard::id).toSet()

        JobsUiState(
            isLoading = featuredResult is QueryResult.Loading || latestResult is QueryResult.Loading,
            featuredJobs = featuredJobs,
            latestJobs = latestJobs.filterNot { it.id in featuredIds },
            savedJobsCount = savedResult.successData()?.size ?: 0,
            errorMessage = featuredResult.toJobsMessage() ?: latestResult.toJobsMessage()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JobsUiState()
    )
}

@HiltViewModel
class SavedJobsViewModel @Inject constructor(
    jobsRepository: JobsRepository
) : ViewModel() {
    val uiState: StateFlow<SavedJobsUiState> = jobsRepository.savedJobs()
        .map { result ->
            SavedJobsUiState(
                isLoading = result is QueryResult.Loading,
                jobs = result.successData().orEmpty(),
                errorMessage = result.toSavedJobsMessage()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SavedJobsUiState()
        )
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobsRepository: JobsRepository
) : ViewModel() {
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])
    private val actionState = MutableStateFlow(JobDetailActionState())

    val uiState: StateFlow<JobDetailUiState> = combine(
        jobsRepository.jobDetail(jobId),
        jobsRepository.jobAnalysis(jobId),
        jobsRepository.savedJobs(),
        actionState
    ) { jobResult, analysisResult, savedJobsResult, currentActionState ->
        val job = jobResult.successData()

        JobDetailUiState(
            isLoading = jobResult is QueryResult.Loading,
            job = job,
            analysis = analysisResult.successData(),
            isSaved = savedJobsResult.successData()?.any { it.id == job?.id } == true,
            isSaving = currentActionState.isSaving,
            errorMessage = when {
                jobResult is QueryResult.Success && job == null -> "This internship is no longer available."
                else -> jobResult.toJobsMessage()
            },
            analysisMessage = analysisResult.toAnalysisMessage(
                emptyMessage = "Analysis has not been generated for this internship yet."
            ),
            saveErrorMessage = currentActionState.saveErrorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JobDetailUiState()
    )

    fun toggleSavedState() {
        val currentState = uiState.value
        val currentJob = currentState.job ?: return

        if (currentState.isSaving) {
            return
        }

        viewModelScope.launch {
            actionState.update {
                it.copy(
                    isSaving = true,
                    saveErrorMessage = null
                )
            }

            val result = if (currentState.isSaved) {
                jobsRepository.unsaveJob(currentJob.id)
            } else {
                jobsRepository.saveJob(currentJob.id)
            }

            actionState.update {
                it.copy(
                    isSaving = false,
                    saveErrorMessage = result.toSaveMessage()
                )
            }
        }
    }
}

// ── Screens ─────────────────────────────────────────────────────────

@Composable
fun JobsScreen(
    viewModel: JobsViewModel = hiltViewModel(),
    onOpenJob: (String) -> Unit,
    onOpenSavedJobs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            JobsStateScreen(
                title = "Loading the curated board",
                description = "Pulling active internships from Supabase.",
                showProgress = true
            )
        }
        uiState.errorMessage != null -> {
            JobsStateScreen(
                title = "Internships unavailable",
                description = uiState.errorMessage ?: "Failed to load internships.",
                actionLabel = "Open saved jobs",
                onAction = onOpenSavedJobs
            )
        }
        uiState.isEmpty -> {
            JobsStateScreen(
                title = "No internships are live yet",
                description = "Once active roles are published, featured and latest internships will appear here."
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.section)
            ) {
                item {
                    Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.small))
                    JobsHero(
                        savedJobsCount = uiState.savedJobsCount,
                        onOpenSavedJobs = onOpenSavedJobs
                    )
                }

                if (uiState.featuredJobs.isNotEmpty()) {
                    item {
                        JobsSectionHeader(
                            title = "Featured Roles",
                            description = "A tighter shortlist for roles worth deeper prep."
                        )
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
                        ) {
                            item { Spacer(modifier = Modifier.width(8.dp)) }
                            items(uiState.featuredJobs) { job ->
                                FeaturedJobCard(
                                    job = job,
                                    onClick = { onOpenJob(job.id) }
                                )
                            }
                            item { Spacer(modifier = Modifier.width(8.dp)) }
                        }
                    }
                }

                if (uiState.latestJobs.isNotEmpty()) {
                    item {
                        JobsSectionHeader(
                            title = "Latest Openings",
                            description = "Freshly active roles, ordered by recency."
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)) {
                            uiState.latestJobs.forEach { job ->
                                JobListCard(
                                    job = job,
                                    onClick = { onOpenJob(job.id) }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(96.dp)) // Bottom padding for nav
                }
            }
        }
    }
}

@Composable
fun SavedJobsScreen(
    viewModel: SavedJobsViewModel = hiltViewModel(),
    onOpenJob: (String) -> Unit,
    onBrowseJobs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            JobsStateScreen(
                title = "Loading saved roles",
                description = "Fetching your shortlist from Supabase.",
                showProgress = true
            )
        }
        uiState.errorMessage != null -> {
            JobsStateScreen(
                title = "Saved internships unavailable",
                description = uiState.errorMessage ?: "Failed to load saved roles.",
                actionLabel = "Browse internships",
                onAction = onBrowseJobs
            )
        }
        uiState.isEmpty -> {
            JobsStateScreen(
                title = "Your shortlist is empty",
                description = "Save internships from the jobs board so your workflow has a concrete target.",
                actionLabel = "Browse internships",
                onAction = onBrowseJobs
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(
                            start = InternshipUncleTheme.spacing.medium,
                            end = InternshipUncleTheme.spacing.medium,
                            top = InternshipUncleTheme.spacing.large
                        ),
                        verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.smallMedium)
                    ) {
                        Text(
                            text = "Saved tracking",
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Your Shortlist",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "Use this list to decide which roles deserve deep analysis and customized resumes.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                items(uiState.jobs) { job ->
                    JobListCard(
                        job = job,
                        onClick = { onOpenJob(job.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
fun JobDetailScreen(
    viewModel: JobDetailViewModel = hiltViewModel(),
    onOpenAnalysis: (String) -> Unit,
    onOpenResume: (String) -> Unit,
    onOpenInterview: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val safeJob = uiState.job

    when {
        uiState.isLoading -> {
            JobsStateScreen(
                title = "Loading internship detail",
                description = "Pulling the role and saved state out of Supabase.",
                showProgress = true
            )
        }
        safeJob == null -> {
            JobsStateScreen(
                title = "Internship unavailable",
                description = uiState.errorMessage ?: "This internship could not be loaded."
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = InternshipUncleTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.large)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = InternshipUncleTheme.spacing.large),
                        verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
                    ) {
                        Text(
                            text = safeJob.company.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = safeJob.title,
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = buildMetadataLine(
                                company = safeJob.company,
                                location = safeJob.location,
                                workMode = safeJob.workMode,
                                employmentType = safeJob.employmentType,
                                stipend = safeJob.stipend,
                                deadline = safeJob.deadline
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (safeJob.isFeatured) {
                                AccentPill(label = "Featured")
                            }
                            if (safeJob.tags.isNotEmpty()) {
                                ScrollablePillRow(labels = safeJob.tags)
                            }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = PureWhite.copy(alpha = 0.85f),
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(InternshipUncleTheme.spacing.mediumLarge),
                            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PillButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::toggleSavedState,
                                    enabled = !uiState.isSaving,
                                    label = if (uiState.isSaving) {
                                        "Updating..."
                                    } else if (uiState.isSaved) {
                                        "Remove saved"
                                    } else {
                                        "Save role"
                                    }
                                )
                                OutlinedPillButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { openExternalLink(context, safeJob.applyUrl) },
                                    enabled = !safeJob.applyUrl.isNullOrBlank(),
                                    label = if (safeJob.applyUrl.isNullOrBlank()) "No generic link" else "Open raw link"
                                )
                            }
                            uiState.saveErrorMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                safeJob.description?.let { description ->
                    item {
                        DetailSectionCard(
                            title = "Role overview",
                            body = description
                        )
                    }
                }

                item {
                    JobsSectionHeader(
                        title = "Role Intelligence",
                        description = "Direct read on requirements and focus points from the JD."
                    )
                }

                uiState.analysis?.let { analysis ->
                    item {
                        DetailSectionCard(
                            title = "Summary",
                            body = analysis.summary
                        )
                    }
                    item {
                        DetailSectionCard(
                            title = "Role reality",
                            body = analysis.roleReality
                        )
                    }
                    if (analysis.requiredSkills.isNotEmpty()) {
                        item {
                            DetailListCard(
                                title = "Required skills",
                                values = analysis.requiredSkills
                            )
                        }
                    }
                    if (analysis.topKeywords.isNotEmpty()) {
                        item {
                            DetailListCard(
                                title = "Top keywords",
                                values = analysis.topKeywords
                            )
                        }
                    }
                    if (analysis.likelyInterviewTopics.isNotEmpty()) {
                        item {
                            DetailListCard(
                                title = "Likely interview topics",
                                values = analysis.likelyInterviewTopics
                            )
                        }
                    }
                    analysis.difficulty?.let { difficulty ->
                        item {
                            DetailSectionCard(
                                title = "Difficulty",
                                body = difficulty.replaceFirstChar { it.titlecase(Locale.US) }
                            )
                        }
                    }
                } ?: item {
                    DetailSectionCard(
                        title = "Analysis status",
                        body = uiState.analysisMessage ?: "Analysis not yet generated for this internship."
                    )
                }

                // Call to action surface
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = DeepNavy,
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(InternshipUncleTheme.spacing.mediumLarge),
                            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
                        ) {
                            Text(
                                text = "Take Action",
                                style = MaterialTheme.typography.titleLarge,
                                color = PureWhite
                            )
                            Text(
                                text = "Move into prep state with this specific role as your target.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite.copy(alpha = 0.78f)
                            )
                            PillButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenAnalysis(safeJob.id) },
                                enabled = true,
                                label = "Deep dive analysis"
                            )
                            PillButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenResume(safeJob.id) },
                                enabled = true,
                                label = "Target resume to this role"
                            )
                            PillButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenInterview(safeJob.id) },
                                enabled = true,
                                label = "Mock interview against JD"
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }
    }
}

// ── Components ──────────────────────────────────────────────────────

@Composable
private fun JobsStateScreen(
    title: String,
    description: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    PlaceholderScreen(
        eyebrow = "Jobs",
        title = title,
        description = description,
        actions = {
            if (showProgress) {
                CircularProgressIndicator(color = RoyalBlue)
            }
            if (actionLabel != null && onAction != null) {
                PillButton(
                    onClick = onAction,
                    enabled = true,
                    isLoading = false,
                    label = actionLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
private fun JobsHero(
    savedJobsCount: Int,
    onOpenSavedJobs: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InternshipUncleTheme.spacing.medium),
        shape = RoundedCornerShape(24.dp),
        color = DeepNavy,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "DISCOVER",
                style = MaterialTheme.typography.labelMedium,
                color = PureWhite.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Target roles\nworth the effort.",
                style = MaterialTheme.typography.displayMedium,
                color = PureWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Curated internships to anchor your prep workflow.",
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite.copy(alpha = 0.78f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            PillButton(
                onClick = onOpenSavedJobs,
                enabled = true,
                isLoading = false,
                label = if (savedJobsCount > 0) "Your Shortlist ($savedJobsCount)" else "Your Shortlist"
            )
        }
    }
}

@Composable
private fun JobsSectionHeader(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier.padding(horizontal = InternshipUncleTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeaturedJobCard(
    job: JobCard,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(300.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.88f),
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                AccentPill(label = "Featured")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = job.company,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CoolGray
                )
            }
            Text(
                text = buildCardSubline(job),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (job.tags.isNotEmpty()) {
                ScrollablePillRow(labels = job.tags.take(3))
            }
        }
    }
}

@Composable
private fun JobListCard(
    job: JobCard,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InternshipUncleTheme.spacing.medium)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = PureWhite.copy(alpha = 0.82f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = job.company,
                style = MaterialTheme.typography.bodyMedium,
                color = CoolGray
            )
            Text(
                text = buildCardSubline(job),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (job.tags.isNotEmpty()) {
                ScrollablePillRow(labels = job.tags.take(4))
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailListCard(
    title: String,
    values: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            values.forEachIndexed { index, value ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalBlue
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Generic Sub-composables ──────────────────────────────────────────

@Composable
private fun PillButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
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
    enabled: Boolean,
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
private fun ScrollablePillRow(
    labels: List<String>
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            Surface(
                color = PureWhite.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolGray
                )
            }
        }
    }
}

@Composable
private fun AccentPill(
    label: String
) {
    Surface(
        color = PaleBlue,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = RoyalBlue,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

private fun buildCardSubline(job: JobCard): String {
    return listOfNotNull(
        job.location?.takeIf(String::isNotBlank),
        job.workMode?.takeIf(String::isNotBlank),
        job.stipend?.takeIf(String::isNotBlank),
        job.deadline?.toReadableDeadline()?.let { "Deadline $it" }
    ).joinToString(" • ")
}

private fun buildMetadataLine(
    company: String,
    location: String?,
    workMode: String?,
    employmentType: String?,
    stipend: String?,
    deadline: String?
): String {
    return listOfNotNull(
        company,
        location?.takeIf(String::isNotBlank),
        workMode?.takeIf(String::isNotBlank),
        employmentType?.takeIf(String::isNotBlank),
        stipend?.takeIf(String::isNotBlank),
        deadline?.toReadableDeadline()?.let { "Deadline $it" }
    ).joinToString(" • ")
}

private fun String.toReadableDeadline(): String {
    val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    val inputFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )
    return inputFormats.firstNotNullOfOrNull { format ->
        runCatching { format.parse(this) }.getOrNull()
    }?.let(outputFormat::format) ?: substringBefore("T")
}

private fun openExternalLink(context: Context, url: String?) {
    val safeUrl = url?.takeIf(String::isNotBlank) ?: return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, safeUrl.toUri()))
    }
}

private fun <T> QueryResult<T>.successData(): T? {
    return (this as? QueryResult.Success<T>)?.data
}

private fun QueryResult<*>.toJobsMessage(): String? {
    return when (this) {
        QueryResult.Loading -> null
        QueryResult.NotConfigured -> "Supabase client config is missing."
        QueryResult.BackendNotReady -> "The jobs backend is not fully ready yet."
        is QueryResult.Failure -> message
        is QueryResult.Success<*> -> null
    }
}

private fun QueryResult<*>.toSavedJobsMessage(): String? {
    return when (this) {
        QueryResult.Loading -> null
        QueryResult.NotConfigured -> "Supabase client config is missing."
        QueryResult.BackendNotReady -> "The saved jobs backend is not fully ready yet."
        is QueryResult.Failure -> message
        is QueryResult.Success<*> -> null
    }
}

private fun QueryResult<JobAnalysis?>.toAnalysisMessage(emptyMessage: String): String? {
    return when (this) {
        QueryResult.Loading -> null
        QueryResult.NotConfigured -> "Supabase client config is missing."
        QueryResult.BackendNotReady -> "Job analysis data is not ready yet."
        is QueryResult.Failure -> message
        is QueryResult.Success -> if (data == null) emptyMessage else null
    }
}

private fun RepositoryStatus.toSaveMessage(): String? {
    return when (this) {
        RepositoryStatus.Success -> null
        RepositoryStatus.NotConfigured -> "Supabase client config is missing."
        RepositoryStatus.BackendNotReady -> "The save jobs backend is not fully ready yet."
        is RepositoryStatus.Failure -> message
    }
}
