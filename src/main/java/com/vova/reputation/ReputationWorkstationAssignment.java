package com.vova.reputation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;

/**
 * Binds a nearby villager/illager specialist to a clicked Reputation workstation.
 * The assigned profession and workstation coordinates live in entity persistent NBT.
 */
public final class ReputationWorkstationAssignment {
    private ReputationWorkstationAssignment() {}

    private static final Map<String, String> JOBS = Map.ofEntries(
        Map.entry("architect_table", "architect"),
        Map.entry("healer_altar", "healer"),
        Map.entry("innkeeper_counter", "innkeeper"),
        Map.entry("steward_desk", "steward"),
        Map.entry("courier_postbox", "courier"),
        Map.entry("diplomat_table", "diplomat"),
        Map.entry("engineer_bench", "engineer"),
        Map.entry("menagerie_stall", "menagerie_keeper"),
        Map.entry("chronicle_lectern", "chronicler"),
        Map.entry("quartermaster_terminal", "quartermaster"),
        Map.entry("warlord_table", "warlord"),
        Map.entry("infiltrator_post", "infiltrator"),
        Map.entry("siege_bench", "siege_engineer"),
        Map.entry("saboteur_table", "saboteur"),
        Map.entry("beastmaster_stall", "beastmaster"),
        Map.entry("dark_standard", "standard_bearer"),
        Map.entry("jailer_station", "jailer"),
        Map.entry("recruitment_board", "recruiter"),
        Map.entry("trophy_counter", "trophy_broker"),
        Map.entry("dark_alchemy_reactor", "dark_alchemist")
    );

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReputationWorkstationAssignment::onWorkstationUse);
    }

    private static void onWorkstationUse(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        // Spectators cannot claim NPC professions; require normal interaction reach.
        if (player.isSpectator() || player.distanceToSqr(event.getPos().getX() + 0.5,
            event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5) > 64.0) return;

        var blockKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(event.getPos()).getBlock());
        // Never treat another mod's block with the same path as our workstation.
        if (!ReputationMod.ID.equals(blockKey.getNamespace())) return;
        String job = JOBS.get(blockKey.getPath());
        if (job == null) return;

        boolean illagerJob = job.equals("warlord") || job.equals("infiltrator")
            || job.equals("siege_engineer") || job.equals("saboteur") || job.equals("beastmaster")
            || job.equals("standard_bearer") || job.equals("jailer") || job.equals("recruiter")
            || job.equals("trophy_broker") || job.equals("dark_alchemist");

        BlockPos workstation = event.getPos().immutable();
        AABB area = new AABB(workstation).inflate(8, 4, 8);
        // Check the current workstation claim before looking for a free worker.
        // Otherwise repeated clicks report "no free specialist" even when this
        // workstation is already assigned, and can reassign another nearby NPC.
        boolean occupied = level.getEntitiesOfClass(Entity.class, area).stream()
            .anyMatch(entity -> entity.isAlive()
                && entity.getPersistentData().getString("ReputationProfession").equals(job)
                && entity.getPersistentData().getInt("ReputationWorkX") == workstation.getX()
                && entity.getPersistentData().getInt("ReputationWorkY") == workstation.getY()
                && entity.getPersistentData().getInt("ReputationWorkZ") == workstation.getZ());
        if (occupied) {
            player.sendSystemMessage(Component.literal("This workstation is already occupied."));
            return;
        }

        Entity worker = illagerJob
            ? nearest(level.getEntitiesOfClass(Pillager.class, area), workstation)
            : nearest(level.getEntitiesOfClass(Villager.class, area), workstation);

        if (worker == null) {
            player.sendSystemMessage(Component.literal(illagerJob
                ? "No free pillager specialist is close enough to this workstation."
                : "No free villager specialist is close enough to this workstation."));
            return;
        }

        String existing = worker.getPersistentData().getString("ReputationProfession");
        if (!existing.isEmpty() && existing.equals(job)) {
            player.sendSystemMessage(Component.literal("This specialist already has this profession."));
            return;
        }

        worker.getPersistentData().putString("ReputationProfession", job);
        worker.getPersistentData().putInt("ReputationWorkX", workstation.getX());
        worker.getPersistentData().putInt("ReputationWorkY", workstation.getY());
        worker.getPersistentData().putInt("ReputationWorkZ", workstation.getZ());
        worker.getPersistentData().putInt("ReputationProfessionLevel", 1);
        worker.getPersistentData().putInt("ReputationProfessionXp", 0);
        worker.setCustomName(Component.translatable("profession.reputation." + job));
        worker.setCustomNameVisible(true);
        player.sendSystemMessage(Component.translatable("message.reputation.profession_assigned",
            Component.translatable("profession.reputation." + job)));
    }

    private static <T extends Entity> T nearest(java.util.List<T> entities, BlockPos position) {
        return entities.stream()
            .filter(Entity::isAlive)
            .filter(entity -> entity.getPersistentData().getString("ReputationProfession").isEmpty())
            // Preserve guard/builder/armorer assignments made by /specialist.
            .filter(entity -> entity.getPersistentData().getString("ReputationSpecialist").isEmpty())
            .min((a, b) -> Double.compare(a.distanceToSqr(position.getX() + 0.5,
                position.getY() + 0.5, position.getZ() + 0.5),
                b.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5)))
            .orElse(null);
    }
}
