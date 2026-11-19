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
    // Never carry a half-finished gesture across respawns or server switches.
    private static net.minecraft.client.player.LocalPlayer shiftGesturePlayer;
    // A delayed overview must not reopen the army menu after another GUI appears.
    private static boolean armyOpenPending;
    // Ignore a delayed overview if the server did not answer within five seconds.
    private static int armyRequestTick = Integer.MIN_VALUE;
    private static net.minecraft.client.player.LocalPlayer armyRequestPlayer;

    public static boolean consumeArmyOpenRequest() {
        Minecraft mc = Minecraft.getInstance();
        boolean pending = armyOpenPending && mc.player != null
            && mc.player == armyRequestPlayer && mc.screen == null
            && mc.player.tickCount >= armyRequestTick
            && mc.player.tickCount - armyRequestTick <= 100;
        armyOpenPending = false;
        return pending;
    }
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
            if (mc.player != shiftGesturePlayer) {
                shiftGesturePlayer = mc.player;
                lastShiftTap = 0;
                shiftWasDown = shiftDown;
            }
            if (mc.player != null && mc.screen == null && mc.player.isAlive()
                    && !mc.player.isSpectator() && shiftDown && !shiftWasDown) {
                long now = System.nanoTime();
                if (now - lastShiftTap < 400_000_000L && now - lastFart > 1_500_000_000L) {
                    PacketDistributor.sendToServer(new ReputationNetwork.FartRequest());
                    lastFart = now;
                    lastShiftTap = 0;
                } else lastShiftTap = now;
            }
            // A double-Shift gesture must consist of two taps during uninterrupted
            // gameplay. Opening a menu, dying or switching players cancels the
            // first tap; otherwise closing a GUI and pressing sneak once could
            // unexpectedly trigger the fart ability and cost reputation.
            if (mc.player == null || mc.screen != null || !mc.player.isAlive()
                    || mc.player.isSpectator()) {
                lastShiftTap = 0;
            }
            shiftWasDown = shiftDown;
            // Opening another GUI cancels any outstanding G request.
            if (mc.screen != null || mc.player == null || mc.player != armyRequestPlayer
                    || (armyOpenPending && (mc.player.tickCount < armyRequestTick
                        || mc.player.tickCount - armyRequestTick > 100))) armyOpenPending = false;
            // Drain every queued press; only one army overview may be in flight.
            // A burst of queued G presses previously sent several ArmyRequests before
            // the server could open the panel, causing duplicate responses/screens.
            // Holding or repeatedly tapping G while awaiting the reply must not resend it.
            boolean armyPressed = false;
            while (ARMY.consumeClick()) armyPressed = true;
            boolean villagePressed = false;
            while (VILLAGE.consumeClick()) villagePressed = true;
            boolean journalPressed = false;
            while (JOURNAL.consumeClick()) journalPressed = true;
            if (mc.player != null && mc.screen == null) {
                // One panel at a time: army takes priority over village and journal.
                // Prevent simultaneous bound keys from racing to replace the GUI.
                if (armyPressed) {
                    if (!armyOpenPending && mc.player.isAlive() && !mc.player.isSpectator()) {
                        armyOpenPending = true;
                        armyRequestPlayer = mc.player;
                        armyRequestTick = mc.player.tickCount;
                        PacketDistributor.sendToServer(new ReputationNetwork.ArmyRequest());
                    }
                }
                else if (villagePressed) PacketDistributor.sendToServer(new ReputationNetwork.VillageRequest());
                else if (journalPressed) PacketDistributor.sendToServer(new ReputationNetwork.JournalRequest());
            }
        }
    }
}
