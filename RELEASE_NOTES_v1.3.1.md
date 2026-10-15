# Notes de version v1.3.1 — HB₂AO (2026-10-14)

Correctifs d’audit externe + dette technique. Suite : 135 tests verts
(108 app + 27 scrapers). Vérifié sur 2 téléphones (Infinix X6840 + X658E),
0 FATAL.

## Correctifs d’audit (données)
- **Statut jugé sur le marché proposé** (`marketHit`, 8 marchés testés) : fini
  l’inversion BTTS (ex. BTTS-No sur 5-3 affiché GAGNÉ). Taux affiché = vrai taux
  marchés (41,7 % au 2026-10-14 — douloureux mais vrai).
- **Historique figé** : un match soldé n’est plus recalculé (fini les flips
  GAGNÉ→PERDU) ; migration unique des anciennes lignes avec probas figées.
- **Cotes gelées** : `bookmakerOdds` mémorisée au soldé (export/simulation
  indépendants des cotes courantes) ; migration Room 4→5 **non destructive**.
- **Terminés multi-marchés** : cartes rejouées (Buts, BTTS, Double Chance,
  Corners) + rappel unique « Pronostic : X (N %) » (le plus haut % prime).

## Dette technique soldée
- 3 `!!` à risque de crash supprimés (sources toutes vides hors-ligne,
  `fetchMergedOdds` vide) + 2 hygiène ; `CouponScreen` mort supprimé.
- **Déduplication inter-synchros** (`isSameFixture`/`dedupeFixtures`, testés) :
  la meilleure source variant, « Hull » + « Hull City AFC » ne cohabitent plus.
  Les doublons déjà en base sont conservés (pas d’orphelins d’historique).
- `StringsFormatTest` : garde-fou `%` dans `strings.xml` (3 FATAL Réglages corrigés).

## Vrai taux marchés au 2026-10-14 (60 résolus, recompté depuis les scores)
BTTS-No 5/15 (33 %) · BTTS-Yes 5/11 (45 %) · Over 12/24 (50 %) · Under 3/10 (30 %).
Confiance non discriminante (47 % gagnés vs 46 % perdus) → re-calibration à
décider sur données, pas à l’aveugle.
