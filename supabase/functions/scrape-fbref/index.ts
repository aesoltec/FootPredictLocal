// Supabase Edge Function — scrape FBref (stats avancées)
// Cron only. NE DOIT PAS tourner dans l'app Android.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
serve(async () => {
  return new Response(JSON.stringify({ status: "ok", message: "FBref scrape placeholder" }), {
    headers: { "Content-Type": "application/json" },
  })
})
