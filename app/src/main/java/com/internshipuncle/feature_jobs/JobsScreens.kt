package com.internshipuncle.feature_jobs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.model.RepositoryStatus
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
            errorMessage = featuredResult.toJobsMessage()
                ?: latestResult.toJobsMessage()
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
                title = "Loading the internship board",
                description = "Pulling active curated roles from Supabase.",
                showProgress = true
            )
        }

        uiState.errorMessage != null -> {
            JobsStateScreen(
                title = "Internships are unavailable right now",
                description = uiState.errorMessage ?: "Internships are unavailable right now.",
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
                    JobsHero(
                        savedJobsCount = uiState.savedJobsCount,
                        onOpenSavedJobs = onOpenSavedJobs
                    )
                }

                if (uiState.featuredJobs.isNotEmpty()) {
                    item {
                        JobsSectionHeader(
                            title = "Featured internships",
                            description = "A tighter shortlist for roles worth deeper prep."
                        )
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
                        ) {
                            items(uiState.featuredJobs) { job ->
                                FeaturedJobCard(
                                    job = job,
                                    onClick = { onOpenJob(job.id) }
                                )
                            }
                        }
                    }
                }

                if (uiState.latestJobs.isNotEmpty()) {
                    item {
                        JobsSectionHeader(
                            title = "Latest internships",
                            description = "Freshly active roles, ordered by recency instead of noise."
                        )
                    }
                    items(uiState.latestJobs) { job ->
                        JobListCard(
                            job = job,
                            onClick = { onOpenJob(job.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.small))
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
                title = "Loading saved internships",
                description = "Fetching your shortlist from `saved_jobs`.",
                showProgress = true
            )
        }

        uiState.errorMessage != null -> {
            JobsStateScreen(
                title = "Saved internships are unavailable",
                description = uiState.errorMessage ?: "Saved internships are unavailable.",
                actionLabel = "Browse internships",
                onAction = onBrowseJobs
            )
        }

        uiState.isEmpty -> {
            JobsStateScreen(
                title = "Your shortlist is empty",
                description = "Save internships from the jobs board so the target-role workflow has something concrete to build around.",
                actionLabel = "Browse internships",
                onAction = onBrowseJobs
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = InternshipUncleTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = InternshipUncleTheme.spacing.large),
                        verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.small)
                    ) {
                        Text(
                            text = "Saved internships",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "Your active shortlist. Use it to decide which roles deserve analysis, resume targeting, and interview prep.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(uiState.jobs) { job ->
                    JobListCard(
                        job = job,
                        onClick = { onOpenJob(job.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.small))
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
    onOpenInterview: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val safeJob = uiState.job

    when {
        uiState.isLoading -> {
            JobsStateScreen(
                title = "Loading internship detail",
                description = "Pulling the role, analysis, and saved state from Supabase.",
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
                        if (safeJob.isFeatured) {
                            AccentPill(label = "Featured")
                        }
                        if (safeJob.tags.isNotEmpty()) {
                            ScrollablePillRow(labels = safeJob.tags)
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::toggleSavedState,
                                    enabled = !uiState.isSaving
                                ) {
                                    Text(
                                        if (uiState.isSaving) {
                                            "Updating..."
                                        } else if (uiState.isSaved) {
                                            "Remove from saved"
                                        } else {
                                            "Save internship"
                                        }
                                    )
                                }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { openExternalLink(context, safeJob.applyUrl) },
                                    enabled = !safeJob.applyUrl.isNullOrBlank()
                                ) {
                                    Text(
                                        if (safeJob.applyUrl.isNullOrBlank()) {
                                            "Apply link unavailable"
                                        } else {
                                            "Open apply link"
                                        }
                                    )
                                }
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
                        title = "Analysis",
                        description = "Structured read of the role. This is data from `job_analysis`, not generated on-device."
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
                        body = uiState.analysisMessage ?: "Analysis has not been generated for this internship yet."
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Next steps",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Once the role makes sense, move into analysis, resume targeting, or interview practice from here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenAnalysis(safeJob.id) }
                            ) {
                                Text("Open analysis workspace")
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenResume(safeJob.id) }
                            ) {
                                Text("Open Resume Lab")
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onOpenInterview
                            ) {
                                Text("Practice interview")
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.small))
                }
            }
        }
    }
}

@Composable
private fun JobsHero(
    savedJobsCount: Int,
    onOpenSavedJobs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = InternshipUncleTheme.spacing.medium,
                end = InternshipUncleTheme.spacing.medium,
                top = InternshipUncleTheme.spacing.large
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Text(
                text = "Find internships worth preparing for.",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "This board is intentionally curated. Featured roles surface the strongest targets first, and the latest section keeps the funnel moving.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenSavedJobs) {
                Text(
                    if (savedJobsCount > 0) {
                        "Saved jobs ($savedJobsCount)"
                    } else {
                        "Saved jobs"
                    }
                )
            }
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
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
    Card(
        modifier = Modifier
            .width(288.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccentPill(label = "Featured")
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = job.company,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = buildCardSubline(job),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (job.tags.isNotEmpty()) {
                ScrollablePillRow(labels = job.tags.take(4))
            }
        }
    }
}

@Composable
private fun JobListCard(
    job: JobCard,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InternshipUncleTheme.spacing.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = job.company,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = buildCardSubline(job),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (job.tags.isNotEmpty()) {
                ScrollablePillRow(labels = job.tags.take(5))
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    body: String
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
                text = title,
                style = MaterialTheme.typography.titleMedium
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            values.forEachIndexed { index, value ->
                Text(
                    text = "${index + 1}. $value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CardDefaults.shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium
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
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = CardDefaults.shape
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun JobsStateScreen(
    title: String,
    description: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(InternshipUncleTheme.spacing.large),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(InternshipUncleTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
            ) {
                if (showProgress) {
                    CircularProgressIndicator()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    OutlinedButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

private fun buildCardSubline(
    job: JobCard
): String {
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

private fun openExternalLink(
    context: Context,
    url: String?
) {
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

private fun QueryResult<JobAnalysis?>.toAnalysisMessage(
    emptyMessage: String
): String? {
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
