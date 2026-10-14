package com.footpredict.scrapers

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PredictionEngineTest {

    private val engine = PredictionEngine()

    private fun pick(outcome: Outcome, weight: Double, prob: Double = 1.0) =
        Prediction(outcome, prob, source = "t", weight = weight)

    private fun result(vararg picks: Prediction, source: String = "s") =
        ScrapeResult(source, 0.8, prediction = picks.firstOrNull())

    @Test
    fun `weighted majority wins`() = runTest {
        val results = listOf(
            result(pick(Outcome.HOME, 0.9)),
            result(pick(Outcome.HOME, 0.9)),
            result(pick(Outcome.AWAY, 0.4))
        )
        val final = engine.aggregate(results)!!
        assertEquals(Outcome.HOME, final.outcome)
        assertEquals(1.8 / 2.2, final.confidence, 0.001)
        assertEquals(3, final.contributors)
    }

    @Test
    fun `failures and nulls are ignored`() = runTest {
        val results = listOf(
            ScrapeResult("bad", 0.9, error = "timeout"),
            ScrapeResult("empty", 0.9),
            result(pick(Outcome.DRAW, 0.7))
        )
        val final = engine.aggregate(results)!!
        assertEquals(Outcome.DRAW, final.outcome)
        assertEquals(1, final.contributors)
        assertTrue(final.warnings.any { it.startsWith("signal-faible") })
    }

    @Test
    fun `empty picks return null`() = runTest {
        assertNull(engine.aggregate(listOf(ScrapeResult("x", 0.5, error = "e"))))
        assertNull(engine.aggregate(emptyList()))
    }

    @Test
    fun `over25 is reliability weighted`() = runTest {
        val results = listOf(
            ScrapeResult(
                "a", 0.9,
                prediction = Prediction(Outcome.HOME, 0.6, over25Prob = 0.8, source = "a", weight = 0.9)
            ),
            ScrapeResult(
                "b", 0.3,
                prediction = Prediction(Outcome.HOME, 0.6, over25Prob = 0.2, source = "b", weight = 0.3)
            )
        )
        val final = engine.aggregate(results)!!
        assertEquals((0.8 * 0.9 + 0.2 * 0.3) / 1.2, final.over25Prob!!, 0.001)
    }
}
