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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.model.QueryResult
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
        (saved + latest)
            .distinctBy(JobCard::id)
            .take(8)
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
    private val skippedCount: Int = savedStateHandle["skippedCount"] ?: 0

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
            title = "Loading interview setup",
            description = "Pulling your shortlist, resume state, and recent sessions.",
            showProgress = true
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = InternshipUncleTheme.spacing.medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
            Text(
                text = "Mock interview",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Set the role, choose the mode, and let the backend generate a realistic practice session.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            uiState.errorMessage?.let { message ->
                InterviewNoticeCard(
                    title = "Setup error",
                    body = message,
                    accent = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = viewModel::retryBackend) {
                    Text("Retry loading")
                }
            }

            uiState.infoMessage?.let { message ->
                InterviewNoticeCard(
                    title = "Status",
                    body = message,
                    accent = MaterialTheme.colorScheme.primary
                )
            }

            SetupCard(
                title = "Target role",
                body = "Pick the role you want to practice for. If you already picked a target internship, we can start from there."
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
                        "Resume inclusion is available because you have at least one resume on file."
                    } else {
                        "You do not have a resume yet, but you can still practice."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SetupCard(
                title = "Optional target job",
                body = "Choose a specific internship to anchor the interview. Saved jobs are shown first, then recent active roles."
            ) {
                if (uiState.suggestedJobs.isEmpty()) {
                    Text(
                        text = "No jobs are available to pick yet. You can still practice with a manually entered target role.",
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
                title = "Practice settings",
                body = "Keep it short for a quick run or turn up the pressure when you want a harder session."
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Include resume", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Backend can optionally bring your resume into the mock session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.includeResume,
                        onCheckedChange = viewModel::onIncludeResumeChange,
                        enabled = uiState.hasResume
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::createSession,
                enabled = uiState.canCreate && !uiState.isCreating
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generating session...")
                } else {
                    Text("Start mock interview")
                }
            }

            if (uiState.recentSessions.isNotEmpty()) {
                SectionTitle(
                    title = "Recent sessions",
                    subtitle = "A quick pulse on what you have practiced already."
                )
                uiState.recentSessions.take(3).forEach { session ->
                    RecentSessionCard(session = session)
                }
            }

            Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
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
            title = "Loading interview session",
            description = "Fetching your generated questions and any saved answers.",
            showProgress = true
        )

        uiState.errorMessage != null && uiState.session == null -> MockInterviewStateScreen(
            title = "Session unavailable",
            description = uiState.errorMessage ?: "The mock interview session could not be loaded.",
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
                        title = "Interview complete",
                        body = "All questions in this session have been covered. You can jump to the summary now.",
                        actionLabel = "View summary",
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
                        title = "Answer error",
                        body = message,
                        accent = MaterialTheme.colorScheme.error
                    )
                }

                uiState.infoMessage?.let { message ->
                    InterviewNoticeCard(
                        title = "Update",
                        body = message,
                        accent = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
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
            title = "Loading summary",
            description = "Collecting the session results and turning them into a concise summary.",
            showProgress = true
        )

        uiState.errorMessage != null && uiState.session == null -> MockInterviewStateScreen(
            title = "Summary unavailable",
            description = uiState.errorMessage ?: "The session summary could not be loaded.",
            actionLabel = "Retry",
            onAction = viewModel::retryBackend,
            secondaryActionLabel = "Start another session",
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
                    text = "Session summary",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "A compact result view so the user sees where they're strong and what to fix next.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SummaryHeroCard(session = session, summary = summary, skippedCount = uiState.skippedCount)
                summary?.let {
                    SummaryInsightCard(title = "Strongest area", body = it.strongestArea)
                    SummaryInsightCard(title = "Weakest area", body = it.weakestArea)
                    SummarySuggestionsCard(suggestions = it.nextStepSuggestions)
                }

                if (session != null) {
                    SessionMetadataCard(session = session)
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { session?.let { onPracticeAgain(it.id) } },
                        enabled = session != null
                    ) {
                        Text("Practice again")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenInterview
                    ) {
                        Text("New session")
                    }
                }

                Spacer(modifier = Modifier.height(InternshipUncleTheme.spacing.large))
            }
        }
    }
}

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(InternshipUncleTheme.spacing.large),
        verticalArrangement = Arrangement.Center
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
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
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
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
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
    accent: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
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
private fun SelectedJobCard(
    title: String,
    company: String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Selected target job", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onClearSelection) {
                Text("No target job")
            }
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
                        }
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
        values.chunked(2).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowValues.forEach { value ->
                    FilterChip(
                        selected = selectedValue == value,
                        onClick = { onSelected(value) },
                        label = { Text(value.replaceFirstChar(Char::uppercase)) }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.small)
        ) {
            Text(
                text = "Question ${currentIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleLarge
            )
            question.category?.let { category ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = category,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (question.expectedPoints.isNotEmpty()) {
                Text(
                    text = "Expected points",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                question.expectedPoints.forEach { point ->
                    Text("- $point", style = MaterialTheme.typography.bodyMedium)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Text("Your answer", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = answer,
                onValueChange = onAnswerChange,
                label = { Text("Type your answer") },
                minLines = 5,
                maxLines = 10
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSkip,
                    enabled = !isSubmitting
                ) {
                    Text("Skip")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSubmit,
                    enabled = canSubmit
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Submitting")
                    } else {
                        Text("Submit")
                    }
                }
            }
            if (showNext) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNext
                ) {
                    Text("Next question")
                }
            }
        }
    }
}

@Composable
private fun EvaluationCard(
    evaluation: MockInterviewAnswerEvaluation
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Text("Feedback", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${evaluation.score}/100",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            evaluation.feedback.strengths.takeIf { it.isNotEmpty() }?.let {
                FeedbackListCard(title = "Strengths", values = it)
            }
            evaluation.feedback.weaknesses.takeIf { it.isNotEmpty() }?.let {
                FeedbackListCard(title = "Weaknesses", values = it)
            }
            evaluation.feedback.missingPoints.takeIf { it.isNotEmpty() }?.let {
                FeedbackListCard(title = "Missing points", values = it)
            }
            evaluation.feedback.followUp?.let { followUp ->
                Text(
                    text = "Follow-up: $followUp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Saved answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(answer.answerText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedbackListCard(
    title: String,
    values: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        values.forEach { value ->
            Text("- $value", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImprovedAnswerCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Improved answer", style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SessionHeaderCard(
    session: MockInterviewSessionDetail?,
    progressLabel: String,
    progressFraction: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
        ) {
            Text(
                text = session?.roleName ?: "Mock interview session",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = listOfNotNull(
                    session?.difficulty?.replaceFirstChar(Char::uppercase),
                    session?.mode?.replace('_', ' '),
                    session?.overallScore?.let { "Overall $it/100" }
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = progressFraction,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SummaryHeroCard(
    session: MockInterviewSessionDetail?,
    summary: MockInterviewPracticeSummary?,
    skippedCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Overall score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                text = summary?.overallScore?.let { "$it/100" } ?: "No score yet",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOfNotNull(
                    session?.roleName,
                    session?.mode?.replace('_', ' '),
                    if (skippedCount > 0) "$skippedCount skipped" else null
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryInsightCard(
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummarySuggestionsCard(
    suggestions: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Next steps", style = MaterialTheme.typography.titleMedium)
            suggestions.forEach { suggestion ->
                Text("- $suggestion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionMetadataCard(
    session: MockInterviewSessionDetail
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Session details", style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(
                    session.roleName,
                    session.difficulty?.replaceFirstChar(Char::uppercase),
                    session.mode?.replace('_', ' '),
                    "Answered ${session.answeredCount}/${session.totalQuestions}"
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentSessionCard(session: InterviewSessionSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(InternshipUncleTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(session.roleName ?: "Mock session", style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(
                    session.difficulty?.replaceFirstChar(Char::uppercase),
                    session.mode?.replace('_', ' '),
                    session.overallScore?.let { "$it/100" }
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
