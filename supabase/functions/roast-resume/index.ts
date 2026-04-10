import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import { ApiError, badRequest, errorResponse, jsonResponse, toErrorResponse, unauthorized } from "../_shared/http.ts";
import { generateStructuredObject, type GeminiSchema, GeminiError } from "../_shared/gemini.ts";
import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import {
  isRecord,
  normalizeStringList,
  parseJsonBody,
  requireString,
  requireUuid,
} from "../_shared/validation.ts";

type RoastResumeResponse = {
  resume_id: string;
  target_job_id: string | null;
  overall_score: number;
  ats_score: number;
  relevance_score: number;
  clarity_score: number;
  formatting_score: number;
  roast_result: RoastResult;
};

type RoastResult = {
  issues: RoastIssue[];
  missing_keywords: string[];
  weak_bullets: string[];
  rewritten_bullets: string[];
  comments: string[];
};

type RoastIssue = {
  section?: string | null;
  severity?: "low" | "medium" | "high" | null;
  message?: string | null;
};

const roastSchema: GeminiSchema = {
  type: "OBJECT",
  required: [
    "overall_score",
    "ats_score",
    "relevance_score",
    "clarity_score",
    "formatting_score",
    "roast_result",
  ],
  propertyOrdering: [
    "overall_score",
    "ats_score",
    "relevance_score",
    "clarity_score",
    "formatting_score",
    "roast_result",
  ],
  properties: {
    overall_score: { type: "INTEGER", minimum: 0, maximum: 100 },
    ats_score: { type: "INTEGER", minimum: 0, maximum: 100 },
    relevance_score: { type: "INTEGER", minimum: 0, maximum: 100 },
    clarity_score: { type: "INTEGER", minimum: 0, maximum: 100 },
    formatting_score: { type: "INTEGER", minimum: 0, maximum: 100 },
    roast_result: {
      type: "OBJECT",
      required: ["issues", "missing_keywords", "weak_bullets", "rewritten_bullets", "comments"],
      propertyOrdering: ["issues", "missing_keywords", "weak_bullets", "rewritten_bullets", "comments"],
      properties: {
        issues: {
          type: "ARRAY",
          items: {
            type: "OBJECT",
            properties: {
              section: { type: "STRING", nullable: true },
              severity: { type: "STRING", enum: ["low", "medium", "high"], nullable: true },
              message: { type: "STRING", nullable: true },
            },
          },
        },
        missing_keywords: { type: "ARRAY", items: { type: "STRING" } },
        weak_bullets: { type: "ARRAY", items: { type: "STRING" } },
        rewritten_bullets: { type: "ARRAY", items: { type: "STRING" } },
        comments: { type: "ARRAY", items: { type: "STRING" } },
      },
    },
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("roast-resume only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const resumeId = requireUuid(body.resume_id, "resume_id");
    const targetJobId = parseOptionalUuid(body.target_job_id);
    const mode = requireMode(body.mode);

    const supabase = getSupabaseAdminClient();
    const { data: resumeRows, error: resumeError } = await supabase
      .from("resumes")
      .select("id,user_id,parsed_text,parsed_sections,file_name,file_url,latest_score")
      .eq("id", resumeId)
      .limit(1);

    if (resumeError) {
      throw new ApiError(500, "database_lookup_failed", "Failed to load resume record.", {
        cause: resumeError.message,
      });
    }

    const resume = resumeRows?.[0];
    if (!resume) {
      throw new ApiError(404, "not_found", "The requested resume was not found.", {
        resume_id: resumeId,
      });
    }

    if (resume.user_id !== user.id) {
      throw new ApiError(403, "forbidden", "You do not have access to this resume.");
    }

    const parsedText = typeof resume.parsed_text === "string" ? resume.parsed_text.trim() : "";
    if (!parsedText) {
      throw new ApiError(400, "invalid_input", "Resume must be parsed before it can be roasted.", {
        resume_id: resumeId,
      });
    }

    const { targetJob, targetAnalysis } = await loadTargetJobContext(supabase, targetJobId);

    const prompt = buildRoastPrompt({
      mode,
      resumeText: parsedText,
      resumeSections: resume.parsed_sections,
      fileName: typeof resume.file_name === "string" ? resume.file_name : null,
      targetJob,
      targetAnalysis,
    });

    const geminiResult = await generateStructuredObject<Record<string, unknown>>(
      prompt,
      roastSchema,
      {
        temperature: 0.25,
        maxOutputTokens: 1536,
        retries: 1,
      },
    );

    const normalized = normalizeRoastResponse(resumeId, targetJobId, geminiResult);

    const { error: insertError } = await supabase
      .from("resume_roasts")
      .insert({
        user_id: user.id,
        resume_id: resumeId,
        target_job_id: targetJobId,
        overall_score: normalized.overall_score,
        ats_score: normalized.ats_score,
        relevance_score: normalized.relevance_score,
        clarity_score: normalized.clarity_score,
        formatting_score: normalized.formatting_score,
        roast_result: normalized.roast_result,
      });

    if (insertError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to store resume roast.", {
        cause: insertError.message,
      });
    }

    const { error: updateError } = await supabase
      .from("resumes")
      .update({
        latest_score: normalized.overall_score,
      })
      .eq("id", resumeId)
      .eq("user_id", user.id);

    if (updateError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to update resume score.", {
        cause: updateError.message,
      });
    }

    const response: RoastResumeResponse = normalized;
    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

function buildRoastPrompt(input: {
  mode: "savage" | "recruiter";
  resumeText: string;
  resumeSections: unknown;
  fileName: string | null;
  targetJob: Record<string, unknown> | null;
  targetAnalysis: Record<string, unknown> | null;
}): string {
  return [
    "You are Internship Uncle, a precise resume reviewer.",
    input.mode === "savage"
      ? "Be blunt, direct, and brutally honest while still being useful."
      : "Be recruiter-like: direct, professional, and practical.",
    "Return only JSON that matches the schema.",
    "",
    `File name: ${input.fileName ?? "unknown"}`,
    "",
    "Resume text:",
    input.resumeText,
    "",
    "Structured sections from parsing:",
    JSON.stringify(input.resumeSections ?? {}, null, 2),
    "",
    input.targetJob
      ? `Target job:\n${JSON.stringify(input.targetJob, null, 2)}`
      : "No target job was provided.",
    "",
    input.targetAnalysis
      ? `Job analysis:\n${JSON.stringify(input.targetAnalysis, null, 2)}`
      : "No job analysis was provided.",
    "",
    "Score the resume on ATS relevance, role fit, clarity, and formatting.",
    "Focus the roast_result on concrete issues, missing keywords, weak bullets, rewritten bullets, and comments.",
  ].join("\n");
}

async function loadTargetJobContext(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  targetJobId: string | null,
): Promise<{
  targetJob: Record<string, unknown> | null;
  targetAnalysis: Record<string, unknown> | null;
}> {
  if (!targetJobId) {
    return { targetJob: null, targetAnalysis: null };
  }

  const { data: jobRows, error: jobError } = await supabase
    .from("jobs")
    .select("id,title,company,location,work_mode,employment_type,stipend,apply_url,deadline,description_raw,description_clean,tags,is_featured,is_active")
    .eq("id", targetJobId)
    .limit(1);

  if (jobError) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load target job.", {
      cause: jobError.message,
    });
  }

  const targetJob = jobRows?.[0] ?? null;
  if (!targetJob) {
    throw new ApiError(404, "not_found", "The requested target job was not found.", {
      job_id: targetJobId,
    });
  }

  const { data: analysisRows, error: analysisError } = await supabase
    .from("job_analysis")
    .select("job_id,summary,role_reality,required_skills,preferred_skills,top_keywords,likely_interview_topics,difficulty")
    .eq("job_id", targetJobId)
    .limit(1);

  if (analysisError) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load job analysis for the target job.", {
      cause: analysisError.message,
    });
  }

  return {
    targetJob,
    targetAnalysis: analysisRows?.[0] ?? null,
  };
}

function normalizeRoastResponse(
  resumeId: string,
  targetJobId: string | null,
  raw: Record<string, unknown>,
): RoastResumeResponse {
  const roastResultValue = raw.roast_result;
  if (!isRecord(roastResultValue)) {
    badRequest("roast_result must be an object.");
  }

  const roastResult: RoastResult = {
    issues: normalizeIssues(roastResultValue.issues),
    missing_keywords: normalizeStringList(roastResultValue.missing_keywords, "roast_result.missing_keywords", {
      maxItems: 20,
      maxItemLength: 80,
    }),
    weak_bullets: normalizeStringList(roastResultValue.weak_bullets, "roast_result.weak_bullets", {
      maxItems: 20,
      maxItemLength: 200,
    }),
    rewritten_bullets: normalizeStringList(roastResultValue.rewritten_bullets, "roast_result.rewritten_bullets", {
      maxItems: 20,
      maxItemLength: 240,
    }),
    comments: normalizeStringList(roastResultValue.comments, "roast_result.comments", {
      maxItems: 20,
      maxItemLength: 220,
    }),
  };

  return {
    resume_id: resumeId,
    target_job_id: targetJobId,
    overall_score: clampScore(raw.overall_score, 70),
    ats_score: clampScore(raw.ats_score, 70),
    relevance_score: clampScore(raw.relevance_score, 70),
    clarity_score: clampScore(raw.clarity_score, 70),
    formatting_score: clampScore(raw.formatting_score, 70),
    roast_result: roastResult,
  };
}

function normalizeIssues(value: unknown): RoastIssue[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .filter(isRecord)
    .map((issue) => ({
      section: normalizeText(issue.section, 80),
      severity: normalizeSeverity(issue.severity),
      message: normalizeText(issue.message, 240),
    }))
    .filter((issue) => Boolean(issue.section || issue.message));
}

function normalizeSeverity(value: unknown): RoastIssue["severity"] {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim().toLowerCase();
  if (normalized === "low" || normalized === "medium" || normalized === "high") {
    return normalized;
  }

  return null;
}

function normalizeText(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, maxLength) : null;
}

function clampScore(value: unknown, fallback: number): number {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return fallback;
  }

  return Math.max(0, Math.min(100, Math.round(value)));
}

function parseOptionalUuid(value: unknown): string | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  return requireUuid(value, "target_job_id");
}

function requireMode(value: unknown): "savage" | "recruiter" {
  const mode = requireString(value, "mode");
  if (mode === "savage" || mode === "recruiter") {
    return mode;
  }

  throw new ApiError(400, "invalid_input", "mode must be either savage or recruiter.");
}
