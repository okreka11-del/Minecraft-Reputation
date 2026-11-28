package com.vova.reputation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Hungry tame cats eat dropped items; starving cats can chew nearby ordinary blocks. */
public final class ReputationCatEating {
    private static final String LAST_MEAL = "reputation_cat_last_meal";
    private static final long STARVING_TICKS = 12000L; // 10 minutes without food
    private ReputationCatEating() {}

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 0) return;
        long now = level.getGameTime();
        Set<UUID> processed = new HashSet<>();
        for (var player : level.players()) {
            for (Cat cat : level.getEntitiesOfClass(Cat.class, player.getBoundingBox().inflate(16))) {
                if (!processed.add(cat.getUUID()) || !cat.isTame() || !cat.isAlive()) continue;
                // Sitting cats retain their owner's sit command, even when starving.
                if (cat.isOrderedToSit()) continue;
                var data = cat.getPersistentData();
                if (!data.contains(LAST_MEAL)) data.putLong(LAST_MEAL, now);
                long lastMeal = data.getLong(LAST_MEAL);
                if (lastMeal > now) { data.putLong(LAST_MEAL, now); lastMeal = now; }

                boolean fed = false;
                for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, cat.getBoundingBox().inflate(6))) {
                    if (!dropped.isAlive() || dropped.getItem().isEmpty()) continue;
                    if (cat.distanceToSqr(dropped) > 2.25) {
                        cat.getNavigation().moveTo(dropped, 1.15);
                    } else {
                        ItemStack stack = dropped.getItem();
                        stack.shrink(1);
                        if (stack.isEmpty()) dropped.discard();
                        else dropped.setItem(stack);
                        cat.heal(2.0F);
                        data.putLong(LAST_MEAL, now);
                        level.sendParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7,
                            cat.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
                    }
                    fed = true;
                    break;
                }
                if (fed || now - lastMeal < STARVING_TICKS) continue;

                // Starvation: angry particles and destructive chewing, only when mob griefing is enabled.
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, cat.getX(), cat.getY() + 0.8,
                    cat.getZ(), 2, 0.2, 0.2, 0.2, 0);
                if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || now % 100 != 0) continue;
                BlockPos center = cat.blockPosition();
                for (int dx = -1; dx <= 1; dx++) {
                    boolean eaten = false;
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos pos = center.offset(dx, 0, dz);
                        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || !state.getFluidState().isEmpty()
                            || state.getDestroySpeed(level, pos) < 0
                            || level.getBlockEntity(pos) != null) continue;
                        // Break without drops: the block is actually consumed.
                        if (level.destroyBlock(pos, false, cat)) {
                            data.putLong(LAST_MEAL, now - STARVING_TICKS / 2);
                            eaten = true;
                            break;
                        }
                    }
                    if (eaten) break;
                }
            }
        }
    }
}
