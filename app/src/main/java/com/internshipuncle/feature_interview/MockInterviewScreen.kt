package com.internshipuncle.feature_interview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.internshipuncle.core.design.CoolGray
import com.internshipuncle.core.design.DeepNavy
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RoyalBlue
import com.internshipuncle.core.design.SkyBlueMedium
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.ui.PlaceholderScreen
import com.internshipuncle.data.repository.InterviewRepository
import com.internshipuncle.data.repository.JobsRepository
import com.internshipuncle.data.repository.ResumeRepository
import com.internshipuncle.domain.model.InterviewSessionSummary
import com.internshipuncle.domain.model.JobCard
import com.internshipuncle.domain.model.MockInterviewAnswerEvaluation
import com.internshipuncle.domain.model.MockInterviewPracticeSummary
import com.internshipuncle.domain.model.MockInterviewQuestionProgress
import com.internshipuncle.domain.model.MockInterviewSessionDetail
import com.internshipuncle.domain.model.toPracticeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DifficultyOptions = listOf("easy", "medium", "hard")
private val ModeOptions = listOf("quick", "full", "pressure", "resume_crossfire")

data class MockInterviewSetupUiState(
    val isLoading: Boolean = true,
    val selectedTargetJobId: String? = null,
    val selectedTargetJobTitle: String? = null,
    val selectedTargetJobCompany: String? = null,
    val targetJobDeselected: Boolean = false,
    val suggestedJobs: List<JobCard> = emptyList(),
    val recentSessions: List<InterviewSessionSummary> = emptyList(),
    val hasResume: Boolean = false,
    val roleName: String = "",
    val difficulty: String = "medium",
    val mode: String = "quick",
    val includeResume: Boolean = true,
    val isCreating: Boolean = false,
    val createdSessionId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val canCreate: Boolean
        get() = roleName.isNotBlank() && !isCreating
}

data class MockInterviewSessionUiState(
    val isLoading: Boolean = true,
    val session: MockInterviewSessionDetail? = null,
    val activeQuestionId: String? = null,
    val draftAnswer: String = "",
    val isSubmitting: Boolean = false,
    val latestEvaluation: MockInterviewAnswerEvaluation? = null,
    val skippedQuestionIds: Set<String> = emptySet(),
    val completedSessionId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val currentQuestion: MockInterviewQuestionProgress?
        get() = session?.currentQuestion(activeQuestionId, skippedQuestionIds)

    val activeEvaluation: MockInterviewAnswerEvaluation?
        get() = latestEvaluation ?: currentQuestion?.answer

    val answeredCount: Int
        get() {
            val backendCount = session?.answeredCount ?: 0
            val localCredit = if (
                latestEvaluation != null &&
                session?.questions?.any { it.id == latestEvaluation.questionId && !it.isAnswered } == true
            ) {
                1
            } else {
                0
            }
            return backendCount + localCredit
        }

    val skippedCount: Int
        get() = skippedQuestionIds.size

    val totalQuestions: Int
        get() = session?.totalQuestions ?: 0

    val progressFraction: Float
        get() {
            val total = totalQuestions
            if (total == 0) return 0f
            return (answeredCount + skippedCount).toFloat() / total.toFloat()
        }

    val isComplete: Boolean
        get() = session != null && (answeredCount + skippedCount) >= totalQuestions && totalQuestions > 0

    val progressLabel: String
        get() = if (totalQuestions == 0) "0/0" else "${answeredCount + skippedCount}/$totalQuestions"

    val canSubmit: Boolean
        get() = currentQuestion != null && draftAnswer.isNotBlank() && !isSubmitting
}

data class MockInterviewSummaryUiState(
    val isLoading: Boolean = true,
    val session: MockInterviewSessionDetail? = null,
    val skippedCount: Int = 0,
    val summary: MockInterviewPracticeSummary? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MockInterviewSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val interviewRepository: InterviewRepository,
    private val jobsRepository: JobsRepository,
    private val resumeRepository: ResumeRepository
) : ViewModel() {
    private val targetJobId: String? = savedStateHandle["targetJobId"]
    private val operationState = MutableStateFlow(MockInterviewSetupUiState())

    private val targetJobFlow: Flow<QueryResult<JobCard?>> = if (targetJobId.isNullOrBlank()) {
        flowOf(QueryResult.Success<JobCard?>(null))
    } else {
        jobsRepository.jobDetail(targetJobId).map { result ->
            when (result) {
                QueryResult.Loading -> QueryResult.Loading
                QueryResult.NotConfigured -> QueryResult.NotConfigured
                QueryResult.BackendNotReady -> QueryResult.BackendNotReady
                is QueryResult.Failure -> QueryResult.Failure(result.message, result.cause)
                is QueryResult.Success -> QueryResult.Success<JobCard?>(
                    result.data?.let {
                        JobCard(
                            id = it.id,
                            title = it.title,
                            company = it.company,
                            location = it.location,
                            stipend = it.stipend,
                            workMode = it.workMode,
                            deadline = it.deadline,
                            tags = it.tags,
                            isFeatured = it.isFeatured
                        )
                    }
                )
            }
        }
    }

    private val suggestedJobsFlow: Flow<List<JobCard>> = combine(
        jobsRepository.savedJobs(),
        jobsRepository.latestJobs()
    ) { savedResult, latestResult ->
        val saved = savedResult.successData().orEmpty()
        val latest = latestResult.successData().orEmpty()
        (saved + latest).distinctBy(JobCard::id).take(8)
    }

    val uiState: StateFlow<MockInterviewSetupUiState> = combine(
        targetJobFlow,
        suggestedJobsFlow,
        resumeRepository.resumes(),
        interviewRepository.recentSessions(),
        operationState
    ) { targetJobResult, suggestedJobs, resumes, recentSessions, state ->
        val targetJob = targetJobResult.successData()
        val selectedJobId = if (state.targetJobDeselected) {
            state.selectedTargetJobId
        } else {
            state.selectedTargetJobId ?: targetJob?.id
        }
        val selectedJob = when {
            selectedJobId == targetJob?.id -> targetJob
            else -> suggestedJobs.firstOrNull { it.id == selectedJobId }
        }
        val prefilledRole = state.roleName.ifBlank { selectedJob?.title.orEmpty() }
        val hasResume = resumes.isNotEmpty()
        val includeResume = if (state.includeResume && !hasResume) false else state.includeResume

        state.copy(
            isLoading = false,
            selectedTargetJobId = selectedJob?.id ?: targetJob?.id,
            selectedTargetJobTitle = selectedJob?.title ?: targetJob?.title,
            selectedTargetJobCompany = selectedJob?.company ?: targetJob?.company,
            suggestedJobs = suggestedJobs,
            recentSessions = recentSessions,
            hasResume = hasResume,
            roleName = prefilledRole,
            includeResume = includeResume,
            errorMessage = when {
                targetJobResult is QueryResult.Failure -> targetJobResult.message
                else -> state.errorMessage
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MockInterviewSetupUiState()
    )

    fun onRoleChange(value: String) {
        operationState.update { it.copy(roleName = value, errorMessage = null, infoMessage = null) }
    }

    fun onDifficultyChange(value: String) {
        if (value !in DifficultyOptions) return
        operationState.update { it.copy(difficulty = value, errorMessage = null, infoMessage = null) }
    }

    fun onModeChange(value: String) {
        if (value !in ModeOptions) return
        operationState.update { it.copy(mode = value, errorMessage = null, infoMessage = null) }
    }

    fun onIncludeResumeChange(value: Boolean) {
        operationState.update { it.copy(includeResume = value, errorMessage = null, infoMessage = null) }
    }

    fun onTargetJobSelected(job: JobCard?) {
        operationState.update { state ->
            state.copy(
                selectedTargetJobId = job?.id,
                selectedTargetJobTitle = job?.title,
                selectedTargetJobCompany = job?.company,
                targetJobDeselected = job == null,
                roleName = if (state.roleName.isBlank()) job?.title.orEmpty() else state.roleName,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun clearCreatedSession() {
        operationState.update { it.copy(createdSessionId = null) }
    }

    fun retryBackend() {
        operationState.update { it.copy(errorMessage = null, infoMessage = null) }
        interviewRepository.refresh()
    }

    fun createSession() {
        val current = uiState.value
        val role = current.roleName.trim()

        if (current.isCreating) return
        if (role.isBlank()) {
            operationState.update {
                it.copy(errorMessage = "Enter a target role before starting the mock interview.")
            }
            return
        }

        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isCreating = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }

            when (
                val result = interviewRepository.createMockSession(
                    targetJobId = current.selectedTargetJobId,
                    roleName = role,
                    difficulty = current.difficulty,
                    mode = current.mode,
                    includeResume = current.includeResume
                )
            ) {
                QueryResult.NotConfigured -> operationState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = "Supabase config is missing."
                    )
                }
                QueryResult.BackendNotReady -> operationState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = "The mock interview backend is not ready yet."
                    )
                }
                is QueryResult.Failure -> operationState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = result.message
                    )
                }
                is QueryResult.Success -> operationState.update {
                    it.copy(
                        isCreating = false,
                        createdSessionId = result.data.id,
                        infoMessage = "Mock interview ready. Loading the session..."
                    )
                }
                QueryResult.Loading -> Unit
            }
        }
    }
}

@HiltViewModel
class MockInterviewSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val interviewRepository: InterviewRepository
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val operationState = MutableStateFlow(MockInterviewSessionUiState())

    val uiState: StateFlow<MockInterviewSessionUiState> = combine(
        interviewRepository.mockSession(sessionId),
        operationState
    ) { sessionResult, state ->
        val session = sessionResult.successData()

        state.copy(
            isLoading = sessionResult is QueryResult.Loading,
            session = session,
            errorMessage = when {
                sessionResult is QueryResult.Failure -> sessionResult.message
                sessionResult is QueryResult.Success && session == null -> "This interview session is no longer available."
                else -> state.errorMessage
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MockInterviewSessionUiState()
    )

    fun onAnswerChange(value: String) {
        operationState.update {
            it.copy(
                draftAnswer = value,
                latestEvaluation = null,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun submitAnswer() {
        val currentState = uiState.value
        val session = currentState.session ?: return
        val question = currentState.currentQuestion ?: return
        val answer = currentState.draftAnswer.trim()

        if (currentState.isSubmitting) return
        if (answer.isBlank()) {
            operationState.update {
                it.copy(errorMessage = "Write an answer before submitting.")
            }
            return
        }

        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }

            when (
                val result = interviewRepository.submitAnswer(
                    sessionId = session.id,
                    questionId = question.id,
                    answerText = answer
                )
            ) {
                QueryResult.NotConfigured -> operationState.update {
                    it.copy(isSubmitting = false, errorMessage = "Supabase config is missing.")
                }
                QueryResult.BackendNotReady -> operationState.update {
                    it.copy(isSubmitting = false, errorMessage = "The answer evaluation backend is not ready yet.")
                }
                is QueryResult.Failure -> operationState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
                is QueryResult.Success -> operationState.update {
                    it.copy(
                        isSubmitting = false,
                        draftAnswer = "",
                        latestEvaluation = result.data,
                        activeQuestionId = question.id,
                        infoMessage = "Feedback saved. Review the evaluation, then move on."
                    )
                }
                QueryResult.Loading -> Unit
            }
        }
    }

    fun skipCurrentQuestion() {
        val currentState = uiState.value
        val session = currentState.session ?: return
        val currentQuestion = currentState.currentQuestion ?: return
        val skippedIds = currentState.skippedQuestionIds + currentQuestion.id
        val nextQuestion = session.nextQuestion(
            currentQuestionId = currentQuestion.id,
            skippedQuestionIds = skippedIds
        )

        operationState.update {
            it.copy(
                skippedQuestionIds = skippedIds,
                activeQuestionId = nextQuestion?.id,
                draftAnswer = "",
                latestEvaluation = null,
                errorMessage = null,
                infoMessage = if (nextQuestion == null) null else "Skipped this question."
            )
        }

        if (nextQuestion == null) {
            operationState.update {
                it.copy(completedSessionId = session.id)
            }
        }
    }

    fun advanceQuestion() {
        val currentState = uiState.value
        val session = currentState.session ?: return
        val currentQuestion = currentState.currentQuestion ?: return
        val nextQuestion = session.nextQuestion(
            currentQuestionId = currentQuestion.id,
            skippedQuestionIds = currentState.skippedQuestionIds
        )

        operationState.update {
            it.copy(
                activeQuestionId = nextQuestion?.id,
                draftAnswer = "",
                latestEvaluation = null,
                errorMessage = null,
                infoMessage = null
            )
        }

        if (nextQuestion == null) {
            operationState.update {
                it.copy(completedSessionId = session.id)
            }
        }
    }

    fun retryBackend() {
        interviewRepository.refresh()
    }
}

@HiltViewModel
class MockInterviewSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val interviewRepository: InterviewRepository
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val skippedCount: Int = checkNotNull(savedStateHandle["skippedCount"])

    val uiState: StateFlow<MockInterviewSummaryUiState> = interviewRepository.mockSession(sessionId)
        .map { result ->
            val session = result.successData()
            MockInterviewSummaryUiState(
                isLoading = result is QueryResult.Loading,
                session = session,
                skippedCount = skippedCount,
                summary = session?.toPracticeSummary(skippedCount),
                errorMessage = when {
                    result is QueryResult.Failure -> result.message
                    result is QueryResult.Success && session == null -> "This session could not be loaded."
                    else -> null
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MockInterviewSummaryUiState()
        )

    fun retryBackend() {
        interviewRepository.refresh()
    }
}

// ── Shared Generic Components ────────────────────────────────────────

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

// ── Screens ────────────────────────────────────────────────────────

@Composable
fun MockInterviewSetupScreen(
    viewModel: MockInterviewSetupViewModel = hiltViewModel(),
    onOpenSession: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTargetJobTitle = uiState.selectedTargetJobTitle

    LaunchedEffect(selectedTargetJobTitle) {
        if (uiState.roleName.isBlank() && selectedTargetJobTitle != null) {
            viewModel.onRoleChange(selectedTargetJobTitle)
        }
    }

    LaunchedEffect(uiState.createdSessionId) {
        uiState.createdSessionId?.let { sessionId ->
            onOpenSession(sessionId)
            viewModel.clearCreatedSession()
        }
    }

    when {
        uiState.isLoading -> MockInterviewStateScreen(
            title = "Loading prep lab",
            description = "Pulling your shortlist, resume context, and history.",
            showProgress = true
        )
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = InternshipUncleTheme.spacing.medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Column(
                modifier = Modifier.padding(top = InternshipUncleTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
            ) {
                Text(
                    text = "MOCK INTERVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Practice lab",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Simulate realistic interview scenarios based on the roles you target.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.errorMessage?.let { message ->
                InterviewNoticeCard(
                    title = "Setup error",
                    body = message,
                    accent = MaterialTheme.colorScheme.error,
                    isDark = false
                )
            }

            uiState.infoMessage?.let { message ->
                InterviewNoticeCard(
                    title = "Generating...",
                    body = message,
                    accent = RoyalBlue,
                    isDark = true
                )
            }

            SetupCard(
                title = "Target Context",
                body = "What are we aiming for? If you selected a target job, we'll anchor around it."
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.roleName,
                    onValueChange = viewModel::onRoleChange,
                    label = { Text("Target role") },
                    singleLine = true
                )
                if (selectedTargetJobTitle != null) {
                    SelectedJobCard(
                        title = selectedTargetJobTitle,
                        company = uiState.selectedTargetJobCompany
                    )
                }
                Text(
                    text = if (uiState.hasResume) {
                        "Resume integration is available. The AI will weave your past experience into follow-ups."
                    } else {
                        "You don't have a resume uploaded. Add one later to get crossfire questions."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SetupCard(
                title = "Base it on a saved role?",
                body = "Optionally generate questions against a specific job description."
            ) {
                if (uiState.suggestedJobs.isEmpty()) {
                    Text(
                        text = "No saved roles available. You can continue with manual text entry above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    JobChoiceRow(
                        jobs = uiState.suggestedJobs,
                        selectedJobId = uiState.selectedTargetJobId,
                        onJobSelected = viewModel::onTargetJobSelected,
                        onClearSelection = { viewModel.onTargetJobSelected(null) }
                    )
                }
            }

            SetupCard(
                title = "Lab Conditions",
                body = "Tune the simulation."
            ) {
                ChoiceSection(
                    title = "Difficulty",
                    values = DifficultyOptions,
                    selectedValue = uiState.difficulty,
                    onSelected = viewModel::onDifficultyChange
                )
                ChoiceSection(
                    title = "Mode",
                    values = ModeOptions,
                    selectedValue = uiState.mode,
                    onSelected = viewModel::onModeChange
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include resume", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Reference your resume in questions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.includeResume,
                        onCheckedChange = viewModel::onIncludeResumeChange,
                        enabled = uiState.hasResume,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = RoyalBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PillButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::createSession,
                enabled = uiState.canCreate,
                isLoading = uiState.isCreating,
                label = "Initialize simulation"
            )

            if (uiState.recentSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.medium))
                SectionTitle(
                    title = "Recent history",
                    subtitle = "Jump back into past results."
                )
                uiState.recentSessions.take(3).forEach { session ->
                    RecentSessionCard(session = session)
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
fun MockInterviewSessionScreen(
    viewModel: MockInterviewSessionViewModel = hiltViewModel(),
    onSessionComplete: (String, Int) -> Unit,
    onOpenSetup: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedSessionId) {
        uiState.completedSessionId?.let { sessionId ->
            onSessionComplete(sessionId, uiState.skippedCount)
        }
    }

    when {
        uiState.isLoading -> MockInterviewStateScreen(
            title = "Warming up session",
            description = "Pulling down your questions and any saved answers.",
            showProgress = true
        )
        uiState.errorMessage != null && uiState.session == null -> MockInterviewStateScreen(
            title = "Session unavailable",
            description = uiState.errorMessage ?: "The session could not be loaded.",
            actionLabel = "Retry",
            onAction = viewModel::retryBackend,
            secondaryActionLabel = "Back to setup",
            onSecondaryAction = onOpenSetup
        )
        else -> {
            val session = uiState.session
            val question = uiState.currentQuestion

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = InternshipUncleTheme.spacing.medium)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
            ) {
                Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
                SessionHeaderCard(
                    session = session,
                    progressLabel = uiState.progressLabel,
                    progressFraction = uiState.progressFraction
                )

                if (question != null) {
                    QuestionCard(
                        question = question,
                        currentIndex = (session?.questions?.indexOfFirst { it.id == question.id } ?: 0).coerceAtLeast(0)
                    )
                    AnswerCard(
                        answer = uiState.draftAnswer,
                        onAnswerChange = viewModel::onAnswerChange,
                        canSubmit = uiState.canSubmit,
                        isSubmitting = uiState.isSubmitting,
                        onSubmit = viewModel::submitAnswer,
                        onSkip = viewModel::skipCurrentQuestion,
                        onNext = viewModel::advanceQuestion,
                        showNext = uiState.activeEvaluation != null || question.answer != null
                    )
                } else {
                    CompletionPreviewCard(
                        title = "Simulation Complete",
                        body = "You've answered or skipped everything. Generate your final feedback summary now.",
                        actionLabel = "View debrief",
                        onAction = {
                            session?.let { onSessionComplete(it.id, uiState.skippedCount) }
                        }
                    )
                }

                uiState.activeEvaluation?.let { evaluation ->
                    EvaluationCard(evaluation = evaluation)
                }

                uiState.errorMessage?.let { message ->
                    InterviewNoticeCard(
                        title = "Issue",
                        body = message,
                        accent = MaterialTheme.colorScheme.error,
                        isDark = false
                    )
                }

                uiState.infoMessage?.let { message ->
                    InterviewNoticeCard(
                        title = "AI Update",
                        body = message,
                        accent = RoyalBlue,
                        isDark = true
                    )
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
fun MockInterviewSummaryScreen(
    viewModel: MockInterviewSummaryViewModel = hiltViewModel(),
    onPracticeAgain: (String) -> Unit,
    onOpenInterview: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> MockInterviewStateScreen(
            title = "Assembling debrief",
            description = "Processing session text to highlight your patterns.",
            showProgress = true
        )
        uiState.errorMessage != null && uiState.session == null -> MockInterviewStateScreen(
            title = "Debrief unavailable",
            description = uiState.errorMessage ?: "The debrief could not be built.",
            actionLabel = "Retry",
            onAction = viewModel::retryBackend,
            secondaryActionLabel = "Start anew",
            onSecondaryAction = onOpenInterview
        )
        else -> {
            val session = uiState.session
            val summary = uiState.summary

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = InternshipUncleTheme.spacing.medium)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
            ) {
                Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
                Text(
                    text = "POST-MOCK DEBRIEF",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Session Summary",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Review your macro performance and find out exactly what to polish.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                SummaryHeroCard(session = session, summary = summary, skippedCount = uiState.skippedCount)
                summary?.let {
                    SummaryInsightCard(title = "Strongest Area", body = it.strongestArea)
                    SummaryInsightCard(title = "Weakest Area", body = it.weakestArea)
                    SummarySuggestionsCard(suggestions = it.nextStepSuggestions)
                }

                if (session != null) {
                    SessionMetadataCard(session = session)
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (session != null) {
                    PillButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPracticeAgain(session.id) },
                        label = "Re-run this scenario"
                    )
                }
                OutlinedPillButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenInterview,
                    label = "New lab setup"
                )

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

// ── Shared Sub-components ──────────────────────────────────────────

@Composable
private fun MockInterviewStateScreen(
    title: String,
    description: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    PlaceholderScreen(
        eyebrow = "Interview Lab",
        title = title,
        description = description,
        actions = {
            if (showProgress) {
                CircularProgressIndicator(color = RoyalBlue)
            }
            if (actionLabel != null && onAction != null) {
                PillButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAction,
                    label = actionLabel
                )
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                OutlinedPillButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSecondaryAction,
                    label = secondaryActionLabel
                )
            }
        }
    )
}

@Composable
private fun SetupCard(
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle(title = title, subtitle = body)
            content()
        }
    }
}

@Composable
private fun InterviewNoticeCard(
    title: String,
    body: String,
    accent: Color,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DeepNavy else PureWhite.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (isDark) PureWhite else accent)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = if (isDark) PureWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedJobCard(title: String, company: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SkyBlueMedium.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Selected Target", style = MaterialTheme.typography.labelMedium, color = RoyalBlue)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!company.isNullOrBlank()) {
                Text(company, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun JobChoiceRow(
    jobs: List<JobCard>,
    selectedJobId: String?,
    onJobSelected: (JobCard?) -> Unit,
    onClearSelection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClearSelection, modifier = Modifier.padding(0.dp)) {
            Text("Clear selection", color = RoyalBlue)
        }
        jobs.chunked(2).forEach { rowJobs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowJobs.forEach { job ->
                    FilterChip(
                        selected = selectedJobId == job.id,
                        onClick = { onJobSelected(job) },
                        label = {
                            Text(
                                text = job.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalBlue,
                            selectedLabelColor = PureWhite
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceSection(
    title: String,
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        values.chunked(3).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowValues.forEach { value ->
                    FilterChip(
                        selected = selectedValue == value,
                        onClick = { onSelected(value) },
                        label = { Text(value.replaceFirstChar(Char::uppercase)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalBlue,
                            selectedLabelColor = PureWhite
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: MockInterviewQuestionProgress,
    currentIndex: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "QUESTION ${currentIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = RoyalBlue,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleLarge
            )
            question.category?.let { category ->
                Surface(
                    color = SkyBlueMedium.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = category,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = RoyalBlue
                    )
                }
            }
            if (question.expectedPoints.isNotEmpty()) {
                Text(
                    text = "Expected concepts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                question.expectedPoints.forEach { point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = RoyalBlue)
                        Text(point, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            question.answer?.let {
                SavedAnswerCard(answer = it)
            }
        }
    }
}

@Composable
private fun AnswerCard(
    answer: String,
    onAnswerChange: (String) -> Unit,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    showNext: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Your verbal response", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = answer,
                onValueChange = onAnswerChange,
                label = { Text("What would you say?") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedPillButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSkip,
                    enabled = !isSubmitting,
                    label = "Skip"
                )
                PillButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSubmit,
                    enabled = canSubmit,
                    isLoading = isSubmitting,
                    label = "Submit"
                )
            }
            if (showNext) {
                PillButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNext,
                    label = "Proceed to next"
                )
            }
        }
    }
}

@Composable
private fun EvaluationCard(
    evaluation: MockInterviewAnswerEvaluation
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DeepNavy,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Evaluation Feedback", style = MaterialTheme.typography.titleLarge, color = PureWhite)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${evaluation.score}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = SkyBlueMedium
                )
                Text("/100", style = MaterialTheme.typography.titleMedium, color = PureWhite.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 6.dp))
            }

            evaluation.feedback.strengths.takeIf { it.isNotEmpty() }?.let {
                FeedbackListCard(title = "Strengths", values = it, isDark = true)
            }
            evaluation.feedback.weaknesses.takeIf { it.isNotEmpty() }?.let {
                FeedbackListCard(title = "Gaps", values = it, isDark = true)
            }
            evaluation.feedback.missingPoints.takeIf { it.isNotEmpty() }?.let {
                FeedbackListCard(title = "Missing expected points", values = it, isDark = true)
            }
            evaluation.feedback.followUp?.let { followUp ->
                Text(
                    text = "Follow-up: $followUp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyBlueMedium.copy(alpha = 0.9f)
                )
            }
            evaluation.improvedAnswer?.let { improved ->
                ImprovedAnswerCard(text = improved)
            }
        }
    }
}

@Composable
private fun SavedAnswerCard(answer: MockInterviewAnswerEvaluation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SkyBlueMedium.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Logged answer", style = MaterialTheme.typography.labelMedium, color = RoyalBlue)
            Text(answer.answerText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedbackListCard(
    title: String,
    values: List<String>,
    isDark: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (isDark) PureWhite else MaterialTheme.colorScheme.onSurface)
        values.forEach { value ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = if (isDark) SkyBlueMedium else RoyalBlue)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = if (isDark) PureWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImprovedAnswerCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PureWhite.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Ideal phrasing", style = MaterialTheme.typography.titleMedium, color = PureWhite)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = PureWhite.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SessionHeaderCard(
    session: MockInterviewSessionDetail?,
    progressLabel: String,
    progressFraction: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = session?.roleName ?: "Mock Session",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = listOfNotNull(
                    session?.difficulty?.replaceFirstChar(Char::uppercase),
                    session?.mode?.replace('_', ' ')
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress", style = MaterialTheme.typography.labelSmall, color = CoolGray)
                    Text(progressLabel, style = MaterialTheme.typography.labelSmall, color = RoyalBlue, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = progressFraction,
                    modifier = Modifier.fillMaxWidth(),
                    color = RoyalBlue,
                    trackColor = SkyBlueMedium.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun CompletionPreviewCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DeepNavy,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = PureWhite)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = PureWhite.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            PillButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAction,
                label = actionLabel
            )
        }
    }
}

@Composable
private fun SummaryHeroCard(
    session: MockInterviewSessionDetail?,
    summary: MockInterviewPracticeSummary?,
    skippedCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DeepNavy,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("EVALUATION", style = MaterialTheme.typography.labelMedium, color = PureWhite.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = summary?.overallScore?.toString() ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Text(
                    text = "/100",
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = listOfNotNull(
                    session?.roleName,
                    session?.mode?.replace('_', ' ')?.replaceFirstChar(Char::uppercase),
                    if (skippedCount > 0) "$skippedCount skipped" else null
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SummaryInsightCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummarySuggestionsCard(
    suggestions: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Targeted adjustments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            suggestions.forEach { suggestion ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", color = RoyalBlue)
                    Text(suggestion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SessionMetadataCard(
    session: MockInterviewSessionDetail
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PureWhite.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Session Metadata", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${session.roleName} — ${session.difficulty} — ${session.mode} — ${session.answeredCount}/${session.totalQuestions} answered",
                style = MaterialTheme.typography.bodySmall,
                color = CoolGray
            )
        }
    }
}

@Composable
private fun RecentSessionCard(session: InterviewSessionSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PureWhite.copy(alpha = 0.85f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(session.roleName ?: "Mock Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${session.mode?.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session.overallScore?.let {
                    Text(
                        text = "Score: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun <T> QueryResult<T>.successData(): T? = (this as? QueryResult.Success<T>)?.data

private fun MockInterviewSessionDetail.currentQuestion(
    activeQuestionId: String?,
    skippedQuestionIds: Set<String>
): MockInterviewQuestionProgress? {
    activeQuestionId?.let { activeId ->
        questions.firstOrNull { it.id == activeId }?.let { return it }
    }
    return questions.firstOrNull { question ->
        !question.isAnswered && question.id !in skippedQuestionIds
    }
}

private fun MockInterviewSessionDetail.nextQuestion(
    currentQuestionId: String,
    skippedQuestionIds: Set<String>
): MockInterviewQuestionProgress? {
    val currentIndex = questions.indexOfFirst { it.id == currentQuestionId }
    if (currentIndex < 0) return null
    return questions.drop(currentIndex + 1).firstOrNull { question ->
        !question.isAnswered && question.id !in skippedQuestionIds
    }
}
