package com.internshipuncle.feature_analyze

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.SurfaceGray
import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.ui.PlaceholderScreen
import com.internshipuncle.data.repository.JobsRepository
import com.internshipuncle.data.repository.ResumeRepository
import com.internshipuncle.domain.model.JobDetail
import com.internshipuncle.domain.model.JobCard
import com.internshipuncle.domain.model.ResumeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeywordAnalysisResult(
    val matchScore: Int,
    val matchedKeywords: List<String>,
    val missingKeywords: List<String>
)

data class AnalysisUiState(
    val inputMode: JobInputMode = JobInputMode.SELECT_SAVED,
    
    // Resume State
    val resumes: List<ResumeSummary> = emptyList(),
    val selectedResumeId: String? = null,
    
    // Select Job State
    val savedJobs: List<JobCard> = emptyList(),
    val selectedJobId: String? = null,
    
    // Paste JD State
    val pastedJobDescription: String = "",
    
    // Analysis State
    val isAnalyzing: Boolean = false,
    val analysisResult: KeywordAnalysisResult? = null,
    val errorMessage: String? = null
) {
    val selectedResume: ResumeSummary?
        get() = resumes.find { it.id == selectedResumeId }
        
    val selectedJob: JobCard?
        get() = savedJobs.find { it.id == selectedJobId }
}

enum class JobInputMode {
    SELECT_SAVED, PASTE_JD
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobsRepository: JobsRepository,
    private val resumeRepository: ResumeRepository
) : ViewModel() {
    private val initialTargetJobId: String? = savedStateHandle["targetJobId"]

    private val _uiState = MutableStateFlow(
        AnalysisUiState(
            inputMode = if (initialTargetJobId != null) JobInputMode.SELECT_SAVED else JobInputMode.PASTE_JD,
            selectedJobId = initialTargetJobId
        )
    )
    val uiState: StateFlow<AnalysisUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                jobsRepository.savedJobs(),
                resumeRepository.resumes()
            ) { jobsResult, resumesResult ->
                val jobs = (jobsResult as? QueryResult.Success)?.data ?: emptyList()
                val resumes = resumesResult.sortedByDescending { it.createdAt }
                
                _uiState.update { state ->
                    state.copy(
                        savedJobs = jobs,
                        resumes = resumes,
                        selectedResumeId = state.selectedResumeId ?: resumes.firstOrNull()?.id,
                        selectedJobId = state.selectedJobId ?: jobs.firstOrNull()?.id
                    )
                }
            }.collect {}
        }
    }

    fun setInputMode(mode: JobInputMode) {
        _uiState.update { it.copy(inputMode = mode, analysisResult = null, errorMessage = null) }
    }
    
    fun selectJob(jobId: String) {
        _uiState.update { it.copy(selectedJobId = jobId, analysisResult = null, errorMessage = null) }
    }
    
    fun selectResume(resumeId: String) {
        _uiState.update { it.copy(selectedResumeId = resumeId, analysisResult = null, errorMessage = null) }
    }
    
    fun updatePastedJD(text: String) {
        _uiState.update { it.copy(pastedJobDescription = text, analysisResult = null, errorMessage = null) }
    }

    fun analyze() {
        val state = _uiState.value
        if (state.selectedResumeId == null) {
            _uiState.update { it.copy(errorMessage = "Please upload a resume first.") }
            return
        }
        
        val textToAnalyze = when (state.inputMode) {
            JobInputMode.PASTE_JD -> state.pastedJobDescription
            JobInputMode.SELECT_SAVED -> state.selectedJob?.title ?: "" // We would ideally fetch full jd
        }
        
        if (textToAnalyze.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Job description cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isAnalyzing = true, errorMessage = null, analysisResult = null) }
        
        viewModelScope.launch {
            // Mock API delay for AI Model processing
            delay(1500)
            
            // Temporary mock logic for keyword comparison. 
            // In a real scenario, this would call a Supabase function that uses an LLM.
            val mockKeywords = listOf("React", "Kotlin", "Android", "Typescript", "Figma", "Firebase", "SQL", "Git", "Java", "Python")
            val textToAnalyzeLower = textToAnalyze.lowercase()
            
            val matched = mutableListOf<String>()
            val missing = mutableListOf<String>()
            
            mockKeywords.forEach { kw ->
                if (textToAnalyzeLower.contains(kw.lowercase())) {
                    // Randomly simulating whether it's in the resume or not
                    if (Math.random() > 0.4) matched.add(kw) else missing.add(kw)
                }
            }
            
            // Add some generic misses if none found to simulate real JD requirements
            if (matched.isEmpty() && missing.isEmpty()) {
                missing.addAll(listOf("Communication", "Teamwork", "Agile"))
                matched.addAll(listOf("Problem Solving"))
            }
            
            val total = matched.size + missing.size
            val score = if (total == 0) 0 else ((matched.size.toFloat() / total) * 100).toInt()

            _uiState.update { 
                it.copy(
                    isAnalyzing = false,
                    analysisResult = KeywordAnalysisResult(
                        matchScore = score,
                        matchedKeywords = matched,
                        missingKeywords = missing
                    )
                )
            }
        }
    }
}

// ── Screens ─────────────────────────────────────────────────────────

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        HeaderSection()
        
        InputSelectionSection(
            uiState = uiState,
            onModeChanged = viewModel::setInputMode,
            onJobSelected = viewModel::selectJob,
            onJDPasted = viewModel::updatePastedJD
        )
        
        ResumeSelectionSection(
            uiState = uiState,
            onResumeSelected = viewModel::selectResume
        )
        
        Button(
            onClick = viewModel::analyze,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkBlack,
                contentColor = PureWhite,
                disabledContainerColor = SurfaceGray,
                disabledContentColor = SlateGray
            ),
            enabled = !uiState.isAnalyzing && uiState.selectedResumeId != null && 
                      (uiState.inputMode == JobInputMode.SELECT_SAVED && uiState.selectedJobId != null || 
                       uiState.inputMode == JobInputMode.PASTE_JD && uiState.pastedJobDescription.isNotBlank())
        ) {
            if (uiState.isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = InkBlack, strokeWidth = 2.dp)
            } else {
                Text("Analyze Job Fit", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
        
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = Color.Red.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        
        uiState.analysisResult?.let { result ->
            HorizontalDivider(color = DividerGray, modifier = Modifier.padding(vertical = 8.dp))
            ResultsSection(result = result)
        }
        
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "JOB ANALYZER",
            style = MaterialTheme.typography.labelMedium,
            color = SlateGray,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Resume Matcher",
            style = MaterialTheme.typography.displaySmall,
            color = InkBlack,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Compare your resume directly against the job requirements using AI keyword extraction.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateGray
        )
    }
}

@Composable
private fun InputSelectionSection(
    uiState: AnalysisUiState,
    onModeChanged: (JobInputMode) -> Unit,
    onJobSelected: (String) -> Unit,
    onJDPasted: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Tabs
        Surface(
            color = SurfaceGray,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                JobInputMode.entries.forEach { mode ->
                    val isSelected = uiState.inputMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PureWhite else Color.Transparent)
                            .clickable { onModeChanged(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (mode == JobInputMode.SELECT_SAVED) "Saved Jobs" else "Paste JD",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) InkBlack else SlateGray
                        )
                    }
                }
            }
        }
        
        // Content
        AnimatedVisibility(visible = uiState.inputMode == JobInputMode.SELECT_SAVED) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a saved job", fontWeight = FontWeight.SemiBold, color = InkBlack)
                if (uiState.savedJobs.isEmpty()) {
                    Text("No saved jobs available.", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceGray,
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.selectedJob?.title ?: "Choose a job...",
                                    color = if (uiState.selectedJob != null) InkBlack else SlateGray,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SlateGray)
                            }
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(PureWhite)
                        ) {
                            uiState.savedJobs.forEach { job ->
                                DropdownMenuItem(
                                    text = { Text(job.title ?: "Untitled Job") },
                                    onClick = { 
                                        onJobSelected(job.id)
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        AnimatedVisibility(visible = uiState.inputMode == JobInputMode.PASTE_JD) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Job Description", fontWeight = FontWeight.SemiBold, color = InkBlack)
                OutlinedTextField(
                    value = uiState.pastedJobDescription,
                    onValueChange = onJDPasted,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Paste the target job description here...", color = SlateGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CharcoalDark,
                        unfocusedBorderColor = DividerGray,
                        cursorColor = InkBlack
                    )
                )
            }
        }
    }
}

@Composable
private fun ResumeSelectionSection(
    uiState: AnalysisUiState,
    onResumeSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Target Resume", fontWeight = FontWeight.SemiBold, color = InkBlack)
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceGray,
                modifier = Modifier.fillMaxWidth().clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.selectedResume?.fileName ?: "No resume found",
                        color = if (uiState.selectedResume != null) InkBlack else SlateGray,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SlateGray)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(PureWhite)
            ) {
                uiState.resumes.forEach { resume ->
                    val fileName = resume.fileName ?: "Untitled Resume"
                    DropdownMenuItem(
                        text = { Text(fileName) },
                        onClick = { 
                            onResumeSelected(resume.id)
                            expanded = false 
                        }
                    )
                }
                if (uiState.resumes.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No uploaded resumes") },
                        onClick = { expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsSection(result: KeywordAnalysisResult) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Match Score Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = InkBlack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Keyword Match",
                    color = SlateGray,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "${result.matchScore}%",
                    color = PureWhite,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Matched Keywords
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Matched", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (result.matchedKeywords.isEmpty()) {
                Text("No technical keywords matched.", color = SlateGray)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.matchedKeywords.forEach { kw ->
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9), // Light green
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC8E6C9))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                Text(kw, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Missing Keywords
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Missing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (result.missingKeywords.isEmpty()) {
                Text("No major keywords missing!", color = SlateGray)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.missingKeywords.forEach { kw ->
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFF0F0), // Light red/pink
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(14.dp))
                                Text(kw, color = Color(0xFFC62828), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
