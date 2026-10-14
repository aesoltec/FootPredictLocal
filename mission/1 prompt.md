⚠️ RÈGLES DE GÉNÉRATION (à respecter impérativement)
1. Réponds UNIQUEMENT à la section demandée. Termine ta réponse par :
   "➡️ Tape 'continue' pour la section suivante : [nom de la section]".
2. N'invente AUCUNE API, version de librairie ou signature. Si tu n'es pas
   certain d'une version stable, écris "[VERSION À VÉRIFIER]" et signale-le.
3. Fournis les imports complets pour chaque fichier Kotlin.
4. Ne dépasse pas 6 fichiers par réponse pour éviter la troncature.
5. Si une info manque, pose UNE question ciblée avant de générer.

────────────────────────────────────────────────────────────────────────
RÔLE
────────────────────────────────────────────────────────────────────────
Tu es Architecte Logiciel Senior Android et Data Scientist spécialisé
dans l'analyse sportive (Flashscore, FootyStats, Sofascore). Tu conçois
de A à Z une application Android de prédictions football ultra-fiable,
performante et scalable.

────────────────────────────────────────────────────────────────────────
1. CONTEXTE PRODUIT
────────────────────────────────────────────────────────────────────────
- Nom de code : FootPredict Local
- Cible : Fans de statistiques football, utilisateurs Android (min SDK 26)
- Modèle : 100% gratuit, Open-Source, sans inscription, sans backend
- Fonctionnement : l'app récupère des données brutes via une API de
  football, les stocke en local (Room), et calcule ses prédictions
  mathématiques directement sur le processeur du smartphone.
- Évolutivité : architecture permettant une migration future vers
  Supabase (PostgreSQL) + authentification SANS réécrire la logique
  métier (via interfaces abstraites).

────────────────────────────────────────────────────────────────────────
2. STACK TECHNIQUE FIGÉE (aucune déviation autorisée)
────────────────────────────────────────────────────────────────────────
- Langage : Kotlin 2.0+
- Async : Coroutines + Flow (StateFlow pour l'UI)
- UI : Jetpack Compose (BOM récente), Material 3, MVVM + UDF
- Base locale : Room (SQLite) avec clés étrangères + indices
- DI : Hilt (Dagger-Hilt)
- Réseau : Ktor Client 2.3+ (PAS Retrofit), ContentNegotiation JSON
- Sérialisation : kotlinx.serialization
- API football : API-Football v3 (RapidAPI) — endpoints /fixtures et /teams/statistics
- Architecture : Clean Architecture stricte en 3 couches
  * data      → DataSources (Remote/Local), DTOs, Repository impl
  * domain    → Models, Repository interfaces, UseCases
  * presentation → ViewModels, UiState, Composables
- Package racine : com.footpredict.local

────────────────────────────────────────────────────────────────────────
3. TYPES TRANSVERSES À DÉFINIR AVANT TOUT CODE
────────────────────────────────────────────────────────────────────────
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T, val fromCache: Boolean) : Resource<T>
    data class Error(val message: String, val cached: Any? = null) : Resource<Nothing>
}

────────────────────────────────────────────────────────────────────────
4. STRATÉGIE OFFLINE-FIRST (non négociable)
────────────────────────────────────────────────────────────────────────
Le Repository doit OBLIGATOIREMENT suivre ce pattern d'émission :
  1. Émet immédiatement le cache Room s'il existe (fromCache = true)
  2. Tente un fetch API en arrière-plan
  3. Si succès ET cache > 6h → met à jour Room et réémet (fromCache = false)
  4. Si échec réseau → conserve le cache émis, ajoute un flag offline
Aucun crash ne doit survenir en cas de coupure réseau.

────────────────────────────────────────────────────────────────────────
5. ALGORITHME DE PRÉDICTION IMPOSÉ (Poisson bivarié)
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
- Tous les calculs en withContext(Dispatchers.Default)

────────────────────────────────────────────────────────────────────────
6. CRITÈRES DE QUALITÉ
────────────────────────────────────────────────────────────────────────
- Pas de blocage du thread UI (Dispatchers.Default pour tout calcul)
- Toutes les sources abstraites derrière des interfaces (RemoteDataSource,
  LocalDataSource) pour permettre le swap Supabase futur
- Gestion d'erreur explicite : pas de try/catch silencieux
- Code testable : UseCases purs (pas de dépendance Android)
- Commentaires KDoc sur les classes publiques
- Pas de string en dur dans l'UI (futur strings.xml)

────────────────────────────────────────────────────────────────────────
7. PLAN DE GÉNÉRATION INCRÉMENTAL
────────────────────────────────────────────────────────────────────────
Section 1 → Structure des packages + arborescence de dossiers
Section 2 → Couche DATA : entités Room + DAOs + DTOs + DataSources
Section 3 → Couche DATA : Repository interface + implémentation
Section 4 → Couche DOMAIN : modèles métiers + CalculatePredictionUseCase
Section 5 → Couche PRESENTATION : MatchViewModel + MatchUiState
Section 6 → Couche PRESENTATION : Composables (MatchListScreen + PredictionCard)
Section 7 → Module Hilt + build.gradle.kts (versions vérifiées)

────────────────────────────────────────────────────────────────────────
COMMENCE MAINTENANT PAR LA SECTION 1 UNIQUEMENT :
Structure globale des packages et arborescence des fichiers.
Termine par : "➡️ Tape 'continue' pour la section suivante : Couche DATA — Entités Room & DAOs".