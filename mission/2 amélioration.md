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
performante et scalable, intégrant une stratégie multi-sources
(API officielles + scraping backend) avec fusion intelligente.

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
  1. API-Football v3 (RapidAPI)   → fixtures, scores, stats basiques
  2. Football-Data.org            → compétitions majeures, classements
  3. Odds-API.io                  → cotes officielles bookmakers

▸ SCRAPING (enrichissement, côté backend UNIQUEMENT)
  4. Understat (via soccerdata)   → xG, xGA, npxG
  5. FBref (via soccerdata)       → stats avancées (tirs, passes)
  6. OddsPortal (via OddsHarvester) → cotes multi-bookmakers, historiques
  7. OddsMath (API interne)       → dropping odds (mouvements de cotes)

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
    val sources: List<String>,       // sources ayant contribué
    val conflicts: List<String> = emptyList()  // données en conflit détectées
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
7. ALGORITHME DE PRÉDICTION IMPOSÉ (Poisson bivarié + cotes)
────────────────────────────────────────────────────────────────────────
Implémentation exacte à respecter :
- Fenêtre historique : 10 derniers matchs de chaque équipe
- Forces calculées :
    attack_home  = (buts marqués dom. / matchs dom.) / moyenne_buts_ligue
    defense_home = (buts encaissés dom. / matchs dom.) / moyenne_buts_ligue
    (idem pour away, avec ses propres stats extérieures)
- Home advantage : multiplicateur fixe 1.15 appliqué à λ_home
- Buts attendus :
    λ_home = attack_home × defense_away × moyenne_buts_ligue × 1.15
    λ_away = attack_away × defense_home × moyenne_buts_ligue
- Fallback : si une équipe a < 5 matchs, utiliser la moyenne ligue (1.35)
- Probas 1X2 : somme des P(i, j) sur i,j ∈ [0..6] (Poisson bivarié
  indépendant : P(i,j) = Poisson(i; λ_home) × Poisson(j; λ_away))
- Marché O/U 2.5 : P(total > 2.5) via convolution Poisson
- Indice de confiance (0-100) = max(P1, PX, P2) × 100, arrondi
- ENRICHISSEMENT PAR LES COTES (si disponibles) :
  * Retirer la marge du bookmaker (méthode proportionnelle)
  * Comparer probas implicites vs probas Poisson
  * Si écart > 10% sur un résultat → flag "value bet" dans LocalPrediction
  * Ajuster l'indice de confiance : confidence = 0.7 × max(Poisson)
    + 0.3 × max(P_implicite)
- Tous les calculs en withContext(Dispatchers.Default)

────────────────────────────────────────────────────────────────────────
8. CRITÈRES DE QUALITÉ
────────────────────────────────────────────────────────────────────────
- Pas de blocage du thread UI (Dispatchers.Default pour calculs,
  Dispatchers.IO pour réseau/disque)
- Toutes les sources abstraites derrière des interfaces
  (RemoteDataSource, LocalDataSource) pour permettre le swap futur
- Gestion d'erreur explicite : pas de try/catch silencieux
- Code testable : UseCases purs (aucune dépendance Android)
- Commentaires KDoc sur toutes les classes publiques
- Pas de string en dur dans l'UI (futur strings.xml)
- Quota tracking : ne jamais dépasser les rate limits des API
  (cache Room agressif, tracker de quota par source)

────────────────────────────────────────────────────────────────────────
9. STRUCTURE DE PACKAGES ATTENDUE (couche Data)
────────────────────────────────────────────────────────────────────────
data/
├── remote/
│   ├── fixture/
│   │   ├── FixtureRemoteDataSource.kt          (interface)
│   │   ├── ApiFootballFixtureDataSource.kt
│   │   └── FootballDataOrgFixtureDataSource.kt
│   ├── stats/
│   │   ├── StatsRemoteDataSource.kt            (interface)
│   │   ├── ApiFootballStatsDataSource.kt
│   │   └── SupabaseScrapedXgDataSource.kt      (xG scrapé via Supabase)
│   └── odds/
│       ├── OddsRemoteDataSource.kt             (interface)
│       ├── OddsApiIoDataSource.kt
│       └── SupabaseScrapedOddsDataSource.kt    (OddsPortal/OddsMath via Supabase)
├── local/
│   ├── dao/{MatchDao, TeamStatsDao, OddsDao, SourceMappingDao}.kt
│   └── entity/{MatchEntity, TeamStatsEntity, OddsEntity,
│               SourceMappingEntity}.kt
├── mapper/{FixtureMapper, OddsMapper, StatsMapper}.kt
├── aggregator/
│   ├── DataFusionAggregator.kt
│   └── EntityResolver.kt
└── repository/
    └── MatchRepositoryImpl.kt

────────────────────────────────────────────────────────────────────────
10. PLAN DE GÉNÉRATION INCRÉMENTAL
────────────────────────────────────────────────────────────────────────
Section 1  → Structure globale des packages + arborescence complète
Section 2  → Couche DATA : entités Room + DAOs
Section 3  → Couche DATA : DTOs + RemoteDataSources (interfaces + impl)
Section 4  → Couche DATA : LocalDataSource + Mappers
Section 5  → Couche DATA : EntityResolver + DataFusionAggregator
Section 6  → Couche DATA : MatchRepository interface + impl (offline-first)
Section 7  → Couche DOMAIN : modèles métiers + CalculatePredictionUseCase
Section 8  → Couche PRESENTATION : MatchViewModel + MatchUiState
Section 9  → Couche PRESENTATION : Composables (MatchListScreen + PredictionCard)
Section 10 → Hilt Modules + build.gradle.kts (versions vérifiées)
Section 11 → Supabase Edge Functions (scraping Understat/FBref/OddsPortal)

────────────────────────────────────────────────────────────────────────
COMMENCE MAINTENANT PAR LA SECTION 1 UNIQUEMENT :
Structure globale des packages et arborescence complète des fichiers,
en distinguant clairement ce qui vit côté app Android et ce qui vit côté
Supabase Edge Functions.

Termine par : "➡️ Tape 'continue' pour la section suivante : Couche DATA — Entités Room & DAOs".