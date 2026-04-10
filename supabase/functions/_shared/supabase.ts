import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { requireEnv } from "./env.ts";

let adminClient: SupabaseClient | null = null;

export function getSupabaseAdminClient(): SupabaseClient {
  if (adminClient) {
    return adminClient;
  }

  adminClient = createClient(
    requireEnv("SUPABASE_URL"),
    requireEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: {
        autoRefreshToken: false,
        persistSession: false,
        detectSessionInUrl: false,
      },
    },
  );

  return adminClient;
}
