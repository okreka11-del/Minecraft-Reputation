package com.vova.reputation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReputationHunterProgressTest {
    @Test
    void incrementsFromZeroAndStopsAtTen() {
        assertEquals(1, HunterProgress.next(0));
        assertEquals(10, HunterProgress.next(9));
        assertEquals(10, HunterProgress.next(10));
    }

    @Test
    void corruptedOrExtremeSavedProgressDoesNotOverflow() {
        assertEquals(1, HunterProgress.next(-100));
        assertEquals(10, HunterProgress.next(Integer.MAX_VALUE));
        assertEquals(1, HunterProgress.next(Integer.MIN_VALUE));
    }
}
