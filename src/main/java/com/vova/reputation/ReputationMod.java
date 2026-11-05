package com.vova.reputation;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

@Mod(ReputationMod.ID)
public final class ReputationMod {
    public static final String ID = "reputation";
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    public static final Supplier<AttachmentType<Integer>> REP = ATTACHMENTS.register("score",
        () -> AttachmentType.builder(() -> 100).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<Integer>> SUSPICION = ATTACHMENTS.register("suspicion",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    public ReputationMod(IEventBus bus) {
        ATTACHMENTS.register(bus);
        NeoForge.EVENT_BUS.addListener(ReputationMod::commands);
        NeoForge.EVENT_BUS.addListener(ReputationMod::onDeath);
    }

    private static void change(ServerPlayer player, int delta, String reason) {
        int before = player.getData(REP);
        int after = Math.max(-100, Math.min(200, before + delta));
        if (before == after) return;
        player.setData(REP, after);
        player.sendSystemMessage(Component.literal("Reputation " + (after - before > 0 ? "+" : "")
            + (after - before) + " (" + reason + ") -> " + after), true);
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (victim instanceof Villager) {
            change(killer, -20, "killed villager");
        } else if (victim instanceof Animal) {
            change(killer, -2, "killed animal");
        } else if (victim instanceof Monster) {
            change(killer, 1, "defeated monster");
        }
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("reputation")
            .then(Commands.literal("get").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                player.sendSystemMessage(Component.literal("Reputation: " + player.getData(REP)
                    + " / Suspicion: " + player.getData(SUSPICION)));
                return 1;
            }))
            .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 200)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    player.setData(REP, value);
                    player.sendSystemMessage(Component.literal("Reputation set to " + value));
                    return value;
                }))));
    }
}
