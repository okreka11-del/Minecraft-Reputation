package com.vova.reputation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.monster.Pillager;
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
        AABB vicinity = player.getBoundingBox().inflate(12);

        for (Villager villager : level.getEntitiesOfClass(Villager.class, vicinity)) {
            if (score <= 0) {
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
            } else if (score >= 151 && tick % 200 < 40) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    villager.getX(), villager.getY() + 1.8, villager.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            }
        }

        for (Animal animal : level.getEntitiesOfClass(Animal.class, vicinity)) {
            if (score >= 151) {
                if (tick % 200 < 40) level.sendParticles(ParticleTypes.HEART,
                    animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(),
                    1, 0.2, 0.2, 0.2, 0.01);
            } else if (score <= 0) {
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
        }

        // High reputation discourages hostile targeting, low reputation attracts it.
        for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(18))) {
            if (score >= 151 && monster.getTarget() == player) {
                monster.setTarget(null);
            } else if (score <= -50 && (monster.getTarget() == null
                    || (monster.getTarget() instanceof ServerPlayer target
                        && target.getData(ReputationMod.REP) > score))) {
                monster.setTarget(player);
            }
        }
        // Pillagers become allies of renowned players and target hostile monsters.
        if (score >= 151) {
            for (Pillager pillager : level.getEntitiesOfClass(Pillager.class, vicinity)) {
                if (pillager.getTarget() == player) pillager.setTarget(null);
                if (pillager.getTarget() == null) {
                    Monster enemy = level.getEntitiesOfClass(Monster.class,
                        pillager.getBoundingBox().inflate(10), m -> !(m instanceof Pillager))
                        .stream().findFirst().orElse(null);
                    if (enemy != null) pillager.setTarget(enemy);
                }
            }
        }
    }
}
