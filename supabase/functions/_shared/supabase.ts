// Client Supabase côté Edge Function (service_role → contourne RLS en écriture).
// Secrets à définir : SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export function supabaseAdmin(): SupabaseClient {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    throw new Error("Secrets manquants : SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(url, key);
}

/** Upsert par batch (PostgREST merge-duplicates). */
export async function upsertAll(
  client: SupabaseClient,
  table: string,
  rows: Record<string, unknown>[],
  onConflict: string,
): Promise<void> {
  const CHUNK = 200;
  for (let i = 0; i < rows.length; i += CHUNK) {
    const { error } = await client
      .from(table)
      .upsert(rows.slice(i, i + CHUNK), { onConflict });
    if (error) throw new Error(`upsert ${table}: ${error.message}`);
  }
}
