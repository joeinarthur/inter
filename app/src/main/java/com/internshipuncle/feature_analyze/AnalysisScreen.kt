package com.internshipuncle.feature_analyze

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.data.repository.JobsRepository
import com.internshipuncle.domain.model.JobAnalysis
import com.internshipuncle.domain.model.JobDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalysisUiState(
    val isLoading: Boolean = true,
    val job: JobDetail? = null,
    val analysis: JobAnalysis? = null,
    val errorMessage: String? = null,
    val backendMessage: String? = null
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobsRepository: JobsRepository
) : ViewModel() {
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    val uiState: StateFlow<AnalysisUiState> = combine(
        jobsRepository.jobDetail(jobId),
        jobsRepository.jobAnalysis(jobId)
    ) { jobResult, analysisResult ->
        val job = jobResult.successData()
        val analysis = analysisResult.successData()

        AnalysisUiState(
            isLoading = jobResult is QueryResult.Loading || analysisResult is QueryResult.Loading,
            job = job,
            analysis = analysis,
            errorMessage = when {
                jobResult is QueryResult.Failure -> jobResult.message
                analysisResult is QueryResult.Failure -> analysisResult.message
                jobResult is QueryResult.Success && job == null -> "This internship is no longer available."
                else -> null
            },
            backendMessage = when {
                analysisResult is QueryResult.BackendNotReady ->
                    "Job analysis data is not ready yet for this role."
                jobResult is QueryResult.NotConfigured || analysisResult is QueryResult.NotConfigured ->
                    "Supabase config is missing."
                else -> null
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalysisUiState()
    )

    fun refresh() {
        jobsRepository.refresh()
    }
}

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val job = uiState.job

    when {
        uiState.isLoading -> AnalysisStateScreen(
            title = "Loading job analysis",
            description = "Pulling the role summary, skill gaps, and interview topics from Supabase.",
            showProgress = true
        )

        job == null -> AnalysisStateScreen(
            title = "Analysis unavailable",
            description = uiState.errorMessage ?: uiState.backendMessage ?: "This analysis could not be loaded.",
            actionLabel = "Retry",
            onAction = viewModel::refresh
        )

        else -> AnalysisContent(
            job = job,
            analysis = uiState.analysis,
            message = uiState.errorMessage ?: uiState.backendMessage
        )
    }
}

@Composable
private fun AnalysisContent(
    job: JobDetail,
    analysis: JobAnalysis?,
    message: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = job.title,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = listOfNotNull(
                job.company,
                job.location?.takeIf(String::isNotBlank),
                job.workMode?.takeIf(String::isNotBlank),
                job.employmentType?.takeIf(String::isNotBlank)
            ).joinToString(" | "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (job.tags.isNotEmpty()) {
            Text(
                text = job.tags.joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (analysis == null) {
            AnalysisNoticeCard(
                title = "Analysis pending",
                body = message ?: "No structured analysis was returned yet for this role."
            )
        } else {
            AnalysisSectionCard(title = "Summary", body = analysis.summary)
            AnalysisSectionCard(title = "Role reality", body = analysis.roleReality)

            if (analysis.requiredSkills.isNotEmpty()) {
                AnalysisListCard(
                    title = "Required skills",
                    values = analysis.requiredSkills
                )
            }

            if (analysis.preferredSkills.isNotEmpty()) {
                AnalysisListCard(
                    title = "Preferred skills",
                    values = analysis.preferredSkills
                )
            }

            if (analysis.topKeywords.isNotEmpty()) {
                AnalysisListCard(
                    title = "Top keywords",
                    values = analysis.topKeywords
                )
            }

            if (analysis.likelyInterviewTopics.isNotEmpty()) {
                AnalysisListCard(
                    title = "Likely interview topics",
                    values = analysis.likelyInterviewTopics
                )
            }

            analysis.difficulty?.takeIf(String::isNotBlank)?.let { difficulty ->
                AnalysisSectionCard(
                    title = "Difficulty",
                    body = difficulty.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                )
            }
        }

        message?.let {
            AnalysisNoticeCard(
                title = "Backend note",
                body = it
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AnalysisSectionCard(
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalysisListCard(
    title: String,
    values: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            values.forEachIndexed { index, value ->
                Text(
                    text = "${index + 1}. $value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AnalysisNoticeCard(
    title: String,
    body: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalysisStateScreen(
    title: String,
    description: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showProgress) {
                    CircularProgressIndicator()
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = "Analysis is rendered from structured backend data, not generated on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

private fun <T> QueryResult<T>.successData(): T? {
    return (this as? QueryResult.Success<T>)?.data
}
