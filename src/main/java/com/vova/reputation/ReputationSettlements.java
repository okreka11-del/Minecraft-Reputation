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
import java.util.UUID;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Persistent, bounded village occupation. No existing blocks are overwritten:
 * every construction step only places decorations into verified empty spaces.
 * This deliberately avoids destroying player-built structures.
 */
public final class ReputationSettlements {
    private ReputationSettlements() {}
    private static final String SAVE_ID = "reputation_settlements";
    private static final int MAX_VILLAGES = 128;
    private static final int VILLAGE_SEPARATION = 96;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReputationSettlements::commands);
        NeoForge.EVENT_BUS.addListener(ReputationSettlements::tick);
        NeoForge.EVENT_BUS.addListener(ReputationVillagePerks::tick);
    }

    public static final class Village {
        public final BlockPos center;
        public boolean occupied;
        public int stage;
        public long nextBuild;
        public boolean houseBuilt;
        public UUID houseOwner;
        public String houseOwnerName = "";
        public int structuresBuilt;
        public int decorationsBuilt;
        public Village(BlockPos center, boolean occupied, int stage, long nextBuild) {
            this.center = center; this.occupied = occupied;
            this.stage = stage; this.nextBuild = nextBuild;
        }
    }

    public static final class HeroHouse {
        public final UUID owner;
        public final BlockPos position;
        public String name;
        public HeroHouse(UUID owner, BlockPos position, String name) {
            this.owner = owner; this.position = position; this.name = name;
        }
    }

    public static final class Data extends SavedData {
        public final List<Village> villages = new ArrayList<>();
        public final List<HeroHouse> heroHouses = new ArrayList<>();
        public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag v = list.getCompound(i);
                Village village = new Village(new BlockPos(v.getInt("X"), v.getInt("Y"), v.getInt("Z")),
                    v.getBoolean("Occupied"), v.getInt("Stage"), v.getLong("NextBuild"));
                village.houseBuilt = v.getBoolean("HouseBuilt");
                if (v.hasUUID("HouseOwner")) village.houseOwner = v.getUUID("HouseOwner");
                village.houseOwnerName = v.getString("HouseOwnerName");
                village.structuresBuilt = v.getInt("StructuresBuilt");
                village.decorationsBuilt = v.getInt("DecorationsBuilt");
                data.villages.add(village);
            }
            for (Tag entry : tag.getList("HeroHouses", Tag.TAG_COMPOUND)) {
                CompoundTag h = (CompoundTag) entry;
                if (h.hasUUID("Owner")) data.heroHouses.add(new HeroHouse(h.getUUID("Owner"),
                    new BlockPos(h.getInt("X"), h.getInt("Y"), h.getInt("Z")), h.getString("Name")));
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
                if (v.houseOwner != null) entry.putUUID("HouseOwner", v.houseOwner);
                entry.putString("HouseOwnerName", v.houseOwnerName);
                entry.putInt("StructuresBuilt", v.structuresBuilt);
                entry.putInt("DecorationsBuilt", v.decorationsBuilt);
                list.add(entry);
            }
            tag.put("Villages", list);
            ListTag houses = new ListTag();
            for (HeroHouse house : heroHouses) {
                CompoundTag h = new CompoundTag();
                h.putUUID("Owner", house.owner);
                h.putInt("X", house.position.getX());
                h.putInt("Y", house.position.getY());
                h.putInt("Z", house.position.getZ());
                h.putString("Name", house.name);
                houses.add(h);
            }
            tag.put("HeroHouses", houses);
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

    public static void sendOverview(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Village village = closest(data(level), player.blockPosition(), 96);
        int villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48)).size();
        ReputationNetwork.sendVillage(player,
            village == null ? "Нет поселения поблизости" : village.occupied ? "Захвачена" : "Свободна",
            village == null ? 0 : village.stage,
            village == null ? 0 : village.structuresBuilt + (village.houseBuilt ? 1 : 0),
            villagers);
    }

    public static boolean isInVillage(ServerPlayer player, boolean occupied) {
        Village village = closest(data(player.serverLevel()), player.blockPosition(), 48);
        return village != null && village.occupied == occupied;
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
        event.getDispatcher().register(Commands.literal("herohouse")
            .then(Commands.literal("locate").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                HeroHouse house = ownHouse(data(player.server.overworld()), player.getUUID());
                if (house == null) {
                    player.sendSystemMessage(Component.literal("No registered hero house found in the Overworld."));
                    return 0;
                }
                player.sendSystemMessage(Component.literal("Your Overworld hero house: " + house.position.getX() + " " + house.position.getY() + " " + house.position.getZ()));
                return 1;
            })));
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


    private static HeroHouse ownHouse(Data saved, UUID owner) {
        for (HeroHouse house : saved.heroHouses) if (house.owner.equals(owner)) return house;
        return null;
    }

    private static void refreshSign(ServerLevel level, HeroHouse house, String name) {
        BlockPos signPos = house.position.offset(1, 2, -4);
        if (!level.hasChunkAt(signPos)) return;
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            String first = name.length() > 15 ? name.substring(0, 8) : name;
            String second = name.length() > 15 ? name.substring(8) : "";
            if (sign.getFrontText().getMessage(0, false).getString().equals("HERO HOUSE")
                    && sign.getFrontText().getMessage(1, false).getString().equals(first)
                    && sign.getFrontText().getMessage(2, false).getString().equals(second)) return;
            sign.setText(sign.getFrontText()
                .setMessage(0, Component.literal("HERO HOUSE"))
                .setMessage(1, Component.literal(first))
                .setMessage(2, Component.literal(second)), true);
            sign.setChanged();
            level.sendBlockUpdated(signPos, level.getBlockState(signPos), level.getBlockState(signPos), 3);
        }
    }

    /** Each eligible player receives at most one house per dimension, regardless of village. */
    private static void welcomeHouse(ServerLevel level, ServerPlayer player, Data saved) {
        // Houses are unique to players on this server, never recreated in the Nether or End.
        if (level != player.server.overworld()) return;
        HeroHouse existing = ownHouse(saved, player.getUUID());
        if (existing != null) {
            String name = player.getGameProfile().getName();
            if (!existing.name.equals(name)) {
                existing.name = name;
                saved.setDirty();
            }
            // Recheck after chunk load: a rename can happen while the sign is unloaded.
            refreshSign(level, existing, name);
            return;
        }
        if (player.getData(ReputationMod.REP) < 151) return;
        // Backward-compatibility: adopt a previously owned cottage if its footprint remains.
        for (Village old : saved.villages) {
            if (old.houseBuilt && player.getUUID().equals(old.houseOwner)) {
                BlockPos site = findLegacyHouse(level, old);
                if (site != null) {
                    HeroHouse migrated = new HeroHouse(player.getUUID(), site, player.getGameProfile().getName());
                    saved.heroHouses.add(migrated);
                    saved.setDirty();
                    refreshSign(level, migrated, migrated.name);
                    player.sendSystemMessage(Component.literal("Your existing hero house was recovered. Use /herohouse locate."));
                }
                // Never generate a duplicate if the legacy home is temporarily unloaded/modified.
                return;
            }
        }
        Village village = closest(saved, player.blockPosition(), VILLAGE_SEPARATION);
        if (village == null) {
            if (saved.villages.size() >= MAX_VILLAGES) return;
            if (level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(28)).size() < 2) return;
            village = new Village(player.blockPosition(), false, 0, level.getGameTime());
            saved.villages.add(village);
            saved.setDirty();
        }
        if (village.occupied) return;
        if (saved.heroHouses.size() >= MAX_VILLAGES * 8) return;
        int[][] offsets = {{24,0},{-24,0},{0,24},{0,-24},{30,12},{-30,-12},{12,-30},{-12,30},{35,0},{-35,0},{0,35},{0,-35},{28,28},{-28,28},{28,-28},{-28,-28},{42,12},{-42,12},{12,42},{12,-42},{48,0},{0,48},{-48,0},{0,-48}};
        for (int[] offset : offsets) {
            BlockPos site = village.center.offset(offset[0], 0, offset[1]);
            if (!level.hasChunkAt(site)) continue;
            BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site);
            if (Math.abs(base.getY() - village.center.getY()) <= 6 && placeCottage(level, base, player.getGameProfile().getName())) {
                saved.heroHouses.add(new HeroHouse(player.getUUID(), base, player.getGameProfile().getName()));
                village.houseBuilt = true;
                if (village.houseOwner == null) {
                    village.houseOwner = player.getUUID();
                    village.houseOwnerName = player.getGameProfile().getName();
                }
                saved.setDirty();
                player.sendSystemMessage(Component.literal("Your hero house is ready! Use /herohouse locate."));
                return;
            }
        }
    }

    /** Recognize only our original cottage, never claim an arbitrary nearby player build. */
    private static BlockPos findLegacyHouse(ServerLevel level, Village village) {
        int[][] offsets = {{24,0},{-24,0},{0,24},{0,-24},{30,12},{-30,-12},
            {12,-30},{-12,30},{35,0},{-35,0},{0,35},{0,-35},
            {28,28},{-28,28},{28,-28},{-28,-28},{42,12},{-42,12},
            {12,42},{12,-42},{48,0},{0,48},{-48,0},{0,-48}};
        for (int[] offset : offsets) for (int y = -6; y <= 6; y++) {
            BlockPos base = village.center.offset(offset[0], y, offset[1]);
            if (!level.hasChunkAt(base)) continue;
            if (level.getBlockState(base).is(Blocks.OAK_PLANKS)
                    && level.getBlockState(base.above(4)).is(Blocks.SPRUCE_PLANKS)
                    && level.getBlockState(base.offset(0, 1, 1)).is(Blocks.CRAFTING_TABLE)
                    && level.getBlockState(base.offset(1, 1, 1)).is(Blocks.CHEST)) return base;
        }
        return null;
    }

    private static boolean placeCottage(ServerLevel level, BlockPos base, String ownerName) {
        // Include the entrance sign in the preflight check; never leave an unnamed home.
        BlockPos signPos = base.offset(1, 2, -4);
        if (!level.hasChunkAt(signPos) || !level.getBlockState(signPos).isAir()) return false;
        // Validate every block before placing anything; no overwrites.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos column = base.offset(x, 0, z);
            if (!level.hasChunkAt(column) || !level.hasChunkAt(column.below())) return false;
            BlockPos foundation = column.below();
            BlockState ground = level.getBlockState(foundation);
            // Hero homes must not claim player-made platforms, just like village extensions.
            // This is a conservative terrain check, not proof of block ownership.
            if (!ground.isSolidRender(level, foundation) || !isNaturalFoundation(ground)) return false;
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
        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = bedFoot.setValue(BedBlock.PART, BedPart.HEAD);
        // Place both halves before neighbor updates to avoid instantly invalidating a half-bed.
        level.setBlock(base.offset(-1, 1, 1), bedFoot, 2);
        level.setBlock(base.offset(-1, 1, 0), bedHead, 2);
        // A wall sign is attached outside the entrance, on the north-facing wall.
        level.setBlockAndUpdate(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState()
            .setValue(WallSignBlock.FACING, Direction.NORTH));
        refreshSign(level, new HeroHouse(new UUID(0, 0), base, ownerName), ownerName);
        return true;
    }

    /** One additional structure per visit stage, with a strict clear-footprint check. */
    private static boolean placeVillageStructure(ServerLevel level, Village village) {
        if (village.structuresBuilt >= 6) return false;
        int kind = village.structuresBuilt;
        int[][] offsets = {{21,0},{-21,0},{0,21},{0,-21},{23,13},{-23,-13},{38,8},{-38,8},{8,38},{8,-38},{-38,-18},{38,-18},{28,24},{-28,24},{24,-28},{-24,-28}};
        for (int[] offset : offsets) {
            BlockPos candidate = village.center.offset(offset[0], 0, offset[1]);
            // Do not query a heightmap in an unloaded chunk: that may trigger generation.
            if (!level.hasChunkAt(candidate)) continue;
            BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                candidate);
            if (Math.abs(base.getY() - village.center.getY()) > 4 || !clearSite(level, base, 2, 7)) continue;
            if (kind >= 3) { buildSpecial(level, base, kind); return true; }
            // Market stall, watch post and supply depot; all are non-destructive.
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                BlockPos pos = base.offset(x, 0, z);
                level.setBlockAndUpdate(pos, kind == 2 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                    : Blocks.OAK_PLANKS.defaultBlockState());
                if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                    for (int y = 1; y <= (kind == 1 ? 4 : 3); y++)
                        level.setBlockAndUpdate(pos.above(y), Blocks.OAK_FENCE.defaultBlockState());
                }
                if (kind != 2 && (Math.abs(x) == 2 || Math.abs(z) == 2))
                    level.setBlockAndUpdate(pos.above(kind == 1 ? 5 : 4),
                        kind == 1 ? Blocks.SPRUCE_SLAB.defaultBlockState() : Blocks.RED_WOOL.defaultBlockState());
            }
            if (kind == 0) {
                level.setBlockAndUpdate(base.above(), Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(base.offset(1,1,0), Blocks.CRAFTING_TABLE.defaultBlockState());
            } else if (kind == 1) {
                level.setBlockAndUpdate(base.above(), Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.NORTH));
            } else {
                level.setBlockAndUpdate(base.above(), Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(base.offset(1,1,0), Blocks.BARREL.defaultBlockState());
            }
            return true;
        }
        return false;
    }

    private static void buildSpecial(ServerLevel level, BlockPos base, int kind) {
        BlockState floor = (kind == 3 ? Blocks.STONE_BRICKS : kind == 4 ? Blocks.MOSSY_COBBLESTONE : Blocks.SPRUCE_PLANKS).defaultBlockState();
        BlockState post = (kind == 3 ? Blocks.STONE_BRICK_WALL : Blocks.OAK_LOG).defaultBlockState();
        BlockState roof = (kind == 3 ? Blocks.DEEPSLATE_TILES : kind == 4 ? Blocks.OAK_LEAVES : Blocks.DARK_OAK_PLANKS).defaultBlockState();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos p = base.offset(x, 0, z);
            level.setBlockAndUpdate(p, floor);
            if (Math.abs(x) == 2 && Math.abs(z) == 2)
                for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(p.above(y), post);
            level.setBlockAndUpdate(p.above(5), roof);
        }
        if (kind == 3) {
            level.setBlockAndUpdate(base.offset(-1, 1, 0), Blocks.BLAST_FURNACE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(1, 1, 0), Blocks.ANVIL.defaultBlockState());
            level.setBlockAndUpdate(base.offset(0, 1, 1), Blocks.SMITHING_TABLE.defaultBlockState());
        } else if (kind == 4) {
            level.setBlockAndUpdate(base.offset(-1, 1, 0), Blocks.FLOWERING_AZALEA.defaultBlockState());
            level.setBlockAndUpdate(base.offset(1, 1, 0), Blocks.POTTED_BLUE_ORCHID.defaultBlockState());
            level.setBlockAndUpdate(base.offset(0, 1, 1), Blocks.COMPOSTER.defaultBlockState());
        } else {
            level.setBlockAndUpdate(base.offset(-1, 1, 1), Blocks.BOOKSHELF.defaultBlockState());
            level.setBlockAndUpdate(base.offset(1, 1, 1), Blocks.BOOKSHELF.defaultBlockState());
            level.setBlockAndUpdate(base.offset(0, 1, 1), Blocks.LECTERN.defaultBlockState());
        }
    }

    private static boolean clearSite(ServerLevel level, BlockPos base, int radius, int height) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos p = base.offset(x, 0, z);
            if (!level.hasChunkAt(p)) return false;
            BlockPos foundation = p.below();
            BlockState ground = level.getBlockState(foundation);
            // A clear air volume is not proof of an unclaimed site: a player may
            // have built a flat floor here. Only extend onto natural terrain.
            if (!ground.isSolidRender(level, foundation) || !isNaturalFoundation(ground)) return false;
            for (int y = 0; y <= height; y++) if (!level.getBlockState(p.above(y)).isAir()) return false;
        }
        return true;
    }

    private static boolean isNaturalFoundation(BlockState ground) {
        return ground.is(net.minecraft.tags.BlockTags.DIRT)
            || ground.is(Blocks.STONE) || ground.is(Blocks.DEEPSLATE)
            || ground.is(Blocks.TUFF) || ground.is(Blocks.GRAVEL)
            || ground.is(Blocks.SAND) || ground.is(Blocks.RED_SAND)
            || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.CLAY)
            || ground.is(Blocks.TERRACOTTA);
    }

    private static boolean placeDecoration(ServerLevel level, Village village) {
        int[][] offsets = {{40, 0}, {-40, 0}, {0, 40}, {0, -40},
            {34, 20}, {-34, -20}, {20, -34}, {-20, 34}};
        for (int[] offset : offsets) {
            BlockPos candidate = village.center.offset(offset[0], 0, offset[1]);
            // Do not query a heightmap in an unloaded chunk: that may trigger generation.
            if (!level.hasChunkAt(candidate)) continue;
            BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                candidate);
            if (Math.abs(base.getY() - village.center.getY()) > 4 || !clearSite(level, base, 1, 4)) continue;
            BlockState ground = level.getBlockState(base.below());
            if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT)
                    && !ground.is(Blocks.STONE)) continue;
            switch (village.decorationsBuilt) {
                case 0 -> {
                    level.setBlockAndUpdate(base, Blocks.COBBLESTONE.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.POTTED_POPPY.defaultBlockState());
                }
                case 1 -> {
                    level.setBlockAndUpdate(base, Blocks.HAY_BLOCK.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.HAY_BLOCK.defaultBlockState());
                }
                case 2 -> {
                    level.setBlockAndUpdate(base, Blocks.OAK_FENCE.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.LANTERN.defaultBlockState());
                }
                case 4 -> {
                    level.setBlockAndUpdate(base, Blocks.COBBLESTONE.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.POTTED_POPPY.defaultBlockState());
                }
                case 5 -> {
                    level.setBlockAndUpdate(base, Blocks.BARREL.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.POTTED_FERN.defaultBlockState());
                }
                case 6 -> {
                    level.setBlockAndUpdate(base, Blocks.OAK_LOG.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.BEE_NEST.defaultBlockState());
                }
                case 7 -> {
                    level.setBlockAndUpdate(base, Blocks.STONE_BRICKS.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.POTTED_DANDELION.defaultBlockState());
                }
                default -> {
                    level.setBlockAndUpdate(base, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    level.setBlockAndUpdate(base.above(), Blocks.OAK_FENCE.defaultBlockState());
                    level.setBlockAndUpdate(base.above(2), Blocks.TORCH.defaultBlockState());
                }
            }
            return true;
        }
        return false;
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
            welcomeHouse(level, player, data);
        }
        for (Village village : data.villages) {
            // Stage reflects real construction progress, not an instant forced level 3.
            // Never lower a previously saved stage when upgrading an old world.
            int earnedStage = village.houseBuilt
                ? (village.structuresBuilt >= 4 ? 3 : village.structuresBuilt >= 2 ? 2 : 1)
                : 0;
            if (earnedStage > village.stage) {
                village.stage = earnedStage;
                data.setDirty();
            }
            if (village.houseBuilt && village.decorationsBuilt < 8
                    && level.getGameTime() >= village.nextBuild
                    && placeDecoration(level, village)) {
                village.decorationsBuilt++;
                village.nextBuild = level.getGameTime() + 6000;
                data.setDirty();
            }
            if (village.houseBuilt && village.structuresBuilt < 6
                    && level.getGameTime() >= village.nextBuild && level.hasChunkAt(village.center)
                    && placeVillageStructure(level, village)) {
                village.structuresBuilt++;
                village.nextBuild = level.getGameTime() + 12000;
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
            if (Math.abs(ground.getY() - village.center.getY()) > 4) continue;
            // Only build on solid ground with three empty blocks above.
            if (!level.getBlockState(ground.below()).isSolidRender(level, ground.below())) continue;
            if (level.getBlockState(ground.below()).is(net.minecraft.tags.BlockTags.PLANKS)) continue;
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
