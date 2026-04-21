package com.internshipuncle.domain.model

data class ResumeSummary(
    val id: String,
    val fileName: String?,
    val isParsed: Boolean,
    val latestScore: Int?,
    val createdAt: String?
)

data class ResumeRoastSummary(
    val resumeId: String,
    val overallScore: Int?,
    val atsScore: Int?,
    val relevanceScore: Int?,
    val clarityScore: Int?,
    val formattingScore: Int?
)
