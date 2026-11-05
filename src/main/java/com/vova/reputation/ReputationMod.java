package com.vova.reputation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    // Server-local anti-farm cooldowns. These are deliberately not persisted.
    private static final Map<UUID, Long> REWARD_COOLDOWN = new HashMap<>();

    public ReputationMod(IEventBus bus) {
        ATTACHMENTS.register(bus);
        NeoForge.EVENT_BUS.addListener(ReputationMod::commands);
        NeoForge.EVENT_BUS.addListener(ReputationMod::onDeath);
        NeoForge.EVENT_BUS.addListener(ReputationMod::onPlayerTick);
        ReputationRules.register();
        NeoForge.EVENT_BUS.addListener(ReputationWorldEvents::onPlayerTick);
    }

    private static String rank(int score) {
        if (score >= 151) return "reputation.rank.legend";
        if (score >= 101) return "reputation.rank.trusted";
        if (score >= 51) return "reputation.rank.neutral";
        if (score >= 1) return "reputation.rank.outcast";
        if (score >= -49) return "reputation.rank.wanted";
        return "reputation.rank.nemesis";
    }

    public static void adjust(ServerPlayer player, int delta, String reason) {
        int before = player.getData(REP);
        int after = Math.max(-100, Math.min(200, before + delta));
        if (before == after) return;
        player.setData(REP, after);
        player.sendSystemMessage(Component.translatable("reputation.message.change", (after > before ? "+" : "") + (after - before), Component.translatable("reputation.reason." + reason.replace(" ", "_")), after, Component.translatable(rank(after))), true);
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (victim instanceof Villager) {
            adjust(killer, -20, "killed villager");
        } else if (victim instanceof ServerPlayer) {
            adjust(killer, -30, "killed player");
        } else if (victim instanceof Animal) {
            adjust(killer, -2, "killed animal");
        } else if (victim instanceof Monster) {
            long now = killer.level().getGameTime();
            long last = REWARD_COOLDOWN.getOrDefault(killer.getUUID(), Long.MIN_VALUE / 2);
            if (now - last >= 100) {
                adjust(killer, 1, "defeated monster");
                REWARD_COOLDOWN.put(killer.getUUID(), now);
            }
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long time = player.level().getGameTime();
        if (time % 40 != 0) return;
        int score = player.getData(REP);


    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("reputation")
            .then(Commands.literal("get").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                player.sendSystemMessage(Component.translatable("reputation.message.status", player.getData(REP), Component.translatable(rank(player.getData(REP))), player.getData(SUSPICION)));
                return 1;
            }))
            .then(Commands.literal("help").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.translatable("reputation.message.help"), false);
                return 1;
            }))
            .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 200)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    player.setData(REP, value);
                    player.sendSystemMessage(Component.translatable("reputation.message.set", value));
                    return value;
                })))
            .then(Commands.literal("add").requires(source -> source.hasPermission(2))
                .then(Commands.argument("delta", IntegerArgumentType.integer(-300, 300)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    adjust(player, IntegerArgumentType.getInteger(ctx, "delta"), "admin");
                    return 1;
                })))
            .then(Commands.literal("suspicion").requires(source -> source.hasPermission(2))
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    player.setData(SUSPICION, value);
                    player.sendSystemMessage(Component.translatable("reputation.message.suspicion", value));
                    return value;
                }))));
    }
}
