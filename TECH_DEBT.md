# Dette technique & couverture vs spec « 3 Dixon Coles.md »

> Mise à jour : 2026-10-14 (cycle audit général v1.3.1 — voir § « Cycle v1.3.x » en fin).
> Historique 2026-10-09 conservé tel quel ci-dessous.

## Traité dans ce sprint
- [x] P0-1 Gradle Wrapper 8.9 : `gradle/wrapper/gradle-wrapper.properties` + stubs `gradlew`/`gradlew.bat`.
      Reste : lancer `gradle wrapper --gradle-version 8.9` sur une machine avec Gradle pour générer `gradle-wrapper.jar`.
- [x] P0-2 Compose Compiler Kotlin 2.0 : plugin `org.jetbrains.kotlin.plugin.compose` 2.0.21 (root + app),
      suppression de `kotlinCompilerExtensionVersion = "1.5.15"`.
- [x] P0-3 Clés API : lecture `local.properties` (fallback env puis `YOUR_*`) + `local.properties.example`.
- [x] P0-4 `OddsApiIoDataSource.fetchOdds` : résolution par noms d'équipes via `LocalDataSource.getMatchById`
      + mapping ligue → sportKey The Odds API, garde-fou clé `YOUR_*`, dégradation gracieuse (null).
- [x] P0-5a Elo : `expApprox` Taylor supprimé, `kotlin.math.exp` réel (`EloRatingUseCase`).
- [x] P0-5b Divergence : `DivergenceLevel` aligné sur paliers confiance → LOW <0.03 / MEDIUM <0.15 / HIGH sinon.
- [x] P1-3 Tests : `EnsemblePredictionUseCaseTest` (6 tests) + `CalculateLiveRestOfMatchUseCaseTest` (4 tests).
- [x] P1-4a Cache 6h : `MatchRepositoryImpl` ne tape le réseau que si cache vide ou >6h (économie quota).
- [x] Room `fallbackToDestructiveMigration()` déjà présent (`di/Modules.kt`).

## Restant (priorisé)

### P0 — bloquant prod
1. ✅ **Clés** : `local.properties` (ignoré par git) avec Supabase réel + clés
   personnelles (`api et ref.md` nettoyé). Sources sans clé auto-désactivées.
2. **Bootstrapper le wrapper** : `gradle wrapper --gradle-version 8.9` pour générer le jar manquant.
3. ✅ **Vérifié 2026-10-09** : `BUILD SUCCESSFUL` — `:app:testDebugUnitTest` (**41/41** pass) +
   `:app:assembleDebug`, installé et lancé (téléphone + émulateur, pas de crash).
   Preuve base embarquée (pull via run-as) : 380 matchs PL 2026 (50 FT + 330 NS),
   20 team_stats, 20 Elos de 1469 à 1545, odds 0 (attendu sans ODDS_API_KEY).
   Gradle 8.9 + JBR 21 + SDK android-35.
4. ✅ **Fix « Aucune source disponible »** : l’agrégateur préférait la source la plus
   fiable même vide → préfère la plus fiable NON VIDE + test de non-régression.
5. ✅ **Prédictions différenciées** : Dixon-Coles sur 10 derniers matchs pondérés
   time decay (`RecentMatch`, repli agrégats puis neutre) ; Elo après chaque FT
   (K=20, sync déterministe persistée). Tri à venir→terminés, filtres 5 ligues +
   recherche + statut, libellés explicites.
6. ✅ **Maquette « les prediction.md »** : BTTS calculé (matrice DC + live, exposé
   Ensemble, affiché côté fort) ; prédictions limitées aux 3 prochains ; carte =
   1X2 + O/U fort + BTTS fort + une ligne `confiance:` (meilleur pick tous marchés) ;
   sections Top 5 vert / À-éviter orange ; alertes confiance bleues ; plafond 12 ;
   arrondis uniformes. Vérifié par captures (ex. Under 82 %, BTTS-No 60 % en picks).
7. ✅ **Contradiction tops (QA capture)** : Top Confiance et À-éviter affichaient
   les mêmes matchs. Exclusion mutuelle (`toAvoid(exclude=…)`), vivier de 10
   (cartes détaillées toujours 3 max + live), tops masqués en filtre « Terminés ».
   Vérifié par capture : 5 orange distincts (divergence / confiance faible),
   43/43 tests.

### P1 — qualité
4. ✅ **Time decay réel** FAIT (voir P0-5).
5. **Quota tracker** par source (rate limits API-Football / Odds-API) — règle 6h déjà en place, tracker restant.
6. ✅ **Elo update** FAIT (voir P0-5).
7. **Settled predictions pipeline** : auto-settle quand status = FT + calcul performance dans ViewModel.

### P2 — enrichissement
8. ✅ Backend prod déployé (projet gipvwromtsqosgmvzamq) : `scrape-understat`
   auto-portée (endpoint `getLeagueData`, 10 ligues-saisons 2025+2026 :
   3504 fixtures / 192 équipes, Invoke `ok` vérifié) + `fetch-espn` (5 ligues,
   0 doublon) + `fetch-openligadb` (Bundesliga + C1) + migration
   `0001_scraped_schema.sql` appliquée + `GRANT SELECT` anon + RLS.
   Déduplication inter-sources : noms (alias + Jaccard) + fenêtre 7 jours
   (décalages de mise à jour) — vérifié 306/306 Bundesliga, C1 intacte.
   Cron pg_cron ACTIF en base (voir supabase/README section Cron).
9. ✅ **Tops marchés (maquette v2)** : Top Over/Under/BTTS-Yes/BTTS-No (2 max,
   cartes neutres), tops/éviter affichés 3 max. Vérifié par capture
   (Over 98 %/96 %, Under 98 %). 44/44 tests.
10. ✅ **Tier 1 C1** : Ligue des Champions au menu (forme récente + Elo, sans
   agrégats/xG — assumé). **Moteur Kotlin v2** (`scrapers/`) : `TeamAliases`
   (trio PSG testé), `aggregateMarkets` (vote 1X2 + Over/Under en %, types
   `MarketConsensus`/`MarketPick`/`OverUnderSide`), runner parallèle,
   FotMob x-mas via env, Understat double mode XHR+Regex.
11. `FootballDataOrg` standings endpoint.
12. ✅ **Tier 2 vérifié sur téléphone (v1.1.0)** : fixtures FD (noms officiels
    « Arsenal FC… ») prioritaires, **1er value bet réel « Edge : 17,0 % »**
    (Marché actif via Odds API), vocabulaire simple affiché. 51 + 27 tests.
12. Notifications système pour alertes HIGH.
13. ✅ **Dashboard UI/UX** : `MatchPredictionCard` (O/U + Double Chance +
   BTTS + badge confiance vert >70 % / orange 55–70 %), `PredictionDashboardScreen`
   + `MatchPredictionViewModel` renommés, curseur confiance 0–80 %. Vérifié par
   capture (value bets 24/16/15 % = Marché live). 55 + 27 tests.
14. Graphique d'évolution live (snapshots) dans l'UI — données déjà collectées dans ViewModel.
15. ✅ **Couche finale v1.2.0** : fenêtres 15j/7j persistées, filtres à validation
    simultanée (statut + DateRangePicker + ligue + confiance + réglages),
    `StatsScreen` (hit rate mesuré 50,8 %) + CSV 16 colonnes exactes (BOM, validé
    octet par octet, destination au choix via sélecteur système SAF),
    `PredictionTriggerWorker` Hilt (Top 5 + conf ≥ 70 % + value ≥ 15 %, 2/jour).
    93 tests (66 + 27), lint 0 erreur, R8 + AAB signés.

## Mapping spec → code

| Section spec | Statut |
|--------------|--------|
| 1 Packages | ✅ |
| 2 Room entities/DAOs | ✅ (+ SettledPrediction) |
| 3 RemoteDataSources | ✅ API-Football, FD.org, Odds-API, Supabase xG/Odds |
| 4 LocalDataSource + Mappers | ✅ (+ getMatchById) |
| 5 EntityResolver + Aggregator | ✅ |
| 6 Repository offline-first | ✅ (cache 6h) |
| 7 Domain models | ✅ |
| 8 Dixon-Coles | ✅ (time decay 10 matchs + BTTS) |
| 9 Market + Elo | ✅ (exp réel + update FT persisté) |
| 10 Ensemble | ✅ (divergence alignée + BTTS) |
| 11–12 Presentation | ✅ (filtres, top 5/à-éviter, BTTS, tri) |
| 13 Hilt + Gradle | ✅ (wrapper stub, compose 2.0) |
| 14 Edge Functions | ✅ (scrape-understat prod ; odds en placeholders) |
| 15 Tests | ✅ (app 49 + scrapers 23, 100 % hors-ligne) |

## Cycle v1.3.x (2026-10-13/14, audit général)
- **État** : `:app:testDebugUnitTest` + `:scrapers:test` verts — **135 tests
  (108 app + 27 scrapers)** ; `compileDebugKotlin` OK ; `assembleRelease` verts
  répétés (R8, 2,3 Mo) ; 0 TODO/FIXME/`unwrap`/`expect` en prod (grep).
- **v1.3.0** : bottom-bar (Coupon/Matchs/Terminés/Stats/Réglages), `DailyCouponScreen`
  (seuils conf ≥ 65 % + edge > 0), `CornersPrediction` (statistique pure),
  thème Slate + Bleu Électrique, bankroll Fixe/Kelly 5 %, CSV 17 col
  (`simulation_gain_10e`), `TeamLogoStore` (blasons par club), pull-to-refresh,
  `rememberSaveable` onglet marché, synchro live (scores + NS→live + FT rapide).
- **v1.3.1 (audit externe)** : `marketHit()` — statut jugé sur le marché proposé
  (fini l’inversion BTTS) ; soldés figés + migration unique ; migration Room 4→5
  non destructive (`predictedMarket`, `bookmakerOdds` figée) ; Terminés rejoués
  multi-marchés ; export SAF depuis tous les onglets ; langage courant + marchés
  séparés par onglet ; `StringsFormatTest` (garde-fou `%`, 3 FATAL Réglages corrigés).
- **Dette soldée ce cycle** : 3 `!!` à risque de crash (agrégateur hors-ligne vide,
  `fetchMergedOdds` vide) + 2 hygiène (`OddsMovementGraph`, `edgeOnPredicted`) ;
  `CouponScreen` mort supprimé (+ imports) ; déduplication inter-synchros
  (`isSameFixture`/`dedupeFixtures`, doublons « Hull »/« Hull City AFC » constatés).
- **Dette restante connue** : lignes soldées antérieures sans cote (non récupérable,
  vide honnête) ; doublons déjà en base non purgés (pas d’orphelins : on garde
  l’existant, on bloque les nouveaux) ; BTTS-No 33 % vrai + Under 30 % + confiance
  non discriminante → re-calibration à décider sur données (pas à l’aveugle) ;
  `MONEYWAY` sans producteur (inoffensif) ; wrapper jar manquant (stub, inchangé).
- **Suivi backend (hors app)** : étendre le scraping aux ~34 nouvelles compétitions
  du registre (`fetch-espn` couvre por.1/ned.1/uefa.champions/uefa.europa/bra.1/usa.1…,
  gratuit sans clé) : nouvelles entrées LEAGUES + cron + vérification doublons ;
  revalider les IDs API-Football via `/leagues`.
