import { ApiError } from "./http.ts";

export interface DownloadedFile {
  url: string;
  mimeType: string;
  fileName?: string;
  size: number;
  bytes: Uint8Array;
}

export async function downloadFile(
  url: string,
  options?: {
    maxBytes?: number;
    fileName?: string;
  },
): Promise<DownloadedFile> {
  let parsedUrl: URL;
  try {
    parsedUrl = new URL(url);
  } catch {
    throw new ApiError(400, "invalid_input", "file_url must be a valid URL.");
  }

  const response = await fetch(parsedUrl.toString());
  if (!response.ok) {
    throw new ApiError(422, "file_processing_failed", "Unable to fetch the uploaded resume file.", {
      status: response.status,
    });
  }

  const bytes = new Uint8Array(await response.arrayBuffer());
  const maxBytes = options?.maxBytes ?? 10 * 1024 * 1024;

  if (bytes.byteLength > maxBytes) {
    throw new ApiError(413, "file_processing_failed", "The uploaded file is too large to process.", {
      maxBytes,
      actualBytes: bytes.byteLength,
    });
  }

  const mimeType = response.headers.get("content-type")?.split(";")[0]?.trim() || inferMimeType(parsedUrl.pathname);

  return {
    url: parsedUrl.toString(),
    mimeType,
    fileName: options?.fileName,
    size: bytes.byteLength,
    bytes,
  };
}

export function bytesToBase64(bytes: Uint8Array): string {
  const chunkSize = 0x8000;
  let binary = "";

  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...chunk);
  }

  return btoa(binary);
}

export function inferMimeType(pathname: string): string {
  const lower = pathname.toLowerCase();

  if (lower.endsWith(".pdf")) {
    return "application/pdf";
  }

  if (lower.endsWith(".txt")) {
    return "text/plain";
  }

  if (lower.endsWith(".md")) {
    return "text/markdown";
  }

  if (lower.endsWith(".html") || lower.endsWith(".htm")) {
    return "text/html";
  }

  if (lower.endsWith(".json")) {
    return "application/json";
  }

  if (lower.endsWith(".docx")) {
    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  }

  return "application/octet-stream";
}
