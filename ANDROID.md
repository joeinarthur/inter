# Android App Plan — Internship Uncle

## 1. Frontend Stack

Recommended stack:
- Kotlin
- Jetpack Compose
- MVVM
- Hilt
- Coroutines + Flow
- Room
- Retrofit or Ktor client
- Supabase integration for auth/storage/data
  

## 2. Android Architecture Goals

The app should be:
- modular enough to grow
- simple enough to ship fast
- easy to generate with Codex in parts
- screen-state driven
- backend-led for AI logic

## 3. Package Structure

```text
com.internshipuncle
├── core
│   ├── network
│   ├── design
│   ├── auth
│   ├── storage
│   ├── util
│   └── model
├── data
│   ├── remote
│   ├── local
│   ├── repository
│   ├── mapper
│   └── dto
├── domain
│   ├── model
│   └── usecase
├── feature_auth
├── feature_jobs
├── feature_analyze
├── feature_resume
├── feature_interview
├── feature_dashboard
└── navigation
```

## 4. Feature Modules

## 4.1 `feature_auth`
Screens:
- splash
- login
- signup
- onboarding

State:
- current user session
- loading
- auth errors
- onboarding completion

## 4.2 `feature_jobs`
Screens:
- jobs home
- filters
- job detail
- saved jobs

State:
- jobs list
- selected filters
- selected job
- save status

## 4.3 `feature_analyze`
Screens:
- JD reality check
- fit analysis
- skill gap
- prep roadmap

State:
- job analysis
- fit score
- skill gaps
- prep recommendations

## 4.4 `feature_resume`
Screens:
- upload resume
- roast results
- builder form
- preview/export
- generated versions

State:
- uploaded file
- parsed sections
- roast scores
- keyword gaps
- generated resume content

## 4.5 `feature_interview`
Screens:
- setup
- question flow
- answer feedback
- session summary
- session history

State:
- session info
- question index
- answer drafts
- feedback list
- final summary

## 4.6 `feature_dashboard`
Screens:
- dashboard home
- recent activity
- readiness details

State:
- readiness score
- recent scores
- deadlines
- activity feed

## 5. Navigation Graph

Suggested main navigation tabs:
- Discover
- Analyze
- Resume Lab
- Interview Prep
- Dashboard

Suggested routes:
```text
splash
login
signup
onboarding
jobs
job/{jobId}
saved_jobs
analysis/{jobId}
resume_upload
resume_roast/{resumeId}
resume_builder
resume_preview/{generatedResumeId}
mock_setup
mock_session/{sessionId}
mock_feedback/{sessionId}
dashboard
```

## 6. Main UI Flows

## 6.1 Onboarding
Fields:
- name
- college
- degree
- graduation year
- target roles

Actions:
- skip optional fields
- finish onboarding
- store profile

## 6.2 Jobs List
UI components:
- search bar
- filter chips
- featured section
- latest section
- internship cards

Card fields:
- title
- company
- location
- tags
- stipend
- deadline

## 6.3 Job Detail
Sections:
- overview
- role reality
- required skills
- keywords
- interview topics
- save button
- apply button

CTA buttons:
- roast my resume
- build resume for this role
- practice interview

## 6.4 Resume Roast Screen
Score cards:
- overall
- ATS
- relevance
- clarity
- formatting

Sections:
- comments
- missing keywords
- weak bullets
- rewritten bullets

Actions:
- copy rewritten bullet
- regenerate
- tailor for selected job
- build improved resume

## 6.5 Resume Builder
Inputs:
- basics
- education
- skills
- projects
- experience
- achievements

Useful UX:
- add/edit/delete entries
- AI rewrite for a single bullet
- AI target this role
- preview button

## 6.6 Mock Interview Session
One-question layout:
- question header
- category badge
- answer box
- submit
- skip
- next

Optional extras later:
- timer
- confidence slider
- audio mode

## 6.7 Dashboard
Cards:
- readiness score
- saved jobs count
- latest roast score
- latest mock score
- next deadlines

## 7. Recommended Data Models

## 7.1 UI Models
```kotlin
data class JobUiModel(
    val id: String,
    val title: String,
    val company: String,
    val location: String?,
    val stipend: String?,
    val tags: List<String>,
    val deadlineLabel: String?
)
```

```kotlin
data class JobAnalysisUiModel(
    val summary: String,
    val roleReality: String,
    val requiredSkills: List<String>,
    val topKeywords: List<String>,
    val interviewTopics: List<String>,
    val difficulty: String?
)
```

```kotlin
data class RoastScores(
    val overall: Int,
    val ats: Int,
    val relevance: Int,
    val clarity: Int,
    val formatting: Int
)
```

## 8. Example UI State Classes

```kotlin
data class JobsUiState(
    val isLoading: Boolean = false,
    val jobs: List<JobUiModel> = emptyList(),
    val error: String? = null
)
```

```kotlin
data class JobDetailUiState(
    val isLoading: Boolean = false,
    val job: JobUiModel? = null,
    val analysis: JobAnalysisUiModel? = null,
    val isSaved: Boolean = false,
    val error: String? = null
)
```

```kotlin
data class ResumeRoastUiState(
    val isLoading: Boolean = false,
    val scores: RoastScores? = null,
    val comments: List<String> = emptyList(),
    val missingKeywords: List<String> = emptyList(),
    val weakBullets: List<String> = emptyList(),
    val rewrittenBullets: List<String> = emptyList(),
    val error: String? = null
)
```

## 9. Repository Interfaces

```kotlin
interface JobsRepository {
    suspend fun getJobs(): List<JobUiModel>
    suspend fun getJobDetail(jobId: String): JobUiModel
    suspend fun getJobAnalysis(jobId: String): JobAnalysisUiModel
    suspend fun saveJob(jobId: String)
    suspend fun unsaveJob(jobId: String)
    suspend fun getSavedJobs(): List<JobUiModel>
}
```

```kotlin
interface ResumeRepository {
    suspend fun uploadResume(filePath: String): String
    suspend fun parseResume(resumeId: String)
    suspend fun roastResume(resumeId: String, targetJobId: String?): ResumeRoastUiState
    suspend fun generateResume(sourceResumeId: String?, targetJobId: String?): String
    suspend fun exportResumePdf(generatedResumeId: String): String
}
```

```kotlin
interface InterviewRepository {
    suspend fun createSession(roleName: String, targetJobId: String?): String
    suspend fun getSession(sessionId: String): MockSessionUiModel
    suspend fun submitAnswer(sessionId: String, questionId: String, answer: String): AnswerFeedbackUiModel
}
```

## 10. ViewModel List

- `AuthViewModel`
- `OnboardingViewModel`
- `JobsViewModel`
- `JobDetailViewModel`
- `SavedJobsViewModel`
- `ResumeUploadViewModel`
- `ResumeRoastViewModel`
- `ResumeBuilderViewModel`
- `MockSetupViewModel`
- `MockInterviewViewModel`
- `DashboardViewModel`

## 11. Design System Suggestions

Brand tone:
- clean
- bold
- slightly playful
- practical

Suggested UI style:
- simple cards
- strong score displays
- clear call-to-action buttons
- chips for keywords and tags
- avoid cluttered dashboards

### Useful reusable components
- JobCard
- ScoreCard
- KeywordChip
- SectionCard
- FeedbackBullet
- TimelineCard
- EmptyState
- RetryState

## 12. Offline and Local Caching

Use Room for:
- jobs cache
- saved jobs snapshot
- recent dashboard summary

Do not try to fully offline-enable all AI features in MVP.

## 13. File Handling Notes

Resume upload flow:
1. choose file using system picker
2. upload to storage
3. create resume row
4. call parse function
5. show progress UI
6. allow retry on parse failure

Supported first:
- PDF
- DOCX later if needed

## 14. Performance Guidelines

- paginate jobs list if needed
- use skeleton loading states
- avoid recomposition-heavy giant screens
- split large forms into steps if needed
- cache job details locally after fetch

## 15. Build Order for Android App

1. auth scaffolding
2. app navigation
3. jobs list and detail
4. saved jobs
5. resume upload flow
6. roast screen
7. resume builder
8. mock interview flow
9. dashboard

## 16. Practical Rule for Codex

Ask Codex to build:
- one feature module at a time
- one screen at a time
- one repository at a time

Do not ask it to build the whole app at once.
