package com.vova.reputation;

import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
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
            .then(Commands.literal("portal").executes(ctx -> talk(ctx.getSource().getPlayerOrException(), 2)))
            .then(Commands.literal("say").then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> respond(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "message"))))));
    }

    private static int respond(ServerPlayer player, String message) {
        int score = player.getData(ReputationMod.REP);
        if (score != 200 && score > -80) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.unworthy"));
            return 0;
        }
        if (player.serverLevel().getEntitiesOfClass(EnderDragon.class,
                player.getBoundingBox().inflate(96)).isEmpty()) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.absent"));
            return 0;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String key;
        if (lower.contains("привет") || lower.contains("hello") || lower.contains("здравствуй")) key = "hello";
        else if (lower.contains("дела") || lower.contains("how are")) key = "how";
        else if (lower.contains("портал") || lower.contains("portal") || lower.contains("домой")) return talk(player, 2);
        else if (lower.contains("помощ") || lower.contains("help")) key = "help";
        else if (lower.contains("друг") || lower.contains("friend")) key = "friend";
        else key = "unknown";
        player.sendSystemMessage(Component.translatable("reputation.dragon." + (score <= -80 ? "dark_" : "") + key));
        return 1;
    }

    private static int talk(ServerPlayer player, int option) {
        if (player.getData(ReputationMod.REP) < 200 && player.getData(ReputationMod.REP) > -80) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.unworthy"));
            return 0;
        }
        EnderDragon dragon = player.serverLevel().getEntitiesOfClass(EnderDragon.class,
            player.getBoundingBox().inflate(96)).stream().findFirst().orElse(null);
        if (dragon == null) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.absent"));
            return 0;
        }
        if (player.getData(ReputationMod.REP) <= -80) {
            player.sendSystemMessage(Component.translatable("reputation.dragon.outlaw"));
            return 1;
        }
        if (option == 0) player.sendSystemMessage(Component.translatable("reputation.dragon.hello"));
        else if (option == 1) player.sendSystemMessage(Component.translatable("reputation.dragon.how"));
        else {
            BlockPos pos = player.blockPosition().offset(3, 0, 0);
            var level = player.serverLevel();
            // A dialogue command must never overwrite existing terrain or player builds.
            if (!level.hasChunkAt(pos)
                    || !level.getBlockState(pos).isAir()
                    || !level.getBlockState(pos.above()).isAir()
                    || !level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                player.sendSystemMessage(Component.literal("Portal needs an empty space with solid ground three blocks east."));
                return 0;
            }
            level.setBlockAndUpdate(pos, Blocks.END_PORTAL.defaultBlockState());
            player.sendSystemMessage(Component.translatable("reputation.dragon.portal"));
        }
        return 1;
    }
}
