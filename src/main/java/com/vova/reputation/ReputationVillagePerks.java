package com.vova.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.resources.ResourceLocation;

/** Village-only reputation perks, refreshed every two seconds with a ten-second grace period. */
public final class ReputationVillagePerks {
    private ReputationVillagePerks() {}
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((player.serverLevel().getGameTime() + player.getId()) % 40 != 0) return;
        int score = player.getData(ReputationMod.REP);
        if (score >= 200) grant(player, "hero_of_light");
        if (score <= -100) grant(player, "lord_of_darkness");
        if (!ReputationSettlements.isInVillage(player, false) && !ReputationSettlements.isInVillage(player, true)) return;
        if (score >= 160) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 240, 0, true, true));
            if (score >= 200) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0, true, true));
        } else if (score <= -80) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 240, 0, true, true));
            if (score <= -100) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 0, true, true));
        }
    }
    private static void grant(ServerPlayer player, String name) {
        var id = ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, name);
        var holder = player.server.getAdvancements().get(id);
        if (holder == null) return;
        var progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) player.getAdvancements().award(holder, "reputation");
    }
}
