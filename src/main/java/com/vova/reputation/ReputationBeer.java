package com.vova.reputation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Drinkable beer with a short-lived, persisted intoxication counter. */
public final class ReputationBeer {
    private ReputationBeer() {}
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReputationMod.ID);
    public static final Supplier<Item> BEER = ITEMS.register("beer",
        () -> new BeerItem(new Item.Properties().stacksTo(16)));
    public static void register(IEventBus bus) { ITEMS.register(bus); }

    private static final class BeerItem extends Item {
        BeerItem(Properties properties) { super(properties); }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide && entity instanceof ServerPlayer player) {
                int drinks = player.getData(ReputationMod.BEER_DRINKS) + 1;
                player.setData(ReputationMod.BEER_DRINKS, Math.min(20, drinks));
                if (drinks >= 3) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
                if (drinks >= 5) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
            }
            if (entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().instabuild) return stack;
            stack.shrink(1);
            return stack.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : stack;
        }
    }
}
