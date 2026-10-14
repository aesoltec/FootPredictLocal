// Supabase Edge Function — get-live-scores (scores en direct, ~40 ligues).
//
// Stratégie HYBRIDE honnête quota (forfait gratuit API-Football : 100 req/jour) :
// - Flux PREMIUM (API-Football `/fixtures?live=all`) : top-up pour les matchs
//   en cours des 8 compétitions premium, avec cache partagé 90 s + PLAFOND
//   40 appels/jour (le reste du quota reste aux synchros). Clé lue UNIQUEMENT
//   via `Deno.env.get("API_FOOTBALL_KEY")` (jamais dans le code).
// - Flux SECONDAIRE (ESPN scoreboard, public, sans clé) : ~30 ligues, déjà
//   prouvé par `fetch-espn`. PAS de SofaScore/Flashscore ni parser HTML :
//   endpoints non contractuels/protégés (probes : 403, JS-only) — voir DECISIONS.
// - Sortie : UN SEUL modèle JSON unifié (scores confirmés uniquement).
//
// Fichier AUTO-PORTÉ (deploy Dashboard OK). Secrets : SUPABASE_URL,
// SUPABASE_SERVICE_ROLE_KEY (+ API_FOOTBALL_KEY pour le top-up premium).
// Mise en route côté SQL : appliquer `migrations/0002_live_scores.sql`
// (tables live_scores_cache + api_quota_day, RLS sans policy = service_role seul).
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CACHE_KEY = "live:all";
const CACHE_TTL_MS = 90_000; // 90 s partagées par tous les téléphones
const LIVE_DAILY_CAP = 40; // appels premium/jour max (100 - synchros)

// Compétitions premium servies par le top-up API-Football (IDs v3).
const PREMIUM_LEAGUE_IDS = new Set([1, 2, 3, 4, 5, 13, 36, 848]);

// Flux secondaire ESPN : slug site.api.espn.com → leagueId (référentiel app).
// Slugs incertains simplement signalés dans `errors` (zéro impact).
const ESPN_LEAGUES: Array<{ leagueId: number; espn: string }> = [
  { leagueId: 39, espn: "eng.1" },
  { leagueId: 140, espn: "esp.1" },
  { leagueId: 135, espn: "ita.1" },
  { leagueId: 78, espn: "ger.1" },
  { leagueId: 61, espn: "fra.1" },
  { leagueId: 94, espn: "por.1" },
  { leagueId: 88, espn: "ned.1" },
  { leagueId: 2, espn: "uefa.champions" },
  { leagueId: 3, espn: "uefa.europa" },
  { leagueId: 848, espn: "uefa.conference" },
  { leagueId: 13, espn: "conmebol.libertadores" },
  { leagueId: 71, espn: "bra.1" },
  { leagueId: 253, espn: "usa.1" },
  { leagueId: 128, espn: "arg.1" },
  { leagueId: 203, espn: "tur.1" },
  { leagueId: 144, espn: "bel.1" },
  { leagueId: 179, espn: "sco.1" },
  { leagueId: 98, espn: "jpn.1" },
  { leagueId: 169, espn: "chn.1" },
  { leagueId: 292, espn: "kor.1" },
  { leagueId: 197, espn: "gre.1" },
  { leagueId: 119, espn: "den.1" },
  { leagueId: 103, espn: "nor.1" },
  { leagueId: 113, espn: "swe.1" },
  { leagueId: 207, espn: "sui.1" },
  { leagueId: 106, espn: "pol.1" },
  { leagueId: 333, espn: "ukr.1" },
  { leagueId: 210, espn: "cro.1" },
  { leagueId: 345, espn: "cze.1" },
  { leagueId: 283, espn: "rou.1" },
  { leagueId: 271, espn: "hun.1" },
  { leagueId: 218, espn: "aut.1" },
  { leagueId: 307, espn: "sau.1" },
  { leagueId: 188, espn: "aus.1" },
  { leagueId: 233, espn: "egy.1" },
  { leagueId: 383, espn: "isr.1" },
  { leagueId: 357, espn: "irl.1" },
  { leagueId: 172, espn: "bul.1" },
  { leagueId: 318, espn: "cyp.1" },
  { leagueId: 286, espn: "srb.1" },
];

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

type Unified = {
  match_id: string;
  league_id: number;
  season: number | null;
  status_short: string; // NS | LIVE | 1H | HT | 2H | ET | FT | PST | CANC
  status_detail: string | null;
  date_ts: number;
  home: { id: string | null; name: string; logo: string | null };
  away: { id: string | null; name: string; logo: string | null };
  goals_home: number | null;
  goals_away: number | null;
  elapsed: number | null;
  source: string;
};

function normalizeTeamName(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function seasonOf(dateTs: number): number {
  const d = new Date(dateTs * 1000);
  const month = d.getUTCMonth() + 1;
  const year = d.getUTCFullYear();
  return month >= 8 ? year : year - 1;
}

function intOrNull(v: unknown): number | null {
  if (typeof v === "number" && Number.isFinite(v)) return Math.trunc(v);
  if (typeof v === "string" && v !== "") {
    const n = parseInt(v, 10);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function todayUTC(): string {
  return new Date().toISOString().slice(0, 10);
}

// Statuts API-Football v3 → codes canoniques app.
function mapApiStatus(short: string | null): string {
  switch ((short ?? "").toUpperCase()) {
    case "1H": return "1H";
    case "HT": return "HT";
    case "2H": return "2H";
    case "ET": return "ET";
    case "BT": return "HT";
    case "P": return "P";
    case "SUSP": return "INT";
    case "INT": return "INT";
    case "FT": case "AET": case "PEN": return "FT";
    case "PST": return "PST";
    case "CANC": case "ABD": case "AWD": case "WO": return "CANC";
    default: return "LIVE";
  }
}

// Détail ESPN → codes canoniques (state "in" uniquement en entrée).
function mapEspnDetail(detail: string | null | undefined, period: number | null | undefined): string {
  const d = (detail ?? "").toUpperCase();
  if (d.includes("HT")) return "HT";
  if (d.includes("ET") || d.includes("EXTRA")) return "ET";
  if (d.includes("PEN") || d === "P") return "P";
  if (period === 1) return "1H";
  if (period === 2) return "2H";
  return "LIVE";
}

function parseClock(detail: string | null | undefined): number | null {
  const m = /(\d+)\s*['’]/.exec(detail ?? "");
  return m ? intOrNull(m[1]) : null;
}

async function fetchPremium(apiKey: string): Promise<Unified[]> {
  const res = await fetch("https://v3.football.api-sports.io/fixtures?live=all", {
    headers: { "x-apisports-key": apiKey, "Accept": "application/json" },
  });
  if (!res.ok) throw new Error(`api-football HTTP ${res.status}`);
  const data = await res.json();
  const list = Array.isArray(data?.response) ? data.response : [];
  const out: Unified[] = [];
  for (const fx of list) {
    const leagueId = intOrNull(fx?.league?.id);
    if (leagueId == null || !PREMIUM_LEAGUE_IDS.has(leagueId)) continue;
    const dateTs = Math.floor(new Date(fx?.fixture?.date ?? 0).getTime() / 1000);
    if (!Number.isFinite(dateTs) || dateTs <= 0) continue;
    const home = fx?.teams?.home ?? {};
    const away = fx?.teams?.away ?? {};
    if (typeof home.name !== "string" || typeof away.name !== "string") continue;
    out.push({
      match_id: `api:${fx?.fixture?.id ?? `${leagueId}-${dateTs}`}`,
      league_id: leagueId,
      season: intOrNull(fx?.league?.season),
      status_short: mapApiStatus(fx?.fixture?.status?.short ?? null),
      status_detail: typeof fx?.fixture?.status?.short === "string" ? fx.fixture.status.short : null,
      date_ts: dateTs,
      home: { id: home.id != null ? `api:${home.id}` : null, name: home.name, logo: home.logo ?? null },
      away: { id: away.id != null ? `api:${away.id}` : null, name: away.name, logo: away.logo ?? null },
      goals_home: intOrNull(fx?.goals?.home),
      goals_away: intOrNull(fx?.goals?.away),
      elapsed: intOrNull(fx?.fixture?.status?.elapsed),
      source: "api-football",
    });
  }
  return out;
}

async function fetchEspnLeague(espn: string, leagueId: number, errors: string[]): Promise<Unified[]> {
  const out: Unified[] = [];
  try {
    const res = await fetch(
      `https://site.api.espn.com/apis/site/v2/sports/soccer/${espn}/scoreboard?limit=100`,
      { headers: { "User-Agent": UA, "Accept": "application/json" } },
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const events = Array.isArray(data?.events) ? data.events : [];
    // v1.3.3 : on remonte aussi les TERMINÉS DU JOUR (state "post") — même
    // payload, zéro quota supplémentaire. L’app y trouve ses bascules FT
    // rapides sans appeler football-data.org en direct.
    const dayStart = Date.now() - 24 * 3600 * 1000;
    for (const raw of events) {
      const e = raw as Record<string, unknown>;
      const state = ((e.status as Record<string, unknown> | undefined)?.type as Record<string, unknown> | undefined)?.state;
      if (state !== "in" && state !== "post") continue; // ni en cours ni fini → ignoré
      const type = (e.status as Record<string, unknown> | undefined)?.type as Record<string, unknown> | undefined;
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
      const isLive = state === "in";
      // Terminés : aujourd’hui seulement (pas tout l’historique).
      if (!isLive && dateTs * 1000 < dayStart) continue;
      const detail = typeof type?.shortDetail === "string" ? type.shortDetail : null;
      const period = intOrNull((e.status as Record<string, unknown> | undefined)?.period);
      const logoOf = (c: Record<string, unknown> | undefined) => {
        const logos = ((c?.team as Record<string, unknown> | undefined)?.logo) as unknown;
        if (typeof logos === "string") return logos;
        if (Array.isArray(logos) && typeof logos[0] === "string") return logos[0] as string;
        return null;
      };
      out.push({
        match_id: `espn:${leagueId}-${normalizeTeamName(homeName)}-${normalizeTeamName(awayName)}-${dateTs}`,
        league_id: leagueId,
        season: seasonOf(dateTs),
        status_short: isLive ? mapEspnDetail(detail, period) : "FT",
        status_detail: detail,
        date_ts: dateTs,
        home: { id: null, name: homeName, logo: logoOf(home) },
        away: { id: null, name: awayName, logo: logoOf(away) },
        goals_home: intOrNull(home?.score),
        goals_away: intOrNull(away?.score),
        elapsed: parseClock(detail),
        source: "espn",
      });
    }
  } catch (err) {
    errors.push(`${espn}: ${(err as Error).message}`);
  }
  return out;
}

function dedupe(matches: Unified[]): Unified[] {
  const seen = new Set<string>();
  const out: Unified[] = [];
  for (const m of matches) {
    const key = `${m.league_id}|${normalizeTeamName(m.home.name)}|${normalizeTeamName(m.away.name)}|${Math.floor(m.date_ts / 43200)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(m);
  }
  return out;
}

const CORS = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  // Garde anti-abus légère : la clé anon Supabase est exigée (comme /rest/v1).
  if (!req.headers.get("authorization") && !req.headers.get("apikey")) {
    return new Response(JSON.stringify({ error: "missing credentials" }), { status: 401, headers: CORS });
  }
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    return new Response(JSON.stringify({ error: "server misconfigured" }), { status: 500, headers: CORS });
  }
  const client = createClient(url, key);
  const nowMs = Date.now();
  const errors: string[] = [];
  let quotaRemaining: number | null = null;

  // 1. Cache partagé (90 s) : 1 réponse sert tous les téléphones.
  try {
    const { data: cached } = await client
      .from("live_scores_cache")
      .select("payload,source,updated_at")
      .eq("cache_key", CACHE_KEY)
      .maybeSingle();
    if (cached && nowMs - (cached.updated_at as number) < CACHE_TTL_MS) {
      return new Response(
        JSON.stringify({ matches: (cached.payload as { matches?: unknown })?.matches ?? [], cached: true, source: cached.source, errors }),
        { headers: CORS },
      );
    }
  } catch (_) { /* cache illisible → on recalcule */ }

  // 2. Top-up premium (quota journalier partagé, facturé uniquement en 2xx).
  let premium: Unified[] = [];
  const apiKey = Deno.env.get("API_FOOTBALL_KEY");
  const day = todayUTC();
  if (apiKey) {
    try {
      const { data: row } = await client.from("api_quota_day").select("live_calls").eq("day", day).maybeSingle();
      const used = (row?.live_calls as number | undefined) ?? 0;
      quotaRemaining = Math.max(0, LIVE_DAILY_CAP - used);
      if (used < LIVE_DAILY_CAP) {
        premium = await fetchPremium(apiKey);
        await client.from("api_quota_day").upsert({ day, live_calls: used + 1 }, { onConflict: "day" });
        quotaRemaining = Math.max(0, LIVE_DAILY_CAP - used - 1);
      } else {
        errors.push("quota api-football épuisé (cache + ESPN seuls)");
      }
    } catch (e) {
      errors.push(`api-football: ${(e as Error).message}`);
    }
  } else {
    errors.push("API_FOOTBALL_KEY non configurée (top-up premium désactivé)");
  }

  // 3. Flux secondaire ESPN (gratuit, sans clé).
  let secondary: Unified[] = [];
  for (const lg of ESPN_LEAGUES) {
    secondary.push(...await fetchEspnLeague(lg.espn, lg.leagueId, errors));
  }

  // 4. Fusion premium prioritaire + écriture cache (best-effort).
  const premiumKeys = new Set(premium.map((m) =>
    `${m.league_id}|${normalizeTeamName(m.home.name)}|${normalizeTeamName(m.away.name)}|${Math.floor(m.date_ts / 43200)}`
  ));
  const merged = dedupe([
    ...premium,
    ...secondary.filter((m) =>
      !premiumKeys.has(`${m.league_id}|${normalizeTeamName(m.home.name)}|${normalizeTeamName(m.away.name)}|${Math.floor(m.date_ts / 43200)}`)
    ),
  ]);
  try {
    await client.from("live_scores_cache").upsert(
      { cache_key: CACHE_KEY, payload: { matches: merged }, source: "api-football+espn", updated_at: nowMs },
      { onConflict: "cache_key" },
    );
  } catch (_) { /* lecture seule malgré tout */ }

  return new Response(
    JSON.stringify({ matches: merged, cached: false, quota_remaining: quotaRemaining, errors }),
    { headers: CORS },
  );
});
