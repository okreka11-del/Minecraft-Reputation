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
    // The same player instance may survive a portal transfer; do not let a
    // first sneak tap in one dimension pair with a tap in another.
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> shiftGestureDimension;
    // A delayed overview must not reopen the army menu after another GUI appears.
    private static boolean armyOpenPending;
    // Use monotonic wall time: game ticks can slow down under server/client lag,
    // leaving the player waiting much longer than five actual seconds.
    private static long armyRequestNanos;
    private static net.minecraft.client.player.LocalPlayer armyRequestPlayer;
    // A portal transfer can keep the same LocalPlayer while changing levels.
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> armyRequestDimension;

    public static boolean consumeArmyOpenRequest() {
        Minecraft mc = Minecraft.getInstance();
        // Snapshot the clock once so a reply arriving at the timeout boundary
        // cannot pass the first time check and fail the second inconsistently.
        long elapsed = System.nanoTime() - armyRequestNanos;
        boolean pending = armyOpenPending && mc.player != null
            && mc.player == armyRequestPlayer && mc.screen == null
            && mc.player.level().dimension() == armyRequestDimension
            && mc.player.isAlive() && !mc.player.isSpectator()
            && armyRequestNanos != 0L
            && elapsed >= 0L && elapsed <= 5_000_000_000L;
        armyOpenPending = false;
        armyRequestNanos = 0L;
        armyRequestPlayer = null;
        armyRequestDimension = null;
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
            if (mc.player != shiftGesturePlayer
                    || (mc.player != null && mc.player.level().dimension() != shiftGestureDimension)) {
                // The cooldown belongs to the local player instance. A new
                // connection or respawn must not inherit the previous player's
                // ability lockout; dimension transfers retain the same cooldown.
                if (mc.player != shiftGesturePlayer) lastFart = 0L;
                shiftGesturePlayer = mc.player;
                shiftGestureDimension = mc.player == null ? null : mc.player.level().dimension();
                lastShiftTap = 0;
                shiftWasDown = shiftDown;
            }
            if (mc.player != null && mc.screen == null && mc.player.isAlive()
                    && !mc.player.isSpectator() && mc.player.getVehicle() == null
                    && !mc.player.isFallFlying() && !mc.player.isSwimming()
                    && !mc.player.isInWaterOrBubble() && !mc.player.onClimbable()
                    && !mc.player.isInLava() && !mc.player.isOnFire()
                    // Sneaking while blocking, eating or drawing a bow is ordinary
                    // combat/item control, not an intentional reputation ability.
                    && !mc.player.isUsingItem()
                    && shiftDown && !shiftWasDown) {
                long now = System.nanoTime();
                // Consume the second tap even during cooldown. Otherwise a rapid
                // third press could be treated as a fresh second tap and fire the
                // ability unexpectedly when the cooldown ends.
                long sinceTap = now - lastShiftTap;
                if (lastShiftTap != 0L && sinceTap >= 0L && sinceTap < 400_000_000L) {
                    lastShiftTap = 0L;
                    if (now - lastFart > 1_500_000_000L) {
                        PacketDistributor.sendToServer(new ReputationNetwork.FartRequest());
                        lastFart = now;
                    }
                } else {
                    lastShiftTap = now;
                }
            }
            // A double-Shift gesture must consist of two taps during uninterrupted
            // gameplay. Opening a menu, dying or switching players cancels the
            // first tap; otherwise closing a GUI and pressing sneak once could
            // unexpectedly trigger the fart ability and cost reputation.
            // Sneak is also used to descend ladders and move in water. Cancel a
            // partial gesture before climbing or entering water, even before the
            // swimming animation begins, to avoid charging reputation for movement.
            // Emergency sneaking while burning or escaping lava must not spend
            // reputation; clear a half-finished gesture in either hazard state.
            // Sneak is also the dismount key. Never treat dismount attempts as
            // reputation-costing double-sneak gestures, including a tap made
            // immediately before mounting a vehicle.
            if (mc.player == null || mc.screen != null || !mc.player.isAlive()
                    || mc.player.isSpectator() || mc.player.getVehicle() != null
                    || mc.player.isFallFlying() || mc.player.isSwimming()
                    || mc.player.isInWaterOrBubble() || mc.player.onClimbable()
                    || mc.player.isInLava() || mc.player.isOnFire()
                    || mc.player.isUsingItem()) {
                lastShiftTap = 0;
            }
            shiftWasDown = shiftDown;
            // A timed-out G request should not fail silently: explain why the
            // panel did not open and how to retry. Only show this once per request.
            // Cancel without a warning when the player changes or opens another GUI.
            if (armyOpenPending && mc.player != null && mc.player == armyRequestPlayer
                    && mc.screen == null && mc.player.isAlive() && !mc.player.isSpectator()
                    && mc.player.level().dimension() == armyRequestDimension
                    && armyRequestNanos != 0L
                    && System.nanoTime() - armyRequestNanos > 5_000_000_000L) {
                armyOpenPending = false;
                armyRequestNanos = 0L;
                armyRequestPlayer = null;
                armyRequestDimension = null;
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "reputation.army.overview_timeout"), true);
            }
            if (mc.screen != null || mc.player == null || mc.player != armyRequestPlayer
                    || (armyOpenPending && mc.player.level().dimension() != armyRequestDimension)
                    || !mc.player.isAlive() || mc.player.isSpectator()
                    || (armyOpenPending && System.nanoTime() - armyRequestNanos < 0L)) {
                armyOpenPending = false;
                armyRequestNanos = 0L;
                armyRequestPlayer = null;
                armyRequestDimension = null;
            }
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
                    // A second deliberate G press cancels a stalled overview request.
                    // The server may still reply, but consumeArmyOpenRequest() will
                    // reject that late response instead of reopening the panel.
                    if (armyOpenPending) {
                        armyOpenPending = false;
                        armyRequestNanos = 0L;
                        armyRequestPlayer = null;
                        armyRequestDimension = null;
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "reputation.army.overview_cancelled"), true);
                    } else if (mc.player.isAlive() && !mc.player.isSpectator()) {
                        armyOpenPending = true;
                        armyRequestPlayer = mc.player;
                        armyRequestDimension = mc.player.level().dimension();
                        armyRequestNanos = System.nanoTime();
                        PacketDistributor.sendToServer(new ReputationNetwork.ArmyRequest());
                        // Give immediate feedback while the server prepares the overview.
                        // The action bar does not block movement or render another screen.
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "reputation.army.overview_loading"), true);
                    }
                }
                // J/V are explicit navigation choices: do not swallow them while
                // a slow G overview is pending. Cancel that overview first so its
                // delayed response cannot replace the newly requested menu.
                // Army still wins when several bindings fire in the same tick.
                else if ((villagePressed || journalPressed)
                        && mc.player.isAlive() && !mc.player.isSpectator()) {
                    if (armyOpenPending) {
                        armyOpenPending = false;
                        armyRequestNanos = 0L;
                        armyRequestPlayer = null;
                        armyRequestDimension = null;
                    }
                    if (villagePressed) PacketDistributor.sendToServer(new ReputationNetwork.VillageRequest());
                    else PacketDistributor.sendToServer(new ReputationNetwork.JournalRequest());
                }
            }
        }
    }
}
