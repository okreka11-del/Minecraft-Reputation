package com.vova.reputation;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
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

public final class ReputationRules {
    private ReputationRules() {}
    // Per-player interaction cooldown, using the server-wide clock to prevent dimension resets.
    private static final Map<UUID, Long> INTERACTION_COOLDOWN = new HashMap<>();
    private static final long INTERACTION_INTERVAL = 1200L;

    private static boolean ready(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        long last = INTERACTION_COOLDOWN.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - last < INTERACTION_INTERVAL) return false;
        INTERACTION_COOLDOWN.put(player.getUUID(), now);
        return true;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReputationRules::interact);
        NeoForge.EVENT_BUS.addListener(ReputationRules::damage);
        // Block protection requires tracked structure ownership, not villager proximity.
        NeoForge.EVENT_BUS.addListener(ReputationRules::explosion);
    }

    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int score = player.getData(ReputationMod.REP);
        int[] factions = ReputationMod.factionStandings(player);
        if (event.getTarget() instanceof Villager villager && !player.isShiftKeyDown()) {
            event.setCanceled(true);
            ReputationNetwork.openDialogue(player, villager);
            return;
        }
        if (event.getTarget() instanceof Villager villager && player.isShiftKeyDown()
                && score >= 0 && factions[0] > -40) {
            event.setCanceled(true);
            ReputationTrading.open(player, villager);
            return;
        }
        // Faction relationships influence interactions without overwriting global reputation.
        if ((score < 0 || factions[0] <= -40) && event.getTarget() instanceof Villager) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("reputation.message.trade_refused"), true);
        } else if (score >= 101 && factions[0] >= 0 && event.getTarget() instanceof Villager villager) {
            int discount = score >= 200 ? -8 : score >= 151 ? -5 : -2;
            for (net.minecraft.world.item.trading.MerchantOffer offer : villager.getOffers()) {
                offer.resetSpecialPriceDiff();
                offer.addToSpecialPriceDiff(discount);
            }
            player.displayClientMessage(Component.translatable("reputation.hero.discount"), true);
        } else if ((score < 0 || factions[2] <= -40) && event.getTarget() instanceof Animal) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("reputation.message.animal_afraid"), true);
        } else if (event.getTarget() instanceof IronGolem && score >= 120 && factions[4] >= 0 && ready(player)) {
            // A small, bounded goodwill reward for maintaining a friendly golem relationship.
            player.giveExperiencePoints(2);
            ReputationMod.adjustFaction(player, 4, 1);
            player.displayClientMessage(Component.literal("Golem alliance: +2 XP, +1 golem standing"), true);
        }
    }

    private static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof ServerPlayer player)) return;
        int score = player.getData(ReputationMod.REP);
        // Light heroes attract monsters; being targeted must NOT grant immunity.
        // Dark-aligned players can be spared by their allied pillagers only.
        if (score <= -80 && ReputationMod.factionStandings(player)[1] >= 0
                && event.getSource().getEntity() instanceof Pillager) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Monster && score <= -40) {
            // Infamous players are less vulnerable to ordinary hostile mobs.
            event.setAmount(event.getAmount() * (score <= -80 ? 0.5f : 0.75f));
        } else if (event.getSource().getEntity() instanceof IronGolem && score <= -50) {
            // Golems still defend villages against villains.
            event.setAmount(event.getAmount() * 1.5f);
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

}
