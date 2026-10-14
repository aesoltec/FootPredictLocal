package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility

/**
 * Scrapers BLOQUÉS : ne pas appeler en production (voir raison chacun).
 * Ils existent pour documenter la matrice de faisabilité et empêcher toute
 * régression silencieuse (un appel réel lèverait une erreur réseau / 403,
 * isolée par l’orchestrateur — ici on rend null sans même appeler).
 */
abstract class BlockedScraper(
    override val sourceName: String,
    override val reliability: Double,
    /** Preuve / motif du blocage. */
    val blockReason: String
) : BaseHttpScraper() {
    override val feasibility = Feasibility.BLOCKED
    override suspend fun fetchData(matchId: String) = null
}

/** HTTP 403 mesuré le 2026-10-09 (protection anti-bots datacenter). */
class FBrefScraper : BlockedScraper("fbref", 0.8, "HTTP 403 mesuré (bot protection)")

/** Cloudflare + réponses chiffrées/rotatives sans contrat public. */
class SofaScoreScraper : BlockedScraper("sofascore", 0.7, "Cloudflare + API non contractuelle")

/** Données Opta derrière login + Cloudflare agressif. */
class WhoScoredScraper : BlockedScraper("whoscored", 0.75, "login Opta + Cloudflare")

/** Blocage agressif des IPs datacenter + ToS interdisant le scraping. */
class TransfermarktScraper : BlockedScraper("transfermarkt", 0.7, "blocage datacenter + ToS")

/** Pages 100 % JavaScript : aucun contenu sans navigateur automatisé. */
class OddsPortalScraper : BlockedScraper("oddsportal", 0.7, "rendu JS requis")

/** Rendu dynamique + API interne signée, anti-bots. */
class FlashscoreScraper : BlockedScraper("flashscore", 0.75, "rendu dynamique + API signée")

/** Contenu JS + paywall ; pas d’endpoint stable gratuit. */
class SquawkaScraper : BlockedScraper("squawka", 0.65, "JS + paywall")

/** HTTP 403 mesuré le 2026-10-12 sur /predictions/. */
class WinDrawWinScraper : BlockedScraper("windrawwin", 0.6, "HTTP 403 mesuré")

/** Pages gratuites tronquées, le fond est payant. */
class FootyStatsScraper : BlockedScraper("footystats", 0.6, "paywall (extraits tronqués)")

/** Contenu éditorial Opta, pas d’endpoint de données stable et gratuit. */
class TheAnalystScraper : BlockedScraper("theanalyst", 0.6, "pas d’endpoint données gratuit")

/** Fabrique : les 15 scrapers du périmètre demandé. */
object AllScrapers {
    fun all(): List<com.footpredict.scrapers.FootballScraper> = listOf(
        UnderstatScraper(),
        StatsBombScraper(),
        FootballDataScraper(),
        SoccerStatsScraper(),
        FBrefScraper(),
        FotMobScraper(),
        SofaScoreScraper(),
        WhoScoredScraper(),
        TransfermarktScraper(),
        FootyStatsScraper(),
        OddsPortalScraper(),
        FlashscoreScraper(),
        TheAnalystScraper(),
        SquawkaScraper(),
        WinDrawWinScraper()
    )
}
