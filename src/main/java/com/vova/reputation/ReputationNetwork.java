package com.vova.reputation;

import com.vova.reputation.client.ReputationHud;
import com.vova.reputation.client.ReputationBookScreen;
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

    public record BookSync(int score, int suspicion, String history, String factions) implements CustomPacketPayload {
        public static final Type<BookSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "book_sync"));
        public static final StreamCodec<FriendlyByteBuf, BookSync> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeInt(packet.score());
                buf.writeInt(packet.suspicion());
                buf.writeUtf(packet.history(), 4096);
                buf.writeUtf(packet.factions(), 128);
            }, buf -> new BookSync(buf.readInt(), buf.readInt(), buf.readUtf(4096), buf.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(ScoreSync.TYPE, ScoreSync.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> ReputationHud.updateScore(payload.score())));
        registrar.playToClient(BookSync.TYPE, BookSync.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new ReputationBookScreen(payload.score(), payload.suspicion(), payload.history(), payload.factions()))));
    }

    public static void openBook(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new BookSync(player.getData(ReputationMod.REP), player.getData(ReputationMod.SUSPICION), player.getData(ReputationMod.HISTORY), player.getData(ReputationMod.FACTION_STANDINGS)));
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ScoreSync(player.getData(ReputationMod.REP)));
    }
}
