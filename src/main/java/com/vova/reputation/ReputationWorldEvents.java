package com.vova.reputation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side reputation interactions. Every reaction is evaluated for each player. */
public final class ReputationWorldEvents {
    private ReputationWorldEvents() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        if ((tick + player.getId()) % 40 != 0) return;

        int score = player.getData(ReputationMod.REP);
        int[] standing = ReputationMod.factionStandings(player);
        boolean light = score >= 120 && standing[2] >= -20;
        boolean dark = score <= -80 && standing[3] >= -60;
        AABB vicinity = player.getBoundingBox().inflate(12);

        for (Villager villager : level.getEntitiesOfClass(Villager.class, vicinity)) {
            // Trading offers are rebuilt when the merchant screen opens.
            // Apply reputation prices while the player is actually trading.
            if (villager.getTradingPlayer() instanceof ServerPlayer customer) {
                int customerScore = customer.getData(ReputationMod.REP);
                int villageStanding = ReputationMod.factionStandings(customer)[0];
                int discount = customerScore >= 200 ? -12
                    : customerScore >= 151 ? -8
                    : customerScore >= 101 ? -4
                    : customerScore <= -80 ? 12
                    : customerScore < 0 ? 6 : 0;
                // Independent village standing changes the final merchant price.
                discount += villageStanding >= 100 ? -5 : villageStanding >= 40 ? -2
                    : villageStanding <= -60 ? 9 : villageStanding <= -20 ? 4 : 0;
                discount = Math.max(-16, Math.min(16, discount));
                for (net.minecraft.world.item.trading.MerchantOffer offer : villager.getOffers()) {
                    offer.resetSpecialPriceDiff();
                    if (discount != 0) offer.addToSpecialPriceDiff(discount);
                }
                // Merchant menus synchronize offers when the player opens them;
                // refresh the displayed offers when reputation discounts change.
                if (customer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu) {
                    menu.setOffers(villager.getOffers());
                    customer.containerMenu.broadcastChanges();
                }
            }

            int villageStanding = ReputationMod.factionStandings(player)[0];
            if (score < 0 || villageStanding <= -40) {
                double dx = villager.getX() - player.getX();
                double dz = villager.getZ() - player.getZ();
                double distance = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                villager.getNavigation().moveTo(
                    villager.getX() + dx / distance * 9,
                    villager.getY(),
                    villager.getZ() + dz / distance * 9,
                    score <= -50 ? 1.5 : 1.2);
                if (tick % 120 < 40) level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    villager.getX(), villager.getY() + 1.8, villager.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            } else if ((score >= 151 || villageStanding >= 80) && tick % 200 < 40) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    villager.getX(), villager.getY() + 1.8, villager.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            }
        }

        for (Animal animal : level.getEntitiesOfClass(Animal.class, vicinity)) {
            if (light) {
                if (animal.distanceToSqr(player) > 12 && animal.distanceToSqr(player) < 100
                    && !animal.isVehicle()) {
                    animal.getNavigation().moveTo(player, 0.85);
                }
                if (tick % 200 < 40) level.sendParticles(ParticleTypes.HEART,
                    animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(),
                    1, 0.2, 0.2, 0.2, 0.01);
            } else if (score < 0) {
                // Sheep, cows and pigs retreat from notorious players.
                if (animal instanceof Sheep || animal instanceof Cow || animal instanceof Pig) {
                    double dx = animal.getX() - player.getX();
                    double dz = animal.getZ() - player.getZ();
                    double distance = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                    animal.getNavigation().moveTo(animal.getX() + dx / distance * 7,
                        animal.getY(), animal.getZ() + dz / distance * 7, 1.3);
                }
                // A visible spit/splatter reaction, once per few seconds, at close range.
                // The splash is cosmetic; the brief slowness is the gameplay penalty.
                if (score <= -50 && animal.distanceToSqr(player) < 16
                        && (tick + animal.getId() * 13L) % 160 < 40) {
                    level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.4, player.getZ(),
                        9, 0.25, 0.3, 0.25, 0.06);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0));
                }
            }
        }

        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, vicinity)) {
            if (score <= -50) golem.setTarget(player);
            else if (golem.getTarget() == player) golem.setTarget(null);
            if (light && golem.getTarget() == null && golem.distanceToSqr(player) < 100) {
                Monster attacker = level.getEntitiesOfClass(Monster.class, golem.getBoundingBox().inflate(12),
                    m -> m.getTarget() == player && !(m instanceof Creeper)).stream().findFirst().orElse(null);
                if (attacker != null) golem.setTarget(attacker);
            }
        }

        // Light: neutral endermen forgive existing aggression, but direct provocation can restart it.
        if (light) {
            for (EnderMan enderman : level.getEntitiesOfClass(EnderMan.class, vicinity)) {
                if (enderman.getTarget() == player) {
                    enderman.setTarget(null);
                    enderman.setPersistentAngerTarget(null);
                    enderman.setRemainingPersistentAngerTime(0);
                }
            }
        }

        // Dark allegiance: eligible hostile mobs stop attacking their master and follow nearby.
        // Creepers and bosses are excluded from forced navigation/targeting for safety.
        for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(18))) {
            if (monster instanceof net.minecraft.world.entity.boss.wither.WitherBoss) continue;
            if (dark) {
                if (monster.getTarget() == player) monster.setTarget(null);
                if (!(monster instanceof Creeper)) {
                    if (monster.getTarget() == null && monster.distanceToSqr(player) > 16
                            && monster.distanceToSqr(player) < 196) {
                        monster.getNavigation().moveTo(player, 1.05);
                    }
                    // Defend the dark player against a nearby attacker; never target other allies.
                    if (monster.getTarget() == null) {
                        Monster enemy = level.getEntitiesOfClass(Monster.class,
                            monster.getBoundingBox().inflate(10),
                            m -> m != monster && m.getTarget() == player && !(m instanceof Creeper))
                            .stream().findFirst().orElse(null);
                        if (enemy != null) monster.setTarget(enemy);
                    }
                }
            } else if (score <= -50 && !(monster instanceof Pillager)
                    && (monster.getTarget() == null
                    || (monster.getTarget() instanceof ServerPlayer target
                        && target.getData(ReputationMod.REP) > score))) {
                monster.setTarget(player);
            }
        }
    }
}
