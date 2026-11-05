package com.vova.reputation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;

/** Server-authoritative comic sound, audible to nearby players. */
public final class ReputationFarts {
    private ReputationFarts() {}
    private static final Map<UUID, Long> LAST = new HashMap<>();
    public static void play(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        long previous = LAST.getOrDefault(player.getUUID(), -1000L);
        if (now - previous < 30) return;
        LAST.put(player.getUUID(), now);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 1.0f, 0.55f);
        player.serverLevel().sendParticles(ParticleTypes.CLOUD,
            player.getX(), player.getY() + 0.3, player.getZ(), 3, 0.15, 0.08, 0.15, 0.01);
    }
}
