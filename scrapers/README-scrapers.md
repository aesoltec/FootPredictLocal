# Module `scrapers` — moteur de scraping multi-sites (Kotlin/JVM pur)

Outil d’analyse : scrape 15 sites **en parallèle** (coroutines `async`/`awaitAll`),
isole les échecs (`supervisorScope` + timeout par site), puis vote
(`PredictionEngine` : majorité pondérée par fiabilité).

```bash
./gradlew :scrapers:test   # 100 % hors-ligne (samples inline, aucun réseau)
```

## Matrice de faisabilité (sondes réelles, pas de promesses)

| # | Site | Statut | Preuve / motif |
|---|---|---|---|
| 1 | FBref | ❌ BLOQUÉ | HTTP 403 mesuré 2026-10-09 |
| 2 | Understat | ✅ SUPPORTÉ | endpoint `getLeagueData` vérifié |
| 3 | FotMob | ⚠️ PARTIEL | endpoint JSON interne, header `x-mas` rotatif via `FOTMOB_X_MAS` (jamais codé en dur) |
| 4 | SofaScore | ❌ BLOQUÉ | Cloudflare + API non contractuelle |
| 5 | WhoScored | ❌ BLOQUÉ | login Opta + Cloudflare |
| 6 | Transfermarkt | ❌ BLOQUÉ | blocage datacenter + ToS |
| 7 | FootyStats | ❌ BLOQUÉ | paywall (extraits tronqués) |
| 8 | Football-Data.co.uk | ✅ SUPPORTÉ | CSV gratuits (historique + clôture) |
| 9 | OddsPortal | ❌ BLOQUÉ | rendu JS intégral |
| 10 | SoccerStats | ⚠️ PARTIEL | HTML 200, structure `#btable` confirmée 2026-10-12, sélecteurs non vérifiés bout en bout |
| 11 | Flashscore | ❌ BLOQUÉ | rendu dynamique + API signée |
| 12 | The Analyst | ❌ BLOQUÉ | pas d’endpoint données gratuit |
| 13 | StatsBomb GitHub | ✅ SUPPORTÉ | open-data (historique uniquement) |
| 14 | Squawka | ❌ BLOQUÉ | JS + paywall |
| 15 | WinDrawWin | ❌ BLOQUÉ | HTTP 403 mesuré 2026-10-12 |

## Architecture

- `Models.kt` — `MatchData`, `TeamStats`, `Prediction`, `Outcome`, `ScrapeResult`
- `FootballScraper.kt` — interface + `BaseHttpScraper` (UA rotatifs, délai
  0,5–2 s, intervalle/hôte 1 s, timeouts, 1 retry 429/5xx) + `Feasibility`
- `MatchKeys.kt` — clé universelle `HOME|AWAY|AAAA-MM-JJ` + normalisation
- `ScrapingOrchestrator.kt` — parallélisme isolé (bloqués exclus par défaut)
- `PredictionEngine.kt` — vote pondéré + moyennes O/U/BTTS + avertissements
- `impl/` — 3 implémentations réelles + 1 partielle + 11 bloquées documentées

## Usage

```kotlin
// v1 : données brutes multi-sites en parallèle
val results = ScrapingOrchestrator().scrapeAll("Arsenal|Chelsea|2025-08-15", AllScrapers.all())
val final = PredictionEngine().aggregate(results)

// v2 : pronostics standardisés + vote de tendance
val meta = MatchMetadata("Arsenal", "Chelsea", "2025-08-15")
val preds = ScraperRunner().runAll(meta, AllScrapers.all())
val agg = PredictionEngine().aggregatePredictions(meta, preds)
// agg?.outcome, agg?.confidence, agg?.trendByOutcome[Outcome.HOME], agg?.warnings
```

## API v2 (contrats demandés)

- `ApiModels.kt` — `MatchMetadata` (+`toMatchId()`), `ScraperPrediction`,
  `AggregatedPrediction` (tendance par issue), `TeamAliases` (synonymes).
- `FootballScraper.predict(metadata)` — défaut null ; implémenté par
  Understat (champ maison `forecast`), Football-Data (cotes de clôture →
  implicites), FotMob (cotes si présentes).
- `ScraperRunner` — `async`/`awaitAll` + timeout 20 s/site, échecs ignorés.
- `PredictionEngine.aggregatePredictions` — vote pondéré, tendance normalisée.
- Understat double mode : XHR `getLeagueData` (vérifié) puis fallback Regex
  `<script>` legacy (`extractLegacyDatesData`, testé hors-ligne).
- FotMob : header `x-mas` via `FOTMOB_X_MAS` (jamais codé en dur), endpoints
  non contractuels parsés en défensif, abstention propre si 403/périmé.

## Éthique / ToS

Scraper = charge sur des serveurs tiers + souvent contraire aux CGU
(Transfermarkt, WhoScored, Flashscore l’interdisent explicitement).
Recommandé : usage personnel modéré (délais intégrés), `robots.txt` respecté,
préférer les sources ouvertes (StatsBomb, Football-Data.co.uk, OpenLigaDB,
API officielles gratuites : football-data.org, The Odds API 500 req/mois).
