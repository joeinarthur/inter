package com.internshipuncle.feature_analyze

import com.internshipuncle.core.design.SurfaceGray
import com.internshipuncle.core.design.SlateGray
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.CharcoalDark
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.ui.PlaceholderScreen
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

// ── Screens ─────────────────────────────────────────────────────────

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val job = uiState.job

    when {
        uiState.isLoading -> AnalysisStateScreen(
            title = "Loading intelligence",
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
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Fintech page header ────────────────────────────────
        Column(
            modifier = Modifier.padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "ROLE INTELLIGENCE",
                style = MaterialTheme.typography.labelMedium,
                color = SlateGray,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text(
                text = job.title,
                style = MaterialTheme.typography.displaySmall,
                color = InkBlack,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    job.company,
                    job.location?.takeIf(String::isNotBlank),
                    job.workMode?.takeIf(String::isNotBlank)
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = SlateGray
            )
            if (job.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    job.tags.take(5).forEach { tag ->
                        Surface(
                            color = SurfaceGray,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateGray
                            )
                        }
                    }
                }
            }
        }

        if (analysis == null) {
            AnalysisNoticeCard(
                title = "Analysis pending",
                body = message ?: "No structured analysis was returned yet for this role."
            )
        } else {
            AnalysisSectionCard(title = "Summary", body = analysis.summary)
            AnalysisSectionCard(title = "Role Reality", body = analysis.roleReality)

            if (analysis.requiredSkills.isNotEmpty()) {
                AnalysisListCard(title = "Required Skills", values = analysis.requiredSkills)
            }
            if (analysis.preferredSkills.isNotEmpty()) {
                AnalysisListCard(title = "Preferred Skills", values = analysis.preferredSkills)
            }
            if (analysis.topKeywords.isNotEmpty()) {
                AnalysisListCard(title = "Top Keywords", values = analysis.topKeywords)
            }
            if (analysis.likelyInterviewTopics.isNotEmpty()) {
                AnalysisListCard(title = "Likely Interview Topics", values = analysis.likelyInterviewTopics)
            }
            analysis.difficulty?.takeIf(String::isNotBlank)?.let { difficulty ->
                AnalysisSectionCard(
                    title = "Difficulty",
                    body = difficulty.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                )
            }
        }

        message?.let {
            AnalysisNoticeCard(title = "Backend Note", body = it)
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

// ── Components ──────────────────────────────────────────────────────

@Composable
private fun AnalysisSectionCard(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = InkBlack,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = SlateGray
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DividerGray)
    }
}

@Composable
private fun AnalysisListCard(
    title: String,
    values: List<String>
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = InkBlack,
            fontWeight = FontWeight.SemiBold
        )
        values.forEachIndexed { index, value ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkBlack,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SlateGray
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DividerGray)
    }
}

@Composable
private fun AnalysisNoticeCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = InkBlack,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateGray
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
    PlaceholderScreen(
        eyebrow = "Analysis",
        title = title,
        description = description,
        actions = {
            if (showProgress) {
                CircularProgressIndicator(color = InkBlack)
            }
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

private fun <T> QueryResult<T>.successData(): T? {
    return (this as? QueryResult.Success<T>)?.data
}
