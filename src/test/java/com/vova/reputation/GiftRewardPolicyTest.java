package com.vova.reputation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiftRewardPolicyTest {
    @Test void giftDayAdvancesOnlyForward() {
        assertTrue(GiftRewardPolicy.shouldResetDailyCount(0, -1));
        assertTrue(GiftRewardPolicy.shouldResetDailyCount(5, 4));
        assertFalse(GiftRewardPolicy.shouldResetDailyCount(5, 5));
        assertFalse(GiftRewardPolicy.shouldResetDailyCount(3, 5));
        assertFalse(GiftRewardPolicy.shouldResetDailyCount(0, Long.MAX_VALUE));
    }

    @Test void dailyLimit() {
        assertTrue(GiftRewardPolicy.canAward(0));
        assertTrue(GiftRewardPolicy.canAward(9));
        assertFalse(GiftRewardPolicy.canAward(10));
        assertFalse(GiftRewardPolicy.canAward(11));
        assertFalse(GiftRewardPolicy.canAward(-1));
        assertFalse(GiftRewardPolicy.canAward(Integer.MAX_VALUE));
    }

    @Test void cooldownBoundaryIsInclusive() {
        assertFalse(GiftRewardPolicy.cooldownElapsed(3399, 1000));
        assertTrue(GiftRewardPolicy.cooldownElapsed(3400, 1000));
        assertTrue(GiftRewardPolicy.cooldownElapsed(3401, 1000));
        assertFalse(GiftRewardPolicy.cooldownElapsed(1000, 1000));
    }

    @Test void firstGiftUsesPersistedSentinel() {
        assertTrue(GiftRewardPolicy.cooldownElapsed(0, -1));
        assertTrue(GiftRewardPolicy.cooldownElapsed(100, -1));
        assertTrue(GiftRewardPolicy.cooldownElapsed(0, Long.MIN_VALUE));
    }

    @Test void restoredClockDoesNotStrandPlayer() {
        assertTrue(GiftRewardPolicy.cooldownElapsed(20, 10000));
        assertTrue(GiftRewardPolicy.cooldownElapsed(0, Long.MAX_VALUE));
        assertFalse(GiftRewardPolicy.cooldownElapsed(2399, 0));
        assertTrue(GiftRewardPolicy.cooldownElapsed(2400, 0));
    }

    @Test void largeClockValuesDoNotOverflowCooldownMath() {
        assertFalse(GiftRewardPolicy.cooldownElapsed(Long.MAX_VALUE - 1, Long.MAX_VALUE - 100));
        assertTrue(GiftRewardPolicy.cooldownElapsed(Long.MAX_VALUE, Long.MAX_VALUE - 2400));
    }
}
