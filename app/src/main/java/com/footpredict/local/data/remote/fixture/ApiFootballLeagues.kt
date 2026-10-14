package com.footpredict.local.data.remote.fixture

/**
 * Registre des compétitions v1.3.2 (pur, testé) : IDs API-Football v3 stables
 * (même référentiel que `leagueId` dans Room), noms FR d’affichage/export,
 * code football-data.org quand le plan gratuit le couvre.
 *
 * Modèle quota (forfait gratuit API-Sports : 100 req/jour) :
 * - [AUTO] (6 ligues) : chargées d’office au démarrage (cache 6 h ensuite).
 * - Autres : chargées UNIQUEMENT sur sélection explicite de l’utilisateur
 *   (1–3 req, puis cache 6 h). Erreur/ID invalide → vide honnête, jamais de crash.
 *
 * IDs issus de la documentation API-Football ; toute entrée invalide se
 * dégrade gracieusement (erreur API captée → liste vide).
 */
enum class LeagueKind { CLUB, CONTINENTAL, NATIONAL }

data class LeagueRef(
    val id: Int,
    val nameFr: String,
    val kind: LeagueKind,
    val fdCode: String? = null,
    val autoLoad: Boolean = false
)

object ApiFootballLeagues {

    val ALL: List<LeagueRef> = listOf(
        // ——— Chargées d’office ———
        LeagueRef(39, "Premier League (Angleterre)", LeagueKind.CLUB, fdCode = "PL", autoLoad = true),
        LeagueRef(140, "La Liga (Espagne)", LeagueKind.CLUB, fdCode = "PD", autoLoad = true),
        LeagueRef(135, "Serie A (Italie)", LeagueKind.CLUB, fdCode = "SA", autoLoad = true),
        LeagueRef(78, "Bundesliga (Allemagne)", LeagueKind.CLUB, fdCode = "BL1", autoLoad = true),
        LeagueRef(61, "Ligue 1 (France)", LeagueKind.CLUB, fdCode = "FL1", autoLoad = true),
        LeagueRef(2, "Ligue des Champions (Europe)", LeagueKind.CONTINENTAL, fdCode = "CL", autoLoad = true),
        // ——— Europe (clubs, à la demande) ———
        LeagueRef(88, "Eredivisie (Pays-Bas)", LeagueKind.CLUB, fdCode = "DED"),
        LeagueRef(94, "Liga Portugal (Portugal)", LeagueKind.CLUB, fdCode = "PPL"),
        LeagueRef(144, "Jupiler Pro League (Belgique)", LeagueKind.CLUB),
        LeagueRef(203, "Süper Lig (Turquie)", LeagueKind.CLUB),
        LeagueRef(218, "Bundesliga (Autriche)", LeagueKind.CLUB),
        LeagueRef(197, "Super League (Grèce)", LeagueKind.CLUB),
        LeagueRef(119, "Superliga (Danemark)", LeagueKind.CLUB),
        LeagueRef(103, "Eliteserien (Norvège)", LeagueKind.CLUB),
        LeagueRef(106, "Ekstraklasa (Pologne)", LeagueKind.CLUB),
        LeagueRef(345, "Chance Liga (Tchéquie)", LeagueKind.CLUB),
        LeagueRef(207, "Super League (Suisse)", LeagueKind.CLUB),
        LeagueRef(113, "Allsvenskan (Suède)", LeagueKind.CLUB),
        LeagueRef(179, "Premiership (Écosse)", LeagueKind.CLUB),
        LeagueRef(172, "Parva Liga (Bulgarie)", LeagueKind.CLUB),
        LeagueRef(357, "Premier Division (Irlande)", LeagueKind.CLUB),
        LeagueRef(333, "Premier League (Ukraine)", LeagueKind.CLUB),
        LeagueRef(318, "First Division (Chypre)", LeagueKind.CLUB),
        LeagueRef(210, "HNL (Croatie)", LeagueKind.CLUB),
        LeagueRef(271, "NB I (Hongrie)", LeagueKind.CLUB),
        LeagueRef(283, "Liga I (Roumanie)", LeagueKind.CLUB),
        LeagueRef(286, "Super Liga (Serbie)", LeagueKind.CLUB),
        LeagueRef(235, "Premier Liga (Russie)", LeagueKind.CLUB),
        LeagueRef(383, "Ligat ha'Al (Israël)", LeagueKind.CLUB),
        // ——— Amériques / Asie / Afrique / Océanie (à la demande) ———
        LeagueRef(128, "Liga Profesional (Argentine)", LeagueKind.CLUB),
        LeagueRef(71, "Brasileirão (Brésil)", LeagueKind.CLUB, fdCode = "BSA"),
        LeagueRef(253, "MLS (États-Unis)", LeagueKind.CLUB),
        LeagueRef(307, "Saudi Pro League (Arabie Saoudite)", LeagueKind.CLUB),
        LeagueRef(98, "J1 League (Japon)", LeagueKind.CLUB),
        LeagueRef(292, "K League 1 (Corée du Sud)", LeagueKind.CLUB),
        LeagueRef(169, "Super League (Chine)", LeagueKind.CLUB),
        LeagueRef(188, "A-League (Australie)", LeagueKind.CLUB),
        LeagueRef(233, "Premier League (Égypte)", LeagueKind.CLUB),
        // ——— Coupes continentales (à la demande) ———
        LeagueRef(3, "Ligue Europa (UEFA)", LeagueKind.CONTINENTAL),
        LeagueRef(848, "Ligue Conférence (UEFA)", LeagueKind.CONTINENTAL),
        LeagueRef(13, "Copa Libertadores (Amérique du Sud)", LeagueKind.CONTINENTAL),
        // ——— Sélections (à la demande ; hors saison → vide honnête) ———
        LeagueRef(1, "Coupe du monde (FIFA)", LeagueKind.NATIONAL, fdCode = "WC"),
        LeagueRef(4, "Euro (UEFA)", LeagueKind.NATIONAL, fdCode = "EC"),
        LeagueRef(5, "Ligue des nations (UEFA)", LeagueKind.NATIONAL),
        LeagueRef(36, "CAN (CAF)", LeagueKind.NATIONAL)
    )

    /** Ligues chargées d’office (référence historique `LEAGUES`). */
    val AUTO_IDS: List<Int> = ALL.filter { it.autoLoad }.map { it.id }

    private val byId: Map<Int, LeagueRef> = ALL.associateBy { it.id }

    fun nameFr(id: Int): String? = byId[id]?.nameFr

    fun names(): Map<Int, String> = ALL.associate { it.id to it.nameFr }
}
