package com.vova.reputation;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for the twenty Reputation profession workstations.
 *
 * Workstations deliberately use unique registry IDs and BlockItems. Their
 * custom voxel models and profession assignment logic are supplied by the
 * corresponding data assets and NPC systems.
 */
public final class ReputationProfessionRegistry {
    private ReputationProfessionRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, ReputationMod.ID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, ReputationMod.ID);

    public enum Faction { VILLAGER, ILLAGER }

    public record Profession(String id, String translationKey, Faction faction,
                             String workstationId) {}

    private static final Map<String, Profession> PROFESSIONS = new LinkedHashMap<>();
    private static final Map<String, Supplier<Block>> WORKSTATIONS = new LinkedHashMap<>();

    // Villager professions
    public static final Supplier<Block> ARCHITECT_TABLE = workstation("architect_table", SoundType.WOOD);
    public static final Supplier<Block> HEALER_ALTAR = workstation("healer_altar", SoundType.STONE);
    public static final Supplier<Block> INNKEEPER_COUNTER = workstation("innkeeper_counter", SoundType.WOOD);
    public static final Supplier<Block> STEWARD_DESK = workstation("steward_desk", SoundType.WOOD);
    public static final Supplier<Block> COURIER_POSTBOX = workstation("courier_postbox", SoundType.WOOD);
    public static final Supplier<Block> DIPLOMAT_TABLE = workstation("diplomat_table", SoundType.WOOD);
    public static final Supplier<Block> ENGINEER_BENCH = workstation("engineer_bench", SoundType.METAL);
    public static final Supplier<Block> MENAGERIE_STALL = workstation("menagerie_stall", SoundType.WOOD);
    public static final Supplier<Block> CHRONICLE_LECTERN = workstation("chronicle_lectern", SoundType.WOOD);
    public static final Supplier<Block> QUARTERMASTER_TERMINAL = workstation("quartermaster_terminal", SoundType.WOOD);

    // Illager professions
    public static final Supplier<Block> WARLORD_TABLE = workstation("warlord_table", SoundType.WOOD);
    public static final Supplier<Block> INFILTRATOR_POST = workstation("infiltrator_post", SoundType.WOOD);
    public static final Supplier<Block> SIEGE_BENCH = workstation("siege_bench", SoundType.METAL);
    public static final Supplier<Block> SABOTEUR_TABLE = workstation("saboteur_table", SoundType.WOOD);
    public static final Supplier<Block> BEASTMASTER_STALL = workstation("beastmaster_stall", SoundType.WOOD);
    public static final Supplier<Block> DARK_STANDARD = workstation("dark_standard", SoundType.WOOL);
    public static final Supplier<Block> JAILER_STATION = workstation("jailer_station", SoundType.METAL);
    public static final Supplier<Block> RECRUITMENT_BOARD = workstation("recruitment_board", SoundType.WOOD);
    public static final Supplier<Block> TROPHY_COUNTER = workstation("trophy_counter", SoundType.WOOD);
    public static final Supplier<Block> DARK_ALCHEMY_REACTOR = workstation("dark_alchemy_reactor", SoundType.GLASS);

    private static Supplier<Block> workstation(String id, SoundType sound) {
        Supplier<Block> block = BLOCKS.register(id, () -> new Block(
            BlockBehaviour.Properties.of().strength(2.0F, 3.0F).sound(sound).noOcclusion()
        ));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        WORKSTATIONS.put(id, block);
        return block;
    }

    private static void profession(String id, String translationKey, Faction faction, String workstationId) {
        PROFESSIONS.put(id, new Profession(id, translationKey, faction, workstationId));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);

        profession("architect", "profession.reputation.architect", Faction.VILLAGER, "architect_table");
        profession("healer", "profession.reputation.healer", Faction.VILLAGER, "healer_altar");
        profession("innkeeper", "profession.reputation.innkeeper", Faction.VILLAGER, "innkeeper_counter");
        profession("steward", "profession.reputation.steward", Faction.VILLAGER, "steward_desk");
        profession("courier", "profession.reputation.courier", Faction.VILLAGER, "courier_postbox");
        profession("diplomat", "profession.reputation.diplomat", Faction.VILLAGER, "diplomat_table");
        profession("engineer", "profession.reputation.engineer", Faction.VILLAGER, "engineer_bench");
        profession("menagerie_keeper", "profession.reputation.menagerie_keeper", Faction.VILLAGER, "menagerie_stall");
        profession("chronicler", "profession.reputation.chronicler", Faction.VILLAGER, "chronicle_lectern");
        profession("quartermaster", "profession.reputation.quartermaster", Faction.VILLAGER, "quartermaster_terminal");

        profession("warlord", "profession.reputation.warlord", Faction.ILLAGER, "warlord_table");
        profession("infiltrator", "profession.reputation.infiltrator", Faction.ILLAGER, "infiltrator_post");
        profession("siege_engineer", "profession.reputation.siege_engineer", Faction.ILLAGER, "siege_bench");
        profession("saboteur", "profession.reputation.saboteur", Faction.ILLAGER, "saboteur_table");
        profession("beastmaster", "profession.reputation.beastmaster", Faction.ILLAGER, "beastmaster_stall");
        profession("standard_bearer", "profession.reputation.standard_bearer", Faction.ILLAGER, "dark_standard");
        profession("jailer", "profession.reputation.jailer", Faction.ILLAGER, "jailer_station");
        profession("recruiter", "profession.reputation.recruiter", Faction.ILLAGER, "recruitment_board");
        profession("trophy_broker", "profession.reputation.trophy_broker", Faction.ILLAGER, "trophy_counter");
        profession("dark_alchemist", "profession.reputation.dark_alchemist", Faction.ILLAGER, "dark_alchemy_reactor");
    }

    public static Map<String, Profession> professions() {
        return Map.copyOf(PROFESSIONS);
    }

    public static Map<String, Supplier<Block>> workstations() {
        return Map.copyOf(WORKSTATIONS);
    }
}
