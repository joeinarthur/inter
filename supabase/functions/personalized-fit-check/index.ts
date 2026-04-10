import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import { ApiError, badRequest, errorResponse, jsonResponse, toErrorResponse, unauthorized } from "../_shared/http.ts";
import { generateStructuredObject, type GeminiSchema, GeminiError } from "../_shared/gemini.ts";
import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import { isRecord, normalizeStringList, parseJsonBody, requireString, requireUuid } from "../_shared/validation.ts";

type PersonalizedFitCheckResponse = {
  fit_score: number;
  classification: "safe" | "match" | "reach";
  missing_skills: string[];
  projects_to_highlight: string[];
  prep_plan: string[];
};

const fitCheckSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["fit_score", "classification", "missing_skills", "projects_to_highlight", "prep_plan"],
  propertyOrdering: ["fit_score", "classification", "missing_skills", "projects_to_highlight", "prep_plan"],
  properties: {
    fit_score: { type: "INTEGER", minimum: 0, maximum: 100 },
    classification: { type: "STRING", enum: ["safe", "match", "reach"] },
    missing_skills: { type: "ARRAY", items: { type: "STRING" } },
    projects_to_highlight: { type: "ARRAY", items: { type: "STRING" } },
    prep_plan: { type: "ARRAY", items: { type: "STRING" } },
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("personalized-fit-check only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const userId = requireUuid(body.user_id, "user_id");
    const jobId = requireUuid(body.job_id, "job_id");
    const resumeId = parseOptionalUuid(body.resume_id, "resume_id");

    if (user.id !== userId) {
      throw new ApiError(403, "forbidden", "You can only request a fit check for your own profile.");
    }

    const supabase = getSupabaseAdminClient();
    const [profile, jobContext, resumeContext] = await Promise.all([
      loadProfileContext(supabase, userId),
      loadJobContext(supabase, jobId),
      resumeId ? loadResumeContext(supabase, userId, resumeId) : Promise.resolve(null),
    ]);

    const prompt = buildFitCheckPrompt({ profile, jobContext, resumeContext });
    const evaluated = await generateStructuredObject<Record<string, unknown>>(
      prompt,
      fitCheckSchema,
      {
        temperature: 0.25,
        maxOutputTokens: 1024,
        retries: 1,
      },
    );

    const response = normalizeFitCheck(evaluated);
    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

async function loadProfileContext(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  userId: string,
): Promise<Record<string, unknown>> {
  const { data, error } = await supabase
    .from("profiles")
    .select("id,name,email,college,degree,graduation_year,target_roles,created_at,updated_at")
    .eq("id", userId)
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load profile context.", {
      cause: error.message,
    });
  }

  const profile = data?.[0];
  if (!profile) {
    throw new ApiError(404, "not_found", "The requested profile was not found.", {
      user_id: userId,
    });
  }

  return profile;
}

async function loadJobContext(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  jobId: string,
): Promise<{
  job: Record<string, unknown>;
  analysis: Record<string, unknown> | null;
}> {
  const { data: jobRows, error: jobError } = await supabase
    .from("jobs")
    .select("id,title,company,location,work_mode,employment_type,stipend,apply_url,deadline,description_raw,description_clean,tags,is_featured,is_active")
    .eq("id", jobId)
    .limit(1);

  if (jobError) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load job context.", {
      cause: jobError.message,
    });
  }

  const job = jobRows?.[0];
  if (!job) {
    throw new ApiError(404, "not_found", "The requested job was not found.", {
      job_id: jobId,
    });
  }

  const { data: analysisRows, error: analysisError } = await supabase
    .from("job_analysis")
    .select("job_id,summary,role_reality,required_skills,preferred_skills,top_keywords,likely_interview_topics,difficulty")
    .eq("job_id", jobId)
    .limit(1);

  if (analysisError) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load job analysis.", {
      cause: analysisError.message,
    });
  }

  return {
    job,
    analysis: analysisRows?.[0] ?? null,
  };
}

async function loadResumeContext(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  userId: string,
  resumeId: string,
): Promise<Record<string, unknown> | null> {
  const { data, error } = await supabase
    .from("resumes")
    .select("id,user_id,file_name,parsed_text,parsed_sections,latest_score,created_at")
    .eq("id", resumeId)
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load resume context.", {
      cause: error.message,
    });
  }

  const resume = data?.[0];
  if (!resume) {
    throw new ApiError(404, "not_found", "The requested resume was not found.", {
      resume_id: resumeId,
    });
  }

  if (resume.user_id !== userId) {
    throw new ApiError(403, "forbidden", "You do not have access to this resume.");
  }

  return resume;
}

function buildFitCheckPrompt(input: {
  profile: Record<string, unknown>;
  jobContext: {
    job: Record<string, unknown>;
    analysis: Record<string, unknown> | null;
  };
  resumeContext: Record<string, unknown> | null;
}): string {
  return [
    "You are Internship Uncle. Estimate how well the candidate fits the target internship.",
    "Return only JSON that matches the schema.",
    "Be practical and honest. Suggest concrete prep actions.",
    "",
    `Profile:\n${JSON.stringify(input.profile, null, 2)}`,
    "",
    `Job:\n${JSON.stringify(input.jobContext.job, null, 2)}`,
    "",
    input.jobContext.analysis
      ? `Job analysis:\n${JSON.stringify(input.jobContext.analysis, null, 2)}`
      : "No job analysis was provided.",
    "",
    input.resumeContext
      ? `Resume context:\n${JSON.stringify(input.resumeContext, null, 2)}`
      : "No resume was provided.",
    "",
    "Return a fit_score from 0 to 100 and classify it as safe, match, or reach.",
  ].join("\n");
}

function normalizeFitCheck(raw: Record<string, unknown>): PersonalizedFitCheckResponse {
  if (!isRecord(raw)) {
    badRequest("Fit check response must be an object.");
  }

  const classification = normalizeClassification(raw.classification);

  return {
    fit_score: clampScore(raw.fit_score),
    classification,
    missing_skills: normalizeStringList(raw.missing_skills, "missing_skills", {
      maxItems: 20,
      maxItemLength: 100,
    }),
    projects_to_highlight: normalizeStringList(raw.projects_to_highlight, "projects_to_highlight", {
      maxItems: 10,
      maxItemLength: 140,
    }),
    prep_plan: normalizeStringList(raw.prep_plan, "prep_plan", {
      maxItems: 10,
      maxItemLength: 180,
    }),
  };
}

function normalizeClassification(value: unknown): "safe" | "match" | "reach" {
  if (typeof value !== "string") {
    return "match";
  }

  const normalized = value.trim().toLowerCase();
  if (normalized === "safe" || normalized === "match" || normalized === "reach") {
    return normalized;
  }

  return "match";
}

function clampScore(value: unknown): number {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return 70;
  }

  return Math.max(0, Math.min(100, Math.round(value)));
}

function parseOptionalUuid(value: unknown, fieldName: string): string | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  return requireUuid(value, fieldName);
}
