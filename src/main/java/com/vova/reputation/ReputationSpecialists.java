package com.vova.reputation;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Three server-driven specialist roles, stored in entity persistent NBT. */
public final class ReputationSpecialists {
    private static final String ROLE = "ReputationSpecialist";
    private static final String LAST = "ReputationSpecialistLastWork";
    private static final String PATROL = "ReputationSpecialistLastPatrol";
    private ReputationSpecialists() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReputationSpecialists::commands);
        NeoForge.EVENT_BUS.addListener(ReputationSpecialists::tick);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("specialist")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("guard").executes(ctx -> assign(ctx.getSource().getPlayerOrException(), "guard")))
            .then(Commands.literal("builder").executes(ctx -> assign(ctx.getSource().getPlayerOrException(), "builder")))
            .then(Commands.literal("armorer").executes(ctx -> assign(ctx.getSource().getPlayerOrException(), "armorer"))));
    }

    private static int assign(ServerPlayer player, String role) {
        boolean illager = role.equals("armorer");
        List<? extends Entity> candidates = illager
            ? player.serverLevel().getEntitiesOfClass(Pillager.class, player.getBoundingBox().inflate(6))
            : player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(6));
        Entity nearest = candidates.stream().min((a,b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
            .orElse(null);
        if (nearest == null) {
            player.sendSystemMessage(Component.literal("No suitable NPC within 6 blocks."));
            return 0;
        }
        nearest.getPersistentData().putString(ROLE, role);
        nearest.setCustomName(Component.literal(switch(role) {
            case "guard" -> "Village Guard";
            case "builder" -> "Village Builder";
            default -> "Pillager Armorer";
        }));
        nearest.setCustomNameVisible(true);
        player.sendSystemMessage(Component.literal("Assigned " + role + " to nearby NPC."));
        return 1;
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 100 != 0) return;
        // Bounded processing around active players; never scan every loaded entity globally.
        // A specialist visible to multiple players must work only once per server tick.
        Set<UUID> processed = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(32))) {
                if (!processed.add(villager.getUUID())) continue;
                String role = villager.getPersistentData().getString(ROLE);
                if (role.equals("guard")) guard(level, villager);
                else if (role.equals("builder")) builder(level, villager);
            }
            for (Pillager pillager : level.getEntitiesOfClass(Pillager.class, player.getBoundingBox().inflate(32))) {
                if (!processed.add(pillager.getUUID())) continue;
                if (pillager.getPersistentData().getString(ROLE).equals("armorer")) armorer(level, pillager);
            }
        }
    }

    private static void guard(ServerLevel level, Villager villager) {
        if (level.getEntitiesOfClass(Pillager.class, villager.getBoundingBox().inflate(16)).isEmpty()) {
            long now = level.getGameTime();
            if (now - villager.getPersistentData().getLong(PATROL) >= 400 && villager.getNavigation().isDone()) {
                int dx = level.random.nextInt(13) - 6;
                int dz = level.random.nextInt(13) - 6;
                villager.getNavigation().moveTo(villager.getX() + dx, villager.getY(), villager.getZ() + dz, 0.65);
                villager.getPersistentData().putLong(PATROL, now);
            }
            return;
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
            villager.getX(), villager.getY() + 1.8, villager.getZ(), 3, .2, .2, .2, .01);
        // A guard raises an alarm and rallies nearby iron golems rather than turning a villager into a fake melee mob.
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(24))) {
            Pillager enemy = level.getEntitiesOfClass(Pillager.class, golem.getBoundingBox().inflate(20))
                .stream().findFirst().orElse(null);
            if (enemy != null) golem.setTarget(enemy);
        }
    }

    private static void builder(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        long last = villager.getPersistentData().getLong(LAST);
        if (now - last < 1200) return;
        IronGolem golem = level.getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(12))
            .stream().filter(g -> g.getHealth() < g.getMaxHealth()).findFirst().orElse(null);
        if (golem == null) return;
        if (villager.distanceToSqr(golem) > 9) {
            villager.getNavigation().moveTo(golem, 0.7);
            return;
        }
        golem.heal(5.0f);
        villager.getPersistentData().putLong(LAST, now);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
            golem.getX(), golem.getY()+1, golem.getZ(), 8, .3, .4, .3, .01);
    }

    private static void armorer(ServerLevel level, Pillager pillager) {
        long now = level.getGameTime();
        long last = pillager.getPersistentData().getLong(LAST);
        if (now - last < 600) return;
        for (Pillager ally : level.getEntitiesOfClass(Pillager.class, pillager.getBoundingBox().inflate(12))) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 500, 0));
        }
        pillager.getPersistentData().putLong(LAST, now);
    }
}
