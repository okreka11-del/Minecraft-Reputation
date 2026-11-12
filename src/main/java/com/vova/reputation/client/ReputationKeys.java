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
    private static boolean shiftWasDown;
    private static long lastShiftTap;
    private static long lastFart;
    public static final KeyMapping JOURNAL = new KeyMapping(
        "key.reputation.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
        "key.categories.reputation");

    public static final KeyMapping VILLAGE = new KeyMapping(
        "key.reputation.village", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
        "key.categories.reputation");

    public static final KeyMapping ARMY = new KeyMapping(
        "key.reputation.army", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
        "key.categories.reputation");

    @EventBusSubscriber(modid = ReputationMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(JOURNAL);
            event.register(VILLAGE);
            event.register(ARMY);
        }
    }

    @EventBusSubscriber(modid = ReputationMod.ID, value = Dist.CLIENT)
    public static final class Handler {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            boolean shiftDown = mc.options.keyShift.isDown();
            if (mc.player != null && mc.screen == null && shiftDown && !shiftWasDown) {
                long now = System.nanoTime();
                if (now - lastShiftTap < 400_000_000L && now - lastFart > 1_500_000_000L) {
                    PacketDistributor.sendToServer(new ReputationNetwork.FartRequest());
                    lastFart = now;
                    lastShiftTap = 0;
                } else lastShiftTap = now;
            }
            shiftWasDown = shiftDown;
            // Drain every queued press, but send at most one screen request per tick.
            // A burst of queued G presses previously sent several ArmyRequests before
            // the server could open the panel, causing duplicate responses/screens.
            boolean armyPressed = false;
            while (ARMY.consumeClick()) armyPressed = true;
            boolean villagePressed = false;
            while (VILLAGE.consumeClick()) villagePressed = true;
            boolean journalPressed = false;
            while (JOURNAL.consumeClick()) journalPressed = true;
            if (mc.player != null && mc.screen == null) {
                // One panel at a time: army takes priority over village and journal.
                // Prevent simultaneous bound keys from racing to replace the GUI.
                if (armyPressed) PacketDistributor.sendToServer(new ReputationNetwork.ArmyRequest());
                else if (villagePressed) PacketDistributor.sendToServer(new ReputationNetwork.VillageRequest());
                else if (journalPressed) PacketDistributor.sendToServer(new ReputationNetwork.JournalRequest());
            }
        }
    }
}
