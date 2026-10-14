# Backend Supabase — mode zéro-clé API (gratuit)

L'app fonctionne **sans aucune clé API payante** si ce backend est déployé :
l'Edge Function `scrape-understat` remplit les tables, l'app les lit en REST.

## 1. Créer le projet (gratuit)

1. https://supabase.com → New project (free tier suffit).
2. Noter : **Project URL** + **anon public key** + **service_role key**
   (Settings → API).

## 2. Créer les tables

Dashboard → SQL Editor → coller et exécuter
`supabase/migrations/0001_scraped_schema.sql`.

Vérif : 4 tables (`scraped_fixtures`, `xg_stats`,
`team_season_stats`, `scraped_odds`) avec politique `public_read`.

## 3. Déployer la fonction de scraping

`scrape-understat/index.ts` est **auto-porté** (aucun import relatif) :
copier-coller son contenu dans Dashboard → Edge Functions → New Function
fonctionne, tout comme la CLI :

```bash
supabase login
supabase link --project-ref <ref>
supabase secrets set SUPABASE_URL=<url> SUPABASE_SERVICE_ROLE_KEY=<service_role>
supabase functions deploy scrape-understat
```

Secrets Dashboard : Edge Functions → Secrets → ajouter `SUPABASE_URL` et
`SUPABASE_SERVICE_ROLE_KEY` (service_role : Settings → API).
Ne déployer QUE ces fonctions (les autres dossiers sont des placeholders documentés) :
- `scrape-understat` — fixtures + stats + xG (source principale, cron 6 h)
- `fetch-espn` — fixtures courantes 5 ligues (redondance, cron 6 h)
- `fetch-openligadb` — Bundesliga + Ligue des Champions via DFB (cron 6 h)

## 4. Planifier le cron (toutes les 6 h)

Deux options équivalentes (une seule suffit) :

**A. pg_cron en base — DÉJÀ ACTIF** (configuré le 2026-10-12) :
`sync-football-data-6am` (6h00 UTC, Understat), `sync-football-espn-6am`
(6h10), `sync-football-openligadb-6am` (6h20). Vérifier :
`SELECT jobname, schedule, active FROM cron.job;`
et l’historique : `SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 5;`

**B. Dashboard** → Edge Functions → Cron triggers → `0 */6 * * *`
(si vous préférez le planificateur Supabase au SQL).

```sql
select count(*) from scraped_fixtures;   -- attendu : ~380/ligue/saison
select count(*) from team_season_stats;  -- attendu : 20/ligue
select count(*) from xg_stats;           -- attendu : 20/ligue
```

## 5. Brancher l'app

Dans `local.properties` (ou variables d'environnement) :

```properties
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_ANON_KEY=<anon>
```

Laisser les autres clés à `YOUR_*` : les sources API se désactivent
toutes seules (`RemoteConfig`) et l'agrégateur prend le relais scraping
(`understat-supabase` fixtures 0.6 / stats 0.65 / xG 0.9).

## Limites honnêtes

- **Sites écartés après sondage réel** (vérifié le 2026-10-09) :
  - TheSportsDB : clé gratuite limitée (10 équipes sur 20 → données tronquées,
    pire que rien) ;
  - ClubElo : API morte (502), site refondu sans endpoint stable ;
  - FBref : bloque les bots datacenter (403 constaté) ;
  - Sofascore / FotMob : tokens volatils, trop fragiles pour un cron ;
  - OddsPortal / BetExplorer / OddsChecker / OddsPortal : pages 100 % JavaScript,
    infaisables sans navigateur automatisé.
- **Clés gratuites recommandées** (sans code supplémentaire, sources déjà câblées) :
  - football-data.org (offre gratuite) → `FOOTBALL_DATA_KEY` ;
  - The Odds API (500 crédits/mois gratuits) → `ODDS_API_KEY` (active le modèle Marché).
- **Cotes** : sans `ODDS_API_KEY`, le modèle Marché reste indisponible
  (l'Ensemble redistribue sur Dixon-Coles + Elo, value bets désactivés).
- **Live** : le scraping ne donne que NS/FT (pas de temps réel) —
  le polling live de l'app reste alimenté par API-Football quand configuré.

## 4. Live mutualisé : fonction get-live-scores (v1.3.2)

Scores en direct ~40 ligues, clé API-Football protégée côté serveur.

1. **Tables** : Dashboard → SQL Editor → coller et exécuter
   `supabase/migrations/0002_live_scores.sql` (cache 90 s + compteur quota,
   RLS sans policy = service_role seul, aucun accès anon direct).
2. **Secret** : Dashboard → Project Settings → Edge Functions → Secrets →
   ajouter `API_FOOTBALL_KEY` = votre clé gratuite API-Sports
   (jamais dans le code ; sans elle, seul le flux ESPN gratuit répond).
3. **Fonction** : Dashboard → Edge Functions → New Function → nom
   `get-live-scores` → coller `supabase/functions/get-live-scores/index.ts`
   → Deploy. (Le déploiement CLI exige la permission `edge_functions_read`,
   absente du token local : passer par le Dashboard.)
4. **Vérif** : `curl -H "apikey: <ANON>" -H "Authorization: Bearer <ANON>"    https://<ref>.supabase.co/functions/v1/get-live-scores` → JSON
   `{matches, cached, quota_remaining, errors}` (slugs ESPN invalides
   listés dans `errors`, sans impact).
5. **Quota** : 40 appels premium/jour max (table `api_quota_day`), cache 90 s
   partagé ; épuisé → cache périmé + ESPN seuls.
