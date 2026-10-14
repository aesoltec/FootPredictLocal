# FootPredict Local — Dixon-Coles + Ensemble Learning

Application Android de prédictions football **offline-first**, open-source, multi-sources.

## Modèle statistique
- **Dixon-Coles** (Poisson + correction τ + time decay ξ=0.0016)
- **Ensemble Learning** 3 modèles : Dixon-Coles · MarketModel · EloRating
- Mesure de désaccord + value bets (edge ≥ 10 %)

## Sources de données

### API officielles (app Android via Ktor)
| Source | Rôle | Reliability |
|--------|------|-------------|
| API-Football v3 | Fixtures, scores, stats basiques | 0.80 |
| Football-Data.org | Compétitions majeures, classements | 0.75 |
| The Odds API (odds-api.io) | Cotes bookmakers licence | 0.85 |

### Scraping backend uniquement (Supabase Edge Functions — cron)
| Source | Rôle | Reliability | Dossier |
|--------|------|-------------|---------|
| **Understat** | xG, xGA, fixtures, agrégats saison | 0.90 (xG) / 0.60 (fixtures) | `scrape-understat` |
| **ESPN** | Fixtures courantes 5 ligues (nourrit `scraped_fixtures`) | via scraping | `fetch-espn` |
| **OpenLigaDB** | Bundesliga + C1, gratuit DFB (nourrit `scraped_fixtures`) | via scraping | `fetch-openligadb` |
| **FBref** | Stats avancées (tirs, passes…) | 0.80 | `scrape-fbref` |
| **OddsPortal** | Cotes multi-book, opening/closing | 0.70 | `scrape-oddsportal` |
| **OddsMath** | Dropping odds / steam moves | 0.65 | `fetch-oddsmath` |
| **Betwatch.fr** | Moneyway Betfair (volumes €) | 0.75 | `scrape-betwatch` |
| **BetExplorer** | Historique cotes profond | 0.72 | `scrape-betexplorer` |
| **Oddspedia** | Dropping odds + surebets | 0.70 | `scrape-oddspedia` |
| **OddsChecker** | Multi-book (forte couverture UK), best odds | 0.72 | `scrape-oddschecker` |

⚠️ Le scraping **ne tourne jamais dans l’app Android**.  
Les Edge Functions poussent dans des tables Supabase ; l’app consomme via REST.

### Sources recommandées en plus (déjà intégrées comme placeholders)
- **BetExplorer** — meilleur historique gratuit pour backtests / closing-line value  
- **Oddspedia** — dropping odds + arbitrage, bon complément moderne d’OddsPortal  
- **OddsChecker** — très large couverture bookmakers (surtout UK) ✅ intégré  
- Préférer l’**API officielle The Odds API** dès que possible (légal, stable)

## Stack
Kotlin 2.0+ · Compose Material 3 · Room · Hilt · Ktor 2.3+ · Clean Architecture

## Production (v1.0.0)
- **APK signée** : `app/build/outputs/apk/release/app-release.apk` (1,8 Mo, R8, v1.0.0)
  → installation directe (autoriser les sources inconnues).
- **AAB Play Store** : `app/build/outputs/bundle/release/app-release.aab` (3,5 Mo).
- **Clé de signature** : `keystore/footpredict-release.jks` (RSA 2048, 10 000 jours),
  mots de passe dans `keystore.properties` (**ne jamais committer ni perdre** :
  sans eux, aucune mise à jour Play n’est possible).
- Rebuild release : `./gradlew :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease`
  (voir `RELEASE_NOTES_v1.0.0.md`).

## Mode zéro-clé API (scraping)
Sans aucune clé payante, l'app fonctionne via le backend Supabase gratuit :
voir `supabase/README.md` (migration SQL → deploy `scrape-understat` →
cron 6h → `SUPABASE_URL`/`SUPABASE_ANON_KEY` dans `local.properties`).
Sans `ODDS_API_KEY`, le modèle Marché se désactive et l'Ensemble
redistribue sur Dixon-Coles + Elo (value bets indisponibles).

## Configuration
Remplacer dans `app/build.gradle.kts` :
- `API_FOOTBALL_KEY`, `FOOTBALL_DATA_KEY`, `ODDS_API_KEY`
- `SUPABASE_URL`, `SUPABASE_ANON_KEY`

## Licence
MIT
