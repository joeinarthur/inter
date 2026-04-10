package com.internshipuncle.data.repository

import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.network.AppConfig
import com.internshipuncle.data.dto.JobDto
import com.internshipuncle.data.dto.MockAnswerDto
import com.internshipuncle.data.dto.MockQuestionDto
import com.internshipuncle.data.dto.MockSessionDto
import com.internshipuncle.data.dto.ProfileDto
import com.internshipuncle.data.dto.ResumeDto
import com.internshipuncle.data.dto.ResumeRoastDto
import com.internshipuncle.data.dto.SavedJobDto
import com.internshipuncle.data.mapper.toDomainModel
import com.internshipuncle.data.remote.SupabaseTables
import com.internshipuncle.domain.model.DashboardActivityItem
import com.internshipuncle.domain.model.DashboardActivityType
import com.internshipuncle.domain.model.DashboardDeadlineItem
import com.internshipuncle.domain.model.DashboardSnapshot
import com.internshipuncle.domain.model.UserProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException

interface DashboardRepository {
    fun snapshot(): Flow<QueryResult<DashboardSnapshot>>
    fun refresh()
}

class SupabaseDashboardRepository @Inject constructor(
    private val appConfig: AppConfig,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val dashboardRefreshBus: DashboardRefreshBus
) : DashboardRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun snapshot(): Flow<QueryResult<DashboardSnapshot>> = dashboardRefreshBus.ticks.flatMapLatest {
        queryFlow { buildSnapshot() }
    }

    override fun refresh() {
        dashboardRefreshBus.refresh()
    }

    private suspend fun buildSnapshot(): DashboardSnapshot = coroutineScope {
        val user = auth.currentUserOrNull()
            ?: return@coroutineScope DashboardSnapshot(
                readinessScore = null,
                latestResumeScore = null,
                latestMockScore = null,
                savedJobsCount = 0,
                upcomingDeadlines = emptyList(),
                recentActivity = emptyList(),
                isConfigured = true
            )

        val profileDeferred = async { loadProfile(user.id) }
        val savedJobsDeferred = async { loadSavedJobs(user.id) }
        val latestResumeDeferred = async { loadLatestResume(user.id) }
        val latestRoastDeferred = async { loadLatestResumeRoast(user.id) }
        val latestSessionDeferred = async { loadLatestSession(user.id) }

        val profile = profileDeferred.await()
        val savedJobs = savedJobsDeferred.await()
        val latestResume = latestResumeDeferred.await()
        val latestRoast = latestRoastDeferred.await()
        val latestSession = latestSessionDeferred.await()

        val latestMockScore = latestSession?.overallScore
            ?: latestSession?.let { loadLatestMockScore(it.id) }
        val latestResumeScore = latestRoast?.overallScore ?: latestResume?.latestScore

        val relatedJobIds = linkedSetOf<String>().apply {
            savedJobs.forEach { add(it.jobId) }
            latestRoast?.targetJobId?.takeIf(String::isNotBlank)?.let(::add)
            latestSession?.targetJobId?.takeIf(String::isNotBlank)?.let(::add)
        }.toList()

        val jobsById = loadJobsByIds(relatedJobIds)
        val upcomingDeadlines = buildUpcomingDeadlines(savedJobs, jobsById)
        val recentActivity = buildRecentActivity(
            savedJobs = savedJobs,
            jobsById = jobsById,
            latestRoast = latestRoast,
            latestSession = latestSession
        )

        DashboardSnapshot(
            readinessScore = computeReadinessScore(
                profile = profile,
                latestResumeScore = latestResumeScore,
                latestMockScore = latestMockScore,
                savedJobsCount = savedJobs.size,
                upcomingDeadlinesCount = upcomingDeadlines.size
            ),
            latestResumeScore = latestResumeScore,
            latestMockScore = latestMockScore,
            savedJobsCount = savedJobs.size,
            upcomingDeadlines = upcomingDeadlines,
            recentActivity = recentActivity,
            isConfigured = true
        )
    }

    private suspend fun loadProfile(userId: String): UserProfile? {
        return try {
            postgrest.from(SupabaseTables.PROFILES)
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<ProfileDto>()
                .firstOrNull()
                ?.toDomainModel()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadSavedJobs(userId: String): List<SavedJobDto> {
        return try {
            postgrest.from(SupabaseTables.SAVED_JOBS)
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "saved")
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<SavedJobDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun loadLatestResume(userId: String): ResumeDto? {
        return try {
            postgrest.from(SupabaseTables.RESUMES)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<ResumeDto>()
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadLatestResumeRoast(userId: String): ResumeRoastDto? {
        return try {
            postgrest.from(SupabaseTables.RESUME_ROASTS)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<ResumeRoastDto>()
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadLatestSession(userId: String): MockSessionDto? {
        return try {
            postgrest.from(SupabaseTables.MOCK_SESSIONS)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<MockSessionDto>()
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadLatestMockScore(sessionId: String): Int? {
        return try {
            val questions = postgrest.from(SupabaseTables.MOCK_QUESTIONS)
                .select {
                    filter {
                        eq("session_id", sessionId)
                    }
                }
                .decodeList<MockQuestionDto>()

            if (questions.isEmpty()) {
                return null
            }

            val questionIds = questions.map(MockQuestionDto::id)
            val answers = postgrest.from(SupabaseTables.MOCK_ANSWERS)
                .select {
                    filter {
                        isIn("question_id", questionIds)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<MockAnswerDto>()

            answers.mapNotNull(MockAnswerDto::score)
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadJobsByIds(jobIds: List<String>): Map<String, JobDto> {
        if (jobIds.isEmpty()) {
            return emptyMap()
        }

        return try {
            postgrest.from(SupabaseTables.JOBS)
                .select {
                    filter {
                        eq("is_active", true)
                        isIn("id", jobIds)
                    }
                }
                .decodeList<JobDto>()
                .associateBy(JobDto::id)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun buildUpcomingDeadlines(
        savedJobs: List<SavedJobDto>,
        jobsById: Map<String, JobDto>
    ): List<DashboardDeadlineItem> {
        val savedJobDeadlines = savedJobs.mapNotNull { savedJob ->
            val job = jobsById[savedJob.jobId] ?: return@mapNotNull null
            val deadline = job.deadline?.takeIf(String::isNotBlank) ?: return@mapNotNull null

            DashboardDeadlineItem(
                jobId = job.id,
                title = job.title,
                company = job.company,
                deadline = deadline,
                location = job.location?.takeIf(String::isNotBlank),
                workMode = job.workMode?.takeIf(String::isNotBlank)
            )
        }.sortedBy { it.deadline }

        if (savedJobDeadlines.isNotEmpty()) {
            return savedJobDeadlines.take(3)
        }

        return try {
            postgrest.from(SupabaseTables.JOBS)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order(column = "deadline", order = Order.ASCENDING)
                }
                .decodeList<JobDto>()
                .mapNotNull { job ->
                    val deadline = job.deadline?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    DashboardDeadlineItem(
                        jobId = job.id,
                        title = job.title,
                        company = job.company,
                        deadline = deadline,
                        location = job.location?.takeIf(String::isNotBlank),
                        workMode = job.workMode?.takeIf(String::isNotBlank)
                    )
                }
                .take(3)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildRecentActivity(
        savedJobs: List<SavedJobDto>,
        jobsById: Map<String, JobDto>,
        latestRoast: ResumeRoastDto?,
        latestSession: MockSessionDto?
    ): List<DashboardActivityItem> {
        val latestSavedJob = savedJobs.firstOrNull()?.let { savedJob ->
            val job = jobsById[savedJob.jobId]
            DashboardActivityItem(
                type = DashboardActivityType.SavedJob,
                title = job?.title ?: "Saved internship",
                details = job?.company?.let { company ->
                    listOfNotNull(company, job.location?.takeIf(String::isNotBlank))
                        .joinToString(" | ")
                } ?: "Saved to your shortlist",
                createdAt = savedJob.createdAt,
                jobId = savedJob.jobId
            )
        }

        val roastActivity = latestRoast?.let { roast ->
            val job = roast.targetJobId?.let(jobsById::get)
            DashboardActivityItem(
                type = DashboardActivityType.ResumeRoast,
                title = job?.title?.let { "Resume roast for $it" } ?: "Latest resume roast",
                details = listOfNotNull(
                    roast.overallScore?.let { "Score $it" },
                    job?.company,
                    job?.location?.takeIf(String::isNotBlank)
                ).joinToString(" | ").ifBlank { "Structured feedback saved" },
                createdAt = roast.createdAt,
                score = roast.overallScore,
                jobId = roast.targetJobId,
                resumeId = roast.resumeId,
                targetJobId = roast.targetJobId
            )
        }

        val mockActivity = latestSession?.let { session ->
            val job = session.targetJobId?.let(jobsById::get)
            DashboardActivityItem(
                type = DashboardActivityType.MockInterview,
                title = job?.title?.let { "Mock interview for $it" } ?: "Latest mock interview",
                details = listOfNotNull(
                    session.overallScore?.let { "Score $it" },
                    session.difficulty?.replaceFirstChar(Char::uppercaseChar),
                    session.mode?.replace("_", " ")?.replaceFirstChar(Char::uppercaseChar)
                ).joinToString(" | ").ifBlank { "Interview session completed" },
                createdAt = session.createdAt,
                score = session.overallScore,
                jobId = session.targetJobId,
                targetJobId = session.targetJobId,
                sessionId = session.id
            )
        }

        return listOfNotNull(roastActivity, mockActivity, latestSavedJob)
            .sortedByDescending { it.createdAt.orEmpty() }
            .take(4)
    }

    private fun computeReadinessScore(
        profile: UserProfile?,
        latestResumeScore: Int?,
        latestMockScore: Int?,
        savedJobsCount: Int,
        upcomingDeadlinesCount: Int
    ): Int {
        val profileScore = profileCompletionScore(profile)
        val resumeScore = latestResumeScore?.coerceIn(0, 100)?.times(25)?.div(100) ?: 0
        val mockScore = latestMockScore?.coerceIn(0, 100)?.times(25)?.div(100) ?: 0
        val savedJobScore = when {
            savedJobsCount <= 0 -> 0
            savedJobsCount == 1 -> 5
            savedJobsCount == 2 -> 8
            savedJobsCount == 3 -> 10
            savedJobsCount == 4 -> 12
            else -> 15
        }
        val deadlineScore = if (upcomingDeadlinesCount > 0) 10 else 0

        return (profileScore + resumeScore + mockScore + savedJobScore + deadlineScore)
            .coerceIn(0, 100)
    }

    private fun profileCompletionScore(profile: UserProfile?): Int {
        val fields = listOf(
            profile?.name?.isNotBlank() == true,
            profile?.college?.isNotBlank() == true,
            profile?.degree?.isNotBlank() == true,
            profile?.graduationYear != null,
            profile?.targetRoles?.isNotEmpty() == true
        )

        return fields.count { it } * 5
    }

    private fun <T> queryFlow(loader: suspend () -> T): Flow<QueryResult<T>> = flow {
        if (!appConfig.isSupabaseConfigured) {
            emit(QueryResult.NotConfigured)
            return@flow
        }

        emit(QueryResult.Loading)

        try {
            emit(QueryResult.Success(loader()))
        } catch (error: Exception) {
            emit(error.toQueryResult())
        }
    }

    private fun Exception.toQueryResult(): QueryResult<Nothing> {
        return when {
            isDashboardBackendMissing() -> QueryResult.BackendNotReady
            this is SerializationException -> QueryResult.Failure(
                message = "The dashboard backend returned an unexpected response.",
                cause = this
            )
            else -> QueryResult.Failure(
                message = message ?: "Unable to load the dashboard.",
                cause = this
            )
        }
    }

    private fun Exception.isDashboardBackendMissing(): Boolean {
        val message = message.orEmpty()
        val referencesBackend = listOf(
            "profiles",
            "jobs",
            "saved_jobs",
            "resumes",
            "resume_roasts",
            "mock_sessions",
            "mock_questions",
            "mock_answers"
        ).any { token -> message.contains(token, ignoreCase = true) }

        return referencesBackend && listOf(
            "does not exist",
            "schema cache",
            "Could not find the table",
            "PGRST",
            "function",
            "not found"
        ).any { marker -> message.contains(marker, ignoreCase = true) }
    }
}
