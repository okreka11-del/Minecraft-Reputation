package com.vova.reputation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ReputationRulesTest {
    @Test
    void cooldownAllowsFirstInteraction() {
        assertTrue(ReputationRules.interactionCooldownElapsed(100L, null));
    }

    @Test
    void cooldownRequiresFullInterval() {
        assertFalse(ReputationRules.interactionCooldownElapsed(1199L, 0L));
        assertTrue(ReputationRules.interactionCooldownElapsed(1200L, 0L));
        assertFalse(ReputationRules.interactionCooldownElapsed(1201L, 2L));
    }

    @Test
    void cooldownRecoversFromClockRollbackAndInvalidSavedTimestamp() {
        assertTrue(ReputationRules.interactionCooldownElapsed(10L, 100L));
        assertTrue(ReputationRules.interactionCooldownElapsed(10L, -1L));
        assertFalse(ReputationRules.interactionCooldownElapsed(Long.MAX_VALUE, Long.MAX_VALUE - 1));
    }

    @Test
    void villagersRejectNegativeScoreOrHostileStanding() {
        assertFalse(ReputationRules.villagerInteractionRefused(0, 0));
        assertFalse(ReputationRules.villagerInteractionRefused(100, -39));
        assertTrue(ReputationRules.villagerInteractionRefused(-1, 100));
        assertTrue(ReputationRules.villagerInteractionRefused(100, -40));
        assertTrue(ReputationRules.villagerInteractionRefused(0, -100));
    }
}
