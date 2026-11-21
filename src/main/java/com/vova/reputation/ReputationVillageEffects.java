package com.vova.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Village-aligned reputation buffs with ten-second grace after leaving. */
public final class ReputationVillageEffects {
    private ReputationVillageEffects() {}
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.serverLevel().getGameTime() % 20 != 0) return;
        int score = player.getData(ReputationMod.REP);
        boolean light = score >= 160 && ReputationSettlements.isInVillage(player, false);
        boolean dark = score <= -80 && ReputationSettlements.isInVillage(player, true);
        if (light) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 220, 0, true, true));
            if (score >= 200)
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 220, 0, true, true));
        } else if (dark) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 220, 0, true, true));
            if (score <= -100)
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 220, 0, true, true));
        }
    }
}
