package com.vova.reputation.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.vova.reputation.ReputationMod;
import com.vova.reputation.ReputationNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** J opens the server-synchronized reputation journal, without chat commands. */
public final class ReputationKeys {
    private ReputationKeys() {}
    public static final KeyMapping JOURNAL = new KeyMapping(
        "key.reputation.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
        "key.categories.reputation");

    public static final KeyMapping VILLAGE = new KeyMapping(
        "key.reputation.village", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
        "key.categories.reputation");

    @EventBusSubscriber(modid = ReputationMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(JOURNAL);
            event.register(VILLAGE);
        }
    }

    @EventBusSubscriber(modid = ReputationMod.ID, value = Dist.CLIENT)
    public static final class Handler {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (VILLAGE.consumeClick()) {
                if (mc.player != null && mc.screen == null)
                    PacketDistributor.sendToServer(new ReputationNetwork.VillageRequest());
            }
            while (JOURNAL.consumeClick()) {
                if (mc.player != null && mc.screen == null)
                    PacketDistributor.sendToServer(new ReputationNetwork.JournalRequest());
            }
        }
    }
}
