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
    @Test
    void clockRollbackAllowsInteractionThenStartsFreshCooldown() {
        // A restored world may have an earlier game time than the persisted
        // last interaction. The first new interaction must be permitted.
        assertTrue(ReputationRules.interactionCooldownElapsed(40L, 5000L));
        // Once ready() records the new timestamp, normal throttling resumes.
        assertFalse(ReputationRules.interactionCooldownElapsed(1239L, 40L));
        assertTrue(ReputationRules.interactionCooldownElapsed(1240L, 40L));
    }

    @Test
    void invalidHistoricalTimestampDoesNotBlockFreshInteractions() {
        assertTrue(ReputationRules.interactionCooldownElapsed(0L, -1L));
        assertTrue(ReputationRules.interactionCooldownElapsed(0L, Long.MIN_VALUE));
        assertTrue(ReputationRules.interactionCooldownElapsed(Long.MAX_VALUE, -1L));
    }

    @Test
    void cooldownCannotBeBypassedBySameTickRepeatedInteractions() {
        assertFalse(ReputationRules.interactionCooldownElapsed(0L, 0L));
        assertFalse(ReputationRules.interactionCooldownElapsed(1199L, 0L));
        assertTrue(ReputationRules.interactionCooldownElapsed(1200L, 0L));
    }
}
