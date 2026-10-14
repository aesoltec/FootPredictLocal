# FootPredict Local 1.1.0 — Notes de release

Tier 2 activé : clés gratuites + sources officielles.

## Nouveau
- **football-data.org** (clé gratuite) : fixtures en direct + classements →
  stats d’équipe (référentiel partagé `FdCompetitions`, throttle auto via headers).
- **The Odds API** (500 crédits/mois) : cotes 1X2 + Over/Under → modèle Marché
  actif, cache 6 h par ligue (1 crédit/ligue, repli cache périmé).
- **Moteur Kotlin v2** (`scrapers/`) : `TeamAliases` (+PSG & co),
  `aggregateMarkets` (vote 1X2 + Over/Under en %), `ScraperRunner`,
  `FootballDataOrgScraper`, `OddsApiScraper`, FotMob x-mas via env,
  Understat double mode XHR + Regex legacy.
- C1 au menu (forme récente + Elo).

## Mises en garde conservees (v1.0.0)
Voir `RELEASE_NOTES_v1.0.0.md`. Rappel cron Supabase `0 */6 * * *`.
Clés dans `local.properties` (ignoré par git) — ne jamais committer.
