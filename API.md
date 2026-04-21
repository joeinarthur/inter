# API Design — Internship Uncle

## 1. API Philosophy

The backend should provide stable, structured contracts for all important AI workflows.
 
Rules:
- the Android app should not call AI providers directly
- every AI-heavy feature should go through a secure backend function
- all major AI responses should be structured JSON
- functions should be idempotent where possible
- save outputs in the database after successful generation

## 2. API Categories

### Read APIs
Used to fetch:
- jobs
- job details
- saved jobs
- roast history
- mock session history
- dashboard summaries

### Action APIs / Edge Functions
Used to perform:
- job analysis
- resume parsing
- resume roast
- resume generation
- PDF export
- mock session generation
- answer evaluation
- fit analysis

## 3. Auth Model

All APIs require authenticated user context except possibly public job reads if you decide that later.

### Validate on every action
- user is authenticated
- user owns referenced resume or session
- admin-only actions enforce admin role

## 4. Core Edge Functions

## 4.1 `analyze-job`

### Purpose
Analyze a raw job description and create structured job insights.

### Trigger
- admin uploads new job
- admin edits job description
- optional re-run if analysis logic changes

### Request
```json
{
  "job_id": "uuid",
  "description_raw": "string"
}
```

### Response
```json
{
  "job_id": "uuid",
  "summary": "string",
  "role_reality": "string",
  "required_skills": ["string"],
  "preferred_skills": ["string"],
  "top_keywords": ["string"],
  "likely_interview_topics": ["string"],
  "difficulty": "easy | medium | hard"
}
```

### Side effects
- upsert row in `job_analysis`

---

## 4.2 `parse-resume`

### Purpose
Extract structured information from a resume file.

### Request
```json
{
  "resume_id": "uuid",
  "file_url": "string"
}
```

### Response
```json
{
  "resume_id": "uuid",
  "parsed_text": "string",
  "parsed_sections": {
    "basics": {},
    "education": [],
    "skills": [],
    "projects": [],
    "experience": []
  }
}
```

### Side effects
- update `resumes.parsed_text`
- update `resumes.parsed_sections`

---

## 4.3 `roast-resume`

### Purpose
Review a resume and return structured scoring and feedback.

### Request
```json
{
  "resume_id": "uuid",
  "target_job_id": "uuid | null",
  "mode": "savage | recruiter"
}
```

### Response
```json
{
  "resume_id": "uuid",
  "target_job_id": "uuid | null",
  "overall_score": 74,
  "ats_score": 82,
  "relevance_score": 64,
  "clarity_score": 69,
  "formatting_score": 80,
  "roast_result": {
    "issues": [],
    "missing_keywords": [],
    "weak_bullets": [],
    "rewritten_bullets": [],
    "comments": []
  }
}
```

### Side effects
- insert into `resume_roasts`
- update `resumes.latest_score`

---

## 4.4 `generate-resume`

### Purpose
Generate a structured, role-aware resume JSON.

### Request
```json
{
  "source_resume_id": "uuid | null",
  "target_job_id": "uuid | null",
  "template_name": "ats_classic",
  "input_profile": {
    "basics": {},
    "education": [],
    "skills": [],
    "projects": [],
    "experience": []
  }
}
```

### Response
```json
{
  "generated_resume_id": "uuid",
  "resume_json": {
    "basics": {},
    "education": [],
    "skills": [],
    "projects": [],
    "experience": [],
    "achievements": []
  }
}
```

### Side effects
- insert or update row in `generated_resumes`

---

## 4.5 `export-resume-pdf`

### Purpose
Render a generated resume as PDF and store it.

### Request
```json
{
  "generated_resume_id": "uuid"
}
```

### Response
```json
{
  "generated_resume_id": "uuid",
  "pdf_url": "string"
}
```

### Side effects
- upload file to `generated-resumes` bucket
- update `generated_resumes.pdf_url`

---

## 4.6 `generate-mock-session`

### Purpose
Create a role-specific interview session and question list.

### Request
```json
{
  "target_job_id": "uuid | null",
  "role_name": "Android Intern",
  "difficulty": "easy | medium | hard",
  "mode": "quick | full | pressure | resume_crossfire",
  "include_resume": true
}
```

### Response
```json
{
  "session_id": "uuid",
  "questions": [
    {
      "question_id": "uuid",
      "question": "Explain a project where you used Kotlin coroutines.",
      "category": "technical",
      "sequence_no": 1,
      "expected_points": [
        "problem",
        "implementation",
        "tradeoff",
        "result"
      ]
    }
  ]
}
```

### Side effects
- create `mock_sessions`
- create `mock_questions`

---

## 4.7 `evaluate-answer`

### Purpose
Score and review one interview answer.

### Request
```json
{
  "session_id": "uuid",
  "question_id": "uuid",
  "answer_text": "string"
}
```

### Response
```json
{
  "question_id": "uuid",
  "score": 7,
  "feedback": {
    "strengths": ["Clear structure"],
    "weaknesses": ["Missing specific example"],
    "missing_points": ["Metric", "challenge"],
    "follow_up": "What was the measurable result?"
  },
  "improved_answer": "string"
}
```

### Side effects
- insert or update `mock_answers`

---

## 4.8 `personalized-fit-check`

### Purpose
Estimate fit for a user against a selected job.

### Request
```json
{
  "user_id": "uuid",
  "job_id": "uuid",
  "resume_id": "uuid | null"
}
```

### Response
```json
{
  "fit_score": 78,
  "classification": "safe | match | reach",
  "missing_skills": ["REST APIs"],
  "projects_to_highlight": ["Campus Connect"],
  "prep_plan": [
    "Revise Android lifecycle",
    "Add metrics to project bullets",
    "Practice 5 behavioral questions"
  ]
}
```

## 5. Suggested Non-AI REST/Query Endpoints

These may be direct DB queries through Supabase client or wrapped endpoints.

### Jobs
- `GET /jobs`
- `GET /jobs/:id`
- `GET /jobs/:id/analysis`

### Saved Jobs
- `GET /saved-jobs`
- `POST /saved-jobs`
- `DELETE /saved-jobs/:jobId`

### Resumes
- `GET /resumes`
- `GET /resumes/:id`

### Roast History
- `GET /resume-roasts`
- `GET /resume-roasts/:id`

### Mock Interviews
- `GET /mock-sessions`
- `GET /mock-sessions/:id`

### Dashboard
- `GET /dashboard-summary`

## 6. Dashboard Summary Contract

### Response
```json
{
  "readiness_score": 71,
  "saved_jobs_count": 8,
  "latest_resume_score": 74,
  "latest_mock_score": 68,
  "upcoming_deadlines": [
    {
      "job_id": "uuid",
      "title": "Android Intern",
      "company": "Acme",
      "deadline": "2026-04-18T00:00:00Z"
    }
  ],
  "recent_activity": [
    {
      "type": "resume_roast",
      "created_at": "2026-04-10T10:00:00Z"
    }
  ]
}
```

## 7. Error Response Format

Use a consistent response shape:

```json
{
  "error": {
    "code": "string",
    "message": "string",
    "details": {}
  }
}
```

### Common error codes
- `unauthorized`
- `forbidden`
- `not_found`
- `invalid_input`
- `file_processing_failed`
- `ai_generation_failed`
- `storage_upload_failed`
- `rate_limited`

## 8. Idempotency and Retry Strategy

### Retry-safe operations
- `analyze-job` for same `job_id`
- `parse-resume` for same `resume_id`
- `export-resume-pdf` for same `generated_resume_id`

### Caution operations
- `generate-mock-session` should not create duplicate sessions on accidental retries unless explicitly desired
- `roast-resume` may create history records intentionally, but consider an optional dedupe key later

## 9. Validation Rules

### Server-side validation
- referenced IDs must exist
- user must own resume/session
- required fields must be present
- file type must be allowed
- text sizes should be capped
- AI output should be schema-validated before saving

## 10. Prompt and Output Strategy

Each AI function should:
1. load DB context
2. create compact normalized input
3. call model with output schema
4. validate response
5. store response
6. return stable contract

Do not expose prompt details directly to client.

## 11. Versioning

If AI outputs evolve, add:
- `schema_version`
- `rubric_version`
- optional `prompt_version`

This is useful later if you want to compare old and new roast logic.

## 12. Security Notes

- Never expose model keys to mobile client
- Verify ownership on every private action
- Restrict admin actions by role
- Keep storage buckets private unless there is a strong reason not to

## 13. API Design Principle Summary

- stable contracts
- structured JSON
- secure server-side orchestration
- save outputs for reuse
- role-aware, not generic
