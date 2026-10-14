// Supabase Edge Function — OpenLigaDB (données officielles DFB, gratuit sans clé).
// Couverture utile : bl1 (Bundesliga, ligue 78) + ucl (Ligue des Champions,
// ligue 2). Insertion des seuls matchs manquants (match flou).
// Fichier AUTO-PORTÉ (deploy Dashboard OK). Secrets : SUPABASE_URL,
// SUPABASE_SERVICE_ROLE_KEY.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const LEAGUES = [
  { leagueId: 78, shortcut: "bl1", seasons: [2025, 2026] },
  { leagueId: 2, shortcut: "ucl", seasons: [2025, 2026] },
];

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
  const x = canonical(normalizeTeamName(a));
  const y = canonical(normalizeTeamName(b));
  if (x === y) return true;
  if (x.length > 3 && y.length > 3 && (x.includes(y) || y.includes(x))) return true;
  return jaccard(x, y) >= 0.5;
}

// Jaccard sur tokens : attrape les insertions au milieu
// ("bayer 04 leverkusen" vs "bayer leverkusen" = 0.67).
function jaccard(x: string, y: string): number {
  const tx = new Set(x.split(" ").filter((t) => t.length > 0));
  const ty = new Set(y.split(" ").filter((t) => t.length > 0));
  if (tx.size === 0 || ty.size === 0) return 0;
  let inter = 0;
  for (const t of tx) if (ty.has(t)) inter++;
  return inter / (tx.size + ty.size - inter);
}

// Variantes inter-sources (anglais Understat/ESPN vs allemand OpenLigaDB).
const ALIASES: Record<string, string> = {
  "fc bayern munchen": "bayern munich",
  "bayern munchen": "bayern munich",
  "1 fc koln": "fc cologne",
  "fc koln": "fc cologne",
  "koln": "cologne",
  "borussia monchengladbach": "borussia m gladbach",
  "rasenballsport leipzig": "rb leipzig",
  "sv 07 elversberg": "elversberg",
  "hamburger sv": "hamburg",
  "fc schalke 04": "schalke",
  "schalke 04": "schalke",
};

function canonical(normalized: string): string {
  return ALIASES[normalized] ?? normalized;
}

function intOrNull(v: unknown): number | null {
  if (typeof v === "number" && Number.isFinite(v)) return Math.trunc(v);
  return null;
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
    for (const season of lg.seasons) {
      const tag = `${lg.shortcut}/${season}`;
      try {
        const res = await fetch(
          `https://api.openligadb.de/getmatchdata/${lg.shortcut}/${season}`,
          { headers: { "Accept": "application/json" } },
        );
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const matches = await res.json();
        if (!Array.isArray(matches)) throw new Error("réponse non-liste");

        const { data: rows, error: selErr } = await client
          .from("scraped_fixtures")
          .select("match_id,league_id,season,date_ts,home_team_name,away_team_name")
          .eq("league_id", lg.leagueId)
          .eq("season", season);
        if (selErr) throw new Error(`select: ${selErr.message}`);
        const existing = (rows ?? []) as Record<string, unknown>[];

        const candidates: Record<string, unknown>[] = [];
        for (const raw of matches) {
          const m = raw as Record<string, unknown>;
          const t1 = (m.team1 as Record<string, unknown> | undefined)?.teamName;
          const t2 = (m.team2 as Record<string, unknown> | undefined)?.teamName;
          if (typeof t1 !== "string" || typeof t2 !== "string" || !t1 || !t2) continue;
          const dateTs = Math.floor(new Date(m.matchDateTimeUTC as string).getTime() / 1000);
          if (!Number.isFinite(dateTs) || dateTs <= 0) continue;
          const finished = m.matchIsFinished === true;
          const results = (Array.isArray(m.matchResults) ? m.matchResults : []) as Record<string, unknown>[];
          const final = results.find((r) => r.resultName === "Endergebnis") ?? results[results.length - 1];
          const gh = finished ? intOrNull(final?.pointsTeam1) : null;
          const ga = finished ? intOrNull(final?.pointsTeam2) : null;
          const group = m.group as Record<string, unknown> | undefined;
          const round = typeof group?.groupName === "string" ? group.groupName as string : null;

          // Tolérance 7 jours : les sources se mettent à jour à des rythmes
          // différents (reports, fuseaux). Deux confrontations identiques ne se
          // rejouent jamais à 7 jours d’intervalle en championnat.
          const dup = existing.some((r) =>
            Math.abs((r.date_ts as number) - dateTs) < 604800 &&
            namesMatch(r.home_team_name as string, t1) &&
            namesMatch(r.away_team_name as string, t2)
          );
          if (dup) continue;

          candidates.push({
            match_id: javaStringHash(
              `${normalizeTeamName(t1)}|${normalizeTeamName(t2)}|${dateTs}`,
            ),
            league_id: lg.leagueId,
            season,
            round,
            date_ts: dateTs,
            status_short: finished ? "FT" : "NS",
            home_team_id: javaStringHash(normalizeTeamName(t1)),
            home_team_name: t1,
            away_team_id: javaStringHash(normalizeTeamName(t2)),
            away_team_name: t2,
            goals_home: gh,
            goals_away: ga,
            xg_home: null,
            xg_away: null,
            source: "openligadb",
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
        errors.push(`${tag}: ${(e as Error).message}`);
      }
    }
  }

  return new Response(
    JSON.stringify({ status: leaguesOk > 0 ? "ok" : "error", leaguesOk, inserted, errors }),
    { headers: { "Content-Type": "application/json" } },
  );
});
