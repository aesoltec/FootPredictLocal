/**
 * Supabase Edge Function — scrape OddsPortal
 * Données : cotes multi-bookmakers (1X2, O/U, AH), opening/closing, historique
 * ⚠️ ToS OddsPortal interdisent le scraping commercial — usage research/perso uniquement
 * Difficulté : moyenne (JS-rendered, rate-limit, fingerprint)
 * Cron only. NE DOIT PAS tourner dans l'app Android.
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async () => {
  // TODO: headless (Playwright/Puppeteer) + parse bookmaker tables
  // Push vers table Supabase: scraped_odds (source='oddsportal')
  return new Response(
    JSON.stringify({
      status: "ok",
      source: "oddsportal",
      message: "OddsPortal scrape placeholder — multi-book 1X2 / O/U / AH",
    }),
    { headers: { "Content-Type": "application/json" } }
  )
})
