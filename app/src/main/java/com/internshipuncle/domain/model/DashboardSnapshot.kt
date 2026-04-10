package com.internshipuncle.domain.model

data class DashboardDeadlineItem(
    val jobId: String,
    val title: String,
    val company: String,
    val deadline: String,
    val location: String? = null,
    val workMode: String? = null
)

enum class DashboardActivityType {
    SavedJob,
    ResumeRoast,
    MockInterview
}

data class DashboardActivityItem(
    val type: DashboardActivityType,
    val title: String,
    val details: String,
    val createdAt: String? = null,
    val score: Int? = null,
    val jobId: String? = null,
    val resumeId: String? = null,
    val targetJobId: String? = null,
    val sessionId: String? = null
)

data class DashboardSnapshot(
    val readinessScore: Int?,
    val latestResumeScore: Int?,
    val latestMockScore: Int?,
    val savedJobsCount: Int,
    val upcomingDeadlines: List<DashboardDeadlineItem> = emptyList(),
    val recentActivity: List<DashboardActivityItem> = emptyList(),
    val isConfigured: Boolean
) {
    val hasContent: Boolean
        get() = latestResumeScore != null ||
            latestMockScore != null ||
            savedJobsCount > 0 ||
            upcomingDeadlines.isNotEmpty() ||
            recentActivity.isNotEmpty()
}
