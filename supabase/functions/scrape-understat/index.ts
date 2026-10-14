// Supabase Edge Function — scrape Understat (fixtures + xG + agrégats saison).
// Cron recommandé : toutes les 6 h. NE TOURNE JAMAIS dans l'app Android.
// Source : endpoint JSON Understat `GET getLeagueData/{ligue}/{saison}`
// (utilisé par le front officiel ; vérifié le 2026-10-09 : 380 matchs EPL,
// `{dates:[{id,isResult,h:{id,title},a:{...},goals:{h,a},xG:{h,a},datetime}]}`).
// Parsé en défensif (entrées malformées ignorées, une ligue en échec
// n'arrête pas les autres).
//
// Fichier AUTO-PORTÉ : aucun import relatif (compatible deploy Dashboard
// copier-coller ET `supabase functions deploy`). Seules dépendances : std Deno
// + supabase-js via esm.sh.
//
// Secrets requis : SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ── IDs stables (synchronisé avec `ScrapedIds.kt` côté Android) ─────────
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
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}

function unifiedTeamId(teamName: string): number {
  return javaStringHash(normalizeTeamName(teamName));
}

function scrapedMatchId(
  homeName: string,
  awayName: string,
  dateTs: number,
): number {
  return javaStringHash(
    `${normalizeTeamName(homeName)}|${normalizeTeamName(awayName)}|${dateTs}`,
  );
}

const LEAGUES: Array<{ leagueId: number; understat: string; season: number }> = [
  { leagueId: 39, understat: "EPL", season: 2025 },
  { leagueId: 140, understat: "La_liga", season: 2025 },
  { leagueId: 135, understat: "Serie_A", season: 2025 },
  { leagueId: 78, understat: "Bundesliga", season: 2025 },
  { leagueId: 61, understat: "Ligue_1", season: 2025 },
  { leagueId: 39, understat: "EPL", season: 2026 },
  { leagueId: 140, understat: "La_liga", season: 2026 },
  { leagueId: 135, understat: "Serie_A", season: 2026 },
  { leagueId: 78, understat: "Bundesliga", season: 2026 },
  { leagueId: 61, understat: "Ligue_1", season: 2026 },
];

// ── Client Supabase (service_role) ─────────────────────────────────────
function supabaseAdmin() {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    throw new Error("Secrets manquants : SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(url, key);
}

async function upsertAll(
  // deno-lint-ignore no-explicit-any
  client: any,
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

// ── Parsing défensif ───────────────────────────────────────────────────
const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

interface Agg {
  leagueId: number;
  season: number;
  name: string;
  playedHome: number;
  playedAway: number;
  gfHome: number;
  gfAway: number;
  gaHome: number;
  gaAway: number;
  xgHome: number;
  xgAway: number;
  xgaHome: number;
  xgaAway: number;
}

function str(v: unknown): string | null {
  if (typeof v === "string" && v !== "") return v;
  if (typeof v === "number") return String(v);
  return null;
}

function intOrNull(v: unknown): number | null {
  const s = str(v);
  if (s === null) return null;
  const n = parseInt(s, 10);
  return Number.isFinite(n) ? n : null;
}

function floatOrNull(v: unknown): number | null {
  const s = str(v);
  if (s === null) return null;
  const n = parseFloat(s);
  return Number.isFinite(n) ? n : null;
}

function parseDateTs(datetime: unknown): number {
  const s = str(datetime);
  if (!s) return 0;
  const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/.exec(s);
  if (!m) return 0;
  return Math.floor(
    Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] ?? "0")) / 1000,
  );
}

function teamOf(side: unknown): { id: string; title: string } | null {
  if (side && typeof side === "object") {
    const o = side as Record<string, unknown>;
    const title = str(o["title"] ?? o["short_title"] ?? o["name"]);
    if (!title) return null;
    return { id: str(o["id"]) ?? title, title };
  }
  if (typeof side === "string" && side !== "") return { id: side, title: side };
  return null;
}

serve(async (_req) => {
  const now = Date.now();
  const fixtures: Record<string, unknown>[] = [];
  const aggs = new Map<string, Agg>();
  const errors: string[] = [];
  let leaguesOk = 0;

  for (const lg of LEAGUES) {
    try {
      const res = await fetch(
        `https://understat.com/getLeagueData/${lg.understat}/${lg.season}`,
        {
          headers: {
            "User-Agent": UA,
            "Accept": "application/json",
            "Referer": `https://understat.com/league/${lg.understat}/${lg.season}`,
            "X-Requested-With": "XMLHttpRequest",
            "Accept-Language": "en-US,en;q=0.9",
          },
        },
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const matches = (data as { dates?: unknown }).dates;
      if (!Array.isArray(matches)) throw new Error("dates non-liste");

      for (const raw of matches) {
        const o = raw as Record<string, unknown>;
        const home = teamOf(o["h"]);
        const away = teamOf(o["a"]);
        if (!home || !away) continue;
        const dateTs = parseDateTs(o["datetime"]);
        const isResult = o["isResult"] === true || o["isResult"] === "True";
        const goals = (o["goals"] ?? {}) as Record<string, unknown>;
        const xg = (o["xG"] ?? {}) as Record<string, unknown>;
        const gh = intOrNull(goals["h"] ?? o["h_goals"]);
        const ga = intOrNull(goals["a"] ?? o["a_goals"]);
        const xgh = floatOrNull(xg["h"]);
        const xga = floatOrNull(xg["a"]);

        fixtures.push({
          match_id: scrapedMatchId(home.title, away.title, dateTs),
          league_id: lg.leagueId,
          season: lg.season,
          round: null,
          date_ts: dateTs,
          status_short: isResult ? "FT" : "NS",
          home_team_id: unifiedTeamId(home.title),
          home_team_name: home.title,
          away_team_id: unifiedTeamId(away.title),
          away_team_name: away.title,
          goals_home: gh,
          goals_away: ga,
          xg_home: xgh,
          xg_away: xga,
          source: "understat",
          last_updated: now,
        });

        if (isResult && gh !== null && ga !== null) {
          const key = (t: string) => `${lg.leagueId}|${lg.season}|${t}`;
          const h = aggs.get(key(home.title)) ?? {
            leagueId: lg.leagueId, season: lg.season,
            name: home.title, playedHome: 0, playedAway: 0,
            gfHome: 0, gfAway: 0, gaHome: 0, gaAway: 0,
            xgHome: 0, xgAway: 0, xgaHome: 0, xgaAway: 0,
          };
          h.playedHome++; h.gfHome += gh; h.gaHome += ga;
          if (xgh !== null) h.xgHome += xgh;
          if (xga !== null) h.xgaHome += xga;
          aggs.set(key(home.title), h);
          const a = aggs.get(key(away.title)) ?? {
            leagueId: lg.leagueId, season: lg.season,
            name: away.title, playedHome: 0, playedAway: 0,
            gfHome: 0, gfAway: 0, gaHome: 0, gaAway: 0,
            xgHome: 0, xgAway: 0, xgaHome: 0, xgaAway: 0,
          };
          a.playedAway++; a.gfAway += ga; a.gaAway += gh;
          if (xga !== null) a.xgAway += xga;
          if (xgh !== null) a.xgaAway += xgh;
          aggs.set(key(away.title), a);
        }
      }
      leaguesOk++;
    } catch (e) {
      errors.push(`${lg.understat}/${lg.season}: ${(e as Error).message}`);
    }
  }

  const teamStats: Record<string, unknown>[] = [];
  const xgStats: Record<string, unknown>[] = [];
  for (const a of aggs.values()) {
    teamStats.push({
      unified_team_id: unifiedTeamId(a.name),
      team_name: a.name,
      league_id: a.leagueId,
      season: a.season,
      played_home: a.playedHome,
      played_away: a.playedAway,
      gf_home: a.gfHome,
      gf_away: a.gfAway,
      ga_home: a.gaHome,
      ga_away: a.gaAway,
      last_updated: now,
    });
    xgStats.push({
      unified_team_id: unifiedTeamId(a.name),
      team_name: a.name,
      league_id: a.leagueId,
      season: a.season,
      xg_for_home: a.xgHome / Math.max(1, a.playedHome),
      xg_for_away: a.xgAway / Math.max(1, a.playedAway),
      xga_home: a.xgaHome / Math.max(1, a.playedHome),
      xga_away: a.xgaAway / Math.max(1, a.playedAway),
      npxg_for: null,
      last_updated: now,
    });
  }

  const client = supabaseAdmin();
  await upsertAll(client, "scraped_fixtures", fixtures, "match_id");
  await upsertAll(
    client,
    "team_season_stats",
    teamStats,
    "unified_team_id,league_id,season",
  );
  await upsertAll(client, "xg_stats", xgStats, "unified_team_id,league_id,season");

  return new Response(
    JSON.stringify({
      status: leaguesOk > 0 ? "ok" : "error",
      leaguesOk,
      leaguesTotal: LEAGUES.length,
      fixtures: fixtures.length,
      teams: teamStats.length,
      errors,
    }),
    { headers: { "Content-Type": "application/json" } },
  );
});
