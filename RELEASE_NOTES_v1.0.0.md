# FootPredict Local 1.0.0 — Notes de release

Première version de production : prédictions football offline-first, zéro clé API payante requise.

## Contenu
- Dixon-Coles (τ + time decay 10 matchs) + Marché (cotes, si configurées) + Elo (calculé et persisté)
- Ensemble pondéré, divergence, value bets (edge ≥ 10 %), BTTS, O/U 2.5
- Backend Supabase gratuit : fixtures, stats saison, xG (Understat, cron 6 h)
- Évaluation rétrospective des terminés (hit rate, Brier) avec approximation documentée
- 5 championnats, recherche, filtres, tops confiance / à-éviter / marchés, alertes plafonnées
- 44 tests unitaires passés, build R8 + AAB signé

## Installation
- `app-release.apk` : installation directe (autoriser les sources inconnues)
- `app-release.aab` : publication Play Store (clé existante `keystore/`, mots de passe dans `keystore.properties` — NE JAMAIS committer ni perdre : sans eux, plus de mise à jour Play possible)

## Limites connues (honnêtes)
- Sans `ODDS_API_KEY` : modèle Marché inactif, Ensemble sur Dixon-Coles + Elo
- Marché BTTS/O/U : modèle uniquement, pas de cotes порівняées
- Début de saison : petits échantillons, forces partiellement neutres
- Elo multi-ligues : dernière ligue synchronisée impose ses ratings partagés
- Scrapers cotes : placeholders (navigateur requis) ; FBref bloqué anti-bot
- Cron Supabase à activer (`0 */6 * * *`)

## Vérifications effectuées
- `BUILD SUCCESSFUL` : tests + `assembleRelease` + `bundleRelease`
- APK installée et lancée sans crash (téléphone + émulateur)
- Base embarquée contrôlée : 380 matchs PL 2026, 20 stats, 20 Elos 1469–1545
- Backend : Invoke `ok` 10/10 ligues-saisons, 3504 fixtures, 192 équipes
- Captures d’écran des cartes, tops et alertes
