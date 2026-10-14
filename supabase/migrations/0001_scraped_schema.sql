-- FootPredict Local — schéma des données scrapées (mode zéro-clé API).
-- À appliquer dans le projet Supabase (gratuit) : Dashboard → SQL Editor.
-- Écrit par les Edge Functions (service_role), lu par l'app (anon).

-- ── Fixtures scrapées (Understat) ──────────────────────────────────────
create table if not exists public.scraped_fixtures (
    match_id      integer primary key,
    league_id     integer not null,
    season        integer not null,
    round         text,
    date_ts       bigint  not null,
    status_short  text    not null default 'NS',
    home_team_id  integer not null,
    home_team_name text   not null,
    away_team_id  integer not null,
    away_team_name text   not null,
    goals_home    integer,
    goals_away    integer,
    xg_home       double precision,
    xg_away       double precision,
    source        text    not null default 'understat',
    last_updated  bigint  not null
);
create index if not exists idx_scraped_fixtures_league
    on public.scraped_fixtures (league_id, season);

-- ── xG par équipe (Understat, consommé tel quel par l'app) ─────────────
create table if not exists public.xg_stats (
    unified_team_id integer not null,
    team_name       text    not null,
    league_id       integer not null,
    season          integer not null,
    xg_for_home     double precision,
    xg_for_away     double precision,
    xga_home        double precision,
    xga_away        double precision,
    npxg_for        double precision,
    last_updated    bigint  not null,
    primary key (unified_team_id, league_id, season)
);

-- ── Agrégats saison par équipe (buts/matchs domicile/extérieur) ────────
-- Permet à Dixon-Coles de tourner sans API-Football.
create table if not exists public.team_season_stats (
    unified_team_id integer not null,
    team_name       text    not null,
    league_id       integer not null,
    season          integer not null,
    played_home     integer not null default 0,
    played_away     integer not null default 0,
    gf_home         integer not null default 0,
    gf_away         integer not null default 0,
    ga_home         integer not null default 0,
    ga_away         integer not null default 0,
    last_updated    bigint  not null,
    primary key (unified_team_id, league_id, season)
);

-- ── Cotes scrapées (OddsPortal/OddsMath…, best-effort) ─────────────────
create table if not exists public.scraped_odds (
    id          bigint generated always as identity primary key,
    match_id    integer not null,
    bookmaker   text    not null,
    home_odds   double precision,
    draw_odds   double precision,
    away_odds   double precision,
    over_25     double precision,
    under_25    double precision,
    last_updated bigint not null,
    unique (match_id, bookmaker)
);
create index if not exists idx_scraped_odds_match
    on public.scraped_odds (match_id, last_updated desc);

-- ── Lecture publique (l'app utilise la clé anon) ───────────────────────
alter table public.scraped_fixtures  enable row level security;
alter table public.xg_stats          enable row level security;
alter table public.team_season_stats enable row level security;
alter table public.scraped_odds      enable row level security;

drop policy if exists public_read on public.scraped_fixtures;
drop policy if exists public_read on public.xg_stats;
drop policy if exists public_read on public.team_season_stats;
drop policy if exists public_read on public.scraped_odds;

create policy public_read on public.scraped_fixtures  for select using (true);
create policy public_read on public.xg_stats          for select using (true);
create policy public_read on public.team_season_stats for select using (true);
create policy public_read on public.scraped_odds      for select using (true);
