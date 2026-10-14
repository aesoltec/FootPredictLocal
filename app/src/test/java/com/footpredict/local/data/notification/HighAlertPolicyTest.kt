package com.footpredict.local.data.notification

import com.footpredict.local.domain.model.AlertSeverity
import com.footpredict.local.domain.model.AlertType
import com.footpredict.local.domain.model.BettingAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighAlertPolicyTest {

    private fun alert(id: String, severity: AlertSeverity) = BettingAlert(
        id = id, matchId = 1, homeTeam = "A", awayTeam = "B",
        type = AlertType.HIGH_CONFIDENCE, severity = severity,
        title = "t", message = "m"
    )

    @Test
    fun `only high alerts pass`() {
        val decision = HighAlertPolicy.select(
            listOf(alert("h", AlertSeverity.HIGH), alert("m", AlertSeverity.MEDIUM)),
            emptySet(), 0
        )
        assertEquals(listOf("h"), decision.toSend.map { it.id })
    }

    @Test
    fun `max two per day and no resend`() {
        val alerts = (1..5).map { alert("h$it", AlertSeverity.HIGH) }
        val first = HighAlertPolicy.select(alerts, emptySet(), 0)
        assertEquals(2, first.toSend.size)
        val second = HighAlertPolicy.select(alerts, first.newSentIds, 2)
        assertTrue(second.toSend.isEmpty())
        val nextDay = HighAlertPolicy.select(alerts, first.newSentIds, 0)
        assertEquals(2, nextDay.toSend.size)
        assertTrue(nextDay.toSend.none { it.id in first.newSentIds })
    }
}
