package com.internshipuncle.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProfileDto(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("college")
    val college: String? = null,
    @SerialName("degree")
    val degree: String? = null,
    @SerialName("graduation_year")
    val graduationYear: Int? = null,
    @SerialName("target_roles")
    val targetRoles: List<String> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class ProfileUpsertDto(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("email")
    val email: String? = null,
    @SerialName("college")
    val college: String,
    @SerialName("degree")
    val degree: String,
    @SerialName("graduation_year")
    val graduationYear: Int,
    @SerialName("target_roles")
    val targetRoles: List<String>
)

@Serializable
data class JobDto(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("company")
    val company: String,
    @SerialName("location")
    val location: String? = null,
    @SerialName("work_mode")
    val workMode: String? = null,
    @SerialName("employment_type")
    val employmentType: String? = null,
    @SerialName("stipend")
    val stipend: String? = null,
    @SerialName("apply_url")
    val applyUrl: String? = null,
    @SerialName("deadline")
    val deadline: String? = null,
    @SerialName("description_raw")
    val descriptionRaw: String? = null,
    @SerialName("description_clean")
    val descriptionClean: String? = null,
    @SerialName("tags")
    val tags: List<String> = emptyList(),
    @SerialName("is_featured")
    val isFeatured: Boolean = false,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_by")
    val createdBy: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class JobAnalysisDto(
    @SerialName("id")
    val id: String,
    @SerialName("job_id")
    val jobId: String,
    @SerialName("summary")
    val summary: String? = null,
    @SerialName("role_reality")
    val roleReality: String? = null,
    @SerialName("required_skills")
    val requiredSkills: List<String> = emptyList(),
    @SerialName("preferred_skills")
    val preferredSkills: List<String> = emptyList(),
    @SerialName("top_keywords")
    val topKeywords: List<String> = emptyList(),
    @SerialName("likely_interview_topics")
    val likelyInterviewTopics: List<String> = emptyList(),
    @SerialName("difficulty")
    val difficulty: String? = null
)

@Serializable
data class ResumeDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("file_url")
    val fileUrl: String? = null,
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("parsed_text")
    val parsedText: String? = null,
    @SerialName("parsed_sections")
    val parsedSections: JsonElement? = null,
    @SerialName("latest_score")
    val latestScore: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class ResumeCreateDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("file_url")
    val fileUrl: String? = null,
    @SerialName("file_name")
    val fileName: String? = null
)

@Serializable
data class ResumeRoastDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("resume_id")
    val resumeId: String,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("overall_score")
    val overallScore: Int? = null,
    @SerialName("ats_score")
    val atsScore: Int? = null,
    @SerialName("relevance_score")
    val relevanceScore: Int? = null,
    @SerialName("clarity_score")
    val clarityScore: Int? = null,
    @SerialName("formatting_score")
    val formattingScore: Int? = null,
    @SerialName("roast_result")
    val roastResult: JsonElement? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class GeneratedResumeDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("source_resume_id")
    val sourceResumeId: String? = null,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("template_name")
    val templateName: String? = null,
    @SerialName("resume_json")
    val resumeJson: JsonElement? = null,
    @SerialName("pdf_url")
    val pdfUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class GeneratedResumeUpsertDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("source_resume_id")
    val sourceResumeId: String? = null,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("template_name")
    val templateName: String? = null,
    @SerialName("resume_json")
    val resumeJson: GeneratedResumeJsonDto,
    @SerialName("pdf_url")
    val pdfUrl: String? = null
)

@Serializable
data class GeneratedResumeBasicsDto(
    @SerialName("name")
    val name: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("phone")
    val phone: String? = null,
    @SerialName("location")
    val location: String? = null,
    @SerialName("linkedin")
    val linkedin: String? = null,
    @SerialName("github")
    val github: String? = null,
    @SerialName("portfolio")
    val portfolio: String? = null
)

@Serializable
data class GeneratedResumeEducationDto(
    @SerialName("school")
    val school: String? = null,
    @SerialName("degree")
    val degree: String? = null,
    @SerialName("start")
    val start: String? = null,
    @SerialName("end")
    val end: String? = null,
    @SerialName("gpa")
    val gpa: String? = null
)

@Serializable
data class GeneratedResumeProjectDto(
    @SerialName("name")
    val name: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("highlights")
    val highlights: List<String> = emptyList()
)

@Serializable
data class GeneratedResumeExperienceDto(
    @SerialName("company")
    val company: String? = null,
    @SerialName("role")
    val role: String? = null,
    @SerialName("start")
    val start: String? = null,
    @SerialName("end")
    val end: String? = null,
    @SerialName("bullets")
    val bullets: List<String> = emptyList()
)

@Serializable
data class GeneratedResumeJsonDto(
    @SerialName("basics")
    val basics: GeneratedResumeBasicsDto = GeneratedResumeBasicsDto(),
    @SerialName("education")
    val education: List<GeneratedResumeEducationDto> = emptyList(),
    @SerialName("skills")
    val skills: List<String> = emptyList(),
    @SerialName("projects")
    val projects: List<GeneratedResumeProjectDto> = emptyList(),
    @SerialName("experience")
    val experience: List<GeneratedResumeExperienceDto> = emptyList(),
    @SerialName("achievements")
    val achievements: List<String> = emptyList()
)

@Serializable
data class ParseResumeRequestDto(
    @SerialName("resume_id")
    val resumeId: String,
    @SerialName("file_url")
    val fileUrl: String
)

@Serializable
data class ParseResumeResponseDto(
    @SerialName("resume_id")
    val resumeId: String,
    @SerialName("parsed_text")
    val parsedText: String,
    @SerialName("parsed_sections")
    val parsedSections: JsonElement
)

@Serializable
data class RoastResumeRequestDto(
    @SerialName("resume_id")
    val resumeId: String,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("mode")
    val mode: String
)

@Serializable
data class RoastResumeResponseDto(
    @SerialName("resume_id")
    val resumeId: String,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("overall_score")
    val overallScore: Int,
    @SerialName("ats_score")
    val atsScore: Int,
    @SerialName("relevance_score")
    val relevanceScore: Int,
    @SerialName("clarity_score")
    val clarityScore: Int,
    @SerialName("formatting_score")
    val formattingScore: Int,
    @SerialName("roast_result")
    val roastResult: JsonElement
)

@Serializable
data class GenerateResumeRequestDto(
    @SerialName("source_resume_id")
    val sourceResumeId: String? = null,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("template_name")
    val templateName: String,
    @SerialName("input_profile")
    val inputProfile: GeneratedResumeJsonDto
)

@Serializable
data class GenerateResumeResponseDto(
    @SerialName("generated_resume_id")
    val generatedResumeId: String,
    @SerialName("resume_json")
    val resumeJson: GeneratedResumeJsonDto
)

@Serializable
data class ExportResumePdfRequestDto(
    @SerialName("generated_resume_id")
    val generatedResumeId: String
)

@Serializable
data class ExportResumePdfResponseDto(
    @SerialName("generated_resume_id")
    val generatedResumeId: String,
    @SerialName("pdf_url")
    val pdfUrl: String
)

@Serializable
data class SavedJobDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("job_id")
    val jobId: String,
    @SerialName("status")
    val status: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class SavedJobUpsertDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("job_id")
    val jobId: String,
    @SerialName("status")
    val status: String = "saved"
)

@Serializable
data class MockSessionDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("role_name")
    val roleName: String? = null,
    @SerialName("difficulty")
    val difficulty: String? = null,
    @SerialName("mode")
    val mode: String? = null,
    @SerialName("overall_score")
    val overallScore: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class MockQuestionDto(
    @SerialName("id")
    val id: String,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("question")
    val question: String,
    @SerialName("category")
    val category: String? = null,
    @SerialName("sequence_no")
    val sequenceNo: Int,
    @SerialName("expected_points")
    val expectedPoints: List<String> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class MockAnswerDto(
    @SerialName("id")
    val id: String,
    @SerialName("question_id")
    val questionId: String,
    @SerialName("answer_text")
    val answerText: String? = null,
    @SerialName("feedback")
    val feedback: JsonElement? = null,
    @SerialName("score")
    val score: Int? = null,
    @SerialName("improved_answer")
    val improvedAnswer: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class GenerateMockSessionRequestDto(
    @SerialName("target_job_id")
    val targetJobId: String? = null,
    @SerialName("role_name")
    val roleName: String,
    @SerialName("difficulty")
    val difficulty: String,
    @SerialName("mode")
    val mode: String,
    @SerialName("include_resume")
    val includeResume: Boolean = true
)

@Serializable
data class GenerateMockSessionQuestionDto(
    @SerialName("question_id")
    val questionId: String,
    @SerialName("question")
    val question: String,
    @SerialName("category")
    val category: String? = null,
    @SerialName("sequence_no")
    val sequenceNo: Int,
    @SerialName("expected_points")
    val expectedPoints: List<String> = emptyList()
)

@Serializable
data class GenerateMockSessionResponseDto(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("questions")
    val questions: List<GenerateMockSessionQuestionDto> = emptyList()
)

@Serializable
data class EvaluateAnswerRequestDto(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("question_id")
    val questionId: String,
    @SerialName("answer_text")
    val answerText: String
)

@Serializable
data class EvaluateAnswerFeedbackDto(
    @SerialName("strengths")
    val strengths: List<String> = emptyList(),
    @SerialName("weaknesses")
    val weaknesses: List<String> = emptyList(),
    @SerialName("missing_points")
    val missingPoints: List<String> = emptyList(),
    @SerialName("follow_up")
    val followUp: String? = null,
    @SerialName("improved_answer")
    val improvedAnswer: String? = null
)

@Serializable
data class EvaluateAnswerResponseDto(
    @SerialName("question_id")
    val questionId: String,
    @SerialName("score")
    val score: Int,
    @SerialName("feedback")
    val feedback: EvaluateAnswerFeedbackDto = EvaluateAnswerFeedbackDto(),
    @SerialName("improved_answer")
    val improvedAnswer: String? = null
)
