package com.vova.reputation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.animal.IronGolem;
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
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<Integer>> SUSPICION = ATTACHMENTS.register("suspicion",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    // Persisted compact action log, independent for each player and copied on death.
    public static final Supplier<AttachmentType<String>> HISTORY = ATTACHMENTS.register("history",
        () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    // Independent, persistent faction standings. Defaults do not alter existing worlds.
    public static final String[] FACTIONS = {"villagers", "pillagers", "animals", "monsters", "golems", "dragon"};
    public static final Supplier<AttachmentType<String>> FACTION_STANDINGS = ATTACHMENTS.register("faction_standings",
        () -> AttachmentType.builder(() -> "0,0,0,0,0,0").serialize(Codec.STRING).copyOnDeath().build());

    public static int[] factionStandings(ServerPlayer player) {
        int[] values = new int[FACTIONS.length];
        String[] raw = player.getData(FACTION_STANDINGS).split(",");
        for (int i = 0; i < Math.min(values.length, raw.length); i++) {
            try { values[i] = Math.max(-100, Math.min(200, Integer.parseInt(raw[i]))); }
            catch (NumberFormatException ignored) { values[i] = 0; }
        }
        return values;
    }

    public static void adjustFaction(ServerPlayer player, int index, int delta) {
        if (index < 0 || index >= FACTIONS.length || delta == 0) return;
        int[] values = factionStandings(player);
        values[index] = clampReputation((long) values[index] + delta);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) encoded.append(',');
            encoded.append(values[i]);
        }
        player.setData(FACTION_STANDINGS, encoded.toString());
    }

    // Persistent introductory quest: defeat ten hostile mobs. Rewards are paid once.
    // Existing saves start at zero progress; all quest state survives death.
    public static final Supplier<AttachmentType<Integer>> HUNTER_PROGRESS = ATTACHMENTS.register("hunter_progress",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<Boolean>> HUNTER_COMPLETED = ATTACHMENTS.register("hunter_completed",
        () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    // Saved quest counters may be corrupted or imported from older versions.
    // Clamp before incrementing to avoid integer overflow and negative progress.
    static int nextHunterProgress(int current) {
        return HunterProgress.next(current);
    }

    private static void advanceHunterQuest(ServerPlayer player) {
        if (player.getData(HUNTER_COMPLETED)) return;
        int progress = nextHunterProgress(player.getData(HUNTER_PROGRESS));
        player.setData(HUNTER_PROGRESS, progress);
        if (progress == 10) {
            player.setData(HUNTER_COMPLETED, true);
            player.giveExperiencePoints(50);
            adjust(player, 10, "hunter quest");
            player.sendSystemMessage(Component.translatable("reputation.quest.hunter.complete"));
        }
    }

    // Server-local anti-farm cooldowns. These are deliberately not persisted.
    private static final Map<UUID, Long> REWARD_COOLDOWN = new HashMap<>();
    // Prevent repeated PvP kills of the same victim from farming negative reputation.
    // Server-local, non-persistent cooldown: no migration of existing player saves.
    private static final Map<UUID, Map<UUID, Long>> PVP_COOLDOWNS = new HashMap<>();
    private static final long PVP_COOLDOWN_TICKS = 1200L;
    public static final Supplier<AttachmentType<Long>> REWARD_DAY = ATTACHMENTS.register("reward_day", () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).copyOnDeath().build());
    public static final Supplier<AttachmentType<Integer>> REWARD_COUNT = ATTACHMENTS.register("reward_count", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<Long>> GIFT_DAY = ATTACHMENTS.register("gift_day", () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).copyOnDeath().build());
    public static final Supplier<AttachmentType<Integer>> GIFT_COUNT = ATTACHMENTS.register("gift_count", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    // Persist the last gift tick to prevent bypassing cooldown by reconnecting/restarting.
    public static final Supplier<AttachmentType<Long>> GIFT_LAST_TICK = ATTACHMENTS.register("gift_last_tick",
        () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).copyOnDeath().build());

    public ReputationMod(IEventBus bus) {
        ATTACHMENTS.register(bus);
        ReputationBeer.register(bus);
        ReputationProfessionRegistry.register(bus);
        ReputationWorkstationAssignment.register();
        NeoForge.EVENT_BUS.addListener(ReputationMod::commands);
        NeoForge.EVENT_BUS.addListener(ReputationDragon::register);
        NeoForge.EVENT_BUS.addListener(ReputationMod::onDeath);
        NeoForge.EVENT_BUS.addListener(ReputationMod::onPlayerTick);
        ReputationRules.register();
        ReputationSettlements.register();
        ReputationSpecialists.register();
        NeoForge.EVENT_BUS.addListener(ReputationWorldEvents::onPlayerTick);
        // Gift caps and cooldowns are enforced by this server-side tick listener.
        NeoForge.EVENT_BUS.addListener(ReputationRewards::tick);
        // ReputationVillagePerks is registered via ReputationSettlements; no duplicate effect tick.
        NeoForge.EVENT_BUS.addListener(ReputationHeroEvents::tick);
        NeoForge.EVENT_BUS.addListener(ReputationCatEating::tick);
    }

    public static String rank(int score) {
        if (score >= 200) return "reputation.rank.hero_of_light";
        if (score >= 180) return "reputation.rank.best_ally";
        if (score >= 160) return "reputation.rank.good_ally";
        if (score >= 140) return "reputation.rank.reliable_ally";
        if (score >= 120) return "reputation.rank.ally";
        if (score >= 100) return "reputation.rank.acquaintance";
        if (score >= 0) return "reputation.rank.neutral";
        if (score >= -20) return "reputation.rank.unfriendly";
        if (score >= -60) return "reputation.rank.enemy";
        if (score >= -80) return "reputation.rank.villain";
        return "reputation.rank.dark_lord";
    }

    // Use long arithmetic: admin commands and large penalties must not wrap the score.
    static int clampReputation(long value) {
        return (int) Math.max(-100L, Math.min(200L, value));
    }

    public static void adjust(ServerPlayer player, int delta, String reason) {
        int before = player.getData(REP);
        int after = clampReputation((long) before + delta);
        if (before == after) return;
        player.setData(REP, after);
        String entry = (after > before ? "+" : "") + (after - before) + " | " + reason + " | " + after;
        String oldHistory = player.getData(HISTORY);
        String[] previous = oldHistory.isEmpty() ? new String[0] : oldHistory.split("\n");
        StringBuilder history = new StringBuilder(entry);
        for (int i = 0; i < Math.min(11, previous.length); i++) {
            history.append('\n').append(previous[i]);
        }
        player.setData(HISTORY, history.toString());
        ReputationNetwork.sync(player);
        grantMilestone(player, after);
        player.sendSystemMessage(Component.translatable("reputation.message.change", (after > before ? "+" : "") + (after - before), Component.translatable("reputation.reason." + reason.replace(" ", "_")), after, Component.translatable(rank(after))), true);
    }

    private static void grantMilestone(ServerPlayer player, int score) {
        String id = score >= 200 ? "hero_of_light" : score <= -100 ? "lord_of_darkness" : null;
        if (id == null) return;
        var advancement = player.server.getAdvancements().get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID, id));
        if (advancement != null) player.getAdvancements().award(advancement, "reached");
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (victim instanceof Villager) {
            adjust(killer, -2, "killed villager");
            adjustFaction(killer, 0, -2);
        } else if (victim instanceof ServerPlayer defeated) {
            if (killer.getUUID().equals(defeated.getUUID())) return;
            long now = killer.server.overworld().getGameTime();
            Map<UUID, Long> victims = PVP_COOLDOWNS.computeIfAbsent(killer.getUUID(), id -> new HashMap<>());
            // Expire stale entries to bound per-killer memory usage during normal play.
            victims.entrySet().removeIf(entry -> entry.getValue() > now || now - entry.getValue() >= PVP_COOLDOWN_TICKS);
            if (!victims.containsKey(defeated.getUUID())) {
                adjust(killer, -30, "killed player");
                victims.put(defeated.getUUID(), now);
            }
        } else if (victim instanceof EnderDragon) {
            // The dragon is a distinct faction, not a generic hostile-mob reward.
            adjustFaction(killer, 5, -40);
            adjust(killer, 20, "defeated dragon");
        } else if (victim instanceof IronGolem) {
            // Iron golems are village defenders; their standing must not be ignored.
            adjustFaction(killer, 4, -15);
            adjustFaction(killer, 0, -5);
            adjust(killer, -10, "killed golem");
        } else if (victim instanceof Animal) {
            adjust(killer, -2, "killed animal");
            adjustFaction(killer, 2, -2);
        } else if (victim instanceof Monster) {
            // Use the server-wide overworld clock so dimension travel cannot reset cooldown timing.
            long now = killer.server.overworld().getGameTime();
            // Use monotonic server game time: /time set must not reset daily anti-farm limits.
            long day = now / 24000L;
            // Apply the same tested monotonic day policy as gifts. Rewinding
            // the clock must not refill the hostile-mob reputation allowance.
            if (GiftRewardPolicy.shouldResetDailyCount(day, killer.getData(REWARD_DAY))) {
                killer.setData(REWARD_DAY, day);
                killer.setData(REWARD_COUNT, 0);
            }
            if (GiftRewardPolicy.canAward(killer.getData(REWARD_COUNT))) {
                killer.setData(REWARD_COUNT, killer.getData(REWARD_COUNT) + 1);
                adjust(killer, 1, "defeated monster");
                adjustFaction(killer, 3, -1);
                if (killer.getData(REP) >= 151) killer.giveExperiencePoints(1);
            }
            // Daily reward limit must never block progression of the one-time hunter quest.
            advanceHunterQuest(killer);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long time = player.level().getGameTime();
        if (time % 20 != 0) return;
        ReputationNetwork.sync(player);
        int score = player.getData(REP);
        if (score == 200 || score == -100) grantMilestone(player, score);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("reputation")
            .then(Commands.literal("get").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                player.sendSystemMessage(Component.translatable("reputation.message.status", player.getData(REP), Component.translatable(rank(player.getData(REP))), player.getData(SUSPICION)));
                return 1;
            }))
            .then(Commands.literal("book").executes(ctx -> {
                ReputationNetwork.openBook(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("talk").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                Villager nearby = player.serverLevel().getEntitiesOfClass(Villager.class,
                    player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
                if (nearby == null) {
                    player.sendSystemMessage(Component.literal("Подойди ближе к жителю."));
                    return 0;
                }
                int score = player.getData(REP);
                int standing = factionStandings(player)[0];
                String line = ReputationRules.villagerInteractionRefused(score, standing)
                    ? "Житель: Мы помним твои поступки. Заслужи наше доверие."
                    : score >= 120 || standing >= 60
                        ? "Житель: Ты наш защитник! Помоги деревне и получишь награду."
                        : "Житель: Защищай деревню, торгуй с нами и выполняй задания.";
                player.sendSystemMessage(Component.literal(line));
                return 1;
            }))
            .then(Commands.literal("quest").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                boolean complete = player.getData(HUNTER_COMPLETED);
                player.sendSystemMessage(Component.literal(complete
                    ? "Hunter quest completed! Reward: 50 XP and +10 reputation."
                    : "Hunter quest: defeat hostile mobs (" + player.getData(HUNTER_PROGRESS) + "/10). Reward: 50 XP and +10 reputation."));
                return 1;
            }))
            .then(Commands.literal("history").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                player.sendSystemMessage(Component.literal("Reputation history (latest first):"));
                String history = player.getData(HISTORY);
                if (history.isEmpty()) {
                    player.sendSystemMessage(Component.literal("No reputation changes recorded yet."));
                } else {
                    for (String line : history.split("\n")) {
                        String[] parts = line.split(" \\| ", 3);
                        if (parts.length == 3) {
                            player.sendSystemMessage(Component.literal(parts[0] + " | ")
                                .append(Component.translatable("reputation.reason." + parts[1].replace(" ", "_")))
                                .append(Component.literal(" | " + parts[2])));
                        } else {
                            player.sendSystemMessage(Component.literal(line));
                        }
                    }
                }
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
                    ReputationNetwork.sync(player);
                    grantMilestone(player, value);
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
