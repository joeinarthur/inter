package com.internshipuncle.data.repository

import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.core.network.AppConfig
import com.internshipuncle.data.dto.JobAnalysisDto
import com.internshipuncle.data.dto.JobDto
import com.internshipuncle.data.dto.SavedJobDto
import com.internshipuncle.data.dto.SavedJobUpsertDto
import com.internshipuncle.data.mapper.toDetailModel
import com.internshipuncle.data.mapper.toDomainModel
import com.internshipuncle.data.remote.SupabaseTables
import com.internshipuncle.domain.model.JobAnalysis
import com.internshipuncle.domain.model.JobCard
import com.internshipuncle.domain.model.JobDetail
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException

interface JobsRepository {
    fun featuredJobs(): Flow<QueryResult<List<JobCard>>>
    fun latestJobs(): Flow<QueryResult<List<JobCard>>>
    fun jobDetail(jobId: String): Flow<QueryResult<JobDetail?>>
    fun jobAnalysis(jobId: String): Flow<QueryResult<JobAnalysis?>>
    fun savedJobs(): Flow<QueryResult<List<JobCard>>>
    fun refresh()
    suspend fun saveJob(jobId: String): RepositoryStatus
    suspend fun unsaveJob(jobId: String): RepositoryStatus
}

class SupabaseJobsRepository @Inject constructor(
    private val appConfig: AppConfig,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val dashboardRefreshBus: DashboardRefreshBus
) : JobsRepository {
    private val refreshSignal = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun featuredJobs(): Flow<QueryResult<List<JobCard>>> =
        refreshSignal.flatMapLatest {
            queryFlow { fetchFeaturedJobs() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun latestJobs(): Flow<QueryResult<List<JobCard>>> =
        refreshSignal.flatMapLatest {
            queryFlow { fetchLatestJobs() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun jobDetail(
        jobId: String
    ): Flow<QueryResult<JobDetail?>> = refreshSignal.flatMapLatest {
        queryFlow { fetchJob(jobId) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun jobAnalysis(
        jobId: String
    ): Flow<QueryResult<JobAnalysis?>> = refreshSignal.flatMapLatest {
        queryFlow { fetchAnalysis(jobId) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun savedJobs(): Flow<QueryResult<List<JobCard>>> =
        refreshSignal.flatMapLatest {
            queryFlow { fetchSavedJobs() }
        }

    override fun refresh() {
        refreshSignal.update { it + 1 }
        dashboardRefreshBus.refresh()
    }

    override suspend fun saveJob(
        jobId: String
    ): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        if (jobId.isBlank()) {
            return RepositoryStatus.Failure("A valid job id is required.")
        }

        val user = auth.currentUserOrNull()
            ?: return RepositoryStatus.Failure("Sign in to save internships.")

        return try {
            postgrest.from(SupabaseTables.SAVED_JOBS).upsert(
                value = SavedJobUpsertDto(
                    userId = user.id,
                    jobId = jobId
                )
            ) {
                onConflict = "user_id,job_id"
            }
            refresh()
            RepositoryStatus.Success
        } catch (error: Exception) {
            when {
                error.isJobsBackendMissing() -> RepositoryStatus.BackendNotReady
                else -> RepositoryStatus.Failure(
                    message = error.message ?: "Unable to save this internship.",
                    cause = error
                )
            }
        }
    }

    override suspend fun unsaveJob(
        jobId: String
    ): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        if (jobId.isBlank()) {
            return RepositoryStatus.Failure("A valid job id is required.")
        }

        val user = auth.currentUserOrNull()
            ?: return RepositoryStatus.Failure("Sign in to manage saved internships.")

        return try {
            postgrest.from(SupabaseTables.SAVED_JOBS)
                .delete {
                    filter {
                        eq("job_id", jobId)
                        eq("user_id", user.id)
                }
                }
            refresh()
            RepositoryStatus.Success
        } catch (error: Exception) {
            when {
                error.isJobsBackendMissing() -> RepositoryStatus.BackendNotReady
                else -> RepositoryStatus.Failure(
                    message = error.message ?: "Unable to remove this internship from saved jobs.",
                    cause = error
                )
            }
        }
    }

    private fun <T> queryFlow(
        loader: suspend () -> T
    ): Flow<QueryResult<T>> = flow {
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

    private suspend fun fetchFeaturedJobs(): List<JobCard> {
        return postgrest.from(SupabaseTables.JOBS)
            .select {
                filter {
                    eq("is_active", true)
                    eq("is_featured", true)
                }
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<JobDto>()
            .map(JobDto::toDomainModel)
    }

    private suspend fun fetchLatestJobs(): List<JobCard> {
        return postgrest.from(SupabaseTables.JOBS)
            .select {
                filter {
                    eq("is_active", true)
                }
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<JobDto>()
            .map(JobDto::toDomainModel)
    }

    private suspend fun fetchJob(
        jobId: String
    ): JobDetail? {
        if (jobId.isBlank()) {
            return null
        }

        return postgrest.from(SupabaseTables.JOBS)
            .select {
                filter {
                    eq("id", jobId)
                }
            }
            .decodeList<JobDto>()
            .firstOrNull()
            ?.toDetailModel()
    }

    private suspend fun fetchAnalysis(
        jobId: String
    ): JobAnalysis? {
        if (jobId.isBlank()) {
            return null
        }

        return postgrest.from(SupabaseTables.JOB_ANALYSIS)
            .select {
                filter {
                    eq("job_id", jobId)
                }
            }
            .decodeList<JobAnalysisDto>()
            .firstOrNull()
            ?.toDomainModel()
    }

    private suspend fun fetchSavedJobs(): List<JobCard> {
        val savedJobs = postgrest.from(SupabaseTables.SAVED_JOBS)
            .select {
                filter {
                    eq("status", "saved")
                }
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<SavedJobDto>()

        if (savedJobs.isEmpty()) {
            return emptyList()
        }

        val savedIds = savedJobs.map(SavedJobDto::jobId)
        val jobsById = postgrest.from(SupabaseTables.JOBS)
            .select {
                filter {
                    eq("is_active", true)
                    isIn("id", savedIds)
                }
            }
            .decodeList<JobDto>()
            .map(JobDto::toDomainModel)
            .associateBy(JobCard::id)

        return savedIds.mapNotNull(jobsById::get)
    }

    private fun Exception.toQueryResult(): QueryResult<Nothing> {
        return when {
            isJobsBackendMissing() -> QueryResult.BackendNotReady
            this is SerializationException -> QueryResult.Failure(
                message = "The jobs backend returned an unexpected response.",
                cause = this
            )
            else -> QueryResult.Failure(
                message = message ?: "Unable to load internship data from Supabase.",
                cause = this
            )
        }
    }

    private fun Exception.isJobsBackendMissing(): Boolean {
        val errorMessage = message.orEmpty()
        val referencesJobsBackend = listOf("jobs", "job_analysis", "saved_jobs")
            .any { table -> errorMessage.contains(table, ignoreCase = true) }

        return referencesJobsBackend && listOf(
            "does not exist",
            "schema cache",
            "Could not find the table",
            "PGRST"
        ).any { marker -> errorMessage.contains(marker, ignoreCase = true) }
    }
}
