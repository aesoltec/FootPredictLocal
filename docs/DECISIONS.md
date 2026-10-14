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

## D-20261014-03 — Garde-fou anti-fuite de secrets
- Logs Ktor cantonnés au debug (`NetworkModule`, `BuildConfig.DEBUG`).
- Vérifié : aucune des deux valeurs n’apparaît sous `app/src`,
  `supabase/`, `mission/` (grep le 2026-10-14 ; seule la clé Supabase
  *publishable* reste dans `mission/api et ref.md`, exposée par nature
  dans le binaire — risque accepté, inchangé).
