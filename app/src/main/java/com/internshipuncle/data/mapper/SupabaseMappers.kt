package com.internshipuncle.data.mapper

import com.internshipuncle.data.dto.JobAnalysisDto
import com.internshipuncle.data.dto.JobDto
import com.internshipuncle.data.dto.MockSessionDto
import com.internshipuncle.data.dto.GeneratedResumeDto
import com.internshipuncle.data.dto.GeneratedResumeExperienceDto
import com.internshipuncle.data.dto.GeneratedResumeJsonDto
import com.internshipuncle.data.dto.GeneratedResumeProjectDto
import com.internshipuncle.data.dto.GeneratedResumeEducationDto
import com.internshipuncle.data.dto.GeneratedResumeBasicsDto
import com.internshipuncle.data.dto.ProfileDto
import com.internshipuncle.data.dto.ProfileUpsertDto
import com.internshipuncle.data.dto.ResumeDto
import com.internshipuncle.data.dto.ResumeRoastDto
import com.internshipuncle.domain.model.InterviewSessionSummary
import com.internshipuncle.domain.model.JobAnalysis
import com.internshipuncle.domain.model.JobCard
import com.internshipuncle.domain.model.JobDetail
import com.internshipuncle.domain.model.GeneratedResumeDocument
import com.internshipuncle.domain.model.GeneratedResumeSummary
import com.internshipuncle.domain.model.OnboardingProfileInput
import com.internshipuncle.domain.model.ResumeBasics
import com.internshipuncle.domain.model.ResumeBuilderInput
import com.internshipuncle.domain.model.ResumeEducationEntry
import com.internshipuncle.domain.model.ResumeExperienceEntry
import com.internshipuncle.domain.model.ResumeProjectEntry
import com.internshipuncle.domain.model.ResumeRoastDetail
import com.internshipuncle.domain.model.ResumeRoastIssue
import com.internshipuncle.domain.model.ResumeRoastResult
import com.internshipuncle.domain.model.ResumeRoastSummary
import com.internshipuncle.domain.model.ResumeSummary
import com.internshipuncle.domain.model.UserProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun ProfileDto.toDomainModel(): UserProfile {
    return UserProfile(
        id = id,
        email = email,
        name = name ?: email?.substringBefore("@").orEmpty(),
        college = college.orEmpty(),
        degree = degree.orEmpty(),
        graduationYear = graduationYear,
        targetRoles = targetRoles.map(String::trim).filter(String::isNotBlank)
    )
}

fun OnboardingProfileInput.toUpsertDto(
    userId: String,
    email: String?
): ProfileUpsertDto {
    return ProfileUpsertDto(
        id = userId,
        name = name.trim(),
        email = email?.trim(),
        college = college.trim(),
        degree = degree.trim(),
        graduationYear = graduationYear,
        targetRoles = targetRoles.map(String::trim).filter(String::isNotBlank)
    )
}

fun JobDto.toDomainModel(): JobCard {
    return JobCard(
        id = id,
        title = title,
        company = company,
        location = location?.takeIf(String::isNotBlank),
        stipend = stipend?.takeIf(String::isNotBlank),
        workMode = workMode?.takeIf(String::isNotBlank),
        deadline = deadline,
        tags = tags.map(String::trim).filter(String::isNotBlank),
        isFeatured = isFeatured
    )
}

fun JobDto.toDetailModel(): JobDetail {
    return JobDetail(
        id = id,
        title = title,
        company = company,
        location = location?.takeIf(String::isNotBlank),
        workMode = workMode?.takeIf(String::isNotBlank),
        employmentType = employmentType?.takeIf(String::isNotBlank),
        stipend = stipend?.takeIf(String::isNotBlank),
        applyUrl = applyUrl?.takeIf(String::isNotBlank),
        deadline = deadline,
        description = descriptionClean?.takeIf(String::isNotBlank)
            ?: descriptionRaw?.takeIf(String::isNotBlank),
        tags = tags.map(String::trim).filter(String::isNotBlank),
        isFeatured = isFeatured
    )
}

fun JobAnalysisDto.toDomainModel(): JobAnalysis {
    return JobAnalysis(
        summary = summary.orEmpty(),
        roleReality = roleReality.orEmpty(),
        requiredSkills = requiredSkills.map(String::trim).filter(String::isNotBlank),
        preferredSkills = preferredSkills.map(String::trim).filter(String::isNotBlank),
        topKeywords = topKeywords.map(String::trim).filter(String::isNotBlank),
        likelyInterviewTopics = likelyInterviewTopics.map(String::trim).filter(String::isNotBlank),
        difficulty = difficulty?.takeIf(String::isNotBlank)
    )
}

fun ResumeDto.toDomainModel(): ResumeSummary {
    return ResumeSummary(
        id = id,
        fileName = fileName,
        latestScore = latestScore,
        createdAt = createdAt
    )
}

fun ResumeRoastDto.toDomainModel(): ResumeRoastSummary {
    return ResumeRoastSummary(
        resumeId = resumeId,
        overallScore = overallScore,
        atsScore = atsScore,
        relevanceScore = relevanceScore,
        clarityScore = clarityScore,
        formattingScore = formattingScore
    )
}

fun ResumeRoastDto.toDetailModel(): ResumeRoastDetail {
    val roastResult = roastResult.toResumeRoastResult()
    return ResumeRoastDetail(
        resumeId = resumeId,
        targetJobId = targetJobId,
        overallScore = overallScore ?: 0,
        atsScore = atsScore ?: 0,
        relevanceScore = relevanceScore ?: 0,
        clarityScore = clarityScore ?: 0,
        formattingScore = formattingScore ?: 0,
        roastResult = roastResult
    )
}

fun GeneratedResumeDto.toDomainModel(): GeneratedResumeDocument {
    return GeneratedResumeDocument(
        generatedResumeId = id,
        resumeJson = resumeJson.toResumeBuilderInput(),
        templateName = templateName.orEmpty(),
        pdfUrl = pdfUrl,
        sourceResumeId = sourceResumeId,
        targetJobId = targetJobId
    )
}

fun GeneratedResumeDto.toSummaryModel(): GeneratedResumeSummary {
    return GeneratedResumeSummary(
        id = id,
        templateName = templateName,
        pdfUrl = pdfUrl,
        createdAt = createdAt
    )
}

private fun GeneratedResumeJsonDto.toResumeBuilderInput(): ResumeBuilderInput {
    return ResumeBuilderInput(
        basics = basics.toDomainModel(),
        education = education.map(GeneratedResumeEducationDto::toDomainModel),
        skills = skills.map(String::trim).filter(String::isNotBlank),
        projects = projects.map(GeneratedResumeProjectDto::toDomainModel),
        experience = experience.map(GeneratedResumeExperienceDto::toDomainModel),
        achievements = achievements.map(String::trim).filter(String::isNotBlank)
    )
}

private fun GeneratedResumeBasicsDto.toDomainModel(): ResumeBasics {
    return ResumeBasics(
        name = name.orEmpty(),
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        location = location.orEmpty(),
        linkedin = linkedin.orEmpty(),
        github = github.orEmpty(),
        portfolio = portfolio.orEmpty()
    )
}

private fun GeneratedResumeEducationDto.toDomainModel(): ResumeEducationEntry {
    return ResumeEducationEntry(
        school = school.orEmpty(),
        degree = degree.orEmpty(),
        start = start.orEmpty(),
        end = end.orEmpty(),
        gpa = gpa.orEmpty()
    )
}

private fun GeneratedResumeProjectDto.toDomainModel(): ResumeProjectEntry {
    return ResumeProjectEntry(
        name = name.orEmpty(),
        description = description.orEmpty(),
        highlights = highlights.map(String::trim).filter(String::isNotBlank)
    )
}

private fun GeneratedResumeExperienceDto.toDomainModel(): ResumeExperienceEntry {
    return ResumeExperienceEntry(
        company = company.orEmpty(),
        role = role.orEmpty(),
        start = start.orEmpty(),
        end = end.orEmpty(),
        bullets = bullets.map(String::trim).filter(String::isNotBlank)
    )
}

private fun JsonElement?.toResumeRoastResult(): ResumeRoastResult {
    val objectValue = this as? JsonObject
    return ResumeRoastResult(
        issues = objectValue?.get("issues").toResumeIssues(),
        missingKeywords = objectValue?.get("missing_keywords").toStringList(),
        weakBullets = objectValue?.get("weak_bullets").toStringList(),
        rewrittenBullets = objectValue?.get("rewritten_bullets").toStringList(),
        comments = objectValue?.get("comments").toStringList()
    )
}

private fun JsonElement?.toResumeIssues(): List<ResumeRoastIssue> {
    val items = this as? JsonArray ?: return emptyList()
    return items.mapNotNull { item ->
        val objectValue = item as? JsonObject ?: return@mapNotNull null
        ResumeRoastIssue(
            section = objectValue["section"].jsonPrimitiveOrNull(),
            severity = objectValue["severity"].jsonPrimitiveOrNull(),
            message = objectValue["message"].jsonPrimitiveOrNull()
        )
    }
}

private fun JsonElement?.toStringList(): List<String> {
    val items = this as? JsonArray ?: return emptyList()
    return items.mapNotNull { it.jsonPrimitiveOrNull()?.trim() }.filter(String::isNotBlank)
}

private fun JsonElement?.jsonPrimitiveOrNull(): String? {
    val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return primitive.contentOrNull?.trim()
}

fun MockSessionDto.toDomainModel(): InterviewSessionSummary {
    return InterviewSessionSummary(
        id = id,
        roleName = roleName,
        difficulty = difficulty,
        mode = mode,
        overallScore = overallScore,
        createdAt = createdAt
    )
}
