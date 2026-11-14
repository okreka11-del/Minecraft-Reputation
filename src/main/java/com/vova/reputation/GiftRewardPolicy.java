package com.vova.reputation;

/** Pure server gift rules, independently testable without a running world. */
public final class GiftRewardPolicy {
    public static final int DAILY_LIMIT = 10;
    public static final long COOLDOWN_TICKS = 2400L;
    private GiftRewardPolicy() {}
    /** Advance the persisted gift day only when the server clock reaches a later day.
     * A /time set or restored clock must not refill a player's daily allowance.
     */
    public static boolean shouldResetDailyCount(long currentDay, long recordedDay) {
        return recordedDay < 0 || currentDay > recordedDay;
    }

    public static boolean canAward(int count) { return count >= 0 && count < DAILY_LIMIT; }

    public static boolean cooldownElapsed(long now, long last) {
        // -1 is the persisted default for a player who has never received a gift.
        if (last < 0) return true;
        // World time may move backwards after a restore or an administrative command.
        if (now < last) return true;
        // Subtract only after ordering the operands, avoiding overflow from negative values.
        return now - last >= COOLDOWN_TICKS;
    }
}
