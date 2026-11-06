package com.vova.reputation;

/** Pure server gift rules, independently testable without a running world. */
public final class GiftRewardPolicy {
    public static final int DAILY_LIMIT = 10;
    public static final long COOLDOWN_TICKS = 2400L;
    private GiftRewardPolicy() {}
    public static boolean canAward(int count) { return count >= 0 && count < DAILY_LIMIT; }
    public static boolean cooldownElapsed(long now, long last) {
        return now < last || now - last >= COOLDOWN_TICKS;
    }
}
