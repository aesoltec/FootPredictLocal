/**
 * Supabase Edge Function — Betwatch.fr (moneyway)
 * Données : volumes matchés Betfair (€), % par issue (1/X/2), dropping odds
 * Signal "smart money" très utile en complément des cotes
 * Note : Betwatch propose aussi une API payante (api.betwatch.fr) — préférer si budget
 * Cron only. NE DOIT PAS tourner dans l'app Android.
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async () => {
  // TODO: scrape /money ou utiliser API Betwatch si clé dispo
  // Push vers table: moneyway (match_id, volume_1, volume_x, volume_2, pct_1, pct_x, pct_2, source='betwatch')
  return new Response(
    JSON.stringify({
      status: "ok",
      source: "betwatch",
      message: "Betwatch moneyway / volume placeholder",
    }),
    { headers: { "Content-Type": "application/json" } }
  )
})
