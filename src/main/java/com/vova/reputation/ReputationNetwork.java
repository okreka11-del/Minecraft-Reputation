package com.vova.reputation;

import com.vova.reputation.client.ReputationHud;
import com.vova.reputation.client.ReputationBookScreen;
import com.vova.reputation.client.ReputationDialogueScreen;
import com.vova.reputation.client.ReputationVillageScreen;
import com.vova.reputation.client.ReputationArmyScreen;
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

    public record ArmyRequest() implements CustomPacketPayload {
        public static final Type<ArmyRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "army_request"));
        public static final StreamCodec<FriendlyByteBuf, ArmyRequest> STREAM_CODEC =
            StreamCodec.unit(new ArmyRequest());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ArmyOverview(int count) implements CustomPacketPayload {
        public static final Type<ArmyOverview> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "army_overview"));
        public static final StreamCodec<FriendlyByteBuf, ArmyOverview> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeInt(p.count()), buf -> new ArmyOverview(buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ArmyOrder(int order) implements CustomPacketPayload {
        public static final Type<ArmyOrder> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "army_order"));
        public static final StreamCodec<FriendlyByteBuf, ArmyOrder> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeInt(p.order()), buf -> new ArmyOrder(buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static java.util.List<net.minecraft.world.entity.animal.Wolf> ownedWolves(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Wolf.class,
            player.getBoundingBox().inflate(24),
            wolf -> wolf.isAlive() && wolf.isTame()
                && player.getUUID().equals(wolf.getOwnerUUID())
                // The bounding box is only a broad-phase lookup; enforce the advertised
                // 24-block radius rather than including distant diagonal corners.
                && wolf.distanceToSqr(player) <= 24.0D * 24.0D);
    }

    public record FartRequest() implements CustomPacketPayload {
        public static final Type<FartRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReputationMod.ID, "fart_request"));
        public static final StreamCodec<FriendlyByteBuf, FartRequest> STREAM_CODEC =
            StreamCodec.unit(new FartRequest());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ArmyRequest.TYPE, ArmyRequest.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player)
                    PacketDistributor.sendToPlayer(player, new ArmyOverview(ownedWolves(player).size()));
            }));
        registrar.playToClient(ArmyOverview.TYPE, ArmyOverview.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new ReputationArmyScreen(payload.count()))));
        registrar.playToServer(ArmyOrder.TYPE, ArmyOrder.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                // Only explicit known orders, only living tame wolves owned by this player.
                if (payload.order() < 0 || payload.order() > 5) return;
                // Attack only the last hostile non-player target actually struck by the owner.
                // Never allow this packet to designate another player or a peaceful villager.
                net.minecraft.world.entity.LivingEntity attackTarget = null;
                if (payload.order() == 4) {
                    var candidate = player.getLastHurtMob();
                    if (candidate == null || !candidate.isAlive()
                            || candidate.level() != player.level()
                            // A previous hit is not a permanent attack marker. Require a
                            // recent strike and a visible target before issuing a squad attack.
                            || player.tickCount - player.getLastHurtMobTimestamp() > 200
                            || !player.hasLineOfSight(candidate)
                            || candidate.distanceToSqr(player) > 24.0D * 24.0D
                            || !(candidate instanceof net.minecraft.world.entity.monster.Monster)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Нет видимой недавно отмеченной цели: ударьте монстра в радиусе 24 блоков."), true);
                        return;
                    }
                    attackTarget = candidate;
                }
                // Select a waypoint from the server-side player's view, not client coordinates.
                net.minecraft.core.BlockPos waypoint = null;
                if (payload.order() == 5) {
                    var hit = player.pick(24.0D, 0.0F, false);
                    if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                            || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Укажите взглядом на блок в радиусе 24 блоков."), true);
                        return;
                    }
                    waypoint = blockHit.getBlockPos().relative(blockHit.getDirection());
                    // Reject unsafe destinations before disturbing any wolf's current order.
                    // A click on a wall, cliff edge or unloaded chunk is not a safe
                    // standing position; don't route pets into a fall or solid block.
                    var level = player.serverLevel();
                    var ground = waypoint.below();
                    var headroom = waypoint.above();
                    // A wolf needs both feet and head clearance. Checking only the
                    // destination block accepted one-block-high gaps and low ceilings.
                    // Reject liquid destinations as well: empty collision alone does
                    // not mean a safe place for a ground-based squad to assemble.
                    if (!level.getWorldBorder().isWithinBounds(waypoint)
                            || !level.getWorldBorder().isWithinBounds(headroom)
                            || !level.hasChunkAt(waypoint)
                            || !level.hasChunkAt(ground)
                            || !level.hasChunkAt(headroom)
                            || !level.getBlockState(waypoint).getCollisionShape(level, waypoint).isEmpty()
                            || !level.getBlockState(headroom).getCollisionShape(level, headroom).isEmpty()
                            || !level.getFluidState(waypoint).isEmpty()
                            || !level.getFluidState(headroom).isEmpty()
                            // Even with a solid top face, hot blocks can injure a wolf.
                            // Check the supporting block as well as the destination.
                            || !level.getFluidState(ground).isEmpty()
                            || level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                            || level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                            || level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.FIRE)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                            // A wolf occupies two vertical blocks. Non-solid hazards in
                            // the upper block can still damage it despite free collision.
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.FIRE)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                            // Thin damaging blocks may have empty collision shapes.
                            // Keep pets out of thorny plants and freezing powder snow.
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.CACTUS)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.CACTUS)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                            || !level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Небезопасная точка: выберите свободное место на твёрдой поверхности."), true);
                        return;
                    }
                    // Cacti hurt entities touching their sides, even when the
                    // destination itself is empty. A free block directly beside
                    // a cactus is not a safe standing spot for a wolf.
                    boolean touchesHazard = false;
                    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        var feetNeighbor = waypoint.relative(direction);
                        var headNeighbor = headroom.relative(direction);
                        if (!level.hasChunkAt(feetNeighbor) || !level.hasChunkAt(headNeighbor)) {
                            touchesHazard = true;
                            break;
                        }
                        // Wolves have a physical width: an adjacent lava source, fire
                        // or cactus can damage them even if their destination is air.
                        var feetState = level.getBlockState(feetNeighbor);
                        var headState = level.getBlockState(headNeighbor);
                        if (feetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || headState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || feetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || headState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || feetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || headState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || level.getFluidState(feetNeighbor).is(net.minecraft.tags.FluidTags.LAVA)
                                || level.getFluidState(headNeighbor).is(net.minecraft.tags.FluidTags.LAVA)
                                // Powder snow traps ground-based pets; wither roses
                                // damage them even when their collision is empty.
                                || feetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || headState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || feetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || headState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)) {
                            touchesHazard = true;
                            break;
                        }
                    }
                    if (touchesHazard) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Небезопасная точка: рядом огонь, лава, колючие растения, рыхлый снег или не загружена местность."), true);
                        return;
                    }
                }
                var wolves = ownedWolves(player);
                if (wolves.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Приказ не выполнен: ваших приручённых волков нет в радиусе 24 блоков."), true);
                    return;
                }
                int changed = 0;
                for (var wolf : wolves) {
                    if (payload.order() == 4) {
                        // Do not dispatch a pet against a target it cannot reach from
                        // the squad's local area. The owner's 24-block check alone
                        // permits a wolf on the opposite edge to chase 48 blocks.
                        if (wolf.distanceToSqr(attackTarget) > 24.0D * 24.0D
                                || !wolf.hasLineOfSight(attackTarget)) continue;
                        wolf.setOrderedToSit(false);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setTarget(attackTarget);
                        changed++;
                    } else if (payload.order() == 5) {
                        // Keep waypoint orders local to each wolf as well as the owner.
                        // A wolf at the far edge of the squad radius must not be sent
                        // up to 48 blocks away across unloaded or dangerous terrain.
                        if (wolf.distanceToSqr(waypoint.getX() + 0.5D,
                                waypoint.getY(), waypoint.getZ() + 0.5D) > 24.0D * 24.0D) continue;
                        // Compute the path before cancelling guard/combat/follow.
                        // If the destination cannot be reached, preserve the wolf's
                        // existing stance and navigation instead of silently losing it.
                        var path = wolf.getNavigation().createPath(waypoint, 0);
                        if (path == null || !path.canReach()) continue;
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setOrderedToSit(false);
                        if (wolf.getNavigation().moveTo(path, 1.2D)) changed++;
                    } else if (payload.order() == 3) {
                        // Retreat must actually withdraw from combat, not just clear a target.
                        // Resume movement even if this wolf was previously guarding.
                        // Do not cancel an existing guard or combat order when a
                        // retreat route cannot be found (e.g. behind a closed door).
                        boolean alreadySafe = wolf.distanceToSqr(player) <= 3.0D * 3.0D;
                        var retreatPath = alreadySafe ? null : wolf.getNavigation().createPath(player, 0);
                        if (!alreadySafe && (retreatPath == null || !retreatPath.canReach())) continue;
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setOrderedToSit(false);
                        if (alreadySafe || wolf.getNavigation().moveTo(retreatPath, 1.35D)) changed++;
                    } else if (payload.order() == 2) {
                        // Rally nearby owned wolves without teleporting or changing their owner.
                        // Keep the existing stance if rally is impossible. An
                        // unreachable player should not accidentally release a guard.
                        boolean alreadyGathered = wolf.distanceToSqr(player) <= 3.0D * 3.0D;
                        var rallyPath = alreadyGathered ? null : wolf.getNavigation().createPath(player, 0);
                        if (!alreadyGathered && (rallyPath == null || !rallyPath.canReach())) continue;
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setOrderedToSit(false);
                        if (alreadyGathered || wolf.getNavigation().moveTo(rallyPath, 1.2D)) changed++;
                    } else if (payload.order() == 1) {
                        // Guard means hold this spot now, even if the wolf was chasing
                        // a target or navigating to a previous rally destination.
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setOrderedToSit(true);
                        changed++;
                    } else {
                        // Following must override a previous guard, rally or combat chase.
                        // Clearing navigation lets vanilla owner-follow AI take control
                        // immediately instead of finishing an obsolete destination.
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setOrderedToSit(false);
                        changed++;
                    }
                }
                // A valid target from the owner's perspective may still be hidden
                // or out of range for every squad member. Explain that case rather
                // than reporting an ambiguous successful "0 wolves" attack.
                if (payload.order() == 5 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Ни один волк не может дойти до точки в радиусе 24 блоков от него."), true);
                } else if (payload.order() == 2 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Сбор не удался: волки не могут проложить путь к вам. Подойдите ближе или уберите препятствия."), true);
                } else if (payload.order() == 3 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Отступление не удалось: волки не могут проложить путь к вам."), true);
                } else if (payload.order() == 4 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Приказ не выполнен: ни один ваш волк не видит цель в радиусе 24 блоков."), true);
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        (payload.order() == 0 ? "Следовать: " : payload.order() == 1 ? "Охранять: " : payload.order() == 2 ? "Сбор отряда: " : payload.order() == 3 ? "Отступают к вам: " : payload.order() == 4 ? "Атакуют цель: " : "Идут к точке: ") + changed + " волков"), true);
                }
            }));
        registrar.playToServer(FartRequest.TYPE, FartRequest.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player)
                    ReputationFarts.play(player);
            }));
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
                        && villager.isAlive()
                        && !player.isSpectator()
                        && player.distanceToSqr(villager) <= 64
                        && player.hasLineOfSight(villager)
                        && player.getData(ReputationMod.REP) >= 0
                        && ReputationMod.factionStandings(player)[0] > -40) {
                    ReputationTrading.open(player, villager);
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
