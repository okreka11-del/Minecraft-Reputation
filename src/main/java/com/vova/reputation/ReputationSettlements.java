package com.vova.reputation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent, bounded village occupation. No existing blocks are overwritten:
 * every construction step only places decorations into verified empty spaces.
 * This deliberately avoids destroying player-built structures.
 */
public final class ReputationSettlements {
    private ReputationSettlements() {}
    private static final String SAVE_ID = "reputation_settlements";
    private static final int MAX_VILLAGES = 128;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReputationSettlements::commands);
        NeoForge.EVENT_BUS.addListener(ReputationSettlements::tick);
    }

    public static final class Village {
        public final BlockPos center;
        public boolean occupied;
        public int stage;
        public long nextBuild;
        public boolean houseBuilt;
        public Village(BlockPos center, boolean occupied, int stage, long nextBuild) {
            this.center = center; this.occupied = occupied;
            this.stage = stage; this.nextBuild = nextBuild;
        }
    }

    public static final class Data extends SavedData {
        public final List<Village> villages = new ArrayList<>();
        public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag v = list.getCompound(i);
                Village village = new Village(new BlockPos(v.getInt("X"), v.getInt("Y"), v.getInt("Z")),
                    v.getBoolean("Occupied"), v.getInt("Stage"), v.getLong("NextBuild"));
                village.houseBuilt = v.getBoolean("HouseBuilt");
                data.villages.add(village);
            }
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Village v : villages) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("X", v.center.getX());
                entry.putInt("Y", v.center.getY());
                entry.putInt("Z", v.center.getZ());
                entry.putBoolean("Occupied", v.occupied);
                entry.putInt("Stage", v.stage);
                entry.putLong("NextBuild", v.nextBuild);
                entry.putBoolean("HouseBuilt", v.houseBuilt);
                list.add(entry);
            }
            tag.put("Villages", list);
            return tag;
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), SAVE_ID);
    }

    /** Server-authoritative summary for the player's nearest registered settlement. */
    public static String nearbyStatus(ServerPlayer player) {
        Village village = closest(data(player.serverLevel()), player.blockPosition(), 96);
        if (village == null) return "none";
        return (village.occupied ? "occupied" : "free") + ":" + village.stage;
    }

    private static Village closest(Data data, BlockPos position, int radius) {
        Village best = null;
        double distance = (double) radius * radius;
        for (Village village : data.villages) {
            double d = village.center.distSqr(position);
            if (d <= distance) { distance = d; best = village; }
        }
        return best;
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("settlement")
            .then(Commands.literal("status").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                Village village = closest(data(player.serverLevel()), player.blockPosition(), 96);
                player.sendSystemMessage(Component.literal(village == null
                    ? "No registered settlement within 96 blocks."
                    : "Settlement: " + (village.occupied ? "occupied" : "free") + ", stage " + village.stage));
                return 1;
            }))
            .then(Commands.literal("register").requires(s -> s.hasPermission(2)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerLevel level = player.serverLevel();
                Data data = data(level);
                if (data.villages.size() >= MAX_VILLAGES || closest(data, player.blockPosition(), 96) != null
                    || level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48)).isEmpty()) {
                    player.sendSystemMessage(Component.literal("Registration denied: need nearby villagers, free space and capacity."));
                    return 0;
                }
                data.villages.add(new Village(player.blockPosition(), false, 0, level.getGameTime()));
                data.setDirty();
                player.sendSystemMessage(Component.literal("Settlement registered. Its state will persist."));
                return 1;
            }))
            .then(Commands.literal("capture").executes(ctx -> change(ctx.getSource().getPlayerOrException(), true)))
            .then(Commands.literal("liberate").executes(ctx -> change(ctx.getSource().getPlayerOrException(), false))));
    }


    /** A one-time village welcome cottage. Triggered by entering a populated village. */
    private static void welcomeHouse(ServerLevel level, ServerPlayer player, Data saved) {
        Village village = closest(saved, player.blockPosition(), 48);
        if (village == null) {
            if (saved.villages.size() >= MAX_VILLAGES) return;
            if (level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(28)).size() < 2) return;
            village = new Village(player.blockPosition(), false, 0, level.getGameTime());
            saved.villages.add(village);
            saved.setDirty();
        }
        if (village.houseBuilt || village.occupied) return;
        if (player.getData(ReputationMod.REP) < 0) return;
        // Try nearby positions rather than replacing any existing terrain or buildings.
        int[][] offsets = {{10,0},{-10,0},{0,10},{0,-10},{14,8},{-14,-8},{8,-14},{-8,14}};
        for (int[] offset : offsets) {
            BlockPos site = village.center.offset(offset[0], 0, offset[1]);
            BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site);
            if (placeCottage(level, base)) {
                village.houseBuilt = true;
                saved.setDirty();
                player.sendSystemMessage(Component.literal("A welcome cottage has appeared in the village!"));
                return;
            }
        }
    }

    private static boolean placeCottage(ServerLevel level, BlockPos base) {
        // Validate every block before placing anything; no overwrites.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos column = base.offset(x, 0, z);
            if (!level.hasChunkAt(column) || !level.getBlockState(column.below()).isSolidRender(level, column.below())) return false;
            for (int y = 0; y <= 5; y++) if (!level.getBlockState(column.above(y)).isAir()) return false;
        }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos floor = base.offset(x, 0, z);
            level.setBlockAndUpdate(floor, Blocks.OAK_PLANKS.defaultBlockState());
            boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3;
            for (int y = 1; y <= 3; y++) {
                if (!wall) continue;
                boolean doorway = x == 0 && z == -3 && y <= 2;
                boolean window = y == 2 && ((Math.abs(x) == 3 && z == 0) || (Math.abs(z) == 3 && x == 0));
                if (!doorway) level.setBlockAndUpdate(floor.above(y),
                    window ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            }
            level.setBlockAndUpdate(floor.above(4), Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(0, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(1, 1, 1), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(base.offset(-1, 1, 1), Blocks.RED_BED.defaultBlockState());
        return true;
    }

    private static int change(ServerPlayer player, boolean capture) {
        int score = player.getData(ReputationMod.REP);
        if ((capture && score > -80) || (!capture && score < 151)) {
            player.sendSystemMessage(Component.literal("Insufficient reputation for this faction."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Data data = data(level);
        Village village = closest(data, player.blockPosition(), 64);
        if (village == null || village.occupied == capture) {
            player.sendSystemMessage(Component.literal("No eligible settlement nearby. Register one first."));
            return 0;
        }
        // Claiming requires local faction control, not just a reputation command.
        if (capture && level.getEntitiesOfClass(Pillager.class,
            new net.minecraft.world.phys.AABB(village.center).inflate(48)).size() < 3) {
            player.sendSystemMessage(Component.literal("Capture requires at least 3 pillagers in the settlement."));
            return 0;
        }
        if (!capture && level.getEntitiesOfClass(Pillager.class,
            new net.minecraft.world.phys.AABB(village.center).inflate(32)).size() > 0) {
            player.sendSystemMessage(Component.literal("Defeat nearby pillagers before liberation."));
            return 0;
        }
        village.occupied = capture;
        village.stage = 0;
        village.nextBuild = level.getGameTime() + 1200;
        data.setDirty();
        player.sendSystemMessage(Component.literal(capture ? "Occupation started." : "Liberation started."));
        return 1;
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 200 != 0) return;
        Data data = data(level);
        // Run the entry detector at most once per 200 ticks per player.
        for (ServerPlayer player : level.players()) {
            if ((level.getGameTime() + player.getId()) % 200 == 0) welcomeHouse(level, player, data);
        }
        for (Village village : data.villages) {
            if (village.stage >= 3 || level.getGameTime() < village.nextBuild
                || !level.hasChunkAt(village.center)) continue;
            int placed = buildStage(level, village);
            // Do not progress without any viable construction location.
            if (placed > 0) {
                village.stage++;
                village.nextBuild = level.getGameTime() + 24000;
                data.setDirty();
            } else {
                village.nextBuild = level.getGameTime() + 2400;
                data.setDirty();
            }
        }
    }

    private static int buildStage(ServerLevel level, Village village) {
        int placed = 0;
        BlockState material = village.occupied
            ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState();
        int radius = 10 + village.stage * 3;
        // Four small lookout posts, not a destructive block replacement algorithm.
        for (int i = 0; i < 4; i++) {
            int dx = i == 0 ? radius : i == 1 ? -radius : 0;
            int dz = i == 2 ? radius : i == 3 ? -radius : 0;
            BlockPos anchor = village.center.offset(dx, 0, dz);
            if (!level.hasChunkAt(anchor)) continue;
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor);
            if (Math.abs(ground.getY() - village.center.getY()) > 8) continue;
            // Only build on solid ground with three empty blocks above.
            if (!level.getBlockState(ground.below()).isSolidRender(level, ground.below())) continue;
            boolean free = true;
            for (int y = 0; y < 3; y++) if (!level.getBlockState(ground.above(y)).isAir()) free = false;
            if (!free) continue;
            for (int y = 0; y < 3; y++) {
                level.setBlockAndUpdate(ground.above(y), material);
                placed++;
            }
            BlockPos cap = ground.above(3);
            if (level.getBlockState(cap).isAir()) {
                level.setBlockAndUpdate(cap, (village.occupied ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState());
                placed++;
            }
        }
        return placed;
    }
}
