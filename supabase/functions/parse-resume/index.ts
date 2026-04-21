import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import { downloadFile, bytesToBase64 } from "../_shared/files.ts";
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

type ParsedResumeResponse = {
  resume_id: string;
  parsed_text: string;
  parsed_sections: ParsedSections;
};

type ParsedSections = {
  basics: ResumeBasics;
  education: ResumeEducationEntry[];
  skills: string[];
  projects: ResumeProjectEntry[];
  experience: ResumeExperienceEntry[];
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

const parsedSectionsSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["basics", "education", "skills", "projects", "experience"],
  propertyOrdering: ["basics", "education", "skills", "projects", "experience"],
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
    skills: {
      type: "ARRAY",
      items: { type: "STRING" },
    },
    projects: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          name: { type: "STRING", nullable: true },
          description: { type: "STRING", nullable: true },
          highlights: {
            type: "ARRAY",
            items: { type: "STRING" },
          },
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
          bullets: {
            type: "ARRAY",
            items: { type: "STRING" },
          },
        },
      },
    },
  },
};

const resumeParseSchema: GeminiSchema = {
  type: "OBJECT",
  required: ["parsed_text", "parsed_sections"],
  propertyOrdering: ["parsed_text", "parsed_sections"],
  properties: {
    parsed_text: { type: "STRING" },
    parsed_sections: parsedSectionsSchema,
  },
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("parse-resume only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const resumeId = requireUuid(body.resume_id, "resume_id");
    const fileUrl = requireString(body.file_url, "file_url", {
      minLength: 8,
      maxLength: 2048,
    });

    try {
      new URL(fileUrl);
    } catch {
      badRequest("file_url must be a valid URL.");
    }

    const supabase = getSupabaseAdminClient();
    const { data: resumeRows, error: resumeError } = await supabase
      .from("resumes")
      .select("id,user_id,file_url,file_name,parsed_text,parsed_sections")
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

    const downloaded = await downloadFile(fileUrl, {
      fileName: typeof resume.file_name === "string" ? resume.file_name : undefined,
      maxBytes: 10 * 1024 * 1024,
    });

    const prompt = buildParsePrompt({
      resumeId,
      fileName: typeof resume.file_name === "string" ? resume.file_name : null,
      mimeType: downloaded.mimeType,
      size: downloaded.size,
    });

    const parsed = await generateStructuredObject<Record<string, unknown>>(
      prompt,
      resumeParseSchema,
      {
        temperature: 0.1,
        maxOutputTokens: 2048,
        retries: 1,
        parts: [
          {
            inlineData: {
              mimeType: downloaded.mimeType,
              data: bytesToBase64(downloaded.bytes),
            },
          },
        ],
      },
    );

    const normalized = normalizeParsedResume(resumeId, parsed);

    const { error: updateError } = await supabase
      .from("resumes")
      .update({
        parsed_text: normalized.parsed_text,
        parsed_sections: normalized.parsed_sections,
      })
      .eq("id", resumeId)
      .eq("user_id", user.id);

    if (updateError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to save parsed resume data.", {
        cause: updateError.message,
      });
    }

    const response: ParsedResumeResponse = normalized;
    return jsonResponse(response);
  } catch (error) {
    if (error instanceof GeminiError) {
      return errorResponse("ai_generation_failed", error.message, error.details, 502);
    }

    return toErrorResponse(error);
  }
});

function buildParsePrompt(input: {
  resumeId: string;
  fileName: string | null;
  mimeType: string;
  size: number;
}): string {
  return [
    "You are an expert resume parser for Internship Uncle. Please read the attached resume document (PDF or image).",
    "Extract all the actual text from the document exactly as it appears into the `parsed_text` field. Do not summarize this field; preserve all the raw content.",
    "Then, extract structured details into the `parsed_sections` object.",
    "Do not invent or hallucinate information. If a field is missing, leave it null or omit it from arrays.",
    "Return only JSON that matches the schema.",
    "",
    `Resume ID: ${input.resumeId}`,
    `File name: ${input.fileName ?? "unknown"}`,
    `MIME type: ${input.mimeType}`,
    `File size bytes: ${input.size}`,
    "",
    "Make sure `parsed_text` is extremely thorough, as downstream features rely on it for evaluating the resume.",
  ].join("\n");
}

function normalizeParsedResume(
  resumeId: string,
  raw: Record<string, unknown>,
): ParsedResumeResponse {
  const parsedText = requireString(raw.parsed_text ?? "", "parsed_text", {
    minLength: 20,
    maxLength: 50_000,
  });
  const parsedSections = normalizeParsedSections(raw.parsed_sections);

  return {
    resume_id: resumeId,
    parsed_text: truncateText(parsedText, 50_000),
    parsed_sections: parsedSections,
  };
}

function normalizeParsedSections(value: unknown): ParsedSections {
  if (!isRecord(value)) {
    badRequest("parsed_sections must be an object.");
  }

  return {
    basics: normalizeBasics(value.basics),
    education: normalizeEducationEntries(value.education),
    skills: normalizeStringList(value.skills, "parsed_sections.skills", {
      maxItems: 40,
      maxItemLength: 80,
    }),
    projects: normalizeProjectEntries(value.projects),
    experience: normalizeExperienceEntries(value.experience),
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
      highlights: normalizeStringList(entry.highlights, "parsed_sections.projects.highlights", {
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
      bullets: normalizeStringList(entry.bullets, "parsed_sections.experience.bullets", {
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
  if (!trimmed) {
    return null;
  }

  return truncateText(trimmed, maxLength);
}
