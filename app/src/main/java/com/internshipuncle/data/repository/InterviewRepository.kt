package com.internshipuncle.data.repository

import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.network.AppConfig
import com.internshipuncle.data.dto.EvaluateAnswerRequestDto
import com.internshipuncle.data.dto.EvaluateAnswerResponseDto
import com.internshipuncle.data.dto.GenerateMockSessionRequestDto
import com.internshipuncle.data.dto.GenerateMockSessionResponseDto
import com.internshipuncle.data.dto.MockAnswerDto
import com.internshipuncle.data.dto.MockQuestionDto
import com.internshipuncle.data.dto.MockSessionDto
import com.internshipuncle.data.mapper.toDomainModel
import com.internshipuncle.data.remote.SupabaseFunctions
import com.internshipuncle.data.remote.SupabaseTables
import com.internshipuncle.domain.model.InterviewSessionSummary
import com.internshipuncle.domain.model.MockInterviewAnswerEvaluation
import com.internshipuncle.domain.model.MockInterviewSessionDetail
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerializationException

interface InterviewRepository {
    fun recentSessions(): Flow<List<InterviewSessionSummary>>
    fun mockSession(sessionId: String): Flow<QueryResult<MockInterviewSessionDetail?>>
    fun refresh()
    suspend fun createMockSession(
        targetJobId: String?,
        roleName: String,
        difficulty: String,
        mode: String,
        includeResume: Boolean
    ): QueryResult<MockInterviewSessionDetail>
    suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        answerText: String
    ): QueryResult<MockInterviewAnswerEvaluation>
}

class SupabaseInterviewRepository @Inject constructor(
    private val appConfig: AppConfig,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
    private val dashboardRefreshBus: DashboardRefreshBus
) : InterviewRepository {
    private val refreshSignal = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun recentSessions(): Flow<List<InterviewSessionSummary>> = refreshSignal.flatMapLatest {
        flow { emit(fetchSessions()) }
    }

    override fun refresh() {
        refreshSignal.update { it + 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun mockSession(sessionId: String): Flow<QueryResult<MockInterviewSessionDetail?>> =
        refreshSignal.flatMapLatest {
            queryFlow { fetchSession(sessionId) }
        }

    override suspend fun createMockSession(
        targetJobId: String?,
        roleName: String,
        difficulty: String,
        mode: String,
        includeResume: Boolean
    ): QueryResult<MockInterviewSessionDetail> {
        if (!appConfig.isSupabaseConfigured) {
            return QueryResult.NotConfigured
        }

        if (roleName.isBlank() || difficulty.isBlank() || mode.isBlank()) {
            return QueryResult.Failure("Mock session setup needs a role, difficulty, and mode.")
        }

        if (auth.currentUserOrNull()?.id == null) {
            return QueryResult.Failure("Sign in to start a mock interview session.")
        }

        return try {
            val response = functions.invoke(
                function = SupabaseFunctions.GENERATE_MOCK_SESSION,
                body = GenerateMockSessionRequestDto(
                    targetJobId = targetJobId,
                    roleName = roleName,
                    difficulty = difficulty,
                    mode = mode,
                    includeResume = includeResume
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<GenerateMockSessionResponseDto>()

            refresh()
            dashboardRefreshBus.refresh()

            QueryResult.Success(
                response.toDomainModel().copy(
                    targetJobId = targetJobId,
                    roleName = roleName,
                    difficulty = difficulty,
                    mode = mode
                )
            )
        } catch (error: Exception) {
            if (error.isMissingInterviewBackend()) {
                QueryResult.BackendNotReady
            } else if (error is SerializationException) {
                QueryResult.Failure(
                    message = "The interview backend returned an unexpected response.",
                    cause = error
                )
            } else {
                QueryResult.Failure(
                    message = error.message ?: "Unable to generate the mock interview session.",
                    cause = error
                )
            }
        }
    }

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        answerText: String
    ): QueryResult<MockInterviewAnswerEvaluation> {
        if (!appConfig.isSupabaseConfigured) {
            return QueryResult.NotConfigured
        }

        if (sessionId.isBlank() || questionId.isBlank() || answerText.isBlank()) {
            return QueryResult.Failure("Answer submission requires a session, question, and answer text.")
        }

        if (auth.currentUserOrNull()?.id == null) {
            return QueryResult.Failure("Sign in to submit interview answers.")
        }

        return try {
            val response = functions.invoke(
                function = SupabaseFunctions.EVALUATE_ANSWER,
                body = EvaluateAnswerRequestDto(
                    sessionId = sessionId,
                    questionId = questionId,
                    answerText = answerText
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<EvaluateAnswerResponseDto>()

            refresh()
            dashboardRefreshBus.refresh()
            QueryResult.Success(response.toDomainModel(answerText))
        } catch (error: Exception) {
            if (error.isMissingInterviewBackend()) {
                QueryResult.BackendNotReady
            } else if (error is SerializationException) {
                QueryResult.Failure(
                    message = "The interview backend returned an unexpected response.",
                    cause = error
                )
            } else {
                QueryResult.Failure(
                    message = error.message ?: "Unable to evaluate the answer.",
                    cause = error
                )
            }
        }
    }

    private suspend fun fetchSessions(): List<InterviewSessionSummary> {
        if (!appConfig.isSupabaseConfigured) {
            return emptyList()
        }

        return try {
            postgrest.from(SupabaseTables.MOCK_SESSIONS)
                .select {
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<MockSessionDto>()
                .map(MockSessionDto::toDomainModel)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSession(sessionId: String): MockInterviewSessionDetail? {
        if (!appConfig.isSupabaseConfigured || sessionId.isBlank()) {
            return null
        }

        val session = postgrest.from(SupabaseTables.MOCK_SESSIONS)
            .select {
                filter {
                    eq("id", sessionId)
                }
            }
            .decodeList<MockSessionDto>()
            .firstOrNull()
            ?: return null

        val questions = postgrest.from(SupabaseTables.MOCK_QUESTIONS)
            .select {
                filter {
                    eq("session_id", sessionId)
                }
                order(column = "sequence_no", order = Order.ASCENDING)
            }
            .decodeList<MockQuestionDto>()

        val answers = if (questions.isEmpty()) {
            emptyList()
        } else {
            val questionIds = questions.map(MockQuestionDto::id)
            postgrest.from(SupabaseTables.MOCK_ANSWERS)
                .select {
                    filter {
                        isIn("question_id", questionIds)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<MockAnswerDto>()
        }

        val latestAnswersByQuestionId = answers
            .groupBy(MockAnswerDto::questionId)
            .mapValues { entry -> entry.value.firstOrNull() }

        return MockInterviewSessionDetail(
            id = session.id,
            targetJobId = session.targetJobId,
            roleName = session.roleName,
            difficulty = session.difficulty,
            mode = session.mode,
            overallScore = session.overallScore,
            createdAt = session.createdAt,
            questions = questions.map { question ->
                question.toDomainModel(latestAnswersByQuestionId[question.id]?.toDomainModel())
            }
        )
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

    private fun Exception.toQueryResult(): QueryResult<Nothing> {
        return when {
            isMissingInterviewBackend() -> QueryResult.BackendNotReady
            else -> QueryResult.Failure(
                message = message ?: "Unable to load interview data from Supabase.",
                cause = this
            )
        }
    }

    private fun Exception.isMissingInterviewBackend(): Boolean {
        val message = message.orEmpty()
        val referencesInterviewBackend = listOf(
            "mock_sessions",
            "mock_questions",
            "mock_answers",
            "generate-mock-session",
            "evaluate-answer"
        ).any { token -> message.contains(token, ignoreCase = true) }

        return referencesInterviewBackend && listOf(
            "does not exist",
            "schema cache",
            "Could not find the table",
            "PGRST",
            "function",
            "not found"
        ).any { marker -> message.contains(marker, ignoreCase = true) }
    }
}
