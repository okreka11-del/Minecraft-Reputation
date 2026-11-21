package com.vova.reputation;

import com.vova.reputation.client.ReputationHud;
import com.vova.reputation.client.ReputationBookScreen;
import com.vova.reputation.client.ReputationDialogueScreen;
import com.vova.reputation.client.ReputationVillageScreen;
import com.vova.reputation.client.ReputationArmyScreen;
import com.vova.reputation.client.ReputationKeys;
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

    // Include mounted pets in the authoritative squad overview. The order loop
    // reports them as restrained instead of silently hiding them from the count.
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
                if (!(context.player() instanceof ServerPlayer player)) return;
                // Dead players and spectators cannot command a squad. Do not scan
                // nearby entities for their forged or stale overview requests.
                // Send zero so an already-open panel immediately disables orders.
                if (!player.isAlive() || player.isSpectator()) {
                    // Invalidate the cached squad snapshot as well as the visible
                    // count. Otherwise a quick return from spectator mode can
                    // reuse a stale positive count during the scan cooldown and
                    // temporarily enable orders for wolves no longer nearby.
                    var inactiveData = player.getPersistentData();
                    inactiveData.putInt("reputationCachedArmyCount", 0);
                    inactiveData.putBoolean("reputationHasArmyRefreshTick", false);
                    PacketDistributor.sendToPlayer(player, new ArmyOverview(0));
                    return;
                }
                // Enforce the refresh interval on the server as well: modified clients
                // must not be able to flood nearby-entity scans with ArmyRequest packets.
                // Use the player's live tick counter, resetting naturally after respawn.
                var data = player.getPersistentData();
                int now = player.tickCount;
                int last = data.getInt("reputationLastArmyRefreshTick");
                // A player can cross a portal and reopen G before the ten-tick
                // scan cooldown expires. Never reuse an Overworld squad count in
                // the Nether (or vice versa): the cached result is dimension-local.
                String dimension = player.level().dimension().location().toString();
                boolean sameDimension = dimension.equals(data.getString("reputationCachedArmyDimension"));
                // A teleport within the short refresh cooldown must not reuse
                // the previous location's squad count. Check squared distance
                // without allocating positions or scanning entities.
                long dx = (long) player.blockPosition().getX() - data.getInt("reputationCachedArmyX");
                long dy = (long) player.blockPosition().getY() - data.getInt("reputationCachedArmyY");
                long dz = (long) player.blockPosition().getZ() - data.getInt("reputationCachedArmyZ");
                boolean sameArea = dx * dx + dy * dy + dz * dz <= 64;
                if (data.getBoolean("reputationHasArmyRefreshTick") && sameDimension && sameArea
                        && now >= last && now - last < 10) {
                    // Still answer a fresh G-open request. Dropping the response made
                    // the panel appear broken when players closed and reopened it
                    // within the scan cooldown. Reuse the last authoritative count
                    // without repeating the nearby-entity search.
                    PacketDistributor.sendToPlayer(player, new ArmyOverview(
                        Math.max(0, data.getInt("reputationCachedArmyCount"))));
                    return;
                }
                int count = ownedWolves(player).size();
                data.putInt("reputationCachedArmyCount", count);
                data.putString("reputationCachedArmyDimension", dimension);
                data.putInt("reputationCachedArmyX", player.blockPosition().getX());
                data.putInt("reputationCachedArmyY", player.blockPosition().getY());
                data.putInt("reputationCachedArmyZ", player.blockPosition().getZ());
                data.putBoolean("reputationHasArmyRefreshTick", true);
                data.putInt("reputationLastArmyRefreshTick", now);
                PacketDistributor.sendToPlayer(player, new ArmyOverview(count));
            }));
        registrar.playToClient(ArmyOverview.TYPE, ArmyOverview.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                // Refresh in place: replacing the screen loses keyboard focus and
                // resets its navigation state every time the player presses R.
                Minecraft client = Minecraft.getInstance();
                if (client.screen instanceof ReputationArmyScreen armyScreen) {
                    // The initial request is no longer pending once the menu is visible.
                    ReputationKeys.consumeArmyOpenRequest();
                    armyScreen.updateSquadCount(payload.count());
                } else if (client.screen == null && ReputationKeys.consumeArmyOpenRequest()
                        && client.player != null && client.player.isAlive()
                        && !client.player.isSpectator()) {
                    // Only the initial G press may open the menu. Late automatic/manual
                    // refresh replies must never reopen a panel the player has closed.
                    client.setScreen(new ReputationArmyScreen(payload.count()));
                } else {
                    // A different GUI took focus or the player became inactive.
                    ReputationKeys.consumeArmyOpenRequest();
                }
            }));
        registrar.playToServer(ArmyOrder.TYPE, ArmyOrder.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                // Spectators and dead players must not issue combat or movement orders.
                // Keep this permission check server-side: clients can forge packets.
                if (player.isSpectator() || !player.isAlive()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Приказы отряду недоступны в режиме наблюдателя или после смерти."), true);
                    return;
                }
                // Only explicit known orders, only living tame wolves owned by this player.
                if (payload.order() < 0 || payload.order() > 5) return;
                // Limit expensive pathfinding and squad scans from repeated or forged
                // packets. This is per player, server authoritative and uses entity
                // ticks (not client timestamps). Allow the first order immediately;
                // a new entity tick counter after respawn must not inherit a lockout.
                var armyOrderData = player.getPersistentData();
                int lastArmyOrderTick = armyOrderData.getInt("reputationLastArmyOrderTick");
                // Emergency retreat must override a recently issued attack or move order.
                // Keep the separate two-tick attempt throttle below to prevent
                // modified clients from flooding the server with retreat packets.
                if (payload.order() != 3 && lastArmyOrderTick > 0
                        && player.tickCount >= lastArmyOrderTick
                        && player.tickCount - lastArmyOrderTick < 5) return;
                // Failed orders must remain quickly retryable, but a modified client
                // must not spam expensive navigation checks every server tick.
                // Keep a separate short attempt throttle: successful orders retain
                // their five-tick cooldown, while failed orders can be corrected
                // after just two ticks instead of waiting for the full cooldown.
                int lastArmyAttemptTick = armyOrderData.getInt("reputationLastArmyAttemptTick");
                if (lastArmyAttemptTick > 0 && player.tickCount >= lastArmyAttemptTick
                        && player.tickCount - lastArmyAttemptTick < 2) return;
                armyOrderData.putInt("reputationLastArmyAttemptTick", player.tickCount);
                // Check the actual owned squad before validating a combat target or
                // waypoint. Without this, a player with no wolves receives a misleading
                // "no marked target" / "unsafe destination" error instead of the
                // actionable reason the command cannot run. Scan only once per order.
                var wolves = ownedWolves(player);
                if (wolves.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Приказ не выполнен: ваших приручённых волков нет в радиусе 24 блоков."), true);
                    return;
                }
                // Attack only the last hostile non-player target actually struck by the owner.
                // Never allow this packet to designate another player or a peaceful villager.
                net.minecraft.world.entity.LivingEntity attackTarget = null;
                if (payload.order() == 4) {
                    var candidate = player.getLastHurtMob();
                    if (candidate == null || !candidate.isAlive()
                            || candidate.level() != player.level()
                            // A previous hit is not a permanent attack marker. Require a
                            // recent strike and a visible target before issuing a squad attack.
                            || player.getLastHurtMobTimestamp() > player.tickCount
                            || player.tickCount - player.getLastHurtMobTimestamp() > 200
                            || !player.hasLineOfSight(candidate)
                            || candidate.distanceToSqr(player) > 24.0D * 24.0D
                            || !(candidate instanceof net.minecraft.world.entity.monster.Monster)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Нет видимой недавно отмеченной цели: ударьте монстра в радиусе 24 блоков."), true);
                        return;
                    }
                    // Wolves are ground-based melee units. A target far above or below
                    // the owner can lure them off cliffs or into unreachable terrain.
                    // Keep the existing stance intact instead of starting unsafe pursuit.
                    if (Math.abs(candidate.getY() - player.getY()) > 6.0D) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Цель слишком высоко или низко: подведите отряд на безопасную высоту."), true);
                        return;
                    }
                    // Respect vanilla scoreboard teams: a monster may be allied to
                    // the player through team rules even though it is hostile by type.
                    // Never turn a squad command into an attack on a teammate.
                    if (player.isAlliedTo(candidate)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Цель состоит в вашей команде: волки не атакуют союзников."), true);
                        return;
                    }
                    // Wolves normally avoid creepers because their explosions can kill
                    // the entire pack. A manual attack order must not bypass that
                    // safety rule and send owned pets into point-blank explosions.
                    if (candidate instanceof net.minecraft.world.entity.monster.Creeper) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Волки не атакуют криперов по приказу: взрыв опасен для отряда."), true);
                        return;
                    }
                    // A Warden can kill an entire wolf squad almost instantly, even
                    // through armor. Preserve vanilla wolf avoidance of this threat.
                    if (candidate instanceof net.minecraft.world.entity.monster.warden.Warden) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Волки не атакуют Хранителя по приказу: он смертельно опасен для отряда."), true);
                        return;
                    }
                    // Wither explosions and its withering projectiles devastate tame
                    // wolves. Do not let a forged or ordinary squad order send pets
                    // into this boss encounter.
                    if (candidate instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Волки не атакуют Иссушителя по приказу: взрывы и иссушение опасны для отряда."), true);
                        return;
                    }
                    // Never pursue a marked monster through an active portal. Wolves
                    // following an attack target could cross dimensions and become
                    // separated from their owner, even if the portal has no collision.
                    var markedFeet = candidate.blockPosition();
                    var markedHead = markedFeet.above();
                    var markedLevel = player.serverLevel();
                    if (!markedLevel.hasChunkAt(markedFeet) || !markedLevel.hasChunkAt(markedHead)
                            || markedLevel.getBlockState(markedFeet).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                            || markedLevel.getBlockState(markedHead).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                            || markedLevel.getBlockState(markedFeet).is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                            || markedLevel.getBlockState(markedHead).is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                            || markedLevel.getBlockState(markedFeet).is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                            || markedLevel.getBlockState(markedHead).is(net.minecraft.world.level.block.Blocks.END_GATEWAY)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Цель находится в портале: волки останутся в вашем измерении."), true);
                        return;
                    }
                    // A marked monster standing in lava is not a safe target for
                    // ground-based wolves. Reject the whole attack before changing
                    // any wolf's current guard or follow stance.
                    if (candidate.isInLava()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Цель находится в лаве: не отправляйте волков в огонь."), true);
                        return;
                    }
                    // Surface-swimming monsters are dangerous too: isUnderWater()
                    // misses targets whose head is above water, even though wolves
                    // would still have to swim after them. Include bubble columns,
                    // which can pull pets away from the owner or trap them underwater.
                    // Reject before changing any existing squad stance.
                    if (candidate.isInWaterOrBubble()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Цель в воде или пузырьковой колонне: выманите её на сушу, чтобы волки не утонули."), true);
                        return;
                    }
                    // Fire and magma can injure wolves even when the monster itself
                    // is not in lava. Do not override the squad's current stance
                    // to chase a target standing in these damaging blocks.
                    var targetFeet = candidate.blockPosition();
                    var targetHead = targetFeet.above();
                    var targetLevel = player.serverLevel();
                    var targetGround = targetLevel.getBlockState(targetFeet.below());
                    var targetFeetState = targetLevel.getBlockState(targetFeet);
                    var targetHeadState = targetLevel.getBlockState(targetHead);
                    if (targetGround.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                            || targetGround.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                && targetGround.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                            || targetGround.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                && targetGround.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                            || targetFeetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                            || targetFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                            || targetHeadState.is(net.minecraft.world.level.block.Blocks.FIRE)
                            || targetHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                            // Thorny plants and powder snow can injure or trap a pack
                            // even though they do not form solid collision blocks.
                            || targetFeetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                            || targetHeadState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                            || targetFeetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                            || targetHeadState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                            || targetFeetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                            || targetHeadState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                            || targetFeetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                            || targetHeadState.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Цель находится на опасной поверхности: сначала выманите её в безопасное место."), true);
                        return;
                    }
                    // Wolves fight at melee range. Even a safe target block can border
                    // lava, fire or cactus that damages pets as they approach it.
                    // Reject the entire attack rather than silently sacrificing a pack.
                    // A single hazardous side must not block an otherwise safe melee
                    // approach. Accept the attack if at least one adjacent standing
                    // position is safe; wolves can approach from that side.
                    boolean safeApproach = false;
                    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        var adjacentFeet = targetFeet.relative(direction);
                        var adjacentHead = adjacentFeet.above();
                        if (!targetLevel.hasChunkAt(adjacentFeet) || !targetLevel.hasChunkAt(adjacentHead)) {
                            continue;
                        }
                        var feet = targetLevel.getBlockState(adjacentFeet);
                        var head = targetLevel.getBlockState(adjacentHead);
                        var ground = targetLevel.getBlockState(adjacentFeet.below());
                        // Adjacent melee positions must be safe underfoot as well as
                        // clear of non-solid hazards that harm or trap tame wolves.
                        if (ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || ground.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                    && ground.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || ground.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                    && ground.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || feet.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || head.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || feet.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || head.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || feet.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || head.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || feet.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || head.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                // Melee wolves must stand next to their target. Even when the target
                                // is dry, water in adjacent melee cells can pull the pack into
                                // a river or deep pool. Reject these attack orders as well.
                                || targetLevel.getFluidState(adjacentFeet).is(net.minecraft.tags.FluidTags.WATER)
                                || targetLevel.getFluidState(adjacentHead).is(net.minecraft.tags.FluidTags.WATER)
                                || targetLevel.getFluidState(adjacentFeet).is(net.minecraft.tags.FluidTags.LAVA)
                                || targetLevel.getFluidState(adjacentHead).is(net.minecraft.tags.FluidTags.LAVA)
                                || feet.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || head.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || feet.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || head.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || feet.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || head.is(net.minecraft.world.level.block.Blocks.CACTUS)) {
                            continue;
                        }
                        // A wolf needs a walkable floor and enough room for its body.
                        // An empty but unsupported or blocked side is not a usable route.
                        if (ground.isFaceSturdy(targetLevel, adjacentFeet.below(), net.minecraft.core.Direction.UP)
                                && feet.getCollisionShape(targetLevel, adjacentFeet).isEmpty()
                                && head.getCollisionShape(targetLevel, adjacentHead).isEmpty()) {
                            safeApproach = true;
                            break;
                        }
                    }
                    if (!safeApproach) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Нет безопасного подхода к цели: проверьте воду, огонь, препятствия и поверхность."), true);
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
                            || !level.getWorldBorder().isWithinBounds(ground)
                            || !level.getWorldBorder().isWithinBounds(headroom)
                            || !level.hasChunkAt(waypoint)
                            || !level.hasChunkAt(ground)
                            || !level.hasChunkAt(headroom)
                            || !level.getBlockState(waypoint).getCollisionShape(level, waypoint).isEmpty()
                            || !level.getBlockState(headroom).getCollisionShape(level, headroom).isEmpty()
                            || !level.getFluidState(waypoint).isEmpty()
                            || !level.getFluidState(headroom).isEmpty()
                            // Portal interiors have no ordinary collision, but a wolf
                            // sent there can be transported into another dimension.
                            // Keep waypoint orders within the owner's current world.
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                            // Even with a solid top face, hot blocks can injure a wolf.
                            // Check the supporting block as well as the destination.
                            || !level.getFluidState(ground).isEmpty()
                            || level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                            || level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                    && level.getBlockState(ground).getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                            || level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                    && level.getBlockState(ground).getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
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
                            // Cobwebs have little collision but trap wolves on arrival.
                            || level.getBlockState(waypoint).is(net.minecraft.world.level.block.Blocks.COBWEB)
                            || level.getBlockState(headroom).is(net.minecraft.world.level.block.Blocks.COBWEB)
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
                        if (!level.hasChunkAt(feetNeighbor) || !level.hasChunkAt(headNeighbor)
                                || !level.getWorldBorder().isWithinBounds(feetNeighbor)
                                || !level.getWorldBorder().isWithinBounds(headNeighbor)) {
                            touchesHazard = true;
                            break;
                        }
                        // Wolves have a physical width: an adjacent lava source, fire
                        // or cactus can damage them even if their destination is air.
                        var feetState = level.getBlockState(feetNeighbor);
                        var headState = level.getBlockState(headNeighbor);
                        // Portals beside a waypoint can transport a wolf when it brushes the edge.
                        // Keep the entire standing footprint clear, not just the target block.
                        // Adjacent powder snow, cobwebs and berry bushes can trap or injure
                        // a wolf brushing the waypoint edge, even when both target blocks are air.
                        if (feetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || headState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || feetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || headState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || feetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || headState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                // Wither roses damage wolves on contact even without a solid collision box.
                                || feetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || headState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)) {
                            touchesHazard = true;
                            break;
                        }
                        if (feetState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                || headState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                || feetState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                || headState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                || feetState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                                || headState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                                || feetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || headState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || feetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || headState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || feetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || headState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || level.getFluidState(feetNeighbor).is(net.minecraft.tags.FluidTags.LAVA)
                                || level.getFluidState(headNeighbor).is(net.minecraft.tags.FluidTags.LAVA)
                                // Powder snow traps ground-based pets; wither roses
                                // damage them even when their collision is empty.
                                // Berry bushes and lit campfires can also hurt wolves
                                // when they brush against the neighboring block.
                                || feetState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || headState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || feetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || headState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || feetState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && feetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || headState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && headState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || feetState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && feetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || headState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && headState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || feetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || headState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || feetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || headState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                // Adjacent webs can trap wolves brushing against the edge.
                                || feetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || headState.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
                            touchesHazard = true;
                            break;
                        }
                    }
                    if (touchesHazard) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Небезопасная точка: рядом огонь, костёр, лава, опасные растения, рыхлый снег или не загружена местность."), true);
                        return;
                    }
                }
                // Rally and retreat navigate toward the owner's live position.
                // If the owner is standing in fire or lava, sending tame wolves
                // directly to that destination can kill the squad. Keep their
                // existing orders and let the owner move to safety first.
                if (payload.order() == 2 || payload.order() == 3) {
                    var ownerFeet = player.blockPosition();
                    var ownerHead = ownerFeet.above();
                    var level = player.serverLevel();
                    var ownerGround = level.getBlockState(ownerFeet.below());
                    // Wolves taking rally/retreat orders stand on the owner's ground.
                    // Magma and lit campfires damage paws even if the owner's feet
                    // are in air; preserve existing orders instead of routing there.
                    boolean dangerousGround = ownerGround.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        || ownerGround.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                            && ownerGround.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                        || ownerGround.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                            && ownerGround.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
                    boolean burningDestination = dangerousGround || player.isInLava()
                        || level.getFluidState(ownerFeet).is(net.minecraft.tags.FluidTags.LAVA)
                        || level.getFluidState(ownerHead).is(net.minecraft.tags.FluidTags.LAVA)
                        // A rally into a river can strand or drown the pack even if the
                        // owner is swimming safely. Require dry ground for squad assembly.
                        || player.isInWaterOrBubble()
                        || level.getFluidState(ownerFeet).is(net.minecraft.tags.FluidTags.WATER)
                        || level.getFluidState(ownerHead).is(net.minecraft.tags.FluidTags.WATER)
                        || level.getBlockState(ownerFeet).is(net.minecraft.world.level.block.Blocks.FIRE)
                        || level.getBlockState(ownerFeet).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                        || level.getBlockState(ownerHead).is(net.minecraft.world.level.block.Blocks.FIRE)
                        || level.getBlockState(ownerHead).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE);
                    // A safe-looking owner's position can still trap or hurt wolves:
                    // powder snow sinks ground mobs, cobwebs immobilize them, and
                    // berry bushes / wither roses damage them without solid collision.
                    // Reject both feet and head hazards before changing any order.
                    var feetState = level.getBlockState(ownerFeet);
                    var headState = level.getBlockState(ownerHead);
                    // Portals and cactus can teleport or hurt wolves even when the
                    // destination appears traversable. Never redirect a squad into them.
                    boolean trappingDestination = feetState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                        || headState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                        || feetState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                        || headState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                        || feetState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                        || headState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                        || feetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                        || headState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                        || feetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                        || headState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                        || feetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                        || headState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                        || feetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                        || headState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                        || feetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                        || headState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE);
                    // The owner may be flying, falling or standing on a ledge.
                    // Wolves should not be ordered to gather at an unsupported
                    // position or a cramped one-block passage, even when it has
                    // no fire or trapping plants. Check without loading chunks.
                    var ownerGroundPos = ownerFeet.below();
                    boolean unsupportedDestination =
                        !level.getWorldBorder().isWithinBounds(ownerFeet)
                        || !level.getWorldBorder().isWithinBounds(ownerHead)
                        || !level.getWorldBorder().isWithinBounds(ownerGroundPos)
                        || !level.hasChunkAt(ownerFeet)
                        || !level.hasChunkAt(ownerHead)
                        || !level.hasChunkAt(ownerGroundPos)
                        || !level.getBlockState(ownerGroundPos).isFaceSturdy(
                            level, ownerGroundPos, net.minecraft.core.Direction.UP)
                        || !feetState.getCollisionShape(level, ownerFeet).isEmpty()
                        || !headState.getCollisionShape(level, ownerHead).isEmpty();
                    // Wolves are wider than a block's centre. A seemingly clear
                    // owner position can still touch a cactus, fire, portal or lava
                    // in a neighboring block. Check both body heights before
                    // redirecting the squad, without loading adjacent chunks.
                    boolean dangerousNeighbor = false;
                    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        var adjacentFeet = ownerFeet.relative(direction);
                        var adjacentHead = ownerHead.relative(direction);
                        var adjacentGround = adjacentFeet.below();
                        if (!level.hasChunkAt(adjacentFeet) || !level.hasChunkAt(adjacentHead)
                                || !level.hasChunkAt(adjacentGround)
                                || !level.getWorldBorder().isWithinBounds(adjacentFeet)
                                || !level.getWorldBorder().isWithinBounds(adjacentHead)
                                || !level.getWorldBorder().isWithinBounds(adjacentGround)) {
                            dangerousNeighbor = true;
                            break;
                        }
                        var adjacentFeetState = level.getBlockState(adjacentFeet);
                        var adjacentHeadState = level.getBlockState(adjacentHead);
                        var adjacentGroundState = level.getBlockState(adjacentGround);
                        if (adjacentGroundState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || adjacentGroundState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                    && adjacentGroundState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || adjacentGroundState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                    && adjacentGroundState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || level.getFluidState(adjacentFeet).is(net.minecraft.tags.FluidTags.LAVA)
                                || level.getFluidState(adjacentHead).is(net.minecraft.tags.FluidTags.LAVA)
                                // Keep retreat and rally positions clear of adjacent water:
                                // a wolf moving around its owner can step into a deep pool.
                                || level.getFluidState(adjacentFeet).is(net.minecraft.tags.FluidTags.WATER)
                                || level.getFluidState(adjacentHead).is(net.minecraft.tags.FluidTags.WATER)
                                // A wolf brushing a neighboring hot floor block can take
                                // contact damage even when the owner's own block is safe.
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                    && adjacentFeetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                    && adjacentFeetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || adjacentFeetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || adjacentHeadState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                            dangerousNeighbor = true;
                            break;
                        }
                    }
                    if (burningDestination || trappingDestination || unsupportedDestination || dangerousNeighbor) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Точка сбора опасна: выберите сухую твёрдую поверхность с двумя свободными блоками высоты, вдали от воды, огня и ловушек."), true);
                        return;
                    }
                }
                // Only valid, actionable requests consume the cooldown. An invalid
                // target, unsafe waypoint or absent squad must not delay the next
                // corrected order. Keep throttling before costly per-wolf pathfinding.
                int changed = 0;
                int restrained = 0;
                int mounted = 0;
                int leashed = 0;
                int attackTooWounded = 0;
                int attackBurning = 0;
                int attackFreezing = 0;
                int attackLowAir = 0;
                int attackOutOfRange = 0;
                int attackNoSight = 0;
                int attackNoPath = 0;
                int waypointUnsafeWolf = 0;
                int waypointOutOfRange = 0;
                int waypointNoPath = 0;
                int waypointNavigationRejected = 0;
                for (var wolf : wolves) {
                    // A mounted wolf cannot act on any squad order until dismounted:
                    // even Follow/Guard would misleadingly report success while it
                    // remains trapped in a vehicle. Leashed wolves may still switch
                    // Follow/Guard stance, but cannot obey movement/attack orders.
                    if (wolf.isPassenger()) {
                        restrained++;
                        mounted++;
                        continue;
                    }
                    if (payload.order() >= 2 && wolf.isLeashed()) {
                        restrained++;
                        leashed++;
                        continue;
                    }
                    if (payload.order() == 4) {
                        // Do not force a burning pet to chase an enemy while it takes
                        // fire damage. The owner can still order it to retreat.
                        if (wolf.isOnFire()) {
                            attackBurning++;
                            continue;
                        }
                        // A wolf trapped in powder snow or already freezing must not
                        // be ordered deeper into combat. Retreat remains available.
                        if (wolf.isInPowderSnow || wolf.isFullyFrozen()) {
                            attackFreezing++;
                            continue;
                        }
                        // A submerged wolf running low on air must not be sent
                        // deeper into combat. Retreat and follow remain available
                        // so its owner can guide it back to safety.
                        if (wolf.isInWaterOrBubble() && wolf.getAirSupply() <= 60) {
                            attackLowAir++;
                            continue;
                        }
                        // Keep critically wounded wolves out of offensive orders.
                        // They retain their current stance and can still receive
                        // follow, guard or retreat commands from their owner.
                        if (wolf.getHealth() <= wolf.getMaxHealth() * 0.25F) {
                            attackTooWounded++;
                            continue;
                        }
                        // Do not dispatch a pet against a target it cannot reach from
                        // the squad's local area. The owner's 24-block check alone
                        // permits a wolf on the opposite edge to chase 48 blocks.
                        if (wolf.distanceToSqr(attackTarget) > 24.0D * 24.0D) {
                            attackOutOfRange++;
                            continue;
                        }
                        if (!wolf.hasLineOfSight(attackTarget)) {
                            attackNoSight++;
                            continue;
                        }
                        // Do not cancel an existing guard or follow order if this
                        // wolf has no traversable route to the marked monster.
                        // Check from the wolf's server-side position before mutating
                        // its combat stance; the owner's line of sight is not enough.
                        // A wolf already within melee reach needs no navigation path.
                        // Pathfinding can return null for a target standing beside it;
                        // that must not discard an otherwise valid attack command.
                        boolean inMeleeRange = wolf.distanceToSqr(attackTarget) <= 2.5D * 2.5D;
                        var attackPath = inMeleeRange ? null : wolf.getNavigation().createPath(attackTarget, 0);
                        if (!inMeleeRange && (attackPath == null || !attackPath.canReach())) {
                            attackNoPath++;
                            continue;
                        }
                        wolf.setOrderedToSit(false);
                        wolf.setLastHurtByMob(null);
                        wolf.getNavigation().stop();
                        wolf.setTarget(attackTarget);
                        changed++;
                    } else if (payload.order() == 5) {
                        // Optional travel must not override a wolf escaping fire, freezing
                        // or drowning. Preserve its current stance and navigation;
                        // Follow and emergency Retreat remain available.
                        if (wolf.isOnFire() || wolf.isInPowderSnow || wolf.isFullyFrozen()
                                || (wolf.isInWaterOrBubble() && wolf.getAirSupply() <= 60)) {
                            waypointUnsafeWolf++;
                            continue;
                        }
                        // Keep waypoint orders local to each wolf as well as the owner.
                        // A wolf at the far edge of the squad radius must not be sent
                        // up to 48 blocks away across unloaded or dangerous terrain.
                        if (wolf.distanceToSqr(waypoint.getX() + 0.5D,
                                waypoint.getY(), waypoint.getZ() + 0.5D) > 24.0D * 24.0D) {
                            waypointOutOfRange++;
                            continue;
                        }
                        // A wolf already standing at the destination has fulfilled
                        // the waypoint order. Pathfinding can return no path for a
                        // zero-distance destination, especially while the wolf sits.
                        // Release a previous guard without requiring a needless route.
                        boolean alreadyAtWaypoint = wolf.distanceToSqr(
                                waypoint.getX() + 0.5D, waypoint.getY(),
                                waypoint.getZ() + 0.5D) <= 1.5D * 1.5D
                                && Math.abs(wolf.getY() - waypoint.getY()) <= 1.0D
                                && wolf.level().clip(new net.minecraft.world.level.ClipContext(
                                    wolf.getEyePosition(),
                                    net.minecraft.world.phys.Vec3.atCenterOf(waypoint),
                                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                                    wolf)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
                        var path = alreadyAtWaypoint ? null : wolf.getNavigation().createPath(waypoint, 0);
                        if (!alreadyAtWaypoint && (path == null || !path.canReach())) {
                            waypointNoPath++;
                            continue;
                        }
                        // Start the new route before discarding the old combat/guard
                        // state. A path may be valid but navigation can still reject it.
                        // Sitting wolves cannot reliably start navigation while the
                        // ordered-to-sit flag remains set. Release that stance only
                        // after validating the route, and restore it if navigation
                        // rejects the move so a failed order preserves the guard.
                        boolean wasSitting = wolf.isOrderedToSit();
                        if (!alreadyAtWaypoint && wasSitting) wolf.setOrderedToSit(false);
                        if (!alreadyAtWaypoint && !wolf.getNavigation().moveTo(path, 1.2D)) {
                            if (wasSitting) wolf.setOrderedToSit(true);
                            waypointNavigationRejected++;
                            continue;
                        }
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.setOrderedToSit(false);
                        if (alreadyAtWaypoint) wolf.getNavigation().stop();
                        changed++;
                    } else if (payload.order() == 3) {
                        // Retreat must actually withdraw from combat, not just clear a target.
                        // Resume movement even if this wolf was previously guarding.
                        // Do not cancel an existing guard or combat order when a
                        // retreat route cannot be found (e.g. behind a closed door).
                        // Proximity through a wall or across a ledge is not a completed retreat.
                        // Require a similar elevation and clear sight before stopping movement.
                        boolean alreadySafe = wolf.distanceToSqr(player) <= 3.0D * 3.0D
                            && Math.abs(wolf.getY() - player.getY()) <= 1.5D
                            && wolf.hasLineOfSight(player);
                        var retreatPath = alreadySafe ? null : wolf.getNavigation().createPath(player, 0);
                        if (!alreadySafe && (retreatPath == null || !retreatPath.canReach())) continue;
                        // Navigation can reject a route even after createPath reports
                        // reachability. Preserve the previous task on that failure.
                        boolean wasSitting = wolf.isOrderedToSit();
                        if (!alreadySafe && wasSitting) wolf.setOrderedToSit(false);
                        if (!alreadySafe && !wolf.getNavigation().moveTo(retreatPath, 1.35D)) {
                            if (wasSitting) wolf.setOrderedToSit(true);
                            continue;
                        }
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.setOrderedToSit(false);
                        if (alreadySafe) wolf.getNavigation().stop();
                        changed++;
                    } else if (payload.order() == 2) {
                        // Rally nearby owned wolves without teleporting or changing their owner.
                        // Keep the existing stance if rally is impossible. An
                        // unreachable player should not accidentally release a guard.
                        // A wolf behind a wall or on another floor is not actually gathered,
                        // even when its straight-line distance to the owner is small.
                        boolean alreadyGathered = wolf.distanceToSqr(player) <= 3.0D * 3.0D
                            && Math.abs(wolf.getY() - player.getY()) <= 1.5D
                            && wolf.hasLineOfSight(player);
                        var rallyPath = alreadyGathered ? null : wolf.getNavigation().createPath(player, 0);
                        if (!alreadyGathered && (rallyPath == null || !rallyPath.canReach())) continue;
                        // Start a viable rally route before discarding an active guard
                        // or attack task. A reachable path may still be rejected by
                        // navigation; in that case preserve the wolf's old orders.
                        boolean wasSitting = wolf.isOrderedToSit();
                        if (!alreadyGathered && wasSitting) wolf.setOrderedToSit(false);
                        if (!alreadyGathered && !wolf.getNavigation().moveTo(rallyPath, 1.2D)) {
                            if (wasSitting) wolf.setOrderedToSit(true);
                            continue;
                        }
                        wolf.setTarget(null);
                        wolf.setLastHurtByMob(null);
                        wolf.setOrderedToSit(false);
                        if (alreadyGathered) wolf.getNavigation().stop();
                        changed++;
                    } else if (payload.order() == 1) {
                        // Sitting disables escape navigation. Never order a wolf to
                        // stay in water, lava, fire, or mid-air where it could drown,
                        // burn, or fall before it can move again. Other wolves may
                        // still accept the order independently.
                        if (!wolf.onGround() || wolf.isInWater() || wolf.isInLava()
                                || wolf.isOnFire()) continue;
                        // A seated guard cannot step away from damaging terrain.
                        // Check the actual wolf position on the server, not the
                        // owner's position or a client-supplied coordinate.
                        var guardFeet = wolf.blockPosition();
                        var guardGround = guardFeet.below();
                        var guardLevel = player.serverLevel();
                        // The guard command must not freeze a wolf over an unloaded
                        // edge or on a non-supporting block (e.g. a collapsing ledge).
                        // Validate both the standing position and its support before
                        // touching the wolf's navigation or sitting state.
                        if (!guardLevel.hasChunkAt(guardFeet)
                                || !guardLevel.hasChunkAt(guardGround)
                                || !guardLevel.hasChunkAt(guardFeet.above())
                                || !guardLevel.getWorldBorder().isWithinBounds(guardFeet)
                                || !guardLevel.getWorldBorder().isWithinBounds(guardGround)
                                || !guardLevel.getWorldBorder().isWithinBounds(guardFeet.above())
                                || !guardLevel.getBlockState(guardGround).isFaceSturdy(
                                    guardLevel, guardGround, net.minecraft.core.Direction.UP)) continue;
                        var guardFeetState = guardLevel.getBlockState(guardFeet);
                        var guardHead = guardFeet.above();
                        var guardHeadState = guardLevel.getBlockState(guardHead);
                        var guardGroundState = guardLevel.getBlockState(guardGround);
                        // A solid support is not enough: a wolf may be standing in a
                        // narrow opening, flowing liquid or a newly placed block.
                        // Never immobilize it where it could suffocate or drown.
                        if (!guardFeetState.getCollisionShape(guardLevel, guardFeet).isEmpty()
                                || !guardHeadState.getCollisionShape(guardLevel, guardHead).isEmpty()
                                || !guardLevel.getFluidState(guardFeet).isEmpty()
                                || !guardLevel.getFluidState(guardHead).isEmpty()
                                || !guardLevel.getFluidState(guardGround).isEmpty()) continue;
                        // A wolf ordered to sit cannot move off a hot supporting block.
                        // Magma and lit campfires have sturdy tops but deal contact
                        // damage; unlit campfires remain valid guard positions.
                        if (guardGroundState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || guardGroundState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                    && guardGroundState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || guardGroundState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                    && guardGroundState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) continue;
                        // A seated guard cannot escape non-solid damaging or trapping
                        // blocks. Collision and fluid checks above do not catch these.
                        // Leave the wolf's existing order intact when its feet or head
                        // occupy thorns, fire, powder snow or cobwebs.
                        if (guardFeetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.COBWEB)) continue;
                        // Guard is a persistent sit order: unlike a moving wolf, a
                        // seated pet cannot step away if a portal is beside it.
                        // Reject portal blocks at its feet, head and immediate sides
                        // before changing its current stance.
                        boolean guardTouchesPortal = false;
                        for (var guardDirection : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            var sideFeet = guardFeet.relative(guardDirection);
                            var sideHead = guardHead.relative(guardDirection);
                            if (!guardLevel.hasChunkAt(sideFeet) || !guardLevel.hasChunkAt(sideHead)
                                    || !guardLevel.getWorldBorder().isWithinBounds(sideFeet)
                                    || !guardLevel.getWorldBorder().isWithinBounds(sideHead)) {
                                guardTouchesPortal = true;
                                break;
                            }
                            var sideFeetState = guardLevel.getBlockState(sideFeet);
                            var sideHeadState = guardLevel.getBlockState(sideHead);
                            // Sitting wolves cannot sidestep adjacent damaging blocks. A cactus,
                            // open flame or lava touching their standing footprint is
                            // unsafe even when the feet/head blocks themselves are air.
                            if (sideFeetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                    // A seated wolf cannot move away when a neighboring
                                    // web or powder-snow block traps its hitbox. Reject
                                    // these before clearing its previous combat stance.
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && sideFeetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && sideHeadState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && sideFeetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && sideHeadState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    // Flowing water beside a sitting wolf can push its hitbox
                                    // into a stream while its ordered-to-sit AI cannot escape.
                                    // Reject only the affected guard order, preserving the
                                    // wolf's previous stance and the rest of the squad.
                                    || guardLevel.getFluidState(sideFeet).is(net.minecraft.tags.FluidTags.WATER)
                                    || guardLevel.getFluidState(sideHead).is(net.minecraft.tags.FluidTags.WATER)
                                    || guardLevel.getFluidState(sideFeet).is(net.minecraft.tags.FluidTags.LAVA)
                                    || guardLevel.getFluidState(sideHead).is(net.minecraft.tags.FluidTags.LAVA)) {
                                guardTouchesPortal = true;
                                break;
                            }
                            if (sideFeetState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                    || sideFeetState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                                    || sideHeadState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)) {
                                guardTouchesPortal = true;
                                break;
                            }
                        }
                        if (guardTouchesPortal
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)) continue;
                        if (guardGroundState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                || guardGroundState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && guardGroundState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                || guardGroundState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && guardGroundState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                // Cactus can hurt a wolf standing inside its block even
                                // without a neighboring cactus. Never freeze it there.
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                // A seated wolf cannot navigate out of cobwebs. Do not
                                // turn a temporary entanglement into a permanent guard post.
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || guardFeetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) continue;
                        // A wolf can also be hurt by hazards at head height or by
                        // touching lava/fire or a hot magma block beside its body.
                        // Apply the same adjacent magma safety rule as waypoint orders.
                        if (guardHeadState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                || guardHeadState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) continue;
                        boolean adjacentDanger = false;
                        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            var beside = guardFeet.relative(direction);
                            // Do not load neighboring chunks merely to evaluate a guard
                            // position. If either adjacent cell is unavailable or outside
                            // the world border, do not immobilize a wolf on that edge.
                            if (!guardLevel.hasChunkAt(beside)
                                    || !guardLevel.hasChunkAt(beside.above())
                                    || !guardLevel.getWorldBorder().isWithinBounds(beside)
                                    || !guardLevel.getWorldBorder().isWithinBounds(beside.above())) {
                                adjacentDanger = true;
                                break;
                            }
                            var feetState = guardLevel.getBlockState(beside);
                            var headState = guardLevel.getBlockState(beside.above());
                            // One pass checks both cactus contact and all other side hazards.
                            // Avoid repeating four neighbor lookups per guard command.
                            if (feetState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                    || headState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                    || headState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                    || headState.is(net.minecraft.world.level.block.Blocks.FIRE)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                    || headState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                                    || guardLevel.getFluidState(beside).is(net.minecraft.tags.FluidTags.LAVA)
                                    || guardLevel.getFluidState(beside.above()).is(net.minecraft.tags.FluidTags.LAVA)
                                    // Non-solid plants and snow can hurt or trap a seated wolf.
                                    || feetState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                    || headState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                    || headState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                    || headState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                    // A seated guard cannot navigate out of neighboring webs.
                                    || feetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                    || headState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                    // Lit campfires are damaging even with a safe floor.
                                    || feetState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && feetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    || headState.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                                        && headState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && feetState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                                    || headState.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                                        && headState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                                adjacentDanger = true;
                                break;
                            }
                        }
                        if (adjacentDanger) continue;
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
                // Failed orders must not lock out an immediate corrected command.
                // Only a squad that actually changed state consumes the rate limit.
                if (changed > 0) armyOrderData.putInt("reputationLastArmyOrderTick", player.tickCount);
                // A valid target from the owner's perspective may still be hidden
                // or out of range for every squad member. Explain that case rather
                // than reporting an ambiguous successful "0 wolves" attack.
                // Mounted wolves cannot obey even Follow or Guard. Explain this instead
                // of reporting a misleading success count of zero for orders 0/1.
                // Leashed wolves remain eligible for Follow and Guard.
                if (changed == 0 && restrained == wolves.size()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        mounted == wolves.size()
                            ? "Приказ не выполнен: все волки находятся в транспорте. Высадите хотя бы одного."
                            : leashed == wolves.size()
                                ? "Приказ не выполнен: все волки на поводках. Отвяжите хотя бы одного для этого приказа."
                                : "Приказ не выполнен: волки в транспорте или на поводках. Освободите хотя бы одного."), true);
                } else if (payload.order() == 5 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Приказ к точке не выполнен: " + waypointUnsafeWolf + " волков в опасности (огонь, мороз или нехватка воздуха), "
                            + waypointOutOfRange + " волков дальше 24 блоков, "
                            + waypointNoPath + " не находят путь, " + waypointNavigationRejected
                            + " не смогли начать движение. Подведите отряд ближе или устраните препятствия."), true);
                } else if (payload.order() == 1 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Охрана не назначена: волки не стоят на земле или находятся рядом с опасными блоками."), true);
                } else if (payload.order() == 2 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Сбор не удался: волки не могут проложить путь к вам. Подойдите ближе или уберите препятствия."), true);
                } else if (payload.order() == 3 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Отступление не удалось: волки не могут проложить путь к вам."), true);
                } else if (payload.order() == 4 && changed == 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Атака не выполнена: " + attackTooWounded + " волков тяжело ранены (≤25% здоровья), " + attackBurning + " горят, " + attackFreezing + " замерзают, " + attackLowAir + " задыхаются в воде, " + attackOutOfRange + " слишком далеко, "
                            + attackNoSight + " не видят цель, " + attackNoPath
                            + " не могут найти путь. Подведите отряд ближе или устраните препятствия."), true);
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        (payload.order() == 0 ? "Следовать: " : payload.order() == 1 ? "Охранять: " : payload.order() == 2 ? "Сбор отряда: " : payload.order() == 3 ? "Отступают к вам: " : payload.order() == 4 ? "Атакуют цель: " : "Идут к точке: ") + changed + " из " + wolves.size() + " волков" + (changed < wolves.size() ? " (не выполнили: " + (wolves.size() - changed) + (restrained > 0 ? ", из них " + mounted + " в транспорте" + (leashed > 0 ? " и " + leashed + " на поводках" : "") : "") + "; проверьте расстояние, путь и безопасность)" : "")), true);
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
