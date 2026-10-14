package com.footpredict.local.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StakingTest {

    @Test
    fun `flat returns fixed capped to capital`() {
        assertEquals(10.0, recommendedStake(StakingStrategy.FLAT, 0.7, 2.0, 500.0, 10.0), 0.0)
        assertEquals(50.0, recommendedStake(StakingStrategy.FLAT, 0.7, 2.0, 50.0, 100.0), 0.0)
        assertEquals(0.0, recommendedStake(StakingStrategy.FLAT, 0.7, 2.0, 0.0, 10.0), 0.0)
    }

    @Test
    fun `kelly is zero without edge and capped at five percent`() {
        // p=0.5 @2.0 → f=0 → 0 € (aucun avantage).
        assertEquals(0.0, kellyStake(0.5, 2.0, 500.0), 0.0)
        // p=0.7 @2.0 → f=0.4 → plafonné 5 % × 500 = 25 €.
        assertEquals(25.0, kellyStake(0.7, 2.0, 500.0), 0.001)
        // p=0.6 @1.9 → f=(0.6×0.9−0.4)/0.9=0.155… → 77.78 €… plafonné 25 €.
        assertEquals(25.0, recommendedStake(StakingStrategy.KELLY, 0.6, 1.9, 500.0), 0.001)
        // Petit capital : plafond suivi (5 % × 100 = 5 €).
        assertEquals(5.0, kellyStake(0.7, 2.0, 100.0), 0.001)
    }

    @Test
    fun `kelly guards invalid inputs`() {
        assertEquals(0.0, kellyFraction(0.7, 1.0), 0.0)
        assertEquals(0.0, kellyFraction(0.0, 2.0), 0.0)
        assertEquals(0.0, recommendedStake(StakingStrategy.KELLY, 0.7, 2.0, -50.0), 0.0)
    }
}
