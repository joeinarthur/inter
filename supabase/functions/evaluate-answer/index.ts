import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import { ApiError, badRequest, errorResponse, jsonResponse, toErrorResponse, unauthorized } from "../_shared/http.ts";
import { generateStructuredObject, type GeminiSchema, GeminiError } from "../_shared/gemini.ts";
import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import { isRecord, normalizeStringList, parseJsonBody, requireString, requireUuid, truncateText } from "../_shared/validation.ts";

type EvaluateAnswerResponse = {
  question_id: string;
  score: number;
  feedback: {
    strengths: string[];
    weaknesses: string[];
    missing_points: string[];
    follow_up: string;
  };
  improved_answer: string;
};

const feedbackSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["strengths", "weaknesses", "missing_points", "follow_up"],
  propertyOrdering: ["strengths", "weaknesses", "missing_points", "follow_up"],
  properties: {
    strengths: { type: "ARRAY", items: { type: "STRING" } },
    weaknesses: { type: "ARRAY", items: { type: "STRING" } },
    missing_points: { type: "ARRAY", items: { type: "STRING" } },
    follow_up: { type: "STRING" },
  },
};

const evaluateAnswerSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["score", "feedback", "improved_answer"],
  propertyOrdering: ["score", "feedback", "improved_answer"],
  properties: {
    score: { type: "INTEGER", minimum: 0, maximum: 10 },
    feedback: feedbackSchema,
    improved_answer: { type: "STRING" },
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("evaluate-answer only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const sessionId = requireUuid(body.session_id, "session_id");
    const questionId = requireUuid(body.question_id, "question_id");
    const answerText = requireString(body.answer_text, "answer_text", {
      minLength: 5,
      maxLength: 20_000,
    });

    const supabase = getSupabaseAdminClient();
    const session = await loadOwnedSession(supabase, user.id, sessionId);
    const question = await loadSessionQuestion(supabase, sessionId, questionId);

    const prompt = buildEvaluationPrompt({
      session,
      question,
      answerText,
    });

    const evaluated = await generateStructuredObject<Record<string, unknown>>(
      prompt,
      evaluateAnswerSchema,
      {
        temperature: 0.25,
        maxOutputTokens: 1024,
        retries: 1,
      },
    );

    const normalized = normalizeEvaluation(questionId, evaluated);
    const existingAnswer = await loadExistingAnswer(supabase, questionId);

    if (existingAnswer) {
      const { error: updateError } = await supabase
        .from("mock_answers")
        .update({
          answer_text: answerText,
          feedback: normalized.feedback,
          score: normalized.score,
          improved_answer: normalized.improved_answer,
        })
        .eq("id", existingAnswer.id);

      if (updateError) {
        throw new ApiError(500, "database_upsert_failed", "Failed to update mock answer.", {
          cause: updateError.message,
        });
      }
    } else {
      const { error: insertError } = await supabase
        .from("mock_answers")
        .insert({
          question_id: questionId,
          answer_text: answerText,
          feedback: normalized.feedback,
          score: normalized.score,
          improved_answer: normalized.improved_answer,
        });

      if (insertError) {
        throw new ApiError(500, "database_upsert_failed", "Failed to store mock answer.", {
          cause: insertError.message,
        });
      }
    }

    const response: EvaluateAnswerResponse = normalized;
    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

async function loadOwnedSession(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  userId: string,
  sessionId: string,
): Promise<Record<string, unknown>> {
  const { data, error } = await supabase
    .from("mock_sessions")
    .select("id,user_id,target_job_id,role_name,difficulty,mode,overall_score,created_at")
    .eq("id", sessionId)
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load mock session.", {
      cause: error.message,
    });
  }

  const session = data?.[0];
  if (!session) {
    throw new ApiError(404, "not_found", "The requested mock session was not found.", {
      session_id: sessionId,
    });
  }

  if (session.user_id !== userId) {
    throw new ApiError(403, "forbidden", "You do not have access to this mock session.");
  }

  return session;
}

async function loadSessionQuestion(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  sessionId: string,
  questionId: string,
): Promise<Record<string, unknown>> {
  const { data, error } = await supabase
    .from("mock_questions")
    .select("id,session_id,question,category,sequence_no,expected_points")
    .eq("id", questionId)
    .eq("session_id", sessionId)
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load mock question.", {
      cause: error.message,
    });
  }

  const question = data?.[0];
  if (!question) {
    throw new ApiError(404, "not_found", "The requested mock question was not found.", {
      question_id: questionId,
    });
  }

  return question;
}

async function loadExistingAnswer(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  questionId: string,
): Promise<Record<string, unknown> | null> {
  const { data, error } = await supabase
    .from("mock_answers")
    .select("id,question_id,answer_text,feedback,score,improved_answer,created_at")
    .eq("question_id", questionId)
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load existing mock answer.", {
      cause: error.message,
    });
  }

  return data?.[0] ?? null;
}

function buildEvaluationPrompt(input: {
  session: Record<string, unknown>;
  question: Record<string, unknown>;
  answerText: string;
}): string {
  return [
    "You are Internship Uncle. Evaluate the interview answer with concise, useful feedback.",
    "Return only JSON that matches the schema.",
    "",
    `Session:\n${JSON.stringify(input.session, null, 2)}`,
    "",
    `Question:\n${JSON.stringify(input.question, null, 2)}`,
    "",
    "Candidate answer:",
    truncateText(input.answerText, 16_000),
    "",
    "Score on a 0-10 scale. Give strengths, weaknesses, missing points, a follow-up question, and an improved answer.",
  ].join("\n");
}

function normalizeEvaluation(
  questionId: string,
  raw: Record<string, unknown>,
): EvaluateAnswerResponse {
  const feedbackValue = raw.feedback;
  if (!isRecord(feedbackValue)) {
    badRequest("feedback must be an object.");
  }

  const feedback = {
    strengths: normalizeStringList(feedbackValue.strengths, "feedback.strengths", {
      minItems: 1,
      maxItems: 6,
      maxItemLength: 140,
    }),
    weaknesses: normalizeStringList(feedbackValue.weaknesses, "feedback.weaknesses", {
      minItems: 1,
      maxItems: 6,
      maxItemLength: 140,
    }),
    missing_points: normalizeStringList(feedbackValue.missing_points, "feedback.missing_points", {
      minItems: 1,
      maxItems: 6,
      maxItemLength: 140,
    }),
    follow_up: requireString(feedbackValue.follow_up, "feedback.follow_up", {
      minLength: 5,
      maxLength: 280,
    }),
  };

  return {
    question_id: questionId,
    score: clampScore(raw.score, 7),
    feedback,
    improved_answer: requireString(raw.improved_answer, "improved_answer", {
      minLength: 10,
      maxLength: 20_000,
    }),
  };
}

function clampScore(value: unknown, fallback: number): number {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return fallback;
  }

  return Math.max(0, Math.min(10, Math.round(value)));
}
