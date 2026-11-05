package com.vova.reputation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ReputationRules {
    private ReputationRules() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReputationRules::interact);
        NeoForge.EVENT_BUS.addListener(ReputationRules::damage);
        NeoForge.EVENT_BUS.addListener(ReputationRules::breakBlock);
        NeoForge.EVENT_BUS.addListener(ReputationRules::explosion);
    }

    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int score = player.getData(ReputationMod.REP);
        if (score < 0 && event.getTarget() instanceof Villager) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("reputation.message.trade_refused"), true);
        } else if (score >= 101 && event.getTarget() instanceof Villager villager) {
            int discount = score >= 200 ? -8 : score >= 151 ? -5 : -2;
            for (net.minecraft.world.item.trading.MerchantOffer offer : villager.getOffers()) {
                offer.resetSpecialPriceDiff();
                offer.addToSpecialPriceDiff(discount);
            }
            player.displayClientMessage(Component.translatable("reputation.hero.discount"), true);
        } else if (score < 0 && event.getTarget() instanceof Animal) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("reputation.message.animal_afraid"), true);
        }
    }

    private static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof ServerPlayer player)) return;
        int score = player.getData(ReputationMod.REP);
        if ((score <= -80 && event.getSource().getEntity() instanceof Pillager)
                || (score >= 151 && event.getSource().getEntity() instanceof Monster)
                || (score == 200 && event.getSource().getEntity() instanceof EnderDragon)) {
            event.setCanceled(true);
            return;
        }
        if (score <= -50 && (event.getSource().getEntity() instanceof Monster
                || event.getSource().getEntity() instanceof IronGolem)) {
            event.setAmount(event.getAmount() * 3.0f);
        }
    }

    private static void explosion(ExplosionEvent.Start event) {
        // Prevent creeper explosions triggered around nearby heroes.
        if (!(event.getExplosion().getDirectSourceEntity() instanceof Creeper creeper)) return;
        if (!(creeper.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        boolean heroNearby = !level.getEntitiesOfClass(ServerPlayer.class,
            creeper.getBoundingBox().inflate(5),
            p -> p.getData(ReputationMod.REP) >= 151).isEmpty();
        if (heroNearby) event.setCanceled(true);
    }

    private static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        int score = player.getData(ReputationMod.REP);
        BlockPos pos = event.getPos();
        // Village proximity heuristic: a villager within 24 blocks.
        boolean village = !player.serverLevel().getEntitiesOfClass(Villager.class,
            new net.minecraft.world.phys.AABB(pos).inflate(24)).isEmpty();
        if (!village) return;
        if (score < 0) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("reputation.message.village_protected"), true);
        } else if (score < 151) {
            ReputationMod.adjust(player, -1, "damaged village");
        }
    }
}
