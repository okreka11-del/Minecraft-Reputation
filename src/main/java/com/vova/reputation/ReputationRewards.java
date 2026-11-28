package com.vova.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class ReputationRewards {
    private ReputationRewards() {}

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long tick = player.server.overworld().getGameTime();
        if (tick % 100 != 0) return;
        int score = player.getData(ReputationMod.REP);
        if (score < 101) return;
        // Persist the daily cap per player, across logouts and dimension changes.
        long day = tick / 24000L;
        if (GiftRewardPolicy.shouldResetDailyCount(day, player.getData(ReputationMod.GIFT_DAY))) {
            player.setData(ReputationMod.GIFT_DAY, day);
            player.setData(ReputationMod.GIFT_COUNT, 0);
        }
        if (!GiftRewardPolicy.canAward(player.getData(ReputationMod.GIFT_COUNT))) return;
        long last = player.getData(ReputationMod.GIFT_LAST_TICK);
        // A reset game clock must not strand a player behind a previous-world cooldown.
        if (last >= 0 && !GiftRewardPolicy.cooldownElapsed(tick, last)) return;
        boolean villagerNearby = !player.level().getEntitiesOfClass(Villager.class,
            player.getBoundingBox().inflate(10)).isEmpty();
        boolean animalNearby = !player.level().getEntitiesOfClass(Animal.class,
            player.getBoundingBox().inflate(7)).isEmpty();
        if (!villagerNearby && !animalNearby) return;
        ItemStack gift = villagerNearby ? new ItemStack(Items.EMERALD, score >= 151 ? 3 : 1)
            : new ItemStack(Items.WHEAT, score >= 151 ? 4 : 2);
        if (!player.addItem(gift)) player.drop(gift, false);
        player.displayClientMessage(Component.translatable("reputation.message.gift"), true);
        player.setData(ReputationMod.GIFT_LAST_TICK, tick);
        player.setData(ReputationMod.GIFT_COUNT, player.getData(ReputationMod.GIFT_COUNT) + 1);
    }
}
