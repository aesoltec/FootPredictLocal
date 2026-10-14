// Helpers partagés — schéma d'IDs stable entre les Edge Functions et l'app.
// Doit rester synchronisé avec `ScrapedIds.kt` côté Android (vecteurs de test
// documentés en bas de fichier).

/** Normalisation d'un nom d'équipe (minuscules, espaces simples, ASCII de base). */
export function normalizeTeamName(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Hash 32 bits signé, compatible `String.hashCode()` (Java/Kotlin).
 * Utilisé pour `unified_team_id` et `match_id` afin que l'app Room
 * (PK integer) et Supabase partagent le même espace d'IDs.
 */
export function javaStringHash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}

export function unifiedTeamId(teamName: string): number {
  return javaStringHash(normalizeTeamName(teamName));
}

export function scrapedMatchId(
  homeName: string,
  awayName: string,
  dateTs: number,
): number {
  return javaStringHash(
    `${normalizeTeamName(homeName)}|${normalizeTeamName(awayName)}|${dateTs}`,
  );
}

// ── Mapping ligues ─────────────────────────────────────────────────────
// leagueId côté app (référentiel API-Football) ↔ slug Understat.
export const LEAGUES: Array<{
  leagueId: number;
  understat: string;
  season: number;
}> = [
  { leagueId: 39, understat: "EPL", season: 2025 },
  { leagueId: 140, understat: "La_liga", season: 2025 },
  { leagueId: 135, understat: "Serie_A", season: 2025 },
  { leagueId: 78, understat: "Bundesliga", season: 2025 },
  { leagueId: 61, understat: "Ligue_1", season: 2025 },
];

// Vecteurs de test (doivent matcher `ScrapedIdsTest.kt`) :
//   normalizeTeamName("Manchester United") === "manchester united"
//   javaStringHash("abc") === 96354
//   javaStringHash("") === 0
