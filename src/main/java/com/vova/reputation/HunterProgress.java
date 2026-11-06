package com.vova.reputation;

/** Pure quest-counter arithmetic; does not initialize Minecraft registries. */
final class HunterProgress {
    private HunterProgress() {}

    static int next(int current) {
        return Math.min(10, Math.max(0, current) + 1);
    }
}
