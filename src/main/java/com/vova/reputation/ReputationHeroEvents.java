package com.vova.reputation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** First-pass hero reactions. Cooldowns are server-session local. */
public final class ReputationHeroEvents {
    private static final Map<UUID, Long> GIFTS = new HashMap<>();

    private ReputationHeroEvents() {}

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // Use a single server clock across dimensions so teleporting cannot bypass gift cooldowns.
        long time = player.getServer().overworld().getGameTime();
        if ((time + player.getId()) % 40 != 0) return;
        int reputation = player.getData(ReputationMod.REP);
        if (reputation < 151) return;

        for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(10))) {
            if (animal.distanceToSqr(player) > 9) {
                animal.getNavigation().moveTo(player, 1.0);
            }
        }

        // Do not cancel hostile targeting: heroes of Light are deliberately hunted.

        long day = player.getServer().overworld().getDayTime() / 24000L;
        if (player.getData(ReputationMod.GIFT_DAY) != day) {
            player.setData(ReputationMod.GIFT_DAY, day);
            player.setData(ReputationMod.GIFT_COUNT, 0);
        }
        if (player.getData(ReputationMod.GIFT_COUNT) >= 10) return;
        long last = GIFTS.getOrDefault(player.getUUID(), -100000L);
        // A world time rollback must not permanently block future gifts.
        if (last <= time && time - last < 2400) return;
        ItemStack gift = ItemStack.EMPTY;
        String donor = "";
        if (!level.getEntitiesOfClass(IronGolem.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.POPPY); donor = "golem";
        } else if (!level.getEntitiesOfClass(EnderMan.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.IRON_ORE); donor = "enderman";
        } else if (!level.getEntitiesOfClass(Blaze.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.BLAZE_POWDER); donor = "blaze";
        } else if (!level.getEntitiesOfClass(WitherSkeleton.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.BONE); donor = "wither_skeleton";
        } else if (!level.getEntitiesOfClass(ZombifiedPiglin.class, player.getBoundingBox().inflate(8)).isEmpty()
                || !level.getEntitiesOfClass(Piglin.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.GOLD_NUGGET); donor = "piglin";
        } else if (!level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.SADDLE); donor = "horse";
        } else if (!level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(8)).isEmpty()) {
            gift = new ItemStack(Items.EMERALD); donor = "villager";
        }
        if (!gift.isEmpty()) {
            if (!player.addItem(gift)) player.drop(gift, false);
            player.displayClientMessage(Component.translatable("reputation.hero.gift", Component.translatable("reputation.hero.donor." + donor)), true);
            GIFTS.put(player.getUUID(), time);
            player.setData(ReputationMod.GIFT_COUNT, player.getData(ReputationMod.GIFT_COUNT) + 1);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.4,
                player.getZ(), 8, .4, .4, .4, .02);
        }
    }
}
