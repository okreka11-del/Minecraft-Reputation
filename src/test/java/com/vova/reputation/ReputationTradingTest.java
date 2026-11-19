package com.vova.reputation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReputationTradingTest {
    @Test
    void neutralStandingKeepsVanillaPrice() {
        assertEquals(0, ReputationTrading.adjustmentFor(0, 0));
        assertEquals(0, ReputationTrading.adjustmentFor(100, 0));
    }

    @Test
    void personalReputationThresholdsAreStable() {
        assertEquals(-4, ReputationTrading.adjustmentFor(101, 0));
        assertEquals(-8, ReputationTrading.adjustmentFor(151, 0));
        assertEquals(-12, ReputationTrading.adjustmentFor(200, 0));
        assertEquals(6, ReputationTrading.adjustmentFor(-1, 0));
        assertEquals(12, ReputationTrading.adjustmentFor(-80, 0));
    }

    @Test
    void factionStandingThresholdsAreStable() {
        assertEquals(-2, ReputationTrading.adjustmentFor(0, 40));
        assertEquals(-5, ReputationTrading.adjustmentFor(0, 100));
        assertEquals(4, ReputationTrading.adjustmentFor(0, -20));
        assertEquals(9, ReputationTrading.adjustmentFor(0, -60));
    }

    @Test
    void combinedAdjustmentsAreClamped() {
        assertEquals(-16, ReputationTrading.adjustmentFor(200, 100));
        assertEquals(16, ReputationTrading.adjustmentFor(-100, -100));
        assertEquals(1, ReputationTrading.adjustmentFor(151, -60));
    }
    @Test
    void boundaryValuesDoNotApplyDiscountsOrPenaltiesEarly() {
        assertEquals(0, ReputationTrading.adjustmentFor(0, 39));
        assertEquals(0, ReputationTrading.adjustmentFor(0, -19));
        assertEquals(0, ReputationTrading.adjustmentFor(100, 0));
        assertEquals(-4, ReputationTrading.adjustmentFor(101, 0));
        assertEquals(-4, ReputationTrading.adjustmentFor(150, 0));
        assertEquals(-8, ReputationTrading.adjustmentFor(151, 0));
        assertEquals(6, ReputationTrading.adjustmentFor(-79, 0));
        assertEquals(12, ReputationTrading.adjustmentFor(-80, 0));
        assertEquals(-2, ReputationTrading.adjustmentFor(0, 99));
        assertEquals(-5, ReputationTrading.adjustmentFor(0, 100));
        assertEquals(4, ReputationTrading.adjustmentFor(0, -59));
        assertEquals(9, ReputationTrading.adjustmentFor(0, -60));
    }

}
