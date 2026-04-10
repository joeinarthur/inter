import "../_shared/runtime.ts";

import { requireAuth } from "../_shared/auth.ts";
import { ApiError, badRequest, errorResponse, jsonResponse, toErrorResponse, unauthorized } from "../_shared/http.ts";
import { createSimpleTextPdf, formatResumeJsonForPdf } from "../_shared/pdf.ts";
import { getSupabaseAdminClient } from "../_shared/supabase.ts";
import { parseJsonBody, requireUuid } from "../_shared/validation.ts";

type ExportResumePdfResponse = {
  generated_resume_id: string;
  pdf_url: string;
};

Deno.serve(async (request) => {
  try {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (request.method !== "POST") {
      badRequest("export-resume-pdf only accepts POST requests.");
    }

    const { user } = await requireAuth(request);
    if (!user) {
      unauthorized("Authentication is required.");
    }

    const body = await parseJsonBody(request);
    const generatedResumeId = requireUuid(body.generated_resume_id, "generated_resume_id");

    const supabase = getSupabaseAdminClient();
    const { data, error } = await supabase
      .from("generated_resumes")
      .select("id,user_id,resume_json,template_name,pdf_url,source_resume_id,target_job_id,created_at,updated_at")
      .eq("id", generatedResumeId)
      .limit(1);

    if (error) {
      throw new ApiError(500, "database_lookup_failed", "Failed to load generated resume.", {
        cause: error.message,
      });
    }

    const generatedResume = data?.[0];
    if (!generatedResume) {
      throw new ApiError(404, "not_found", "The requested generated resume was not found.", {
        generated_resume_id: generatedResumeId,
      });
    }

    if (generatedResume.user_id !== user.id) {
      throw new ApiError(403, "forbidden", "You do not have access to this generated resume.");
    }

    const resumeJson = generatedResume.resume_json as Record<string, unknown> | undefined;
    if (!resumeJson) {
      throw new ApiError(400, "invalid_input", "Generated resume JSON is missing.");
    }

    const pdfBytes = createSimpleTextPdf(formatResumeJsonForPdf(resumeJson));
    const storagePath = `${user.id}/${generatedResumeId}.pdf`;
    const blob = new Blob([pdfBytes], { type: "application/pdf" });

    const { error: uploadError } = await supabase.storage
      .from("generated-resumes")
      .upload(storagePath, blob, {
        contentType: "application/pdf",
        upsert: true,
      });

    if (uploadError) {
      throw new ApiError(500, "storage_upload_failed", "Failed to upload generated resume PDF.", {
        cause: uploadError.message,
      });
    }

    const { data: signedUrlData, error: signedUrlError } = await supabase.storage
      .from("generated-resumes")
      .createSignedUrl(storagePath, 60 * 60 * 24 * 7);

    if (signedUrlError || !signedUrlData?.signedUrl) {
      throw new ApiError(500, "storage_upload_failed", "Failed to create a signed PDF URL.", {
        cause: signedUrlError?.message,
      });
    }

    const pdfUrl = signedUrlData.signedUrl;
    const { error: updateError } = await supabase
      .from("generated_resumes")
      .update({
        pdf_url: pdfUrl,
      })
      .eq("id", generatedResumeId)
      .eq("user_id", user.id);

    if (updateError) {
      throw new ApiError(500, "database_upsert_failed", "Failed to update generated resume PDF URL.", {
        cause: updateError.message,
      });
    }

    const response: ExportResumePdfResponse = {
      generated_resume_id: generatedResumeId,
      pdf_url: pdfUrl,
    };

    return jsonResponse(response);
  } catch (error) {
    return toErrorResponse(error);
  }
});
