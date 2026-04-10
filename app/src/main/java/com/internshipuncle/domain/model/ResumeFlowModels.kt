package com.internshipuncle.domain.model

data class ResumeBasics(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val linkedin: String = "",
    val github: String = "",
    val portfolio: String = ""
)

data class ResumeEducationEntry(
    val school: String = "",
    val degree: String = "",
    val start: String = "",
    val end: String = "",
    val gpa: String = ""
)

data class ResumeProjectEntry(
    val name: String = "",
    val description: String = "",
    val highlights: List<String> = emptyList()
)

data class ResumeExperienceEntry(
    val company: String = "",
    val role: String = "",
    val start: String = "",
    val end: String = "",
    val bullets: List<String> = emptyList()
)

data class ResumeBuilderInput(
    val basics: ResumeBasics = ResumeBasics(),
    val education: List<ResumeEducationEntry> = emptyList(),
    val skills: List<String> = emptyList(),
    val projects: List<ResumeProjectEntry> = emptyList(),
    val experience: List<ResumeExperienceEntry> = emptyList(),
    val achievements: List<String> = emptyList()
)

data class GeneratedResumeDocument(
    val generatedResumeId: String,
    val resumeJson: ResumeBuilderInput,
    val templateName: String,
    val pdfUrl: String? = null,
    val sourceResumeId: String? = null,
    val targetJobId: String? = null
)

data class ResumeRoastIssue(
    val section: String? = null,
    val severity: String? = null,
    val message: String? = null
)

data class ResumeRoastResult(
    val issues: List<ResumeRoastIssue> = emptyList(),
    val missingKeywords: List<String> = emptyList(),
    val weakBullets: List<String> = emptyList(),
    val rewrittenBullets: List<String> = emptyList(),
    val comments: List<String> = emptyList()
)

data class ResumeRoastDetail(
    val resumeId: String,
    val targetJobId: String?,
    val overallScore: Int,
    val atsScore: Int,
    val relevanceScore: Int,
    val clarityScore: Int,
    val formattingScore: Int,
    val roastResult: ResumeRoastResult
)

data class ResumeUploadResult(
    val resumeId: String,
    val fileName: String,
    val fileUrl: String,
    val parsedText: String? = null
)

data class GeneratedResumeSummary(
    val id: String,
    val templateName: String?,
    val pdfUrl: String?,
    val createdAt: String?
)
