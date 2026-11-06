package com.vova.reputation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiftRewardPolicyTest {
    @Test void dailyLimit() {
        assertTrue(GiftRewardPolicy.canAward(0));
        assertTrue(GiftRewardPolicy.canAward(9));
        assertFalse(GiftRewardPolicy.canAward(10));
        assertFalse(GiftRewardPolicy.canAward(11));
        assertFalse(GiftRewardPolicy.canAward(-1));
    }
    @Test void cooldownAndClockReset() {
        assertFalse(GiftRewardPolicy.cooldownElapsed(3399, 1000));
        assertTrue(GiftRewardPolicy.cooldownElapsed(3400, 1000));
        assertTrue(GiftRewardPolicy.cooldownElapsed(20, 10000));
    }
}
