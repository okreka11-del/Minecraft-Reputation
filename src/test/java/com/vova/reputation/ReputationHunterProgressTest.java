package com.vova.reputation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReputationHunterProgressTest {
    @Test
    void incrementsFromZeroAndStopsAtTen() {
        assertEquals(1, ReputationMod.nextHunterProgress(0));
        assertEquals(10, ReputationMod.nextHunterProgress(9));
        assertEquals(10, ReputationMod.nextHunterProgress(10));
    }

    @Test
    void corruptedOrExtremeSavedProgressDoesNotOverflow() {
        assertEquals(1, ReputationMod.nextHunterProgress(-100));
        assertEquals(10, ReputationMod.nextHunterProgress(Integer.MAX_VALUE));
        assertEquals(1, ReputationMod.nextHunterProgress(Integer.MIN_VALUE));
    }
}
