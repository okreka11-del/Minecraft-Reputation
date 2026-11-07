package com.vova.reputation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReputationVillagerInteractionTest {
    @Test
    void neutralAndFriendlyPlayersMayInteract() {
        assertFalse(ReputationRules.villagerInteractionRefused(0, 0));
        assertFalse(ReputationRules.villagerInteractionRefused(200, -39));
        assertFalse(ReputationRules.villagerInteractionRefused(0, -39));
    }

    @Test
    void negativePersonalReputationBlocksInteraction() {
        assertTrue(ReputationRules.villagerInteractionRefused(-1, 100));
        assertTrue(ReputationRules.villagerInteractionRefused(-100, 0));
    }

    @Test
    void hostileVillageStandingBlocksInteractionAtBoundary() {
        assertTrue(ReputationRules.villagerInteractionRefused(200, -40));
        assertTrue(ReputationRules.villagerInteractionRefused(0, -100));
        assertFalse(ReputationRules.villagerInteractionRefused(0, -39));
    }
}
