package com.internshipuncle.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Login : AppDestination("login")
    data object Signup : AppDestination("signup")
    data object Onboarding : AppDestination("onboarding")
    data object Jobs : AppDestination("jobs")
    data object Analyze : AppDestination("more")

    data object ResumeMatcher : AppDestination("resume-matcher?targetJobId={targetJobId}&startMode={startMode}") {
        fun createRoute(targetJobId: String? = null, startMode: String? = null): String {
            return "resume-matcher?targetJobId=${targetJobId ?: ""}&startMode=${startMode ?: ""}"
        }
    }
    data object SavedJobs : AppDestination("jobs/saved")
    data object JobDetail : AppDestination("job/{jobId}") {
        fun createRoute(jobId: String) = "job/$jobId"
    }

    data object Analysis : AppDestination("analysis/{jobId}") {
        fun createRoute(jobId: String) = "analysis/$jobId"
    }

    data object ResumeUpload : AppDestination("resume/upload?targetJobId={targetJobId}") {
        fun createRoute(targetJobId: String? = null): String {
            return targetJobId?.let { "resume/upload?targetJobId=$it" } ?: "resume/upload"
        }
    }

    data object ResumeBuilder : AppDestination("resume/builder?targetJobId={targetJobId}") {
        fun createRoute(targetJobId: String? = null): String {
            return targetJobId?.let { "resume/builder?targetJobId=$it" } ?: "resume/builder"
        }
    }

    data object ResumeRoast : AppDestination("resume/roast/{resumeId}?targetJobId={targetJobId}") {
        fun createRoute(resumeId: String, targetJobId: String? = null): String {
            return targetJobId?.let { "resume/roast/$resumeId?targetJobId=$it" } ?: "resume/roast/$resumeId"
        }
    }

    data object MockInterview : AppDestination("interview/mock?targetJobId={targetJobId}") {
        fun createRoute(targetJobId: String? = null): String {
            return targetJobId?.let { "interview/mock?targetJobId=$it" } ?: "interview/mock"
        }
    }

    data object CommunityFeed : AppDestination("more/community")
    data object RealityCheck : AppDestination("more/reality")
    data object Progress : AppDestination("more/progress")
    data object Referrals : AppDestination("more/referrals")
    data object Profile : AppDestination("profile")
    data object MockInterviewSession : AppDestination("interview/mock/session/{sessionId}") {
        fun createRoute(sessionId: String): String = "interview/mock/session/$sessionId"
    }
    data object MockInterviewSummary : AppDestination("interview/mock/summary/{sessionId}?skippedCount={skippedCount}") {
        fun createRoute(sessionId: String, skippedCount: Int = 0): String {
            return "interview/mock/summary/$sessionId?skippedCount=$skippedCount"
        }
    }
    data object Dashboard : AppDestination("dashboard")
}
