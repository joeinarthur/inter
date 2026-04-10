export type ErrorCode =
  | "invalid_input"
  | "unauthorized"
  | "forbidden"
  | "not_found"
  | "file_processing_failed"
  | "ai_generation_failed"
  | "database_lookup_failed"
  | "database_upsert_failed"
  | "storage_upload_failed"
  | "rate_limited"
  | "not_implemented"
  | "internal_error";

export interface ApiErrorBody {
  error: {
    code: ErrorCode;
    message: string;
    details?: unknown;
  };
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: ErrorCode,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function jsonResponse<T>(
  payload: T,
  status = 200,
): Response {
  return Response.json(payload, { status });
}

export function errorResponse(
  code: ErrorCode,
  message: string,
  details?: unknown,
  status = 400,
): Response {
  const body: ApiErrorBody = {
    error: {
      code,
      message,
      details,
    },
  };

  return Response.json(body, { status });
}

export function toErrorResponse(error: unknown): Response {
  if (error instanceof ApiError) {
    return errorResponse(error.code, error.message, error.details, error.status);
  }

  if (error instanceof Error) {
    return errorResponse("internal_error", error.message, undefined, 500);
  }

  return errorResponse("internal_error", "Unexpected server error.", { error }, 500);
}

export function badRequest(message: string, details?: unknown): never {
  throw new ApiError(400, "invalid_input", message, details);
}

export function unauthorized(message = "Authentication is required.", details?: unknown): never {
  throw new ApiError(401, "unauthorized", message, details);
}

export function forbidden(message = "You do not have permission to perform this action.", details?: unknown): never {
  throw new ApiError(403, "forbidden", message, details);
}

export function notFound(message = "Requested resource was not found.", details?: unknown): never {
  throw new ApiError(404, "not_found", message, details);
}

export function notImplemented(message: string, details?: unknown): Response {
  return errorResponse("not_implemented", message, details, 501);
}
