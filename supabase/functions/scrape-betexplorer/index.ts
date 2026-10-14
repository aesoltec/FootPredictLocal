/**
 * Supabase Edge Function — BetExplorer
 * Données : cotes historiques très profondes, opening/closing, multi-bookmakers
 * Excellent pour backtests et closing-line value
 * Cron only. NE DOIT PAS tourner dans l'app Android.
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async () => {
  return new Response(
    JSON.stringify({
      status: "ok",
      source: "betexplorer",
      message: "BetExplorer historical odds placeholder",
    }),
    { headers: { "Content-Type": "application/json" } }
  )
})
