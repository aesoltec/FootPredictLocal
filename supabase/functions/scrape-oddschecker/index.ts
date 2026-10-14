/**
 * Supabase Edge Function — OddsChecker
 * Données : comparaison multi-bookmakers (forte couverture UK), best odds, marchés 1X2 / O/U / AH
 * Excellent complément à OddsPortal / Oddspedia pour la profondeur bookmakers
 * Cron only. NE DOIT PAS tourner dans l'app Android.
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async () => {
  // TODO: scrape oddschecker.com (JS-rendered) — best odds + bookmaker grid
  // Push vers table Supabase: scraped_odds (source='oddschecker')
  return new Response(
    JSON.stringify({
      status: "ok",
      source: "oddschecker",
      message: "OddsChecker multi-bookmaker odds placeholder",
    }),
    { headers: { "Content-Type": "application/json" } }
  )
})
