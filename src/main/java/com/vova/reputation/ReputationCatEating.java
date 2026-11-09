package com.vova.reputation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Cats may eat dropped fish, but never valuable items or player inventory. */
public final class ReputationCatEating {
    private ReputationCatEating() {}
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 0) return;
        for (var player : level.players()) {
            for (Cat cat : level.getEntitiesOfClass(Cat.class, player.getBoundingBox().inflate(16))) {
                if (!cat.isTame() || cat.isOrderedToSit() || !cat.isAlive()) continue;
                for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, cat.getBoundingBox().inflate(5))) {
                    ItemStack stack = dropped.getItem();
                    if (!stack.is(Items.COD) && !stack.is(Items.SALMON)
                            && !stack.is(Items.COOKED_COD) && !stack.is(Items.COOKED_SALMON)) continue;
                    if (cat.distanceToSqr(dropped) > 2.25) {
                        cat.getNavigation().moveTo(dropped, 1.1);
                    } else {
                        stack.shrink(1);
                        if (stack.isEmpty()) dropped.discard();
                        else dropped.setItem(stack);
                        cat.heal(2.0F);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                            cat.getX(), cat.getY() + 0.7, cat.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
                    }
                    break;
                }
            }
        }
    }
}
