package com.vova.reputation;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Dragon dialogue commands require hero rank and proximity to a living dragon. */
public final class ReputationDragon {
    private ReputationDragon() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dragon")
            .then(Commands.literal("hello").executes(ctx -> talk(ctx.getSource().getPlayerOrException(), 0)))
            .then(Commands.literal("how").executes(ctx -> talk(ctx.getSource().getPlayerOrException(), 1)))
            .then(Commands.literal("portal").executes(ctx -> talk(ctx.getSource().getPlayerOrException(), 2))));
    }

    private static int talk(ServerPlayer player, int option) {
        if (player.getData(ReputationMod.REP) < 200) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.unworthy"));
            return 0;
        }
        EnderDragon dragon = player.serverLevel().getEntitiesOfClass(EnderDragon.class,
            player.getBoundingBox().inflate(96)).stream().findFirst().orElse(null);
        if (dragon == null) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.absent"));
            return 0;
        }
        if (option == 0) player.sendSystemMessage(Component.translatable("reputation.dragon.hello"));
        else if (option == 1) player.sendSystemMessage(Component.translatable("reputation.dragon.how"));
        else {
            BlockPos pos = player.blockPosition().offset(3, 0, 0);
            player.serverLevel().setBlockAndUpdate(pos, Blocks.END_PORTAL.defaultBlockState());
            player.sendSystemMessage(Component.translatable("reputation.dragon.portal"));
        }
        return 1;
    }
}
