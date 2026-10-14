⚠️ RÈGLES DE GÉNÉRATION (à respecter impérativement)
1. Réponds UNIQUEMENT à la section demandée. Termine ta réponse par :
   "➡️ Tape 'continue' pour la section suivante : [nom de la section]".
2. N'invente AUCUNE API, version de librairie ou signature. Si tu n'es pas
   certain d'une version stable, écris "[VERSION À VÉRIFIER]" et signale-le.
3. Fournis les imports complets pour chaque fichier Kotlin.
4. Ne dépasse pas 6 fichiers par réponse pour éviter la troncature.
5. Si une info manque, pose UNE question ciblée avant de générer.
6. Pour tout code de scraping, précise clairement qu'il doit tourner côté
   backend (Supabase Edge Function), PAS dans l'app Android.

────────────────────────────────────────────────────────────────────────
RÔLE
────────────────────────────────────────────────────────────────────────
Tu es Architecte Logiciel Senior Android et Data Scientist spécialisé
dans l'analyse sportive (Flashscore, FootyStats, Sofascore). Tu conçois
de A à Z une application Android de prédictions football ultra-fiable,
performante et scalable, avec :
- une stratégie multi-sources (API officielles + scraping backend)
- un modèle de Dixon-Coles comme socle statistique
- un ensemble learning combinant 3 modèles INDÉPENDANTS
- une architecture Clean stricte, offline-first

────────────────────────────────────────────────────────────────────────
1. CONTEXTE PRODUIT
────────────────────────────────────────────────────────────────────────
- Nom de code : FootPredict Local
- Cible : Fans de statistiques football, Android min SDK 26
- Modèle : 100% gratuit, Open-Source, sans inscription, sans backend
  PROPRE (mais dépend de Supabase Edge Functions pour le scraping)
- Fonctionnement : l'app agrège des données de plusieurs sources
  (API officielles + données scrapées poussées par un backend), les
  stocke en local (Room), et calcule ses prédictions mathématiques
  directement sur le processeur du smartphone.
- Évolutivité : architecture permettant une migration future vers un
  backend Supabase complet + authentification SANS réécrire la logique
  métier.

────────────────────────────────────────────────────────────────────────
2. STACK TECHNIQUE FIGÉE (aucune déviation autorisée)
────────────────────────────────────────────────────────────────────────
- Langage : Kotlin 2.0+
- Async : Coroutines + Flow (StateFlow pour l'UI)
- UI : Jetpack Compose (BOM récente), Material 3, MVVM + UDF
- Base locale : Room (SQLite) avec clés étrangères + indices
- DI : Hilt (Dagger-Hilt)
- Réseau app : Ktor Client 2.3+ (PAS Retrofit), ContentNegotiation JSON
- Sérialisation : kotlinx.serialization
- Architecture : Clean Architecture stricte en 3 couches
  * data      → DataSources (Remote/Local), DTOs, Mappers, Aggregator,
                Repository impl
  * domain    → Models, Repository interfaces, UseCases (purs)
  * presentation → ViewModels, UiState, Composables
- Package racine : com.footpredict.local

────────────────────────────────────────────────────────────────────────
3. SOURCES DE DONNÉES (stratégie multi-API + scraping)
────────────────────────────────────────────────────────────────────────
L'application doit agréger AU MOINS 3 sources de données :

▸ API OFFICIELLES (solidité, données critiques)
  1. API-Football v3 (RapidAPI)    → fixtures, scores, stats basiques
  2. Football-Data.org             → compétitions majeures, classements
  3. Odds-API.io                   → cotes officielles bookmakers

▸ SCRAPING (enrichissement, côté backend UNIQUEMENT)
  4. Understat (via soccerdata)    → xG, xGA, npxG
  5. FBref (via soccerdata)        → stats avancées (tirs, passes)
  6. OddsPortal (via OddsHarvester)→ cotes multi-bookmakers, historiques
  7. OddsMath (API interne)        → dropping odds (mouvements de cotes)

▸ RÈGLES
- Le scraping NE DOIT PAS tourner dans l'app Android. Il est exécuté
  par des Supabase Edge Functions (cron) qui poussent les résultats
  dans une table distante, que l'app consomme ensuite via REST.
- Chaque source est isolée derrière une interface (RemoteDataSource).
- Chaque source expose un `reliabilityScore` (0.0 à 1.0) utilisé pour
  la fusion :
    Understat (xG)     = 0.9
    API-Football       = 0.8
    Football-Data.org  = 0.75
    Odds-API.io        = 0.85
    OddsPortal scrapé  = 0.7
    OddsMath scrapé    = 0.65

────────────────────────────────────────────────────────────────────────
4. TYPES TRANSVERSES À DÉFINIR AVANT TOUT CODE
────────────────────────────────────────────────────────────────────────
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T, val fromCache: Boolean) : Resource<T>
    data class Error(val message: String, val cached: Any? = null) : Resource<Nothing>
}

data class EnrichedMatch(
    val fixture: Match,
    val homeStats: TeamStats,
    val awayStats: TeamStats,
    val odds: Odds?,
    val eloHome: Double?,
    val eloAway: Double?,
    val sources: List<String>,
    val conflicts: List<String> = emptyList()
)

────────────────────────────────────────────────────────────────────────
5. STRATÉGIE OFFLINE-FIRST (non négociable)
────────────────────────────────────────────────────────────────────────
Le Repository doit OBLIGATOIREMENT suivre ce pattern d'émission :
  1. Émet immédiatement le cache Room s'il existe (fromCache = true)
  2. Tente un fetch multi-sources en arrière-plan (async/await)
  3. Si succès ET cache > 6h → met à jour Room et réémet (fromCache = false)
  4. Si échec réseau → conserve le cache émis, ajoute un flag offline
Aucun crash ne doit survenir en cas de coupure réseau.

────────────────────────────────────────────────────────────────────────
6. STRATÉGIE DE FUSION MULTI-SOURCES
────────────────────────────────────────────────────────────────────────
Le `DataFusionAggregator` doit :
  1. Appeler toutes les sources en PARALLÈLE via async/awaitAll
  2. Résoudre les identités d'équipes (EntityResolver basé sur une table
     SourceMappingEntity : source_name, source_team_id, unified_team_id)
  3. Prioriser par `reliabilityScore` décroissant pour chaque type de donnée
     - Fixtures/scores → API-Football prioritaire
     - xG             → Understat prioritaire
     - Cotes          → Odds-API.io prioritaire, scraping en fallback
  4. Détecter les conflits (scores divergents, etc.) et les marquer
  5. Ne JAMAIS bloquer l'affichage si une source échoue (dégradation
     gracieuse)

────────────────────────────────────────────────────────────────────────
7. MODÈLE STATISTIQUE : DIXON-COLES (PAS de Poisson pur)
────────────────────────────────────────────────────────────────────────
⚠️ IMPORTANT : on utilise UNIQUEMENT Dixon-Coles comme socle
statistique. Le modèle de Poisson pur n'est PAS implémenté séparément,
car Dixon-Coles EST Poisson + une correction τ sur 4 scores faibles.
Les combiner n'apporterait aucun gain (corrélation > 95%).

▸ Étape 1 — Forces normalisées (avec Time Decay)
  Fenêtre : 10 derniers matchs pondérés par exp(-ξ × Δt)
  ξ = 0.0016 par jour (valeur standard Dixon-Coles)
  attack_home  = Σ(w_i × buts marqués dom.) / Σ(w_i) / moyenne_buts_ligue
  defense_home = Σ(w_i × buts encaissés dom.) / Σ(w_i) / moyenne_buts_ligue
  (idem away)
  Fallback si < 5 matchs : forces neutres = 1.0
  Clamp final : coerceIn(0.3, 3.0)

▸ Étape 2 — Buts attendus (λ)
  λ_home = attack_home × defense_away × moyenne_buts_ligue × 1.15
  λ_away = attack_away × defense_home × moyenne_buts_ligue

▸ Étape 3 — Matrice des scores avec correction τ
  P_DC(x, y) = Poisson(x; λ_home) × Poisson(y; λ_away) × τ(x, y; λ_home, λ_away, ρ)
  avec :
    τ(0,0) = 1 - λ_home × λ_away × ρ
    τ(0,1) = 1 + λ_home × ρ
    τ(1,0) = 1 + λ_away × ρ
    τ(1,1) = 1 - ρ
    τ(x,y) = 1 sinon
  ρ par défaut = -0.13 (valeur de la littérature)
  Troncature : x, y ∈ [0..6] puis NORMALISATION (la correction modifie
  légèrement la somme, diviser par le total)

▸ Étape 4 — Sorties
  - P(1X2) = sommes sur la matrice normalisée
  - P(Over 2.5) / P(Under 2.5) = sommes sur total > 2.5
  - λ_home et λ_away exposés pour debug

▸ Étape 5 — Signature du UseCase
  `CalculateDixonColesUseCase` doit être PUR (aucune dépendance Android,
  aucun I/O), prendre en entrée Match + TeamStats + leagueAvg + rho,
  et tourner dans withContext(Dispatchers.Default).

────────────────────────────────────────────────────────────────────────
8. ENSEMBLE LEARNING : 3 MODÈLES INDÉPENDANTS
────────────────────────────────────────────────────────────────────────
⚠️ Ne PAS combiner Dixon-Coles avec Poisson (quasi identiques).
Les 3 modèles réellement complémentaires sont :

  1. Dixon-Coles    → modèle statistique (structure de score)
  2. Marché         → modèle cotes (info externe : blessures, tactique)
  3. Elo / xG-based → modèle rating dynamique (force globale, momentum)

▸ Modèle 1 : Dixon-Coles (voir Section 7)

▸ Modèle 2 : MarketModelUseCase
  - Retirer la marge bookmaker (méthode proportionnelle) :
      rawHome = 1/cote_home, rawDraw = 1/cote_draw, rawAway = 1/cote_away
      margin = rawHome + rawDraw + rawAway
      P_imp = raw / margin
  - Retourne MarketPrediction(homeProb, drawProb, awayProb)

▸ Modèle 3 : EloRatingUseCase
  - Elo de base : R' = R + K × (résultat - attendu)
  - attendu_home = 1 / (1 + 10^((R_away - R_home - HFA) / 400))
  - HFA (home field advantage) = 65 points Elo
  - K adaptatif : 20 (match normal), 30 (coupe), 40 (match international)
  - Conversion Elo → probas 1X2 via formule logistique adaptée

▸ Agrégation : EnsemblePredictionUseCase
  - Pondération par défaut :
      3 modèles dispo → DC 0.40 / Marché 0.40 / Elo 0.20
      2 modèles      → redistribution proportionnelle
      1 modèle       → poids 1.0 sur celui-ci
  - Moyenne pondérée des probas home/draw/away
  - Mesure du DÉSACCORD entre modèles :
      σ = écart-type des 3 probas (home, draw, away)
  - Ajustement de la confiance :
      σ < 0.03          → boost 5%   (fort accord)
      0.03 ≤ σ < 0.08   → neutre
      0.08 ≤ σ < 0.15   → malus 10%  (divergence modérée)
      σ ≥ 0.15          → malus 25%  (forte divergence)
  - Bonus modèles : 3 modèles → ×1.03 / 2 → ×1.00 / 1 → ×0.95
  - Confiance finale = max(probas) × agreementFactor × modelsBonus
                       coerceIn(0, 0.99)
  - DivergenceLevel : LOW / MEDIUM / HIGH exposé pour transparence UI

▸ Détection value bet
  - Comparer P_ensemble vs P_implicite (marge retirée)
  - Si edge = P_ensemble - P_implicite ≥ 0.10 → ValueBet détecté

▸ Signature
  `EnsemblePredictionUseCase` prend en entrée tous les inputs des 3 modèles,
  lance les 3 en parallèle via async/awaitAll, et retourne un
  `EnsemblePrediction` incluant les prédictions individuelles
  (transparence UI).

────────────────────────────────────────────────────────────────────────
9. CRITÈRES DE QUALITÉ
────────────────────────────────────────────────────────────────────────
- Pas de blocage du thread UI (Dispatchers.Default pour calculs,
  Dispatchers.IO pour réseau/disque)
- Toutes les sources abstraites derrière des interfaces
  (RemoteDataSource, LocalDataSource) pour permettre le swap futur
- Gestion d'erreur explicite : pas de try/catch silencieux
- UseCases PURS (aucune dépendance Android) → testables unitairement
- Commentaires KDoc sur toutes les classes publiques
- Pas de string en dur dans l'UI (futur strings.xml)
- Quota tracking : ne jamais dépasser les rate limits des API
  (cache Room agressif, tracker de quota par source)
- Contrainte mathématique : ρ doit rester dans l'intervalle
  [max(-1/λ_home, -1/λ_away), min(1/(λ_home·λ_away), 1)]

────────────────────────────────────────────────────────────────────────
10. STRUCTURE DE PACKAGES ATTENDUE
────────────────────────────────────────────────────────────────────────
data/
├── remote/
│   ├── fixture/
│   │   ├── FixtureRemoteDataSource.kt
│   │   ├── ApiFootballFixtureDataSource.kt
│   │   └── FootballDataOrgFixtureDataSource.kt
│   ├── stats/
│   │   ├── StatsRemoteDataSource.kt
│   │   ├── ApiFootballStatsDataSource.kt
│   │   └── SupabaseScrapedXgDataSource.kt
│   └── odds/
│       ├── OddsRemoteDataSource.kt
│       ├── OddsApiIoDataSource.kt
│       └── SupabaseScrapedOddsDataSource.kt
├── local/
│   ├── dao/{MatchDao, TeamStatsDao, OddsDao, SourceMappingDao, EloDao}.kt
│   └── entity/{MatchEntity, TeamStatsEntity, OddsEntity,
│               SourceMappingEntity, EloEntity}.kt
├── mapper/{FixtureMapper, OddsMapper, StatsMapper, EloMapper}.kt
├── aggregator/
│   ├── DataFusionAggregator.kt
│   └── EntityResolver.kt
└── repository/
    └── MatchRepositoryImpl.kt

domain/
├── model/
│   ├── Match.kt
│   ├── TeamStats.kt
│   ├── Odds.kt
│   ├── MatchPrediction.kt
│   ├── MarketPrediction.kt
│   ├── EloPrediction.kt
│   ├── EnsemblePrediction.kt
│   ├── ValueBet.kt
│   └── Resource.kt
├── repository/
│   └── MatchRepository.kt
└── usecase/
    ├── CalculateDixonColesUseCase.kt
    ├── MarketModelUseCase.kt
    ├── EloRatingUseCase.kt
    └── EnsemblePredictionUseCase.kt

presentation/
├── screen/matchlist/
│   ├── MatchListScreen.kt
│   ├── MatchListViewModel.kt
│   ├── MatchUiState.kt
│   └── components/
│       ├── PredictionCard.kt
│       └── ProbabilityBar.kt
└── theme/{Color, Theme, Type}.kt

di/
├── NetworkModule.kt
├── DatabaseModule.kt
├── RepositoryModule.kt
└── UseCaseModule.kt

supabase/
└── functions/
    ├── scrape-understat/index.ts
    ├── scrape-fbref/index.ts
    ├── scrape-oddsportal/index.ts
    └── fetch-oddsmath/index.ts

────────────────────────────────────────────────────────────────────────
11. PLAN DE GÉNÉRATION INCRÉMENTAL
────────────────────────────────────────────────────────────────────────
Section 1  → Structure globale des packages + arborescence complète
Section 2  → Couche DATA : entités Room + DAOs
Section 3  → Couche DATA : DTOs + RemoteDataSources (interfaces + impl)
Section 4  → Couche DATA : LocalDataSource + Mappers
Section 5  → Couche DATA : EntityResolver + DataFusionAggregator
Section 6  → Couche DATA : MatchRepository interface + impl (offline-first)
Section 7  → Couche DOMAIN : modèles métiers (Match, TeamStats, Odds,
              Elo, Predictions, ValueBet, Resource)
Section 8  → Couche DOMAIN : CalculateDixonColesUseCase (avec τ + time decay)
Section 9  → Couche DOMAIN : MarketModelUseCase + EloRatingUseCase
Section 10 → Couche DOMAIN : EnsemblePredictionUseCase (agrégation + confiance)
Section 11 → Couche PRESENTATION : MatchListViewModel + MatchUiState
Section 12 → Couche PRESENTATION : Composables (MatchListScreen +
              PredictionCard + ProbabilityBar + badge divergence)
Section 13 → Hilt Modules + build.gradle.kts (versions vérifiées)
Section 14 → Supabase Edge Functions (scraping Understat/FBref/OddsPortal/OddsMath)
Section 15 → Tests unitaires (CalculateDixonColesUseCaseTest +
              EnsemblePredictionUseCaseTest)

────────────────────────────────────────────────────────────────────────
COMMENCE MAINTENANT PAR LA SECTION 1 UNIQUEMENT :
Structure globale des packages et arborescence complète des fichiers,
en distinguant clairement ce qui vit côté app Android et ce qui vit côté
Supabase Edge Functions.

Termine par : "➡️ Tape 'continue' pour la section suivante : Couche DATA — Entités Room & DAOs".