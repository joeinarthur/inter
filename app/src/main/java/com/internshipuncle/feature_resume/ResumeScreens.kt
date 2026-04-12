package com.internshipuncle.feature_resume

import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.SurfaceGray
import com.internshipuncle.core.design.SilverMist
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.CanvasWhite
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.internshipuncle.core.design.CharcoalDark
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.SurfaceLight
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.data.repository.JobsRepository
import com.internshipuncle.data.repository.ResumeRepository
import com.internshipuncle.domain.model.GeneratedResumeDocument
import com.internshipuncle.domain.model.GeneratedResumeSummary
import com.internshipuncle.domain.model.JobDetail
import com.internshipuncle.domain.model.ResumeBasics
import com.internshipuncle.domain.model.ResumeBuilderInput
import com.internshipuncle.domain.model.ResumeEducationEntry
import com.internshipuncle.domain.model.ResumeExperienceEntry
import com.internshipuncle.domain.model.ResumeProjectEntry
import com.internshipuncle.domain.model.ResumeRoastDetail
import com.internshipuncle.domain.model.ResumeRoastIssue
import com.internshipuncle.domain.model.ResumeRoastResult
import com.internshipuncle.domain.model.ResumeRoastSummary
import com.internshipuncle.domain.model.ResumeSummary
import com.internshipuncle.core.ui.PlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
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

private const val DefaultResumeTemplate = "ats_classic"

data class ResumeUploadUiState(
    val isLoading: Boolean = true,
    val resumes: List<ResumeSummary> = emptyList(),
    val targetJob: JobDetail? = null,
    val uploadPhase: ResumeUploadPhase = ResumeUploadPhase.Idle,
    val selectedFileName: String? = null,
    val selectedResumeId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

enum class ResumeUploadPhase {
    Idle,
    Uploading,
    Parsing,
    Success
}

data class ResumeRoastUiState(
    val isLoading: Boolean = true,
    val resume: ResumeSummary? = null,
    val targetJob: JobDetail? = null,
    val roastSummary: ResumeRoastSummary? = null,
    val selectedMode: String = "savage",
    val isRoasting: Boolean = false,
    val roastDetail: ResumeRoastDetail? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class ResumeBuilderUiState(
    val isLoading: Boolean = true,
    val targetJob: JobDetail? = null,
    val sourceResumes: List<ResumeSummary> = emptyList(),
    val selectedSourceResumeId: String? = null,
    val basics: ResumeBasicsEditor = ResumeBasicsEditor(),
    val education: List<ResumeEducationEditor> = listOf(ResumeEducationEditor()),
    val skills: List<ResumeTextEditor> = listOf(ResumeTextEditor()),
    val projects: List<ResumeProjectEditor> = listOf(ResumeProjectEditor()),
    val experience: List<ResumeExperienceEditor> = listOf(ResumeExperienceEditor()),
    val achievements: List<ResumeTextEditor> = listOf(ResumeTextEditor()),
    val isGenerating: Boolean = false,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val generatedResume: GeneratedResumeDocument? = null,
    val exportedPdfUrl: String? = null
) {
    val canGenerate: Boolean
        get() = !isGenerating &&
            basics.name.isNotBlank() &&
            (
                skills.any { it.value.isNotBlank() } ||
                    projects.any { it.name.isNotBlank() } ||
                    experience.any { it.company.isNotBlank() } ||
                    education.any { it.school.isNotBlank() } ||
                    achievements.any { it.value.isNotBlank() }
                )
}

data class ResumeBasicsEditor(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val linkedin: String = "",
    val github: String = "",
    val portfolio: String = ""
)

data class ResumeTextEditor(
    val id: String = newEditorId(),
    val value: String = ""
)

data class ResumeEducationEditor(
    val id: String = newEditorId(),
    val school: String = "",
    val degree: String = "",
    val start: String = "",
    val end: String = "",
    val gpa: String = ""
)

data class ResumeProjectEditor(
    val id: String = newEditorId(),
    val name: String = "",
    val description: String = "",
    val highlights: String = ""
)

data class ResumeExperienceEditor(
    val id: String = newEditorId(),
    val company: String = "",
    val role: String = "",
    val start: String = "",
    val end: String = "",
    val bullets: String = ""
)

@HiltViewModel
class ResumeUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeRepository: ResumeRepository,
    private val jobsRepository: JobsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val targetJobId: String? = savedStateHandle["targetJobId"]
    private val operationState = MutableStateFlow(ResumeUploadUiState())

    private val targetJobFlow: Flow<QueryResult<JobDetail?>> = if (targetJobId.isNullOrBlank()) {
        flowOf(QueryResult.Success(null))
    } else {
        jobsRepository.jobDetail(targetJobId)
    }

    val uiState: StateFlow<ResumeUploadUiState> = combine(
        resumeRepository.resumes(),
        targetJobFlow,
        operationState
    ) { resumesResult, targetJobResult, state ->
        state.copy(
            isLoading = false,
            resumes = resumesResult,
            targetJob = targetJobResult.successData(),
            errorMessage = state.errorMessage,
            infoMessage = state.infoMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumeUploadUiState()
    )

    fun onPickResume(uri: Uri) {
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    uploadPhase = ResumeUploadPhase.Uploading,
                    errorMessage = null,
                    infoMessage = null
                )
            }

            val fileName = resolveFileName(uri) ?: "resume.pdf"
            val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
            val bytes = readBytes(uri)

            if (bytes == null) {
                operationState.update {
                    it.copy(
                        uploadPhase = ResumeUploadPhase.Idle,
                        errorMessage = "Unable to read the selected PDF."
                    )
                }
                return@launch
            }

            operationState.update { it.copy(uploadPhase = ResumeUploadPhase.Parsing, selectedFileName = fileName) }

            when (
                val result = resumeRepository.uploadResumeAndParse(
                    fileName = fileName,
                    mimeType = mimeType,
                    bytes = bytes,
                    targetJobId = targetJobId
                )
            ) {
                QueryResult.NotConfigured -> operationState.update {
                    it.copy(
                        uploadPhase = ResumeUploadPhase.Idle,
                        errorMessage = "Supabase config is missing."
                    )
                }
                QueryResult.BackendNotReady -> operationState.update {
                    it.copy(
                        uploadPhase = ResumeUploadPhase.Idle,
                        errorMessage = "Resume storage or schema is not ready yet."
                    )
                }
                is QueryResult.Failure -> operationState.update {
                    it.copy(
                        uploadPhase = ResumeUploadPhase.Idle,
                        errorMessage = result.message
                    )
                }
                is QueryResult.Success -> operationState.update {
                    it.copy(
                        uploadPhase = ResumeUploadPhase.Success,
                        selectedResumeId = result.data.id,
                        infoMessage = "Parsed and saved. You can roast this resume now."
                    )
                }
                QueryResult.Loading -> Unit
            }
        }
    }

    fun clearMessage() {
        operationState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun readBytes(uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun resolveFileName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }
}

@HiltViewModel
class ResumeRoastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeRepository: ResumeRepository,
    private val jobsRepository: JobsRepository
) : ViewModel() {
    private val resumeId: String = checkNotNull(savedStateHandle["resumeId"])
    private val targetJobId: String? = savedStateHandle["targetJobId"]
    private val operationState = MutableStateFlow(ResumeRoastUiState(selectedMode = "savage"))

    private val selectedResumeFlow: Flow<QueryResult<ResumeSummary?>> = resumeRepository.resumes().map { resumes ->
        QueryResult.Success(resumes.firstOrNull { it.id == resumeId })
    }

    private val targetJobFlow: Flow<QueryResult<JobDetail?>> = if (targetJobId.isNullOrBlank()) {
        flowOf(QueryResult.Success(null))
    } else {
        jobsRepository.jobDetail(targetJobId)
    }

    private val roastSummaryFlow: Flow<QueryResult<ResumeRoastSummary?>> = resumeRepository.roastSummary(resumeId).map {
        QueryResult.Success(it)
    }

    val uiState: StateFlow<ResumeRoastUiState> = combine(
        selectedResumeFlow,
        targetJobFlow,
        roastSummaryFlow,
        operationState
    ) { resumeResult, jobResult, summaryResult, currentState ->
        currentState.copy(
            isLoading = false,
            resume = resumeResult.successData(),
            targetJob = jobResult.successData(),
            roastSummary = summaryResult.successData(),
            errorMessage = currentState.errorMessage,
            infoMessage = currentState.infoMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumeRoastUiState()
    )

    fun setMode(mode: String) {
        operationState.update { it.copy(selectedMode = mode) }
    }

    fun roast() {
        val current = uiState.value
        if (current.isRoasting) return
        val resume = current.resume ?: return

        viewModelScope.launch {
            operationState.update {
                it.copy(isRoasting = true, errorMessage = null, infoMessage = null)
            }

            when (
                val result = resumeRepository.roastResume(
                    resumeId = resume.id,
                    targetJobId = targetJobId,
                    mode = current.selectedMode
                )
            ) {
                QueryResult.NotConfigured -> operationState.update {
                    it.copy(isRoasting = false, errorMessage = "Supabase config is missing.")
                }
                QueryResult.BackendNotReady -> operationState.update {
                    it.copy(isRoasting = false, errorMessage = "Resume roast backend is not ready yet.")
                }
                is QueryResult.Failure -> operationState.update {
                    it.copy(isRoasting = false, errorMessage = result.message)
                }
                is QueryResult.Success -> operationState.update {
                    it.copy(
                        isRoasting = false,
                        roastDetail = result.data,
                        infoMessage = "Resume roast completed."
                    )
                }
                QueryResult.Loading -> Unit
            }
        }
    }
}

@HiltViewModel
class ResumeBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeRepository: ResumeRepository,
    private val jobsRepository: JobsRepository
) : ViewModel() {
    private val targetJobId: String? = savedStateHandle["targetJobId"]
    private val operationState = MutableStateFlow(ResumeBuilderUiState())

    private val targetJobFlow: Flow<QueryResult<JobDetail?>> = if (targetJobId.isNullOrBlank()) {
        flowOf(QueryResult.Success(null))
    } else {
        jobsRepository.jobDetail(targetJobId)
    }

    val uiState: StateFlow<ResumeBuilderUiState> = combine(
        resumeRepository.resumes(),
        targetJobFlow,
        operationState
    ) { resumes, jobResult, state ->
        val selectedSource = state.selectedSourceResumeId ?: resumes.firstOrNull()?.id
        state.copy(
            isLoading = false,
            sourceResumes = resumes,
            selectedSourceResumeId = selectedSource,
            targetJob = jobResult.successData(),
            errorMessage = state.errorMessage,
            infoMessage = state.infoMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumeBuilderUiState()
    )

    fun setBasics(value: ResumeBasicsEditor) {
        operationState.update { it.copy(basics = value) }
    }

    fun updateEducation(id: String, transform: (ResumeEducationEditor) -> ResumeEducationEditor) {
        operationState.update { state ->
            state.copy(
                education = state.education.map { entry -> if (entry.id == id) transform(entry) else entry }
            )
        }
    }

    fun addEducation() {
        operationState.update { it.copy(education = it.education + ResumeEducationEditor()) }
    }

    fun removeEducation(id: String) {
        operationState.update { it.copy(education = it.education.filterNot { entry -> entry.id == id }) }
    }

    fun updateSkill(id: String, value: String) {
        operationState.update { state ->
            state.copy(
                skills = state.skills.map { if (it.id == id) it.copy(value = value) else it }
            )
        }
    }

    fun addSkill() {
        operationState.update { it.copy(skills = it.skills + ResumeTextEditor()) }
    }

    fun removeSkill(id: String) {
        operationState.update { it.copy(skills = it.skills.filterNot { entry -> entry.id == id }) }
    }

    fun updateProject(id: String, transform: (ResumeProjectEditor) -> ResumeProjectEditor) {
        operationState.update { state ->
            state.copy(
                projects = state.projects.map { entry -> if (entry.id == id) transform(entry) else entry }
            )
        }
    }

    fun addProject() {
        operationState.update { it.copy(projects = it.projects + ResumeProjectEditor()) }
    }

    fun removeProject(id: String) {
        operationState.update { it.copy(projects = it.projects.filterNot { entry -> entry.id == id }) }
    }

    fun updateExperience(id: String, transform: (ResumeExperienceEditor) -> ResumeExperienceEditor) {
        operationState.update { state ->
            state.copy(
                experience = state.experience.map { entry -> if (entry.id == id) transform(entry) else entry }
            )
        }
    }

    fun addExperience() {
        operationState.update { it.copy(experience = it.experience + ResumeExperienceEditor()) }
    }

    fun removeExperience(id: String) {
        operationState.update { it.copy(experience = it.experience.filterNot { entry -> entry.id == id }) }
    }

    fun updateAchievement(id: String, value: String) {
        operationState.update { state ->
            state.copy(
                achievements = state.achievements.map { if (it.id == id) it.copy(value = value) else it }
            )
        }
    }

    fun addAchievement() {
        operationState.update { it.copy(achievements = it.achievements + ResumeTextEditor()) }
    }

    fun removeAchievement(id: String) {
        operationState.update { it.copy(achievements = it.achievements.filterNot { entry -> entry.id == id }) }
    }

    fun selectSourceResume(id: String) {
        operationState.update { it.copy(selectedSourceResumeId = id) }
    }

    fun generateResume() {
        val current = uiState.value
        if (!current.canGenerate || current.isGenerating) return

        viewModelScope.launch {
            operationState.update { it.copy(isGenerating = true, errorMessage = null, infoMessage = null) }

            when (
                val result = resumeRepository.generateResume(
                    sourceResumeId = current.selectedSourceResumeId,
                    targetJobId = targetJobId,
                    templateName = DefaultResumeTemplate,
                    inputProfile = current.toInput()
                )
            ) {
                QueryResult.NotConfigured -> operationState.update {
                    it.copy(isGenerating = false, errorMessage = "Supabase config is missing.")
                }
                QueryResult.BackendNotReady -> operationState.update {
                    it.copy(isGenerating = false, errorMessage = "Resume builder backend is not ready yet.")
                }
                is QueryResult.Failure -> operationState.update {
                    it.copy(isGenerating = false, errorMessage = result.message)
                }
                is QueryResult.Success -> operationState.update {
                    it.copy(
                        isGenerating = false,
                        generatedResume = result.data,
                        infoMessage = "Resume JSON generated. Export it when you are ready."
                    )
                }
                QueryResult.Loading -> Unit
            }
        }
    }

    fun exportResumePdf() {
        val generatedResumeId = uiState.value.generatedResume?.generatedResumeId ?: return
        if (uiState.value.isExporting) return

        viewModelScope.launch {
            operationState.update { it.copy(isExporting = true, errorMessage = null, infoMessage = null) }

            when (val result = resumeRepository.exportResumePdf(generatedResumeId)) {
                QueryResult.NotConfigured -> operationState.update {
                    it.copy(isExporting = false, errorMessage = "Supabase config is missing.")
                }
                QueryResult.BackendNotReady -> operationState.update {
                    it.copy(isExporting = false, errorMessage = "PDF export backend is not ready yet.")
                }
                is QueryResult.Failure -> operationState.update {
                    it.copy(isExporting = false, errorMessage = result.message)
                }
                is QueryResult.Success -> operationState.update {
                    it.copy(
                        isExporting = false,
                        exportedPdfUrl = result.data,
                        infoMessage = "PDF exported successfully."
                    )
                }
                QueryResult.Loading -> Unit
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
            containerColor = InkBlack,
            contentColor = PureWhite,
            disabledContainerColor = InkBlack.copy(alpha = 0.4f),
            disabledContentColor = SurfaceGray
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
        Text(label, fontWeight = FontWeight.SemiBold, color = if (enabled) InkBlack else CoolGray)
    }
}

@Composable
private fun ResumeNoticeCard(
    title: String,
    body: String,
    accent: Color,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) CharcoalDark else SurfaceGray,
        shadowElevation = 0.dp
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

// ── Screens ────────────────────────────────────────────────────────

@Composable
fun ResumeUploadScreen(
    viewModel: ResumeUploadViewModel = hiltViewModel(),
    onOpenRoast: (String, String?) -> Unit,
    onOpenBuilder: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onPickResume(uri)
        }
    }

    when {
        uiState.isLoading -> PlaceholderScreen(
            eyebrow = "Resume Lab",
            title = "Loading lab...",
            description = "Fetching your uploaded resumes.",
            actions = { CircularProgressIndicator(color = InkBlack) }
        )
        else -> {
            ResumeLabScaffold(
                eyebrow = "Resume Lab",
                title = "Upload, parse, roast, and export from one place.",
                description = "Pick a PDF, let Supabase handle the parsing via Edge Functions, then iterate on the output.",
                targetJobLabel = uiState.targetJob?.let { "${it.title} at ${it.company}" },
                primaryActionLabel = "Pick PDF resume",
                secondaryActionLabel = "Open builder",
                onPrimaryAction = { picker.launch(arrayOf("application/pdf")) },
                onSecondaryAction = { onOpenBuilder(uiState.targetJob?.id) },
                loading = uiState.uploadPhase == ResumeUploadPhase.Uploading || uiState.uploadPhase == ResumeUploadPhase.Parsing,
                message = uiState.errorMessage ?: uiState.infoMessage,
                isError = uiState.errorMessage != null
            ) {
                if (uiState.uploadPhase != ResumeUploadPhase.Idle || uiState.selectedFileName != null) {
                    StatusPanel(
                        title = when (uiState.uploadPhase) {
                            ResumeUploadPhase.Idle -> "Ready to upload"
                            ResumeUploadPhase.Uploading -> "Uploading to Supabase Storage"
                            ResumeUploadPhase.Parsing -> "Parsing with Edge Functions"
                            ResumeUploadPhase.Success -> "Resume parsed and saved"
                        },
                        body = uiState.selectedFileName ?: "No file selected yet.",
                        showProgress = uiState.uploadPhase == ResumeUploadPhase.Uploading || uiState.uploadPhase == ResumeUploadPhase.Parsing
                    )
                }

                if (uiState.resumes.isEmpty()) {
                    EmptyResumeState(
                        title = "No uploads yet",
                        body = "Select a PDF to create your first resume row, upload it to storage, and parse it through the backend."
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        title = "Uploaded resumes",
                        description = "Tap roast on any parsed resume to get a structured review."
                    )
                    uiState.resumes.forEach { resume ->
                        ResumeSummaryCard(
                            resume = resume,
                            onRoast = { onOpenRoast(resume.id, uiState.targetJob?.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResumeRoastScreen(
    viewModel: ResumeRoastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> PlaceholderScreen(
            eyebrow = "Resume Roast",
            title = "Loading resume...",
            description = "Fetching parsed document.",
            actions = { CircularProgressIndicator(color = InkBlack) }
        )
        else -> {
            ResumeLabScaffold(
                eyebrow = "Resume Roast",
                title = "Brutal feedback, strictly actionable.",
                description = "Roast your parsed resume. Tailored to an optional target job for precision evaluation.",
                targetJobLabel = uiState.targetJob?.let { "${it.title} at ${it.company}" },
                primaryActionLabel = if (uiState.isRoasting) "Roasting..." else "Roast resume",
                secondaryActionLabel = null,
                onPrimaryAction = viewModel::roast,
                onSecondaryAction = null,
                loading = uiState.isRoasting,
                message = uiState.errorMessage ?: uiState.infoMessage,
                isError = uiState.errorMessage != null
            ) {
                val resume = uiState.resume
                if (resume != null) {
                    StatusPanel(
                        title = resume.fileName ?: "Uploaded resume",
                        body = resume.createdAt ?: "Saved to Supabase.",
                        accent = "Latest score: ${resume.latestScore?.toString() ?: "Pending"}"
                    )
                }

                ModeSelector(
                    selectedMode = uiState.selectedMode,
                    onModeSelected = viewModel::setMode
                )

                val roastSummary = uiState.roastSummary
                if (roastSummary != null && uiState.roastDetail == null) {
                    ScoreOverviewCard(
                        title = "Latest stored roast",
                        result = ResumeRoastDetail(
                            resumeId = resume?.id ?: "",
                            targetJobId = uiState.targetJob?.id,
                            overallScore = roastSummary.overallScore ?: 0,
                            atsScore = roastSummary.atsScore ?: 0,
                            relevanceScore = roastSummary.relevanceScore ?: 0,
                            clarityScore = roastSummary.clarityScore ?: 0,
                            formattingScore = roastSummary.formattingScore ?: 0,
                            roastResult = ResumeRoastResult()
                        )
                    )
                }

                uiState.roastDetail?.let { roast ->
                    ScoreOverviewCard(title = "Roast result", result = roast)
                    RoastResultSection("Comments", roast.roastResult.comments)
                    RoastResultSection("Missing keywords", roast.roastResult.missingKeywords)
                    RoastResultSection("Weak bullets", roast.roastResult.weakBullets)
                    RoastResultSection("Rewritten bullets", roast.roastResult.rewrittenBullets)
                    RoastIssuesSection(roast.roastResult.issues)
                }
            }
        }
    }
}

@Composable
fun ResumeBuilderScreen(
    viewModel: ResumeBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> PlaceholderScreen(
            eyebrow = "Resume Builder",
            title = "Loading builder...",
            description = "Fetching builder state and existing data.",
            actions = { CircularProgressIndicator(color = InkBlack) }
        )
        else -> {
            ResumeLabScaffold(
                eyebrow = "Resume Builder",
                title = "Construct your resume deliberately.",
                description = "Edit the structured resume sections, generate a backed JSON node, then export to PDF.",
                targetJobLabel = uiState.targetJob?.let { "${it.title} at ${it.company}" },
                primaryActionLabel = if (uiState.isGenerating) "Generating..." else "Generate resume JSON",
                secondaryActionLabel = if (uiState.generatedResume != null) "Export PDF" else null,
                onPrimaryAction = viewModel::generateResume,
                onSecondaryAction = if (uiState.generatedResume != null) viewModel::exportResumePdf else null,
                loading = uiState.isGenerating || uiState.isExporting,
                message = uiState.errorMessage ?: uiState.infoMessage,
                isError = uiState.errorMessage != null
            ) {
                SourceResumePicker(
                    sourceResumes = uiState.sourceResumes,
                    selectedSourceResumeId = uiState.selectedSourceResumeId,
                    onSelect = viewModel::selectSourceResume
                )

                BasicsEditor(
                    basics = uiState.basics,
                    onChange = viewModel::setBasics
                )

                EditableTextListSection(
                    title = "Skills",
                    description = "Add one targeted skill per block.",
                    items = uiState.skills,
                    onAdd = viewModel::addSkill,
                    onDelete = viewModel::removeSkill,
                    onChange = viewModel::updateSkill
                )

                EditableEducationSection(
                    items = uiState.education,
                    onAdd = viewModel::addEducation,
                    onDelete = viewModel::removeEducation,
                    onChange = viewModel::updateEducation
                )

                EditableProjectSection(
                    items = uiState.projects,
                    onAdd = viewModel::addProject,
                    onDelete = viewModel::removeProject,
                    onChange = viewModel::updateProject
                )

                EditableExperienceSection(
                    items = uiState.experience,
                    onAdd = viewModel::addExperience,
                    onDelete = viewModel::removeExperience,
                    onChange = viewModel::updateExperience
                )

                EditableTextListSection(
                    title = "Achievements",
                    description = "Add proof of success. Keep it measurable.",
                    items = uiState.achievements,
                    onAdd = viewModel::addAchievement,
                    onDelete = viewModel::removeAchievement,
                    onChange = viewModel::updateAchievement
                )

                uiState.generatedResume?.let { generated ->
                    GeneratedResumePreview(generated = generated, pdfUrl = uiState.exportedPdfUrl)
                }
            }
        }
    }
}

// ── Components Refactored to Premium UI ────────────────────────────

@Composable
private fun ResumeLabScaffold(
    eyebrow: String,
    title: String,
    description: String,
    targetJobLabel: String?,
    primaryActionLabel: String,
    secondaryActionLabel: String?,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)?,
    loading: Boolean,
    message: String?,
    isError: Boolean,
    content: @Composable () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateGray,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = InkBlack,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray
                )
                targetJobLabel?.let {
                    Surface(
                        color = SurfaceGray,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkBlack
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceGray,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    message?.let {
                        ResumeNoticeCard(
                            title = if (isError) "Error" else "Info",
                            body = it,
                            accent = if (isError) MaterialTheme.colorScheme.error else InkBlack,
                            isDark = !isError
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            onClick = onPrimaryAction,
                            enabled = !loading,
                            isLoading = loading,
                            label = primaryActionLabel
                        )
                        if (secondaryActionLabel != null && onSecondaryAction != null) {
                            OutlinedPillButton(
                                modifier = Modifier.weight(1f),
                                onClick = onSecondaryAction,
                                enabled = !loading,
                                label = secondaryActionLabel
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun ResumeSummaryCard(
    resume: ResumeSummary,
    onRoast: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = resume.fileName ?: "Untitled resume",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Latest score: ${resume.latestScore?.toString() ?: "pending"}",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateGray
            )
            PillButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRoast,
                label = "Roast this resume"
            )
        }
    }
}

@Composable
private fun StatusPanel(
    title: String,
    body: String,
    accent: String? = null,
    showProgress: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            accent?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = InkBlack)
            }
            if (showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = InkBlack,
                    trackColor = SurfaceLight.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun EmptyResumeState(title: String, body: String) {
    StatusPanel(title = title, body = body)
}

@Composable
private fun SectionHeader(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = SlateGray
        )
    }
}

@Composable
private fun ModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Roast Mode", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("savage" to "Savage (Cruel but fair)", "recruiter" to "Recruiter (Standard)").forEach { (value, label) ->
                    FilterChip(
                        selected = selectedMode == value,
                        onClick = { onModeSelected(value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkBlack,
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
private fun ScoreOverviewCard(
    title: String,
    result: ResumeRoastDetail
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CharcoalDark,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PureWhite.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${result.overallScore}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Text(
                    text = "/100 overall",
                    style = MaterialTheme.typography.titleMedium,
                    color = SurfaceGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            ScoreRow("ATS Optimization", result.atsScore)
            ScoreRow("Role Relevance", result.relevanceScore)
            ScoreRow("Writing Clarity", result.clarityScore)
            ScoreRow("Visual Formatting", result.formattingScore)
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = PureWhite)
            Text("$score", style = MaterialTheme.typography.titleMedium, color = SurfaceLight)
        }
        HorizontalDivider(color = PureWhite.copy(alpha = 0.1f))
    }
}

@Composable
private fun RoastResultSection(
    title: String,
    items: List<String>
) {
    if (items.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", color = InkBlack)
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateGray
                    )
                }
            }
        }
    }
}

@Composable
private fun RoastIssuesSection(items: List<ResumeRoastIssue>) {
    if (items.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Critical Issues", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            items.forEach { issue ->
                Text(
                    text = listOfNotNull(issue.section, issue.severity, issue.message).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun SourceResumePicker(
    sourceResumes: List<ResumeSummary>,
    selectedSourceResumeId: String?,
    onSelect: (String) -> Unit
) {
    if (sourceResumes.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceGray,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Source resume seed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sourceResumes.take(4).forEach { resume ->
                    FilterChip(
                        selected = selectedSourceResumeId == resume.id,
                        onClick = { onSelect(resume.id) },
                        label = { Text(resume.fileName ?: "Resume") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkBlack,
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
private fun BasicsEditor(
    basics: ResumeBasicsEditor,
    onChange: (ResumeBasicsEditor) -> Unit
) {
    EditorSectionContainer(title = "Basics", description = "Your core contact identity.") {
        EditableTextField("Full Name", basics.name) { onChange(basics.copy(name = it)) }
        EditableTextField("Email Address", basics.email) { onChange(basics.copy(email = it)) }
        EditableTextField("Phone Number", basics.phone) { onChange(basics.copy(phone = it)) }
        EditableTextField("Location", basics.location) { onChange(basics.copy(location = it)) }
        EditableTextField("LinkedIn URL", basics.linkedin) { onChange(basics.copy(linkedin = it)) }
        EditableTextField("GitHub URL", basics.github) { onChange(basics.copy(github = it)) }
        EditableTextField("Portfolio URL", basics.portfolio) { onChange(basics.copy(portfolio = it)) }
    }
}

@Composable
private fun EditableTextListSection(
    title: String,
    description: String,
    items: List<ResumeTextEditor>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onChange: (String, String) -> Unit
) {
    EditorSectionContainer(
        title = title,
        description = description,
        onAdd = onAdd
    ) {
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditableTextField(
                    label = title.dropLastWhile { it == 's' },
                    value = item.value,
                    modifier = Modifier.weight(1f),
                    onValueChange = { onChange(item.id, it) }
                )
                IconButton(onClick = { onDelete(item.id) }) {
                    Text("×", style = MaterialTheme.typography.headlineMedium, color = SlateGray)
                }
            }
        }
    }
}

@Composable
private fun EditableEducationSection(
    items: List<ResumeEducationEditor>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onChange: (String, (ResumeEducationEditor) -> ResumeEducationEditor) -> Unit
) {
    EditorSectionContainer(
        title = "Education",
        description = "Keep it concise and recent. One row per entry.",
        onAdd = onAdd
    ) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableTextField("School / University", item.school) { value -> onChange(item.id) { it.copy(school = value) } }
                EditableTextField("Degree / Field of Study", item.degree) { value -> onChange(item.id) { it.copy(degree = value) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditableTextField("Start Date", item.start, modifier = Modifier.weight(1f)) { value ->
                        onChange(item.id) { it.copy(start = value) }
                    }
                    EditableTextField("End Date", item.end, modifier = Modifier.weight(1f)) { value ->
                        onChange(item.id) { it.copy(end = value) }
                    }
                    EditableTextField("GPA", item.gpa, modifier = Modifier.weight(1f)) { value ->
                        onChange(item.id) { it.copy(gpa = value) }
                    }
                }
                TextButton(onClick = { onDelete(item.id) }) { Text("− Remove education entry", color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(color = SlateGray.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun EditableProjectSection(
    items: List<ResumeProjectEditor>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onChange: (String, (ResumeProjectEditor) -> ResumeProjectEditor) -> Unit
) {
    EditorSectionContainer(
        title = "Projects",
        description = "Describe impact, not just activity. Frame your projects around the roles you want.",
        onAdd = onAdd
    ) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableTextField("Project Title", item.name) { value -> onChange(item.id) { it.copy(name = value) } }
                EditableTextField("Short Description or Tech Stack", item.description) { value -> onChange(item.id) { it.copy(description = value) } }
                EditableMultilineField("Impact Highlights (One per line)", item.highlights) { value ->
                    onChange(item.id) { it.copy(highlights = value) }
                }
                TextButton(onClick = { onDelete(item.id) }) { Text("− Remove project", color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(color = SlateGray.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun EditableExperienceSection(
    items: List<ResumeExperienceEditor>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onChange: (String, (ResumeExperienceEditor) -> ResumeExperienceEditor) -> Unit
) {
    EditorSectionContainer(
        title = "Experience",
        description = "Internships, freelance work, or campus work experience.",
        onAdd = onAdd
    ) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableTextField("Company / Organization", item.company) { value -> onChange(item.id) { it.copy(company = value) } }
                EditableTextField("Role Title", item.role) { value -> onChange(item.id) { it.copy(role = value) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditableTextField("Start Date", item.start, modifier = Modifier.weight(1f)) { value ->
                        onChange(item.id) { it.copy(start = value) }
                    }
                    EditableTextField("End Date", item.end, modifier = Modifier.weight(1f)) { value ->
                        onChange(item.id) { it.copy(end = value) }
                    }
                }
                EditableMultilineField("Responsibilities & Impact (One bullet per line)", item.bullets) { value ->
                    onChange(item.id) { it.copy(bullets = value) }
                }
                TextButton(onClick = { onDelete(item.id) }) { Text("− Remove experience", color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(color = SlateGray.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun EditorSectionContainer(
    title: String,
    description: String,
    onAdd: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = SlateGray)
            }
            if (onAdd != null) {
                TextButton(onClick = onAdd) { Text("+ Add", color = InkBlack) }
            }
        }
        content()
        HorizontalDivider(color = DividerGray)
    }
}

@Composable
private fun GeneratedResumePreview(
    generated: GeneratedResumeDocument,
    pdfUrl: String?
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceLight.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, InkBlack.copy(alpha = 0.3f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Generated output", style = MaterialTheme.typography.titleLarge, color = CharcoalDark)
            Text("Engine: ${generated.templateName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pdfUrl != null) {
                ResumeNoticeCard(
                    title = "PDF Exported successfully",
                    body = "Ready to download or print.",
                    accent = InkBlack,
                    isDark = true
                )
                PillButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openExternalUrl(context, pdfUrl) },
                    label = "Open PDF Document"
                )
            }
            ResumePreviewSection("Basics", listOfNotNull(
                generated.resumeJson.basics.name.takeIf(String::isNotBlank),
                generated.resumeJson.basics.email.takeIf(String::isNotBlank),
                generated.resumeJson.basics.phone.takeIf(String::isNotBlank),
                generated.resumeJson.basics.location.takeIf(String::isNotBlank),
                generated.resumeJson.basics.linkedin.takeIf(String::isNotBlank),
                generated.resumeJson.basics.github.takeIf(String::isNotBlank),
                generated.resumeJson.basics.portfolio.takeIf(String::isNotBlank)
            ))
            ResumePreviewSection("Skills", generated.resumeJson.skills)
            ResumePreviewSection("Achievements", generated.resumeJson.achievements)
            ResumePreviewSection(
                "Education",
                generated.resumeJson.education.map {
                    listOfNotNull(it.school, it.degree, it.start, it.end, it.gpa).joinToString(" • ")
                }
            )
            ResumePreviewSection(
                "Projects",
                generated.resumeJson.projects.map {
                    listOfNotNull(it.name, it.description).joinToString(" - ")
                }
            )
            ResumePreviewSection(
                "Experience",
                generated.resumeJson.experience.map {
                    listOfNotNull(it.company, it.role).joinToString(" - ")
                }
            )
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }
}

@Composable
private fun ResumePreviewSection(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = InkBlack)
        items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditableTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = CoolGray.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun EditableMultilineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = CoolGray.copy(alpha = 0.5f)
        )
    )
}

private fun ResumeBuilderUiState.toInput(): ResumeBuilderInput {
    return ResumeBuilderInput(
        basics = ResumeBasics(
            name = basics.name.trim(),
            email = basics.email.trim(),
            phone = basics.phone.trim(),
            location = basics.location.trim(),
            linkedin = basics.linkedin.trim(),
            github = basics.github.trim(),
            portfolio = basics.portfolio.trim()
        ),
        education = education
            .mapNotNull { entry ->
                if (entry.school.isBlank() && entry.degree.isBlank()) return@mapNotNull null
                ResumeEducationEntry(
                    school = entry.school.trim(),
                    degree = entry.degree.trim(),
                    start = entry.start.trim(),
                    end = entry.end.trim(),
                    gpa = entry.gpa.trim()
                )
            },
        skills = skills.mapNotNull { it.value.trim().takeIf(String::isNotBlank) },
        projects = projects
            .mapNotNull { entry ->
                if (entry.name.isBlank() && entry.description.isBlank()) return@mapNotNull null
                ResumeProjectEntry(
                    name = entry.name.trim(),
                    description = entry.description.trim(),
                    highlights = splitLines(entry.highlights)
                )
            },
        experience = experience
            .mapNotNull { entry ->
                if (entry.company.isBlank() && entry.role.isBlank()) return@mapNotNull null
                ResumeExperienceEntry(
                    company = entry.company.trim(),
                    role = entry.role.trim(),
                    start = entry.start.trim(),
                    end = entry.end.trim(),
                    bullets = splitLines(entry.bullets)
                )
            },
        achievements = achievements.mapNotNull { it.value.trim().takeIf(String::isNotBlank) }
    )
}

private fun splitLines(value: String): List<String> {
    return value
        .lineSequence()
        .map { it.trim() }
        .filter(String::isNotBlank)
        .toList()
}

private fun newEditorId(): String = UUID.randomUUID().toString()

private fun <T> QueryResult<T>.successData(): T? = (this as? QueryResult.Success<T>)?.data
