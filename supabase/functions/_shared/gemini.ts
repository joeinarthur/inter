import { getEnv, requireEnv } from "./env.ts";
import { ApiError } from "./http.ts";

export type GeminiSchema = {
  type: "OBJECT" | "ARRAY" | "STRING" | "NUMBER" | "INTEGER" | "BOOLEAN";
  description?: string;
  properties?: Record<string, GeminiSchema>;
  required?: string[];
  propertyOrdering?: string[];
  items?: GeminiSchema;
  enum?: string[];
  nullable?: boolean;
  minItems?: number;
  maxItems?: number;
  minimum?: number;
  maximum?: number;
};

export type GeminiInlineData = {
  mimeType: string;
  data: string;
};

export interface GeminiGenerationOptions {
  model?: string;
  temperature?: number;
  maxOutputTokens?: number;
  retries?: number;
  parts?: Array<
    | { text: string }
    | {
      inlineData: GeminiInlineData;
    }
  >;
}

const DEFAULT_MODEL = "gemini-2.5-flash";
const GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";

export class GeminiError extends Error {
  constructor(
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = "GeminiError";
  }
}

export async function generateText(
  prompt: string,
  options: GeminiGenerationOptions = {},
): Promise<string> {
  const rawText = await generateContent(prompt, options);
  return rawText;
}

export async function generateStructuredObject<T>(
  prompt: string,
  schema: GeminiSchema,
  options: GeminiGenerationOptions = {},
): Promise<T> {
  const retries = options.retries ?? 1;
  let lastError: unknown = null;

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const rawText = await generateContent(prompt, { ...options, retries: 0 }, schema);
    const parsed = safeParseJson(rawText);

    if (parsed !== null) {
      return parsed as T;
    }

    lastError = new GeminiError("Gemini returned malformed JSON.", {
      rawText,
    });

    if (attempt < retries) {
      await delay(250 * (attempt + 1));
      continue;
    }
  }

  if (lastError instanceof GeminiError) {
    throw lastError;
  }

  throw new GeminiError("Gemini returned malformed JSON.");
}

async function generateContent(
  prompt: string,
  options: GeminiGenerationOptions,
  schema?: GeminiSchema,
): Promise<string> {
  const apiKey = requireEnv("GEMINI_API_KEY");
  const model = options.model ?? getEnv("GEMINI_MODEL") ?? DEFAULT_MODEL;
  const retries = options.retries ?? 1;
  const temperature = options.temperature ?? 0.2;
  const maxOutputTokens = options.maxOutputTokens ?? 2048;

  let lastError: unknown = null;

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      const response = await fetch(`${GEMINI_ENDPOINT}/models/${model}:generateContent?key=${apiKey}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          contents: [
            {
              role: "user",
              parts: [
                {
                  text: prompt,
                },
                ...(options.parts ?? []),
              ],
            },
          ],
          generationConfig: {
            temperature,
            maxOutputTokens,
            responseMimeType: schema ? "application/json" : "text/plain",
            ...(schema ? { responseSchema: schema } : {}),
          },
        }),
      });

      const payload = await response.json().catch(() => null);

      if (!response.ok) {
        throw new GeminiError("Gemini request failed.", {
          status: response.status,
          payload,
        });
      }

      const text = extractCandidateText(payload);
      if (!text) {
        throw new GeminiError("Gemini returned an empty response.", {
          payload,
        });
      }

      return text;
    } catch (error) {
      lastError = error;
      if (attempt < retries && isRetryableGeminiError(error)) {
        await delay(250 * (attempt + 1));
        continue;
      }

      break;
    }
  }

  if (lastError instanceof GeminiError) {
    throw lastError;
  }

  throw new GeminiError("Gemini generation failed.", {
    cause: String(lastError),
  });
}

function extractCandidateText(payload: unknown): string {
  if (!payload || typeof payload !== "object") {
    return "";
  }

  const candidates = (payload as Record<string, unknown>).candidates;
  if (!Array.isArray(candidates) || candidates.length === 0) {
    return "";
  }

  const firstCandidate = candidates[0] as Record<string, unknown>;
  const content = firstCandidate.content as Record<string, unknown> | undefined;
  const parts = content?.parts;
  if (!Array.isArray(parts)) {
    return "";
  }

  const text = parts
    .map((part) => (part as Record<string, unknown>).text)
    .filter((part): part is string => typeof part === "string")
    .join("")
    .trim();

  return text;
}

function safeParseJson(text: string): unknown | null {
  const stripped = stripCodeFences(text).trim();
  const candidates = [stripped, extractJsonSubstring(stripped)];

  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }

    try {
      return JSON.parse(candidate);
    } catch {
      // Try the next candidate.
    }
  }

  return null;
}

function stripCodeFences(text: string): string {
  return text
    .replace(/^```json\s*/i, "")
    .replace(/^```\s*/i, "")
    .replace(/\s*```$/i, "");
}

function extractJsonSubstring(text: string): string | null {
  const objectStart = text.indexOf("{");
  const objectEnd = text.lastIndexOf("}");
  if (objectStart >= 0 && objectEnd > objectStart) {
    return text.slice(objectStart, objectEnd + 1);
  }

  const arrayStart = text.indexOf("[");
  const arrayEnd = text.lastIndexOf("]");
  if (arrayStart >= 0 && arrayEnd > arrayStart) {
    return text.slice(arrayStart, arrayEnd + 1);
  }

  return null;
}

function isRetryableGeminiError(error: unknown): boolean {
  if (error instanceof GeminiError) {
    const status = typeof error.details === "object" && error.details !== null
      ? (error.details as Record<string, unknown>).status
      : undefined;

    if (typeof status === "number" && [408, 429, 500, 502, 503, 504].includes(status)) {
      return true;
    }

    const message = error.message.toLowerCase();
    return message.includes("malformed json") ||
      message.includes("empty response") ||
      message.includes("gemini request failed");
  }

  return error instanceof TypeError;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Future AI-backed functions should prefer generateStructuredObject(...) and
// keep their post-processing/normalization local to the function entrypoint.
