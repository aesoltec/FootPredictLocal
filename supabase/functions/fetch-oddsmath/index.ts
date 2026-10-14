/**
 * Supabase Edge Function — OddsMath (oddsmath.com)
 * Données : dropping odds, mouvements de cotes en temps quasi-réel, historique
 * Utile pour détecter steam moves / sharp money
 * Cron only. NE DOIT PAS tourner dans l'app Android.
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async () => {
  // TODO: scrape dropping odds + % drop depuis opening
  // Push vers table: dropping_odds (source='oddsmath')
  return new Response(
    JSON.stringify({
      status: "ok",
      source: "oddsmath",
      message: "OddsMath dropping-odds placeholder",
    }),
    { headers: { "Content-Type": "application/json" } }
  )
})
