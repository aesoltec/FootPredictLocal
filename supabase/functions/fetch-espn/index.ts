// Supabase Edge Function — ESPN scoreboard (fixtures courantes, 5 ligues).
// Redondance de Understat : mêmes tables, insertion des seuls matchs
// manquants (match flou tolérant aux variantes de noms).
// Fichier AUTO-PORTÉ (deploy Dashboard OK). Secrets : SUPABASE_URL,
// SUPABASE_SERVICE_ROLE_KEY.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const LEAGUES = [
  { leagueId: 39, espn: "eng.1" },
  { leagueId: 140, espn: "esp.1" },
  { leagueId: 135, espn: "ita.1" },
  { leagueId: 78, espn: "ger.1" },
  { leagueId: 61, espn: "fra.1" },
];

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

function normalizeTeamName(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function javaStringHash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

function namesMatch(a: string, b: string): boolean {
  const x = normalizeTeamName(a);
  const y = normalizeTeamName(b);
  return x === y ||
    (x.length > 3 && y.length > 3 && (x.includes(y) || y.includes(x)));
}

function intOrNull(v: unknown): number | null {
  if (typeof v === "number" && Number.isFinite(v)) return Math.trunc(v);
  if (typeof v === "string" && v !== "") {
    const n = parseInt(v, 10);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

// Saison de démarrage (août→année courante, sinon année-1), convention app.
function seasonOf(dateTs: number): number {
  const month = new Date(dateTs * 1000).getUTCMonth() + 1;
  const year = new Date(dateTs * 1000).getUTCFullYear();
  return month >= 8 ? year : year - 1;
}

serve(async (_req) => {
  const now = Date.now();
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) throw new Error("Secrets manquants");
  const client = createClient(url, key);

  let leaguesOk = 0;
  let inserted = 0;
  const errors: string[] = [];

  for (const lg of LEAGUES) {
    try {
      const res = await fetch(
        `https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.espn}/scoreboard?limit=100`,
        { headers: { "User-Agent": UA, "Accept": "application/json" } },
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const events = Array.isArray(data.events) ? data.events : [];

      // Lignes existantes pour le match flou.
      const probeSeasons = [...new Set(events.map((e: { date?: string }) => {
        const ts = Math.floor(new Date(e.date ?? 0).getTime() / 1000);
        return seasonOf(ts);
      }))];
      const existing: Record<string, unknown>[] = [];
      for (const season of probeSeasons) {
        const { data: rows, error } = await client
          .from("scraped_fixtures")
          .select("match_id,league_id,season,date_ts,home_team_name,away_team_name")
          .eq("league_id", lg.leagueId)
          .eq("season", season);
        if (!error && rows) existing.push(...rows);
      }

      const candidates: Record<string, unknown>[] = [];
      for (const raw of events) {
        const e = raw as Record<string, unknown>;
        const comp = ((e.competitions ?? []) as Record<string, unknown>[])[0];
        if (!comp) continue;
        const competitors = (comp.competitors ?? []) as Record<string, unknown>[];
        const home = competitors.find((c) => c.homeAway === "home");
        const away = competitors.find((c) => c.homeAway === "away");
        const homeName = (home?.team as Record<string, unknown> | undefined)?.displayName;
        const awayName = (away?.team as Record<string, unknown> | undefined)?.displayName;
        if (typeof homeName !== "string" || typeof awayName !== "string") continue;
        const dateTs = Math.floor(new Date(e.date as string).getTime() / 1000);
        if (!Number.isFinite(dateTs) || dateTs <= 0) continue;
        const state = ((e.status as Record<string, unknown> | undefined)?.type as Record<string, unknown> | undefined)?.state;
        const status = state === "post" ? "FT" : state === "in" ? "LIVE" : "NS";
        const gh = status === "FT" || status === "LIVE" ? intOrNull(home?.score) : null;
        const ga = status === "FT" || status === "LIVE" ? intOrNull(away?.score) : null;
        const season = seasonOf(dateTs);

        // Tolérance 7 jours (reports, fuseaux, rythmes de mise à jour).
        const dup = existing.some((r) =>
          (r.league_id as number) === lg.leagueId &&
          (r.season as number) === season &&
          Math.abs((r.date_ts as number) - dateTs) < 604800 &&
          namesMatch(r.home_team_name as string, homeName) &&
          namesMatch(r.away_team_name as string, awayName)
        );
        if (dup) continue;

        candidates.push({
          match_id: javaStringHash(
            `${normalizeTeamName(homeName)}|${normalizeTeamName(awayName)}|${dateTs}`,
          ),
          league_id: lg.leagueId,
          season,
          round: null,
          date_ts: dateTs,
          status_short: status,
          home_team_id: javaStringHash(normalizeTeamName(homeName)),
          home_team_name: homeName,
          away_team_id: javaStringHash(normalizeTeamName(awayName)),
          away_team_name: awayName,
          goals_home: gh,
          goals_away: ga,
          xg_home: null,
          xg_away: null,
          source: "espn",
          last_updated: now,
        });
      }

      if (candidates.length > 0) {
        const { error } = await client
          .from("scraped_fixtures")
          .upsert(candidates, { onConflict: "match_id" });
        if (error) throw new Error(`upsert: ${error.message}`);
        inserted += candidates.length;
      }
      leaguesOk++;
    } catch (e) {
      errors.push(`${lg.espn}: ${(e as Error).message}`);
    }
  }

  return new Response(
    JSON.stringify({
      status: leaguesOk > 0 ? "ok" : "error",
      leaguesOk,
      leaguesTotal: LEAGUES.length,
      inserted,
      errors,
    }),
    { headers: { "Content-Type": "application/json" } },
  );
});
