# FootPredict Local 1.2.0 — Notes de release

Couche finale de contrôle : temps, filtres, stats, export, notifications.

## Nouveau (depuis 1.1.0)
- **Fenêtres temporelles strictes** : 15 j à venir / 7 j terminés par défaut,
  modifiables dans Réglages globaux (persistés).
- **Panneau de filtres à validation simultanée** : statut, DateRangePicker,
  compétition, confiance min, réglages + bouton Valider.
- **Écran Statistiques** : hit rate mesuré (50,8 % sur 59 matchs),
  Brier, par issue + bouton **Télécharger l’historique complet**
  (CSV 16 colonnes exactes, BOM UTF-8, ~80 lignes).
- **Notifications push HIGH** : `PredictionTriggerWorker` Hilt après synchro
  (Top 5 + conf ≥ 70 % + value ≥ 15 %), plafond 2/jour, opt-in, anti-doublon.
- **Écran de détail** : donut value animé, courbe xG, barres H2H, courbe cotes.
- **Marché Double Chance** + badge confiance vert/orange + curseur 0–80 %.
- Batch sync (stats/cotes groupées), upserts Room corrigés (clés métier),
  xG enrichi inter-sources, logging réseau coupé en release, SDK 36, icône.
- Backend : Understat + ESPN + OpenLigaDB (dédupliqués 306/306).

## Vérifié
- 93 tests verts (66 app + 27 scrapers), lint 0 erreur, R8 + AAB signés.
- Téléphone : sync complète, value bets réels, CSV validé octet par octet,
  worker planifié (jobscheduler), zéro crash.
