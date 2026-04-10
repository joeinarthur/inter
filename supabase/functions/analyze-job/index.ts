import "../_shared/runtime.ts";

import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import {
  badRequest,
  ApiError,
  errorResponse,
  jsonResponse,
  notFound,
  toErrorResponse,
  unauthorized,
} from "../_shared/http.ts";
import {
  generateStructuredObject,
  type GeminiSchema,
  GeminiError,
} from "../_shared/gemini.ts";
import {
  normalizeDifficulty,
  normalizeStringList,
  optionalString,
  parseJsonBody,
  requireUuid,
  requireString,
  truncateText,
} from "../_shared/validation.ts";
import { requireAuth } from "../_shared/auth.ts";

type AnalyzeJobResponse = {
  job_id: string;
  summary: string;
  role_reality: string;
  required_skills: string[];
  preferred_skills: string[];
  top_keywords: string[];
  likely_interview_topics: string[];
  difficulty: "easy" | "medium" | "hard";
};

const analysisSchema: GeminiSchema = {
  type: "OBJECT",
  required: [
    "summary",
    "role_reality",
    "required_skills",
    "preferred_skills",
    "top_keywords",
    "likely_interview_topics",
    "difficulty",
  ],
  propertyOrdering: [
    "summary",
    "role_reality",
    "required_skills",
    "preferred_skills",
    "top_keywords",
    "likely_interview_topics",
    "difficulty",
  ],
  properties: {
    summary: { type: "STRING" },
    role_reality: { type: "STRING" },
    required_skills: {
      type: "ARRAY",
      items: { type: "STRING" },
    },
    preferred_skills: {
      type: "ARRAY",
      items: { type: "STRING" },
    },
    top_keywords: {
      type: "ARRAY",
      items: { type: "STRING" },
    },
    likely_interview_topics: {
      type: "ARRAY",
      items: { type: "STRING" },
    },
    difficulty: {
      type: "STRING",
      enum: ["easy", "medium", "hard"],
    },
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("analyze-job only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const jobId = requireUuid(body.job_id, "job_id");
    const descriptionRaw = requireString(body.description_raw, "description_raw", {
      minLength: 30,
      maxLength: 20_000,
    });

    const supabase = getSupabaseAdminClient();
    const { data: jobRows, error: jobError } = await supabase
      .from("jobs")
      .select("id,title,company,location,work_mode,employment_type,stipend,apply_url,deadline,description_raw,description_clean,tags,is_featured,is_active")
      .eq("id", jobId)
      .limit(1);

    if (jobError) {
      throw new ApiError(500, "database_lookup_failed", "Failed to load job record.", {
        cause: jobError.message,
      });
    }

    const job = jobRows?.[0];
    if (!job) {
      notFound("The requested job was not found.", { job_id: jobId });
    }

    const analysisPrompt = buildAnalysisPrompt({
      title: String(job.title ?? ""),
      company: String(job.company ?? ""),
      location: optionalString(job.location, "location") ?? "Unknown",
      workMode: optionalString(job.work_mode, "work_mode") ?? "Unknown",
      employmentType: optionalString(job.employment_type, "employment_type") ?? "Unknown",
      stipend: optionalString(job.stipend, "stipend") ?? "Unknown",
      tags: Array.isArray(job.tags) ? job.tags.join(", ") : "",
      descriptionRaw,
    });

    const geminiResult = await generateStructuredObject<Record<string, unknown>>(
      analysisPrompt,
      analysisSchema,
      {
        temperature: 0.2,
        maxOutputTokens: 1024,
        retries: 1,
      },
    );

    const normalized = normalizeAnalysis(jobId, geminiResult, descriptionRaw);

    const { error: upsertError } = await supabase
      .from("job_analysis")
      .upsert(
        {
          job_id: jobId,
          summary: normalized.summary,
          role_reality: normalized.role_reality,
          required_skills: normalized.required_skills,
          preferred_skills: normalized.preferred_skills,
          top_keywords: normalized.top_keywords,
          likely_interview_topics: normalized.likely_interview_topics,
          difficulty: normalized.difficulty,
        },
        { onConflict: "job_id" },
      );

    if (upsertError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to save job analysis.", {
        cause: upsertError.message,
      });
    }

    const response: AnalyzeJobResponse = {
      job_id: jobId,
      summary: normalized.summary,
      role_reality: normalized.role_reality,
      required_skills: normalized.required_skills,
      preferred_skills: normalized.preferred_skills,
      top_keywords: normalized.top_keywords,
      likely_interview_topics: normalized.likely_interview_topics,
      difficulty: normalized.difficulty,
    };

    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

function buildAnalysisPrompt(input: {
  title: string;
  company: string;
  location: string;
  workMode: string;
  employmentType: string;
  stipend: string;
  tags: string;
  descriptionRaw: string;
}): string {
  return [
    "You are Internship Uncle, a brutally honest internship prep copilot.",
    "Analyze the job description below and return only valid JSON that matches the requested schema.",
    "Keep the writing specific, practical, and student-focused.",
    "Avoid fluff, avoid generic advice, and avoid mentioning that you are an AI.",
    "",
    "Job metadata:",
    `- Title: ${input.title}`,
    `- Company: ${input.company}`,
    `- Location: ${input.location}`,
    `- Work mode: ${input.workMode}`,
    `- Employment type: ${input.employmentType}`,
    `- Stipend: ${input.stipend}`,
    `- Tags: ${input.tags}`,
    "",
    "Raw job description:",
    truncateText(input.descriptionRaw, 12_000),
    "",
    "Output requirements:",
    "- summary: 2 to 4 sentences, concise and role-aware.",
    "- role_reality: explain what this internship actually means day to day.",
    "- required_skills: 4 to 8 concrete skills.",
    "- preferred_skills: 3 to 6 helpful extras.",
    "- top_keywords: 6 to 10 ATS / JD keywords.",
    "- likely_interview_topics: 4 to 8 topics a candidate should prepare for.",
    "- difficulty: one of easy, medium, hard.",
  ].join("\n");
}

function normalizeAnalysis(
  jobId: string,
  raw: Record<string, unknown>,
  descriptionRaw: string,
): AnalyzeJobResponse {
  const summary = requireString(raw.summary ?? fallbackSummary(descriptionRaw), "summary", {
    minLength: 20,
    maxLength: 1200,
  });
  const roleReality = requireString(raw.role_reality ?? fallbackRoleReality(descriptionRaw), "role_reality", {
    minLength: 20,
    maxLength: 1600,
  });
  const requiredSkills = normalizeStringList(raw.required_skills, "required_skills", {
    minItems: 3,
    maxItems: 12,
    maxItemLength: 80,
  });
  const preferredSkills = normalizeStringList(raw.preferred_skills ?? [], "preferred_skills", {
    maxItems: 12,
    maxItemLength: 80,
  });
  const topKeywords = normalizeStringList(raw.top_keywords ?? [], "top_keywords", {
    minItems: 3,
    maxItems: 12,
    maxItemLength: 60,
  });
  const likelyInterviewTopics = normalizeStringList(raw.likely_interview_topics ?? [], "likely_interview_topics", {
    minItems: 3,
    maxItems: 12,
    maxItemLength: 100,
  });
  const difficulty = normalizeDifficulty(raw.difficulty, "medium") as AnalyzeJobResponse["difficulty"];

  return {
    job_id: jobId,
    summary,
    role_reality: roleReality,
    required_skills: requiredSkills,
    preferred_skills: preferredSkills,
    top_keywords: topKeywords,
    likely_interview_topics: likelyInterviewTopics,
    difficulty,
  };
}

function fallbackSummary(descriptionRaw: string): string {
  return truncateText(
    descriptionRaw.replace(/\s+/g, " ").slice(0, 320),
    320,
  );
}

function fallbackRoleReality(descriptionRaw: string): string {
  return truncateText(
    `This role likely expects a candidate who can handle the work described in the posting without needing the basics explained step by step. ${descriptionRaw.replace(/\s+/g, " ").slice(0, 260)}`,
    420,
  );
}
