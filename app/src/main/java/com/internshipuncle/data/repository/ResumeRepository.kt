package com.internshipuncle.data.repository

import com.internshipuncle.core.model.QueryResult
import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.core.network.AppConfig
import com.internshipuncle.data.dto.ExportResumePdfRequestDto
import com.internshipuncle.data.dto.ExportResumePdfResponseDto
import com.internshipuncle.data.dto.GenerateResumeRequestDto
import com.internshipuncle.data.dto.GenerateResumeResponseDto
import com.internshipuncle.data.dto.GeneratedResumeDto
import com.internshipuncle.data.dto.GeneratedResumeJsonDto
import com.internshipuncle.data.dto.ParseResumeRequestDto
import com.internshipuncle.data.dto.ParseResumeResponseDto
import com.internshipuncle.data.dto.ResumeCreateDto
import com.internshipuncle.data.dto.ResumeDto
import com.internshipuncle.data.dto.ResumeRoastDto
import com.internshipuncle.data.dto.RoastResumeRequestDto
import com.internshipuncle.data.dto.RoastResumeResponseDto
import com.internshipuncle.data.mapper.toDomainModel
import com.internshipuncle.data.mapper.toSummaryModel
import com.internshipuncle.data.remote.SupabaseBuckets
import com.internshipuncle.data.remote.SupabaseFunctions
import com.internshipuncle.data.remote.SupabaseTables
import com.internshipuncle.domain.model.GeneratedResumeDocument
import com.internshipuncle.domain.model.GeneratedResumeSummary
import com.internshipuncle.domain.model.ResumeBuilderInput
import com.internshipuncle.domain.model.ResumeRoastDetail
import com.internshipuncle.domain.model.ResumeRoastSummary
import com.internshipuncle.domain.model.ResumeSummary
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

interface ResumeRepository {
    fun resumes(): Flow<List<ResumeSummary>>
    fun roastSummary(resumeId: String): Flow<ResumeRoastSummary?>
    fun generatedResumes(): Flow<List<GeneratedResumeSummary>>

    suspend fun uploadResumeAndParse(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        targetJobId: String? = null
    ): QueryResult<ResumeSummary>

    suspend fun requestResumeParsing(
        resumeId: String,
        fileUrl: String
    ): RepositoryStatus

    suspend fun roastResume(
        resumeId: String,
        targetJobId: String?,
        mode: String
    ): QueryResult<ResumeRoastDetail>

    suspend fun requestResumeRoast(
        resumeId: String,
        targetJobId: String?,
        mode: String
    ): RepositoryStatus

    suspend fun generateResume(
        sourceResumeId: String?,
        targetJobId: String?,
        templateName: String,
        inputProfile: ResumeBuilderInput
    ): QueryResult<GeneratedResumeDocument>

    suspend fun exportResumePdf(
        generatedResumeId: String
    ): QueryResult<String>
}

class SupabaseResumeRepository @Inject constructor(
    private val appConfig: AppConfig,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val storage: Storage,
    private val functions: Functions
) : ResumeRepository {
    private val refreshSignal = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun resumes(): Flow<List<ResumeSummary>> =
        refreshSignal.flatMapLatest { flow { emit(fetchResumes()) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun roastSummary(resumeId: String): Flow<ResumeRoastSummary?> =
        refreshSignal.flatMapLatest { flow { emit(fetchRoastSummary(resumeId)) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun generatedResumes(): Flow<List<GeneratedResumeSummary>> =
        refreshSignal.flatMapLatest { flow { emit(fetchGeneratedResumes()) } }

    override suspend fun uploadResumeAndParse(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        targetJobId: String?
    ): QueryResult<ResumeSummary> {
        if (!appConfig.isSupabaseConfigured) {
            return QueryResult.NotConfigured
        }

        val userId = auth.currentUserOrNull()?.id
            ?: return QueryResult.Failure("You must be signed in to upload a resume.")

        if (fileName.isBlank() || bytes.isEmpty()) {
            return QueryResult.Failure("Select a non-empty PDF resume file.")
        }

        if (!mimeType.contains("pdf", ignoreCase = true)) {
            return QueryResult.Failure("Only PDF resumes are supported.")
        }

        return try {
            val resumeId = kotlin.runCatching { java.util.UUID.randomUUID().toString() }
                .getOrElse { throw IllegalStateException("Unable to generate a resume id.") }
            val storagePath = buildResumeStoragePath(userId, resumeId, fileName)

            postgrest.from(SupabaseTables.RESUMES).insert(
                value = ResumeCreateDto(
                    id = resumeId,
                    userId = userId,
                    fileName = fileName
                )
            )

            storage.from(SupabaseBuckets.RESUME_UPLOADS)
                .upload(storagePath, bytes) {
                    upsert = true
                }

            val signedUrl = storage.from(SupabaseBuckets.RESUME_UPLOADS)
                .createSignedUrl(storagePath, 60 * 60 * 24 * 7)

            postgrest.from(SupabaseTables.RESUMES).upsert(
                ResumeCreateDto(
                    id = resumeId,
                    userId = userId,
                    fileUrl = signedUrl,
                    fileName = fileName
                )
            ) {
                onConflict = "id"
            }

            val parseResult = functions.invoke(
                function = SupabaseFunctions.PARSE_RESUME,
                body = ParseResumeRequestDto(
                    resumeId = resumeId,
                    fileUrl = signedUrl
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<ParseResumeResponseDto>()

            val summary = ResumeSummary(
                id = parseResult.resumeId,
                fileName = fileName,
                latestScore = null,
                createdAt = null
            )

            refreshSignal.update { it + 1 }
            QueryResult.Success(summary)
        } catch (error: Exception) {
            if (error.isMissingResumeBackend()) {
                QueryResult.BackendNotReady
            } else {
                QueryResult.Failure(
                    message = error.message ?: "Unable to upload and parse the resume.",
                    cause = error
                )
            }
        }
    }

    override suspend fun requestResumeParsing(
        resumeId: String,
        fileUrl: String
    ): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        if (resumeId.isBlank() || fileUrl.isBlank()) {
            return RepositoryStatus.Failure("Resume parse requires a resume id and file URL.")
        }

        return try {
            functions.invoke(
                function = SupabaseFunctions.PARSE_RESUME,
                body = ParseResumeRequestDto(
                    resumeId = resumeId,
                    fileUrl = fileUrl
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<ParseResumeResponseDto>()
            RepositoryStatus.Success
        } catch (error: Exception) {
            if (error.isMissingResumeBackend()) {
                RepositoryStatus.BackendNotReady
            } else {
                RepositoryStatus.Failure(
                    message = error.message ?: "Unable to request resume parsing.",
                    cause = error
                )
            }
        }
    }

    override suspend fun roastResume(
        resumeId: String,
        targetJobId: String?,
        mode: String
    ): QueryResult<ResumeRoastDetail> {
        if (!appConfig.isSupabaseConfigured) {
            return QueryResult.NotConfigured
        }

        if (resumeId.isBlank() || mode.isBlank()) {
            return QueryResult.Failure("Resume roast requires a resume id and mode.")
        }

        if (auth.currentUserOrNull()?.id == null) {
            return QueryResult.Failure("You must be signed in to roast a resume.")
        }

        return try {
            val response = functions.invoke(
                function = SupabaseFunctions.ROAST_RESUME,
                body = RoastResumeRequestDto(
                    resumeId = resumeId,
                    targetJobId = targetJobId,
                    mode = mode
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<RoastResumeResponseDto>()

            refreshSignal.update { it + 1 }
            QueryResult.Success(response.toDomainModel())
        } catch (error: Exception) {
            if (error.isMissingResumeBackend()) {
                QueryResult.BackendNotReady
            } else {
                QueryResult.Failure(
                    message = error.message ?: "Unable to roast the resume.",
                    cause = error
                )
            }
        }
    }

    override suspend fun requestResumeRoast(
        resumeId: String,
        targetJobId: String?,
        mode: String
    ): RepositoryStatus {
        return when (val result = roastResume(resumeId, targetJobId, mode)) {
            QueryResult.NotConfigured -> RepositoryStatus.NotConfigured
            QueryResult.BackendNotReady -> RepositoryStatus.BackendNotReady
            is QueryResult.Failure -> RepositoryStatus.Failure(result.message, result.cause)
            is QueryResult.Success -> RepositoryStatus.Success
            QueryResult.Loading -> RepositoryStatus.Success
        }
    }

    override suspend fun generateResume(
        sourceResumeId: String?,
        targetJobId: String?,
        templateName: String,
        inputProfile: ResumeBuilderInput
    ): QueryResult<GeneratedResumeDocument> {
        if (!appConfig.isSupabaseConfigured) {
            return QueryResult.NotConfigured
        }

        if (templateName.isBlank()) {
            return QueryResult.Failure("Template name is required.")
        }

        return try {
            val response = functions.invoke(
                function = SupabaseFunctions.GENERATE_RESUME,
                body = GenerateResumeRequestDto(
                    sourceResumeId = sourceResumeId,
                    targetJobId = targetJobId,
                    templateName = templateName,
                    inputProfile = inputProfile.toDto()
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<GenerateResumeResponseDto>()

            refreshSignal.update { it + 1 }
            QueryResult.Success(
                GeneratedResumeDocument(
                    generatedResumeId = response.generatedResumeId,
                    resumeJson = response.resumeJson.toDomainModel(),
                    templateName = templateName,
                    pdfUrl = null,
                    sourceResumeId = sourceResumeId,
                    targetJobId = targetJobId
                )
            )
        } catch (error: Exception) {
            if (error.isMissingResumeBackend()) {
                QueryResult.BackendNotReady
            } else {
                QueryResult.Failure(
                    message = error.message ?: "Unable to generate the resume.",
                    cause = error
                )
            }
        }
    }

    override suspend fun exportResumePdf(
        generatedResumeId: String
    ): QueryResult<String> {
        if (!appConfig.isSupabaseConfigured) {
            return QueryResult.NotConfigured
        }

        if (generatedResumeId.isBlank()) {
            return QueryResult.Failure("Generated resume id is required.")
        }

        return try {
            val response = functions.invoke(
                function = SupabaseFunctions.EXPORT_RESUME_PDF,
                body = ExportResumePdfRequestDto(
                    generatedResumeId = generatedResumeId
                ),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<ExportResumePdfResponseDto>()

            refreshSignal.update { it + 1 }
            QueryResult.Success(response.pdfUrl)
        } catch (error: Exception) {
            if (error.isMissingResumeBackend()) {
                QueryResult.BackendNotReady
            } else {
                QueryResult.Failure(
                    message = error.message ?: "Unable to export the resume PDF.",
                    cause = error
                )
            }
        }
    }

    private suspend fun fetchResumes(): List<ResumeSummary> {
        if (!appConfig.isSupabaseConfigured) {
            return emptyList()
        }

        return try {
            postgrest.from(SupabaseTables.RESUMES)
                .select()
                .decodeList<ResumeDto>()
                .map(ResumeDto::toDomainModel)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchRoastSummary(
        resumeId: String
    ): ResumeRoastSummary? {
        if (!appConfig.isSupabaseConfigured || resumeId.isBlank()) {
            return null
        }

        return try {
            postgrest.from(SupabaseTables.RESUME_ROASTS)
                .select {
                    filter {
                        eq("resume_id", resumeId)
                    }
                }
                .decodeList<ResumeRoastDto>()
                .firstOrNull()
                ?.toDomainModel()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchGeneratedResumes(): List<GeneratedResumeSummary> {
        if (!appConfig.isSupabaseConfigured) {
            return emptyList()
        }

        return try {
            postgrest.from(SupabaseTables.GENERATED_RESUMES)
                .select()
                .decodeList<GeneratedResumeDto>()
                .map(GeneratedResumeDto::toSummaryModel)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ResumeBuilderInput.toDto(): GeneratedResumeJsonDto {
        return GeneratedResumeJsonDto(
            basics = com.internshipuncle.data.dto.GeneratedResumeBasicsDto(
                name = basics.name,
                email = basics.email,
                phone = basics.phone,
                location = basics.location,
                linkedin = basics.linkedin,
                github = basics.github,
                portfolio = basics.portfolio
            ),
            education = education.map {
                com.internshipuncle.data.dto.GeneratedResumeEducationDto(
                    school = it.school,
                    degree = it.degree,
                    start = it.start,
                    end = it.end,
                    gpa = it.gpa
                )
            },
            skills = skills,
            projects = projects.map {
                com.internshipuncle.data.dto.GeneratedResumeProjectDto(
                    name = it.name,
                    description = it.description,
                    highlights = it.highlights
                )
            },
            experience = experience.map {
                com.internshipuncle.data.dto.GeneratedResumeExperienceDto(
                    company = it.company,
                    role = it.role,
                    start = it.start,
                    end = it.end,
                    bullets = it.bullets
                )
            },
            achievements = achievements
        )
    }

    private fun buildResumeStoragePath(
        userId: String,
        resumeId: String,
        fileName: String
    ): String {
        val safeName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "resume.pdf" }
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")

        return "$userId/$resumeId/$safeName"
    }

    private fun Exception.isMissingResumeBackend(): Boolean {
        val message = message.orEmpty()
        return message.contains("resume", ignoreCase = true) &&
            (
                message.contains("does not exist", ignoreCase = true) ||
                    message.contains("schema cache", ignoreCase = true) ||
                    message.contains("Could not find the table", ignoreCase = true) ||
                    message.contains("PGRST", ignoreCase = true) ||
                    message.contains("bucket", ignoreCase = true)
                )
    }
}
