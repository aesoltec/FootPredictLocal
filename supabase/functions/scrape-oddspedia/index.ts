/**
 * Supabase Edge Function — Oddspedia
 * Données : dropping odds, sure bets / arbitrage, comparaison multi-book
 * Complément moderne d'OddsPortal
 * Cron only. NE DOIT PAS tourner dans l'app Android.
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async () => {
  return new Response(
    JSON.stringify({
      status: "ok",
      source: "oddspedia",
      message: "Oddspedia dropping-odds + surebets placeholder",
    }),
    { headers: { "Content-Type": "application/json" } }
  )
})
