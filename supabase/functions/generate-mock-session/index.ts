import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import { ApiError, badRequest, errorResponse, jsonResponse, toErrorResponse, unauthorized } from "../_shared/http.ts";
import { generateStructuredObject, type GeminiSchema, GeminiError } from "../_shared/gemini.ts";
import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import {
  isRecord,
  normalizeStringList,
  parseJsonBody,
  requireBoolean,
  requireString,
  requireUuid,
  truncateText,
} from "../_shared/validation.ts";

type GenerateMockSessionResponse = {
  session_id: string;
  questions: MockQuestionResponse[];
};

type MockQuestionResponse = {
  question_id: string;
  question: string;
  category: string | null;
  sequence_no: number;
  expected_points: string[];
};

const questionsSchema: GeminiSchema = {
  type: "ARRAY",
  minItems: 3,
  maxItems: 10,
  items: {
    type: "OBJECT",
    required: ["question", "category", "sequence_no", "expected_points"],
    propertyOrdering: ["question", "category", "sequence_no", "expected_points"],
    properties: {
      question: { type: "STRING" },
      category: { type: "STRING", nullable: true },
      sequence_no: { type: "INTEGER", minimum: 1, maximum: 20 },
      expected_points: {
        type: "ARRAY",
        items: { type: "STRING" },
      },
    },
  },
};

const generateMockSessionSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["questions"],
  propertyOrdering: ["questions"],
  properties: {
    questions: questionsSchema,
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("generate-mock-session only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const targetJobId = parseOptionalUuid(body.target_job_id, "target_job_id");
    const roleName = requireString(body.role_name, "role_name", {
      minLength: 2,
      maxLength: 120,
    });
    const difficulty = requireDifficulty(body.difficulty);
    const mode = requireMode(body.mode);
    const includeResume = requireBoolean(body.include_resume, "include_resume");

    const supabase = getSupabaseAdminClient();
    const [targetJobContext, resumeContext] = await Promise.all([
      loadTargetJobContext(supabase, targetJobId),
      includeResume ? loadLatestResumeContext(supabase, user.id) : Promise.resolve(null),
    ]);

    const sessionId = crypto.randomUUID();
    const generatedQuestions = await generateStructuredObject<Record<string, unknown>>(
      buildSessionPrompt({
        roleName,
        difficulty,
        mode,
        includeResume,
        targetJobContext,
        resumeContext,
      }),
      generateMockSessionSchema,
      {
        temperature: 0.4,
        maxOutputTokens: 1536,
        retries: 1,
      },
    );

    const questions = normalizeQuestions(generatedQuestions.questions);

    const { error: sessionError } = await supabase
      .from("mock_sessions")
      .insert({
        id: sessionId,
        user_id: user.id,
        target_job_id: targetJobId,
        role_name: roleName,
        difficulty,
        mode,
      });

    if (sessionError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to create mock interview session.", {
        cause: sessionError.message,
      });
    }

    const questionRows = questions.map((question) => ({
      id: crypto.randomUUID(),
      session_id: sessionId,
      question: question.question,
      category: question.category,
      sequence_no: question.sequence_no,
      expected_points: question.expected_points,
    }));

    const { error: questionsError } = await supabase
      .from("mock_questions")
      .insert(questionRows);

    if (questionsError) {
      await supabase.from("mock_sessions").delete().eq("id", sessionId).eq("user_id", user.id);
      throw new ApiError(500, "database_upsert_failed", "Failed to save mock interview questions.", {
        cause: questionsError.message,
      });
    }

    const response: GenerateMockSessionResponse = {
      session_id: sessionId,
      questions: questionRows.map((question) => ({
        question_id: question.id,
        question: question.question,
        category: question.category,
        sequence_no: question.sequence_no,
        expected_points: question.expected_points,
      })),
    };

    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

function buildSessionPrompt(input: {
  roleName: string;
  difficulty: "easy" | "medium" | "hard";
  mode: "quick" | "full" | "pressure" | "resume_crossfire";
  includeResume: boolean;
  targetJobContext: {
    targetJob: Record<string, unknown> | null;
    targetAnalysis: Record<string, unknown> | null;
  };
  resumeContext: Record<string, unknown> | null;
}): string {
  return [
    "You are Internship Uncle. Generate a role-specific mock interview question set.",
    "Return only JSON that matches the schema.",
    "Focus on realistic internship interview prompts and include expected answer points.",
    "",
    `Role name: ${input.roleName}`,
    `Difficulty: ${input.difficulty}`,
    `Mode: ${input.mode}`,
    `Include resume context: ${input.includeResume}`,
    "",
    input.targetJobContext.targetJob
      ? `Target job:\n${JSON.stringify(input.targetJobContext.targetJob, null, 2)}`
      : "No target job was provided.",
    "",
    input.targetJobContext.targetAnalysis
      ? `Target analysis:\n${JSON.stringify(input.targetJobContext.targetAnalysis, null, 2)}`
      : "No job analysis was provided.",
    "",
    input.resumeContext
      ? `Resume context:\n${JSON.stringify(input.resumeContext, null, 2)}`
      : "No resume context was provided.",
    "",
    "Generate 3 to 7 questions depending on mode.",
    "Sequence numbers must start at 1 and increase by 1.",
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
    throw new ApiError(500, "database_lookup_failed", "Failed to load target job analysis.", {
      cause: analysisError.message,
    });
  }

  return {
    targetJob,
    targetAnalysis: analysisRows?.[0] ?? null,
  };
}

async function loadLatestResumeContext(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  userId: string,
): Promise<Record<string, unknown> | null> {
  const { data, error } = await supabase
    .from("resumes")
    .select("id,file_name,parsed_text,parsed_sections,latest_score,created_at")
    .eq("user_id", userId)
    .order("created_at", { ascending: false })
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load resume context.", {
      cause: error.message,
    });
  }

  return data?.[0] ?? null;
}

function normalizeQuestions(value: unknown): Array<{
  question: string;
  category: string | null;
  sequence_no: number;
  expected_points: string[];
}> {
  if (!Array.isArray(value)) {
    badRequest("questions must be an array.");
  }

  return value
    .filter(isRecord)
    .map((question, index) => ({
      question: requireString(question.question, "questions.question", {
        minLength: 5,
        maxLength: 280,
      }),
      category: normalizeText(question.category, 80),
      sequence_no: normalizeSequenceNo(question.sequence_no, index),
      expected_points: normalizeStringList(question.expected_points, "questions.expected_points", {
        minItems: 2,
        maxItems: 6,
        maxItemLength: 120,
      }),
    }))
    .slice(0, 10);
}

function normalizeSequenceNo(value: unknown, index: number): number {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return index + 1;
  }

  return Math.max(1, Math.min(20, Math.round(value)));
}

function normalizeText(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const trimmed = value.trim();
  return trimmed ? truncateText(trimmed, maxLength) : null;
}

function parseOptionalUuid(value: unknown, fieldName: string): string | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  return requireUuid(value, fieldName);
}

function requireDifficulty(value: unknown): "easy" | "medium" | "hard" {
  const difficulty = requireString(value, "difficulty");
  if (difficulty === "easy" || difficulty === "medium" || difficulty === "hard") {
    return difficulty;
  }

  throw new ApiError(400, "invalid_input", "difficulty must be easy, medium, or hard.");
}

function requireMode(value: unknown): "quick" | "full" | "pressure" | "resume_crossfire" {
  const mode = requireString(value, "mode");
  if (mode === "quick" || mode === "full" || mode === "pressure" || mode === "resume_crossfire") {
    return mode;
  }

  throw new ApiError(400, "invalid_input", "mode must be quick, full, pressure, or resume_crossfire.");
}
