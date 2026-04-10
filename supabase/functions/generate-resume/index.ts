import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import {
  ApiError,
  badRequest,
  errorResponse,
  jsonResponse,
  toErrorResponse,
  unauthorized,
} from "../_shared/http.ts";
import { generateStructuredObject, type GeminiSchema, GeminiError } from "../_shared/gemini.ts";
import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import {
  isRecord,
  normalizeStringList,
  parseJsonBody,
  requireString,
  requireUuid,
  truncateText,
} from "../_shared/validation.ts";

type GenerateResumeResponse = {
  generated_resume_id: string;
  resume_json: GeneratedResumeJson;
};

type GeneratedResumeJson = {
  basics: ResumeBasics;
  education: ResumeEducationEntry[];
  skills: string[];
  projects: ResumeProjectEntry[];
  experience: ResumeExperienceEntry[];
  achievements: string[];
};

type ResumeBasics = {
  name?: string | null;
  email?: string | null;
  phone?: string | null;
  location?: string | null;
  linkedin?: string | null;
  github?: string | null;
  portfolio?: string | null;
};

type ResumeEducationEntry = {
  school?: string | null;
  degree?: string | null;
  start?: string | null;
  end?: string | null;
  gpa?: string | null;
};

type ResumeProjectEntry = {
  name?: string | null;
  description?: string | null;
  highlights?: string[];
};

type ResumeExperienceEntry = {
  company?: string | null;
  role?: string | null;
  start?: string | null;
  end?: string | null;
  bullets?: string[];
};

const resumeJsonSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["basics", "education", "skills", "projects", "experience", "achievements"],
  propertyOrdering: ["basics", "education", "skills", "projects", "experience", "achievements"],
  properties: {
    basics: {
      type: "OBJECT",
      properties: {
        name: { type: "STRING", nullable: true },
        email: { type: "STRING", nullable: true },
        phone: { type: "STRING", nullable: true },
        location: { type: "STRING", nullable: true },
        linkedin: { type: "STRING", nullable: true },
        github: { type: "STRING", nullable: true },
        portfolio: { type: "STRING", nullable: true },
      },
    },
    education: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          school: { type: "STRING", nullable: true },
          degree: { type: "STRING", nullable: true },
          start: { type: "STRING", nullable: true },
          end: { type: "STRING", nullable: true },
          gpa: { type: "STRING", nullable: true },
        },
      },
    },
    skills: { type: "ARRAY", items: { type: "STRING" } },
    projects: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          name: { type: "STRING", nullable: true },
          description: { type: "STRING", nullable: true },
          highlights: { type: "ARRAY", items: { type: "STRING" } },
        },
      },
    },
    experience: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          company: { type: "STRING", nullable: true },
          role: { type: "STRING", nullable: true },
          start: { type: "STRING", nullable: true },
          end: { type: "STRING", nullable: true },
          bullets: { type: "ARRAY", items: { type: "STRING" } },
        },
      },
    },
    achievements: { type: "ARRAY", items: { type: "STRING" } },
  },
};

const generateResumeSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["resume_json"],
  propertyOrdering: ["resume_json"],
  properties: {
    resume_json: resumeJsonSchema,
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("generate-resume only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const sourceResumeId = parseOptionalUuid(body.source_resume_id, "source_resume_id");
    const targetJobId = parseOptionalUuid(body.target_job_id, "target_job_id");
    const templateName = requireString(body.template_name, "template_name", {
      minLength: 2,
      maxLength: 80,
    });

    const inputProfile = parseInputProfile(body.input_profile);

    const supabase = getSupabaseAdminClient();
    const [sourceResume, targetJobContext] = await Promise.all([
      loadSourceResumeContext(supabase, user.id, sourceResumeId),
      loadTargetJobContext(supabase, targetJobId),
    ]);

    const prompt = buildGenerateResumePrompt({
      templateName,
      inputProfile,
      sourceResume,
      targetJobContext,
    });

    const generated = await generateStructuredObject<Record<string, unknown>>(
      prompt,
      generateResumeSchema,
      {
        temperature: 0.35,
        maxOutputTokens: 2048,
        retries: 1,
      },
    );

    const normalizedResumeJson = normalizeGeneratedResumeJson(generated.resume_json);
    const generatedResumeId = crypto.randomUUID();

    const { error: insertError } = await supabase
      .from("generated_resumes")
      .insert({
        id: generatedResumeId,
        user_id: user.id,
        source_resume_id: sourceResumeId,
        target_job_id: targetJobId,
        template_name: templateName,
        resume_json: normalizedResumeJson,
      });

    if (insertError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to store generated resume.", {
        cause: insertError.message,
      });
    }

    const response: GenerateResumeResponse = {
      generated_resume_id: generatedResumeId,
      resume_json: normalizedResumeJson,
    };

    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

function buildGenerateResumePrompt(input: {
  templateName: string;
  inputProfile: Record<string, unknown>;
  sourceResume: Record<string, unknown> | null;
  targetJobContext: {
    targetJob: Record<string, unknown> | null;
    targetAnalysis: Record<string, unknown> | null;
  };
}): string {
  return [
    "You are Internship Uncle. Generate a resume JSON optimized for a target internship or role.",
    "Return only JSON that matches the schema.",
    "Keep it concise, ATS-friendly, and grounded in the provided input profile.",
    "",
    `Template name: ${input.templateName}`,
    "",
    "Input profile:",
    JSON.stringify(input.inputProfile, null, 2),
    "",
    input.sourceResume
      ? `Source resume context:\n${JSON.stringify(input.sourceResume, null, 2)}`
      : "No source resume was provided.",
    "",
    input.targetJobContext.targetJob
      ? `Target job:\n${JSON.stringify(input.targetJobContext.targetJob, null, 2)}`
      : "No target job was provided.",
    "",
    input.targetJobContext.targetAnalysis
      ? `Target job analysis:\n${JSON.stringify(input.targetJobContext.targetAnalysis, null, 2)}`
      : "No target job analysis was provided.",
    "",
    "The resume_json field must contain basics, education, skills, projects, experience, and achievements.",
  ].join("\n");
}

async function loadSourceResumeContext(
  supabase: ReturnType<typeof getSupabaseAdminClient>,
  userId: string,
  sourceResumeId: string | null,
): Promise<Record<string, unknown> | null> {
  if (!sourceResumeId) {
    return null;
  }

  const { data: resumeRows, error } = await supabase
    .from("resumes")
    .select("id,user_id,parsed_text,parsed_sections,file_name")
    .eq("id", sourceResumeId)
    .limit(1);

  if (error) {
    throw new ApiError(500, "database_lookup_failed", "Failed to load source resume.", {
      cause: error.message,
    });
  }

  const resume = resumeRows?.[0];
  if (!resume) {
    throw new ApiError(404, "not_found", "The requested source resume was not found.", {
      resume_id: sourceResumeId,
    });
  }

  if (resume.user_id !== userId) {
    throw new ApiError(403, "forbidden", "You do not have access to the source resume.");
  }

  return resume;
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

function parseInputProfile(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) {
    badRequest("input_profile must be a JSON object.");
  }

  return value;
}

function normalizeGeneratedResumeJson(value: unknown): GeneratedResumeJson {
  if (!isRecord(value)) {
    badRequest("resume_json must be an object.");
  }

  return {
    basics: normalizeBasics(value.basics),
    education: normalizeEducationEntries(value.education),
    skills: normalizeStringList(value.skills, "resume_json.skills", {
      maxItems: 40,
      maxItemLength: 80,
    }),
    projects: normalizeProjectEntries(value.projects),
    experience: normalizeExperienceEntries(value.experience),
    achievements: normalizeStringList(value.achievements, "resume_json.achievements", {
      maxItems: 20,
      maxItemLength: 140,
    }),
  };
}

function normalizeBasics(value: unknown): ResumeBasics {
  if (!isRecord(value)) {
    return {};
  }

  return {
    name: normalizeOptionalString(value.name, 120),
    email: normalizeOptionalString(value.email, 180),
    phone: normalizeOptionalString(value.phone, 60),
    location: normalizeOptionalString(value.location, 120),
    linkedin: normalizeOptionalString(value.linkedin, 200),
    github: normalizeOptionalString(value.github, 200),
    portfolio: normalizeOptionalString(value.portfolio, 200),
  };
}

function normalizeEducationEntries(value: unknown): ResumeEducationEntry[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .filter(isRecord)
    .map((entry) => ({
      school: normalizeOptionalString(entry.school, 160),
      degree: normalizeOptionalString(entry.degree, 160),
      start: normalizeOptionalString(entry.start, 40),
      end: normalizeOptionalString(entry.end, 40),
      gpa: normalizeOptionalString(entry.gpa, 40),
    }));
}

function normalizeProjectEntries(value: unknown): ResumeProjectEntry[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .filter(isRecord)
    .map((entry) => ({
      name: normalizeOptionalString(entry.name, 160),
      description: normalizeOptionalString(entry.description, 500),
      highlights: normalizeStringList(entry.highlights, "resume_json.projects.highlights", {
        maxItems: 8,
        maxItemLength: 120,
      }),
    }));
}

function normalizeExperienceEntries(value: unknown): ResumeExperienceEntry[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .filter(isRecord)
    .map((entry) => ({
      company: normalizeOptionalString(entry.company, 160),
      role: normalizeOptionalString(entry.role, 160),
      start: normalizeOptionalString(entry.start, 40),
      end: normalizeOptionalString(entry.end, 40),
      bullets: normalizeStringList(entry.bullets, "resume_json.experience.bullets", {
        maxItems: 8,
        maxItemLength: 160,
      }),
    }));
}

function normalizeOptionalString(value: unknown, maxLength: number): string | null {
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
