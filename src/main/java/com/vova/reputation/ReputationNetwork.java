package com.vova.reputation;

import com.vova.reputation.client.ReputationHud;
import com.vova.reputation.client.ReputationBookScreen;
import com.vova.reputation.client.ReputationDialogueScreen;
import com.vova.reputation.client.ReputationVillageScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ReputationMod.ID, bus = EventBusSubscriber.Bus.MOD)
public final class ReputationNetwork {
    private ReputationNetwork() {}

    public record ScoreSync(int score) implements CustomPacketPayload {
        public static final Type<ScoreSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "score_sync"));
        public static final StreamCodec<FriendlyByteBuf, ScoreSync> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> buf.writeInt(packet.score()),
                buf -> new ScoreSync(buf.readInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BookSync(int score, int suspicion, String history, String factions, String settlement, int hunterProgress, boolean hunterCompleted) implements CustomPacketPayload {
        public static final Type<BookSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "book_sync"));
        public static final StreamCodec<FriendlyByteBuf, BookSync> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeInt(packet.score());
                buf.writeInt(packet.suspicion());
                buf.writeUtf(packet.history(), 4096);
                buf.writeUtf(packet.factions(), 128);
                buf.writeUtf(packet.settlement(), 64);
                buf.writeInt(packet.hunterProgress());
                buf.writeBoolean(packet.hunterCompleted());
            }, buf -> new BookSync(buf.readInt(), buf.readInt(), buf.readUtf(4096), buf.readUtf(128), buf.readUtf(64), buf.readInt(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record JournalRequest() implements CustomPacketPayload {
        public static final Type<JournalRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "journal_request"));
        public static final StreamCodec<FriendlyByteBuf, JournalRequest> STREAM_CODEC =
            StreamCodec.unit(new JournalRequest());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DialogueOpen(int entityId, int score, String greeting) implements CustomPacketPayload {
        public static final Type<DialogueOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "dialogue_open"));
        public static final StreamCodec<FriendlyByteBuf, DialogueOpen> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeInt(p.entityId()); buf.writeInt(p.score()); buf.writeUtf(p.greeting(), 256);
            }, buf -> new DialogueOpen(buf.readInt(), buf.readInt(), buf.readUtf(256)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record DialogueAction(int entityId, int action) implements CustomPacketPayload {
        public static final Type<DialogueAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "dialogue_action"));
        public static final StreamCodec<FriendlyByteBuf, DialogueAction> STREAM_CODEC =
            StreamCodec.of((buf, p) -> { buf.writeInt(p.entityId()); buf.writeInt(p.action()); },
                buf -> new DialogueAction(buf.readInt(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record VillageRequest() implements CustomPacketPayload {
        public static final Type<VillageRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "village_request"));
        public static final StreamCodec<FriendlyByteBuf, VillageRequest> STREAM_CODEC =
            StreamCodec.unit(new VillageRequest());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record VillageSync(String status, int stage, int buildings, int villagers, int standing, int score)
            implements CustomPacketPayload {
        public static final Type<VillageSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "village_sync"));
        public static final StreamCodec<FriendlyByteBuf, VillageSync> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeUtf(p.status(), 128);
                buf.writeInt(p.stage()); buf.writeInt(p.buildings()); buf.writeInt(p.villagers());
                buf.writeInt(p.standing()); buf.writeInt(p.score());
            }, buf -> new VillageSync(buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(VillageRequest.TYPE, VillageRequest.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) ReputationSettlements.sendOverview(player);
            }));
        registrar.playToClient(VillageSync.TYPE, VillageSync.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new ReputationVillageScreen(payload.status(), payload.stage(),
                    payload.buildings(), payload.villagers(), payload.standing(), payload.score()))));
        registrar.playToServer(JournalRequest.TYPE, JournalRequest.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) openBook(player);
            }));
        registrar.playToClient(DialogueOpen.TYPE, DialogueOpen.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new ReputationDialogueScreen(payload.entityId(), payload.score(), payload.greeting()))));
        registrar.playToServer(DialogueAction.TYPE, DialogueAction.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player) || payload.action() != 1) return;
                var entity = player.serverLevel().getEntity(payload.entityId());
                if (entity instanceof net.minecraft.world.entity.npc.Villager villager
                        && player.distanceToSqr(villager) <= 64
                        && player.getData(ReputationMod.REP) >= 0
                        && ReputationMod.factionStandings(player)[0] > -40) {
                    villager.setTradingPlayer(player);
                    villager.openTradingScreen(player, villager.getDisplayName(), villager.getVillagerData().getLevel());
                }
            }));
        registrar.playToClient(ScoreSync.TYPE, ScoreSync.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> ReputationHud.updateScore(payload.score())));
        registrar.playToClient(BookSync.TYPE, BookSync.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new ReputationBookScreen(payload.score(), payload.suspicion(), payload.history(), payload.factions(), payload.settlement(), payload.hunterProgress(), payload.hunterCompleted()))));
    }

    public static void openDialogue(ServerPlayer player, net.minecraft.world.entity.npc.Villager villager) {
        int score = player.getData(ReputationMod.REP);
        int standing = ReputationMod.factionStandings(player)[0];
        String greeting = score <= -80 ? "Прошу, пощади нас..."
            : score < 0 || standing <= -40 ? "Я тебе не доверяю."
            : score >= 200 ? "Герой Света! Добро пожаловать!"
            : score >= 151 ? "Наш защитник вернулся!"
            : score >= 100 ? "Рады видеть союзника!"
            : "Приветствую, путник!";
        PacketDistributor.sendToPlayer(player, new DialogueOpen(villager.getId(), score, greeting));
    }

    public static void sendVillage(ServerPlayer player, String status, int stage, int buildings, int villagers) {
        PacketDistributor.sendToPlayer(player, new VillageSync(status, stage, buildings, villagers,
            ReputationMod.factionStandings(player)[0], player.getData(ReputationMod.REP)));
    }

    public static void openBook(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new BookSync(player.getData(ReputationMod.REP), player.getData(ReputationMod.SUSPICION), player.getData(ReputationMod.HISTORY), player.getData(ReputationMod.FACTION_STANDINGS), ReputationSettlements.nearbyStatus(player), player.getData(ReputationMod.HUNTER_PROGRESS), player.getData(ReputationMod.HUNTER_COMPLETED)));
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ScoreSync(player.getData(ReputationMod.REP)));
    }
}
