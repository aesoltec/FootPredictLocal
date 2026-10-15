# DECISIONS — FootPredictLocal



> Registre des décisions structurantes (AGENTS.md §2.1). Valeurs de clés :

> UNIQUEMENT dans `local.properties` (gitignoré), jamais ici.



## D-20261014-01 — Activation API-Sports (api-football v3)

- **Contexte** : clé gratuite fournie par l’utilisateur (100 req/jour).

- **Décision** : clé renseignée dans `local.properties` (`API_FOOTBALL_KEY`,

  fichier gitignoré, vérifié absent du code par grep). Source

  `ApiFootballFixtureDataSource` (reliability 0.8) + `ApiFootballStatsDataSource`

  (fallback) déjà câblées : aucune modification de code requise.

- **Budget** : ~1–3 req/sync/ligue (fixtures + stats inconnues), cache 6 h,

  polling live sur football-data.org uniquement → tient dans 100/jour pour

  1–2 ligues. Erreurs/quota → dégradation gracieuse (cache Room).

- **Non retenu (volontaire)** :

  - Endpoints Odds In-Play (`get-odds-live`, `get-bets`) : 1 req/match,

    vaporiseraient le quota journalier. Réévaluer si forfait supérieur.

  - Widgets web `<api-sports-widget>` : JavaScript pour sites web,

    inutilisables dans l’app native. Logos `media.api-sports.io` : profités

    automatiquement via les réponses fixtures + `TeamLogoStore` (aucun code).

  - `worldcup26.ir` : tiers non vérifié, modèle d’authentification inconnu → écarté.



## D-20261014-02 — Token Sportmonks stocké, non câblé

- **Contexte** : nouveau token fourni ; endpoint `livescores/inplay` cité.

- **Décision** : token mis à jour dans `local.properties` uniquement.

  AUCUN câblage : l’utilisateur avait écarté Sportmonks (payant) et le forfait

  gratuit ne couvre pas l’in-play cité. Réévaluer sur passage Pro explicite.



## D-20261014-03 — Garde-fou anti-fuite de secrets

- Logs Ktor cantonnés au debug (`NetworkModule`, `BuildConfig.DEBUG`).

- Vérifié : aucune des deux valeurs n’apparaît sous `app/src`,

  `supabase/`, `mission/` (grep le 2026-10-14 ; seule la clé Supabase

  *publishable* reste dans `mission/api et ref.md`, exposée par nature

  dans le binaire — risque accepté, inchangé).



## D-20261014-04 — Audit externe : statut jugé sur le marché proposé

- **Constat vérifié ( Écart 2 CONCÉDÉ )** : `wasHit` ne validait que le 1X2

  alors que la colonne CSV présente le marché proposé (ex. BTTS-No sur 5-3

  affiché GAGNÉ). Recomptage indépendant depuis les scores : vrai taux marchés

  25/60 (41,7 %) contre 29/60 affiché.

- **Décision** : `marketHit()` pur + testé (8 marchés) ; `settleFinished`

  juge les NOUVEAUX soldés sur `bestMarketPick` ; lignes anciennes recorrigées

  UNE FOIS avec probas figées (`predictedMarket` null → renseigné), gel ensuite

  (requête `id !in alreadySettled`) ; colonnes `predictedMarket`/`bookmakerOdds`

  + migration Room 4→5 NON destructive (historique préservé).

- **Écart 3 (cotes vides) : mécanisme confirmé** : 18/20 EN_ATTENTE ont des

  cotes, 0/60 soldés → les sources ne couvrent que les matchs à venir, rien

  n’est écrasé. Correctif : cote figée au soldé (`sp.bookmakerOdds` prioritaire

  à l’export). Passé non récupérable (vide honnête conservé).

- **Écart 1 (clubs de Championship) : PARTIELLEMENT REJETÉ** : Leeds et

  Sunderland sont réellement promus en PL (saison 25-26, connaissance vérifiée) ;

  les équipes proviennent d’Understat `getLeagueData/EPL/2026` recopié tel quel

  (aucune invention côté script, vérifié dans `scrape-understat`). Coventry/Hull

  non vérifiables depuis les connaissances (saison future) → à recouper via la

  clé API-Sports désormais active, pas de conclusion sans preuve.

  (Correctif auditeur lui-même : composition 26/27 validée à 100 %.)



## D-20261014-05 — Cycle audit général : dette + doublons + publication
- **Audit `!!`** : 3 crashs potentiels supprimés (`fetchMergedFixturesFull`

  toutes-sources-vides, `fetchMergedOdds` vide — crash au lancement hors-ligne

  sans cache possible) + 2 hygiène (`OddsMovementGraph`, filtre redondant).

  `CouponScreen` mort (0 appel) supprimé + imports.

- **Doublons inter-synchros** : la meilleure source variant selon les synchros,

  des lignes d’ID différents cohabitaient (« Hull »/« Hull City AFC », constaté

  sur téléphone avec alertes doublées). `isSameFixture`/`dedupeFixtures` purs +

  testés ; le dépôt garde les lignes existantes (historique intact) et écarte

  les doublons entrants. Doublons déjà en base : conservés volontairement.

- **Publication** : dépôt privé `aesoltec/FootPredictLocal`, branche

  `release/v1.3.1-audit-fixes`, PR #1 vers `main` (revue utilisateur),

  secrets/binaires exclus (vérifié : seul `local.properties.example`).

- **Vérité unique Terminés** : un seul « Pronostic : X (N %) » (plus haut %),

  sections par marché conservées (demande utilisateur, conflit apparent résolu).

## D-20261014-06 — 40 compétitions : registre + chargement à la demande- **Contexte** : demande de ~40 compétitions (coupes UEFA/CONMEBOL, sélections,
  MLS, Brasileirão, Jupiler, Süper Lig…). Seule API-Sports les couvre toutes ;
  forfait gratuit 100 req/jour → tout-charger-d’un-coup impossible (30+ ligues
  = 30–90 req).
- **Décision** : registre `ApiFootballLeagues` (IDs v3 + noms FR + codes FD
  gratuits, testé : unicité, inclus AUTO, couverture demandée) ; 6 ligues en
  chargement d’office, le reste UNIQUEMENT sur sélection explicite (1–3 req
  puis cache 6 h) ; ID invalide → vide honnête. Noms UI/CSV unifiés depuis le
  registre (les libellés CSV s’allongent : « Premier League (Angleterre) »).
- **Non fait (suivi)** : extension backend scraping (ESPN `scoreboard` couvre
  por.1/ned.1/uefa.* — nouveau cron + vérification) ; IDs du registre à
  revalider via `/leagues` (gracieux en cas d’erreur).

## D-20261014-07 — Live mutualisé : fonction get-live-scores + substitutions
- **Contexte** : demande d’un système hybride serveur (clé protégée) + client.
- **Substitution 1 (SofaScore/Flashscore/HTML → ESPN)** : endpoints non
  contractuels/protégés, déjà rejetés sur probes (403, JS-only) ; ToS fragiles.
  ESPN `scoreboard` (public, sans clé, ~35 ligues) déjà prouvé par `fetch-espn`.
  Documenté ici comme l’exige AGENTS.md (règle d’architecture modifiée par écrit).
- **Substitution 2 (routage)** : ESPN en premier (gratuit, illimité), top-up
  API-Football `live=all` pour les 8 premium UNIQUEMENT, avec cache serveur
  90 s partagé + plafond 40 appels/jour (table `api_quota_day`, facturé en 2xx
  seulement). Sans ça, le polling multi-utilisateurs vaporiserait les 100/jour.
- **Client** : `SupabaseLiveScoresDataSource` (Ktor existant, pas Retrofit —
  stack du projet) + DTOs + IDs stables négatifs + tests ; branché AVANT le
  chemin FD direct dans `validateLiveMatches` (conservé en second).
- **Sécurité** : clé API lue via `Deno.env` uniquement, jamais dans le code ;
  logs Ktor au debug seul ; la clé ayant transité par le chat, RÉGÉNÉRATION
  conseillée côté dashboard API-Sports après mise en service.
