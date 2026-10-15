-- Migration 0002 — scores live mutualisés (fonction get-live-scores).
-- Écrite par la fonction (service_role), lue UNIQUEMENT via la fonction :
-- aucun accès anon direct (pas de policy SELECT : RLS activé sans policy).

-- ── Cache partagé du flux live (TTL 90 s, tous utilisateurs) ──────────────
create table if not exists public.live_scores_cache (
  cache_key  text primary key,          -- ex. 'live:all'
  payload    jsonb not null,            -- modèle JSON unifié (voir fonction)
  source     text not null default '',  -- ex. 'api-football+espn'
  updated_at bigint not null            -- epoch ms
);

alter table public.live_scores_cache enable row level security;
-- Pas de policy : seul service_role (Edge Function) y accède.

-- ── Compteur quota API-Football (100 req/jour forfait gratuit) ─────────────
create table if not exists public.api_quota_day (
  day        text primary key,          -- 'YYYY-MM-DD' (UTC)
  live_calls integer not null default 0 -- appels live facturés ce jour
);

alter table public.api_quota_day enable row level security;
-- Pas de policy : seul service_role (Edge Function) y accède.
