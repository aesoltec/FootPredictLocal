package com.footpredict.local.data.notification

import com.footpredict.local.domain.model.AlertSeverity
import com.footpredict.local.domain.model.BettingAlert

/**
 * Politique pure de notification : quelles alertes HIGH pousser, sans spam.
 * - Uniquement sévérité HIGH (jamais MEDIUM/LOW) ;
 * - max [MAX_PER_DAY] par jour calendaire ;
 * - jamais deux fois la même alerte.
 * Testée unitairement ; [AlertNotifier] ne fait que l’I/O Android.
 */
object HighAlertPolicy {

    const val MAX_PER_DAY = 2

    data class Decision(val toSend: List<BettingAlert>, val newSentIds: Set<String>)

    fun select(
        alerts: List<BettingAlert>,
        alreadySentIds: Set<String>,
        sentToday: Int
    ): Decision {
        if (sentToday >= MAX_PER_DAY) return Decision(emptyList(), alreadySentIds)
        val fresh = alerts
            .filter { it.severity == AlertSeverity.HIGH && it.id !in alreadySentIds }
            .take(MAX_PER_DAY - sentToday)
        return Decision(fresh, alreadySentIds + fresh.map { it.id })
    }
}
