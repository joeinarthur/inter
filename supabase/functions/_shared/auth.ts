import type { User } from "npm:@supabase/supabase-js@2";
import { forbidden, unauthorized } from "./http.ts";
import { getSupabaseAdminClient } from "./supabase.ts";

export interface AuthContext {
  user: User;
  token: string;
}

export async function requireAuth(request: Request): Promise<AuthContext> {
  const authHeader = request.headers.get("Authorization") ?? request.headers.get("authorization");
  if (!authHeader) {
    unauthorized("Missing Authorization header.");
  }

  const [scheme, token] = authHeader.split(" ");
  if (scheme !== "Bearer" || !token) {
    unauthorized("Authorization header must be a Bearer token.");
  }

  const supabase = getSupabaseAdminClient();
  const { data, error } = await supabase.auth.getUser(token);

  if (error || !data?.user) {
    unauthorized("Invalid or expired Supabase session.", {
      reason: error?.message,
    });
  }

  return {
    user: data.user,
    token,
  };
}

export function isAdminUser(user: User): boolean {
  const appMetadata = user.app_metadata as Record<string, unknown> | undefined;
  const role = appMetadata?.role;
  const roles = appMetadata?.roles;
  const adminFlag = appMetadata?.is_admin;

  if (role === "admin" || adminFlag === true) {
    return true;
  }

  if (Array.isArray(roles) && roles.includes("admin")) {
    return true;
  }

  return false;
}

export function requireAdminUser(user: User): void {
  if (!isAdminUser(user)) {
    forbidden("Admin access is required for this action.");
  }
}
