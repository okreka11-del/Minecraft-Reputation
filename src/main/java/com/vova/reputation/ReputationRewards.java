package com.vova.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReputationRewards {
    private ReputationRewards() {}
    private static final Map<UUID, Long> LAST_REWARD = new HashMap<>();

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long tick = player.server.overworld().getGameTime();
        if (tick % 100 != 0) return;
        int score = player.getData(ReputationMod.REP);
        if (score < 101) return;
        // Persist the daily cap per player, across logouts and dimension changes.
        long day = tick / 24000L;
        if (player.getData(ReputationMod.GIFT_DAY) != day) {
            player.setData(ReputationMod.GIFT_DAY, day);
            player.setData(ReputationMod.GIFT_COUNT, 0);
        }
        if (player.getData(ReputationMod.GIFT_COUNT) >= 10) return;
        long last = LAST_REWARD.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        // A new world/server may restart its game clock while this JVM retains the static map.
        // Do not strand a returning player behind a cooldown from the previous world.
        if (tick >= last && tick - last < 2400) return;
        boolean villagerNearby = !player.level().getEntitiesOfClass(Villager.class,
            player.getBoundingBox().inflate(10)).isEmpty();
        boolean animalNearby = !player.level().getEntitiesOfClass(Animal.class,
            player.getBoundingBox().inflate(7)).isEmpty();
        if (!villagerNearby && !animalNearby) return;
        ItemStack gift = villagerNearby ? new ItemStack(Items.EMERALD, score >= 151 ? 3 : 1)
            : new ItemStack(Items.WHEAT, score >= 151 ? 4 : 2);
        if (!player.addItem(gift)) player.drop(gift, false);
        player.displayClientMessage(Component.translatable("reputation.message.gift"), true);
        LAST_REWARD.put(player.getUUID(), tick);
        player.setData(ReputationMod.GIFT_COUNT, player.getData(ReputationMod.GIFT_COUNT) + 1);
    }
}
