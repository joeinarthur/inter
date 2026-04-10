import { ApiError, badRequest } from "./http.ts";

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export async function parseJsonBody(request: Request): Promise<Record<string, unknown>> {
  try {
    const body = await request.json();
    if (!isRecord(body)) {
      badRequest("Request body must be a JSON object.");
    }
    return body;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }

    badRequest("Request body must be valid JSON.", { cause: String(error) });
  }
}

export function requireString(
  value: unknown,
  fieldName: string,
  options?: {
    minLength?: number;
    maxLength?: number;
  },
): string {
  if (typeof value !== "string") {
    badRequest(`${fieldName} must be a string.`);
  }

  const trimmed = value.trim();
  const minLength = options?.minLength ?? 1;
  const maxLength = options?.maxLength ?? Number.POSITIVE_INFINITY;

  if (trimmed.length < minLength) {
    badRequest(`${fieldName} must be at least ${minLength} character(s) long.`);
  }

  if (trimmed.length > maxLength) {
    badRequest(`${fieldName} must be at most ${maxLength} character(s) long.`);
  }

  return trimmed;
}

export function optionalString(
  value: unknown,
  fieldName: string,
  options?: {
    maxLength?: number;
  },
): string | null {
  if (value === null || value === undefined) {
    return null;
  }

  const trimmed = requireString(value, fieldName, {
    minLength: 1,
    maxLength: options?.maxLength,
  });

  return trimmed;
}

export function requireUuid(value: unknown, fieldName: string): string {
  const uuid = requireString(value, fieldName);
  const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  if (!uuidPattern.test(uuid)) {
    badRequest(`${fieldName} must be a valid UUID.`);
  }

  return uuid;
}

export function normalizeStringList(
  value: unknown,
  fieldName: string,
  options?: {
    minItems?: number;
    maxItems?: number;
    maxItemLength?: number;
  },
): string[] {
  if (!Array.isArray(value)) {
    badRequest(`${fieldName} must be an array of strings.`);
  }

  const maxItems = options?.maxItems ?? 50;
  const maxItemLength = options?.maxItemLength ?? 120;

  const normalized = value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
    .map((item) => item.slice(0, maxItemLength));

  const deduped = Array.from(new Set(normalized));

  if ((options?.minItems ?? 0) > 0 && deduped.length < (options?.minItems ?? 0)) {
    badRequest(`${fieldName} must contain at least ${options?.minItems} item(s).`);
  }

  return deduped.slice(0, maxItems);
}

export function normalizeDifficulty(value: unknown, fallback = "medium"): string {
  if (typeof value !== "string") {
    return fallback;
  }

  const normalized = value.trim().toLowerCase();
  if (normalized === "easy" || normalized === "medium" || normalized === "hard") {
    return normalized;
  }

  return fallback;
}

export function requireBoolean(value: unknown, fieldName: string): boolean {
  if (typeof value !== "boolean") {
    badRequest(`${fieldName} must be a boolean.`);
  }

  return value;
}

export function truncateText(value: string, maxLength: number): string {
  if (value.length <= maxLength) {
    return value;
  }

  return value.slice(0, maxLength).trimEnd();
}
