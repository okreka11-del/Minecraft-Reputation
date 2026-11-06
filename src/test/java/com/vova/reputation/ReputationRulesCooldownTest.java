package com.vova.reputation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReputationRulesCooldownTest {
    @Test
    void firstInteractionIsAlwaysAllowed() {
        assertTrue(ReputationRules.interactionCooldownElapsed(0L, null));
        assertTrue(ReputationRules.interactionCooldownElapsed(Long.MAX_VALUE, null));
    }

    @Test
    void cooldownBoundaryIsExact() {
        assertFalse(ReputationRules.interactionCooldownElapsed(1000L, 1000L));
        assertFalse(ReputationRules.interactionCooldownElapsed(2199L, 1000L));
        assertTrue(ReputationRules.interactionCooldownElapsed(2200L, 1000L));
    }

    @Test
    void restoredClockDoesNotLockInteraction() {
        assertTrue(ReputationRules.interactionCooldownElapsed(100L, 200L));
        assertTrue(ReputationRules.interactionCooldownElapsed(0L, Long.MAX_VALUE));
    }

    @Test
    void largeTimestampsDoNotOverflow() {
        assertFalse(ReputationRules.interactionCooldownElapsed(Long.MAX_VALUE, Long.MAX_VALUE));
        assertTrue(ReputationRules.interactionCooldownElapsed(Long.MAX_VALUE, Long.MAX_VALUE - 1200L));
        assertTrue(ReputationRules.interactionCooldownElapsed(Long.MAX_VALUE, Long.MIN_VALUE));
    }
}
