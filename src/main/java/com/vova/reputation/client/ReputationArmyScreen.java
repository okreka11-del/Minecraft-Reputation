package com.vova.reputation.client;

import com.vova.reputation.ReputationNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * First server-controlled squad orders: only the player's own nearby tamed wolves.
 * This deliberately does not claim to implement villager/illager recruitment.
 */
public final class ReputationArmyScreen extends Screen {
    private int available;
    private final java.util.List<Button> orderButtons = new java.util.ArrayList<>();
    // Reuse the navigation buffer for keyboard, controller and mouse-wheel input.
    // A menu with seven controls should not allocate a fresh list on every gesture.
    private final java.util.List<Button> selectableControls = new java.util.ArrayList<>(7);
    private Button closeButton;
    private double pendingWheelScroll;
    // Prevent held R / key repeat from flooding the server with squad scans.
    // The server response recreates this screen; keep the cooldown across instances.
    // Otherwise holding R rebuilds the screen and resets its per-instance throttle.
    private static int lastRefreshTick = Integer.MIN_VALUE;
    // The cooldown belongs to a specific local player, not the entire client.
    // A reconnect or respawn may start a new tick clock at the same value.
    private static net.minecraft.client.player.LocalPlayer refreshCooldownOwner;
    private int lastAutomaticRefreshTick = Integer.MIN_VALUE;
    // Distinguish a confirmed server snapshot from a stale count when periodic
    // requests receive no replies (for example during connection lag).
    private int lastSquadResponseTick = Integer.MIN_VALUE;
    // A manual refresh keeps the last confirmed count visible but marks it as
    // pending until the server replies (or the short status timeout expires).
    private int manualRefreshDeadlineTick = Integer.MIN_VALUE;
    // A timed-out manual refresh must not silently present the old squad count as fresh.
    private boolean manualRefreshTimedOut;
    // The squad overview belongs to the dimension where it was opened. Do not
    // display or refresh an old squad after a portal transfer or world change.
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> openedDimension;
    // A respawn can replace LocalPlayer without changing the dimension or
    // resetting tickCount. Bind this screen to the exact player instance.
    private net.minecraft.client.player.LocalPlayer openedPlayer;
    private boolean lastOrdersEnabled;
    private String lastUnavailableReason = "";
    // Reuse these labels instead of allocating a new array every rendered frame.
    // Mouse hover takes precedence over keyboard focus, so the tactical hint
    // always describes the command the player is currently pointing at.
    private static final String[] ORDER_PREVIEWS = {
        "За мной · следовать за игроком",
        "Охранять · удерживать позицию",
        "Собрать · вернуться к игроку",
        "Отступить · прекратить бой",
        "Атаковать · отмеченная цель",
        "К точке · идти к блоку взгляда"
    };
    // Reuse the same immutable tooltips when server snapshots enable or disable
    // orders. Avoid rebuilding six long components on every state transition.
    private static final Tooltip[] ORDER_TOOLTIPS = {
        Tooltip.create(Component.literal("За мной — волки следуют за вами и выходят из режима охраны.")),
        Tooltip.create(Component.literal("Охранять позицию — волки остаются охранять текущую позицию.")),
        Tooltip.create(Component.literal("Собрать рядом — позвать принадлежащих вам волков к себе.")),
        Tooltip.create(Component.literal("Отступить ко мне — волки прекращают бой, встают и возвращаются к вам. Близкие волки уже в безопасности.")),
        Tooltip.create(Component.literal("Атаковать отмеченного монстра — ударьте монстра не более 10 секунд назад. Вы и волки должны видеть цель в радиусе 24 блоков. Криперы, Хранитель и Иссушитель исключены: эти цели смертельно опасны для волков.")),
        Tooltip.create(Component.literal("К точке взгляда — наведите прицел на блок в радиусе 24 блоков ДО открытия панели G. После выбора приказа волки пойдут туда, если путь безопасен."))
    };
    private static final Component NO_SQUAD_HELP = Component.literal("Нет доступных волков: приручите волка костями, затем подойдите к нему на расстояние приказов.");
    private static final Component STALE_SQUAD_HELP = Component.literal("Состав отряда не подтверждён сервером. Нажмите 0/R/F5, среднюю кнопку мыши или заголовок панели и дождитесь ответа сервера.");
    private static final Component PENDING_SQUAD_HELP = Component.literal("Обновляем состав отряда. Дождитесь ответа сервера перед отправкой приказа.");
    private static final Component INACTIVE_PLAYER_HELP = Component.literal("Нельзя отдавать приказы отряду в режиме наблюдателя или после гибели.");
    // Disabled commands share immutable status tooltips. Reusing one instance per
    // state avoids six allocations whenever a verification starts or times out.
    private static final Tooltip NO_SQUAD_TOOLTIP = Tooltip.create(NO_SQUAD_HELP);
    private static final Tooltip STALE_SQUAD_TOOLTIP = Tooltip.create(STALE_SQUAD_HELP);
    private static final Tooltip PENDING_SQUAD_TOOLTIP = Tooltip.create(PENDING_SQUAD_HELP);
    private static final Tooltip INACTIVE_PLAYER_TOOLTIP = Tooltip.create(INACTIVE_PLAYER_HELP);

    private Tooltip unavailableTooltip() {
        if (minecraft != null && minecraft.player != null
                && (!minecraft.player.isAlive() || minecraft.player.isSpectator())) {
            return INACTIVE_PLAYER_TOOLTIP;
        }
        if (isManualRefreshPending()) return PENDING_SQUAD_TOOLTIP;
        if (manualRefreshTimedOut || hasManualRefreshExpired() || isSquadSnapshotStale()) {
            return STALE_SQUAD_TOOLTIP;
        }
        return NO_SQUAD_TOOLTIP;
    }


    private boolean isSquadSnapshotStale() {
        if (minecraft == null || minecraft.player == null || lastSquadResponseTick == Integer.MIN_VALUE) return false;
        int now = minecraft.player.tickCount;
        return now >= lastSquadResponseTick && now - lastSquadResponseTick > 120;
    }

    private boolean hasManualRefreshExpired() {
        return minecraft != null && minecraft.player != null
            && manualRefreshDeadlineTick != Integer.MIN_VALUE
            && minecraft.player.tickCount > manualRefreshDeadlineTick;
    }

    // Display a whole-second countdown using the player's existing tick clock.
    // No extra packets or timers: the number updates during normal screen rendering.
    private int manualRefreshSecondsRemaining() {
        if (!isManualRefreshPending()) return 0;
        int ticksLeft = Math.max(0, manualRefreshDeadlineTick - minecraft.player.tickCount);
        return (ticksLeft + 19) / 20;
    }

    private boolean isManualRefreshPending() {
        return minecraft != null && minecraft.player != null
            && manualRefreshDeadlineTick != Integer.MIN_VALUE
            && minecraft.player.tickCount <= manualRefreshDeadlineTick;
    }

    private boolean isOriginalDimension() {
        // Input can arrive between a dimension transfer and the next screen tick.
        // Reject stale orders immediately instead of relying on tick() to close us.
        return minecraft != null && minecraft.player != null && minecraft.player == openedPlayer
            && openedDimension != null && minecraft.player.level().dimension() == openedDimension;
    }

    // A respawn or client world replacement can reset the player tick counter
    // while an old tactical screen is still open. Never trust its old snapshot.
    private boolean isPlayerClockValid() {
        return minecraft != null && minecraft.player != null
            && (lastSquadResponseTick == Integer.MIN_VALUE
                || minecraft.player.tickCount >= lastSquadResponseTick);
    }

    private boolean canIssueOrders() {
        return isPlayerClockValid() && isOriginalDimension()
            // Keep commands locked until the server actually replies, even if
            // the deadline passed between two client ticks. The tick handler
            // will turn that pending request into a visible timeout warning.
            && manualRefreshDeadlineTick == Integer.MIN_VALUE && !manualRefreshTimedOut
            && !isSquadSnapshotStale() && available > 0
            && minecraft.player.isAlive() && !minecraft.player.isSpectator();
    }

    private Component unavailableHelp() {
        return minecraft != null && minecraft.player != null
            && (!minecraft.player.isAlive() || minecraft.player.isSpectator())
            ? INACTIVE_PLAYER_HELP : isManualRefreshPending() ? PENDING_SQUAD_HELP
            : manualRefreshTimedOut || hasManualRefreshExpired() || isSquadSnapshotStale() ? STALE_SQUAD_HELP : NO_SQUAD_HELP;
    }

    public ReputationArmyScreen(int available) {
        super(Component.literal("Отряд — приказы"));
        this.available = available;
    }

    /** Update the authoritative count without recreating the menu or losing focus. */
    public void updateSquadCount(int count) {
        // A delayed server reply can arrive after a portal transfer or respawn,
        // before the next screen tick has closed this old menu. Never let that
        // reply overwrite the old timestamp and re-enable stale commands.
        if (!isOriginalDimension() || !isPlayerClockValid()) {
            onClose();
            return;
        }
        // A reply to an expired manual verification is not proof of a fresh
        // squad snapshot. Keep the timeout visible and orders disabled until
        // the player explicitly retries with R, middle-click or the heading.
        // Without this guard, a delayed packet could silently clear the error.
        if (hasManualRefreshExpired()) {
            manualRefreshDeadlineTick = Integer.MIN_VALUE;
            manualRefreshTimedOut = true;
            refreshOrderAvailability();
            return;
        }
        if (manualRefreshTimedOut && manualRefreshDeadlineTick == Integer.MIN_VALUE) return;
        available = Math.max(0, count);
        if (minecraft != null && minecraft.player != null) {
            lastSquadResponseTick = minecraft.player.tickCount;
        }
        manualRefreshDeadlineTick = Integer.MIN_VALUE;
        manualRefreshTimedOut = false;
        refreshOrderAvailability();
    }

    private boolean initialized;

    @Override protected void init() {
        // Screen.init also runs on window resize / GUI-scale changes. Preserve
        // pending server verification and stale-response timing across relayouts:
        // otherwise resizing could falsely unlock orders using an old count.
        if (!initialized) {
            openedPlayer = minecraft != null ? minecraft.player : null;
            openedDimension = openedPlayer != null ? openedPlayer.level().dimension() : null;
            // Do not inherit a previous world's scan throttle when G is opened
            // immediately after reconnecting or respawning. Keep it across
            // ordinary screen reopenings for the same player instance.
            if (refreshCooldownOwner != openedPlayer) {
                refreshCooldownOwner = openedPlayer;
                lastRefreshTick = Integer.MIN_VALUE;
            }
            lastAutomaticRefreshTick = minecraft != null && minecraft.player != null
                ? minecraft.player.tickCount : Integer.MIN_VALUE;
            lastSquadResponseTick = lastAutomaticRefreshTick;
            initialized = true;
        }
        // Preserve keyboard/controller selection across GUI-scale and window resizes.
        // Recreating widgets otherwise jumps focus back to the first command,
        // potentially selecting a different order just before Enter is pressed.
        int previousFocus = orderButtons.indexOf(getFocused());
        boolean closeWasFocused = getFocused() == closeButton;
        orderButtons.clear();
        pendingWheelScroll = 0;
        closeButton = null;
        lastOrdersEnabled = canIssueOrders();
        lastUnavailableReason = unavailableHelp().getString();
        int w = Math.max(24, Math.min(238, width - 16));
        int x = (width - w) / 2;
        int y = panelTop();
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int firstButtonY = y + headerHeight();
        Button follow = Button.builder(commandLabel(1, "За мной", w - 16), b -> issue(0))
            .bounds(x + 8, firstButtonY, w - 16, buttonHeight).build();
        follow.setTooltip(ORDER_TOOLTIPS[0]);
        if (!canIssueOrders()) follow.setTooltip(unavailableTooltip());
        follow.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(follow));
        Button guard = Button.builder(commandLabel(2, "Охранять позицию", w - 16), b -> issue(1))
            .bounds(x + 8, firstButtonY + buttonHeight + buttonGap, w - 16, buttonHeight).build();
        guard.setTooltip(ORDER_TOOLTIPS[1]);
        if (!canIssueOrders()) guard.setTooltip(unavailableTooltip());
        guard.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(guard));
        Button rally = Button.builder(commandLabel(3, "Собрать рядом", w - 16), b -> issue(2))
            .bounds(x + 8, firstButtonY + 2 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        rally.setTooltip(ORDER_TOOLTIPS[2]);
        if (!canIssueOrders()) rally.setTooltip(unavailableTooltip());
        rally.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(rally));
        Button disengage = Button.builder(commandLabel(4, "Отступить ко мне", w - 16), b -> issue(3))
            .bounds(x + 8, firstButtonY + 3 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        disengage.setTooltip(ORDER_TOOLTIPS[3]);
        if (!canIssueOrders()) disengage.setTooltip(unavailableTooltip());
        disengage.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(disengage));
        Button attack = Button.builder(commandLabel(5, "Атаковать отмеченного монстра", w - 16), b -> issue(4))
            .bounds(x + 8, firstButtonY + 4 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        attack.setTooltip(ORDER_TOOLTIPS[4]);
        if (!canIssueOrders()) attack.setTooltip(unavailableTooltip());
        attack.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(attack));
        Button waypoint = Button.builder(commandLabel(6, "К точке взгляда", w - 16), b -> issue(5))
            .bounds(x + 8, firstButtonY + 5 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        waypoint.setTooltip(ORDER_TOOLTIPS[5]);
        if (!canIssueOrders()) waypoint.setTooltip(unavailableTooltip());
        waypoint.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(waypoint));
        closeButton = addRenderableWidget(Button.builder(commandLabel(7, "Закрыть", w - 16), b -> onClose())
            .bounds(x + 8, firstButtonY + 6 * (buttonHeight + buttonGap), w - 16, buttonHeight).build());
        // Keyboard/controller navigation should start on an actionable control.
        // When the squad is unavailable, focus Close instead of a disabled order.
        if (closeWasFocused && closeButton != null) {
            setInitialFocus(closeButton);
        } else if (previousFocus >= 0 && previousFocus < orderButtons.size()
                && orderButtons.get(previousFocus).active) {
            setInitialFocus(orderButtons.get(previousFocus));
        } else {
            setInitialFocus(canIssueOrders() ? follow : closeButton);
        }
    }

    // Player state can change while this non-pausing screen is open (death, spectator
    // switch). Refresh button availability instead of retaining the init-time snapshot.
    private void refreshOrderAvailability() {
        boolean enabled = canIssueOrders();
        String reason = unavailableHelp().getString();
        // A disabled panel may change from no wolves to spectator/death without
        // ever becoming enabled. Refresh the explanation as well as the buttons.
        if (enabled == lastOrdersEnabled && reason.equals(lastUnavailableReason)) return;
        boolean availabilityChanged = enabled != lastOrdersEnabled;
        lastOrdersEnabled = enabled;
        lastUnavailableReason = reason;
        for (Button button : orderButtons) button.active = enabled;
        // Never leave keyboard/controller focus on a disabled order after death or
        // a spectator switch. Restore the first command when control returns.
        if (availabilityChanged) {
            // An automatic squad refresh must not steal keyboard focus from Close
            // when wolves walk back into range. Only move focus if the currently
            // selected control has become disabled (or no control is selected).
            if (!enabled && closeButton != null) {
                setFocused(closeButton);
            } else if (enabled && !orderButtons.isEmpty()
                    && (!(getFocused() instanceof Button focused) || !focused.active)) {
                setFocused(orderButtons.get(0));
            }
        }
        if (!enabled) {
            Tooltip statusTooltip = unavailableTooltip();
            for (Button button : orderButtons) button.setTooltip(statusTooltip);
        } else {
            // Restore the individual command explanations when the player becomes active.
            for (int i = 0; i < orderButtons.size(); i++) {
                orderButtons.get(i).setTooltip(ORDER_TOOLTIPS[i]);
            }
        }
    }

    // Full action names remain readable in tooltips even when narrow GUI scales
    // truncate button labels with an ellipsis.
    // Numbered actions make the keyboard shortcuts discoverable without a separate
    // tooltip. Fit labels into narrow GUI-scaled windows at native pixel resolution.
    private Component commandLabel(int shortcut, String name, int buttonWidth) {
        String prefix = shortcut + " · ";
        int availableWidth = Math.max(0, buttonWidth - 12);
        // At extreme GUI scales the numeric prefix itself can exceed the button.
        // Show only the shortcut (or no glyph if even that cannot fit) rather than
        // allowing the label to bleed into adjacent controls or outside the panel.
        if (font.width(prefix) > availableWidth) {
            String digit = Integer.toString(shortcut);
            return Component.literal(font.width(digit) <= availableWidth ? digit : "");
        }
        int textWidth = availableWidth - font.width(prefix);
        // Reserve the ellipsis inside the available width so long order names do not
        // silently appear complete at large GUI scales or narrow windows.
        if (font.width(name) <= textWidth) return Component.literal(prefix + name);
        String ellipsis = "…";
        int visibleWidth = Math.max(0, textWidth - font.width(ellipsis));
        return Component.literal(prefix + font.plainSubstrByWidth(name, visibleWidth)
            + (textWidth >= font.width(ellipsis) ? ellipsis : ""));
    }

    // Fit all six commands into short GUI-scaled windows without fractional font scaling.
    private int headerHeight() { return height < 105 ? 12 : height < 120 ? 26 : height < 165 ? 37 : 48; }
    private int commandButtonGap() { return height < 80 ? 0 : height < 165 ? 1 : height < 240 ? 2 : 6; }
    private int commandButtonHeight() {
        // Reserve room for all six orders, Close and the frame at medium GUI heights.
        // Fixed 20px buttons previously pushed Close off-screen at heights 165-235.
        int space = height - 12 - headerHeight() - 6 * commandButtonGap();
        // Very short GUI-scaled windows need smaller buttons rather than clipping Close.
        // Keep integer pixel sizes; normal windows retain their existing dimensions.
        return Math.max(1, Math.min(20, space / 7));
    }

    private int panelTop() {
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int contentHeight = headerHeight() + 7 * buttonHeight + 6 * buttonGap + 8;
        return Math.max(1, (height - contentHeight) / 2);
    }

    private void issue(int order) {
        if (!canIssueOrders()) {
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(unavailableHelp(), true);
            return;
        }
        PacketDistributor.sendToServer(new ReputationNetwork.ArmyOrder(order));
        onClose();
    }

    // Keep the tactical overview current while the player is looking at it.
    // Wolves can walk into range, leave it or dismount without another key press.
    // Scan at most once every two seconds; the server still validates and throttles.
    @Override public void tick() {
        super.tick();
        // Portal travel can leave this client-side Screen alive briefly while
        // the server switches levels. Close instead of showing stale orders.
        if (!isPlayerClockValid() || !isOriginalDimension()) {
            onClose();
            return;
        }
        // Expire manual verification during the game tick, not during rendering.
        // Input events can arrive before the next frame; commands must already
        // be disabled when a pending server confirmation has timed out.
        if (hasManualRefreshExpired()) {
            manualRefreshDeadlineTick = Integer.MIN_VALUE;
            manualRefreshTimedOut = true;
            refreshOrderAvailability();
        }
        if (minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.player.isSpectator()) return;
        int now = minecraft.player.tickCount;
        // A manual verification is already waiting for the server. Do not issue
        // a competing automatic scan that could overwrite its pending result.
        // Automatic polling resumes normally after the reply or timeout.
        if (isManualRefreshPending()) return;
        // Once an explicit verification times out, do not let background polling
        // silently clear the warning with an unrelated late reply. The player
        // must request a fresh scan with R/F5, middle-click or the panel heading.
        // This also avoids wasting packets while the connection is unresponsive.
        if (manualRefreshTimedOut) return;
        // Stop unattended polling after the last server-confirmed squad count
        // becomes stale. A disconnected or overloaded server must not receive
        // repeated scans forever while this overlay remains open. Keep the
        // existing manual refresh action available for explicit recovery.
        if (isSquadSnapshotStale()) return;
        if (lastAutomaticRefreshTick == Integer.MIN_VALUE || now < lastAutomaticRefreshTick) {
            lastAutomaticRefreshTick = now;
            return;
        }
        if (now - lastAutomaticRefreshTick < 40) return;
        lastAutomaticRefreshTick = now;
        if (lastRefreshTick != Integer.MIN_VALUE && now >= lastRefreshTick
            && now - lastRefreshTick < 10) return;
        lastRefreshTick = now;
        PacketDistributor.sendToServer(new ReputationNetwork.ArmyRequest());
    }

    // Vanilla Screen.renderBackground applies the menu blur to the world. This
    // tactical overlay intentionally leaves the scene readable so players can
    // see threats while choosing an order. The panel itself is drawn in render().
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No fullscreen darkening or post-processing blur.
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshOrderAvailability();
        // Keep the world fully visible behind squad commands; no fullscreen dim layer.
        int w = Math.max(24, Math.min(238, width - 16));
        int x = (width - w) / 2, y = panelTop();
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int firstButtonY = y + headerHeight();
        // Include all six orders AND the Close button in the panel frame.
        int panelBottom = Math.min(height - 2, firstButtonY + 7 * buttonHeight + 6 * buttonGap + 8);
        g.fill(x - 3, y - 3, x + w + 3, panelBottom, 0xFF30251A);
        g.fill(x, y, x + w, panelBottom - 3, 0xFFE2C796);
        // Pixel-sharp inset frame and faction-neutral gold accent.
        g.fill(x + 2, y + 2, x + w - 2, y + 4, 0xFFD4A64E);
        g.fill(x + 3, panelBottom - 7, x + w - 3, panelBottom - 5, 0xFF8B6334);
        // The heading is a real refresh target: expose its hit area visually on
        // hover, including compact GUI layouts where the text hint is hidden.
        // Use integer pixel fills so GUI scaling stays crisp and inexpensive.
        boolean headerHovered = mouseX >= x && mouseX < x + w
            && mouseY >= y && mouseY < y + headerHeight();
        if (headerHovered) {
            g.fill(x + 4, y + 5, x + w - 4, y + 6, 0xFF8B6334);
            g.fill(x + 4, y + headerHeight() - 3, x + w - 4,
                y + headerHeight() - 2, 0xFF8B6334);
        }
        // Windows below 165 GUI pixels cannot fit the secondary squad-count line: show the
        // actionable squad state in the heading instead of silently hiding it.
        // Show why commands are disabled directly in the panel, not only in tooltips.
        // Spectators and dead players may still have a nonzero cached squad count.
        boolean playerInactive = minecraft != null && minecraft.player != null
            && (!minecraft.player.isAlive() || minecraft.player.isSpectator());
        // Compact layouts omit the secondary status row, so surface pending and
        // failed server refreshes in the heading instead of hiding the feedback.
        boolean refreshing = isManualRefreshPending();
        boolean staleSquad = !playerInactive && (isSquadSnapshotStale() || hasManualRefreshExpired());
        boolean refreshFailed = manualRefreshTimedOut || hasManualRefreshExpired();
        String headingText = playerInactive
            ? (minecraft.player.isSpectator() ? "Режим наблюдателя" : "Игрок погиб")
            : height < 165
                ? (refreshing ? "Проверка · " + manualRefreshSecondsRemaining() + " с"
                    : refreshFailed ? "Нет ответа · 0/R/F5"
                    : staleSquad ? "Нет связи · 0/R/F5"
                    : available == 0 ? "Нет отряда" : "Отряд: " + available)
                : title.getString();
        String heading = font.plainSubstrByWidth(headingText, Math.max(1, w - 16));
        // Compact GUI scales hide the secondary status row. Keep critical
        // server/time-out feedback visible in the heading itself, using the
        // same accessible status palette as the full-size panel.
        int headingColor = height < 165
            ? (playerInactive || refreshFailed || staleSquad ? 0xFF9E3030
                : refreshing ? 0xFF956019
                : available > 0 ? 0xFF28613C : 0xFF45301B)
            : 0xFF45301B;
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + (height < 105 ? 1 : 8), headingColor, false);
        String count = playerInactive ? "Приказы временно недоступны"
            : refreshing ? "Проверка: " + manualRefreshSecondsRemaining() + " с · было: " + available
            : refreshFailed ? "Нет ответа сервера · 0/R/F5"
            : staleSquad ? "Состав устарел · 0/R/F5 обновить"
            : available == 0 ? "Приручите волка и подойдите ближе" : "Ваши волки рядом: " + available;
        if (height >= 165) {
            // Show server verification status without blur, overlays or extra render passes.
            // Red = unavailable/stale, amber = pending, green = confirmed squad.
            int statusColor = playerInactive || refreshFailed || staleSquad
                ? 0xFF9E3030 : refreshing ? 0xFF956019
                : available > 0 ? 0xFF28613C : 0xFF705438;
            g.drawString(font, font.plainSubstrByWidth(count, w - 16), x + 8, y + 25, statusColor, false);
        }
        // Keep the verification progress visible even on compact GUI scales:
        // previously the entire bar disappeared below 165px, leaving only text.
        // Use a single pixel on short headers so the bar cannot cover commands.
        // Integer fills remain sharp and require no shaders or extra packets.
        if (refreshing) {
            int barLeft = x + 8;
            int barRight = x + w - 8;
            int barHeight = height < 165 ? 1 : 2;
            int barY = y + headerHeight() - barHeight;
            int totalWidth = Math.max(0, barRight - barLeft);
            // Fill left-to-right as the verification deadline approaches. The
            // previous shrinking bar looked like the request was being undone.
            // Clamp the elapsed fraction to keep the bar inside its frame even
            // when a frame arrives just after the timeout.
            int ticksRemaining = Math.max(0, manualRefreshDeadlineTick - minecraft.player.tickCount);
            int elapsedTicks = 40 - Math.min(40, ticksRemaining);
            int filledWidth = totalWidth * elapsedTicks / 40;
            g.fill(barLeft, barY, barRight, barY + barHeight, 0xFF9D825A);
            if (filledWidth > 0) {
                g.fill(barLeft, barY, barLeft + filledWidth, barY + barHeight, 0xFF956019);
            }
        }
        // Only taller GUI heights have room for the optional range hint.
        // Omit this optional hint rather than drawing text over a clickable command.
        // The compact 37px header (GUI heights 165-239) has no room for a
        // third text row: its 9px glyphs would overlap the first order button.
        // Reserve the range hint for the 48px header on taller windows.
        if (height >= 240) {
            // Keyboard and wheel users need to understand the focused order
            // without hovering a tooltip. Reuse the spare header row as a
            // contextual command preview; no extra widgets or render passes.
            int selectedOrder = orderButtons.indexOf(getFocused());
            boolean hoveringClose = closeButton != null && closeButton.isMouseOver(mouseX, mouseY);
            // Hovering a command should describe that command even when a
            // different button still owns keyboard/controller focus.
            for (int i = 0; i < orderButtons.size(); i++) {
                if (orderButtons.get(i).isMouseOver(mouseX, mouseY)) {
                    selectedOrder = i;
                    break;
                }
            }
            // A focused but disabled order must explain why it cannot be used,
            // not advertise an action that the server will reject.
            String range = !canIssueOrders() && !hoveringClose && getFocused() != closeButton
                ? (refreshing ? "Ожидание ответа сервера"
                    : refreshFailed || staleSquad ? "0/R/F5 · повторить проверку"
                    : playerInactive ? "Приказы сейчас недоступны"
                    : "Нет волков в радиусе приказов")
                : hoveringClose ? "Закрыть · вернуться в игру"
                : selectedOrder >= 0 && selectedOrder < ORDER_PREVIEWS.length
                    ? ORDER_PREVIEWS[selectedOrder]
                    : getFocused() == closeButton ? "Закрыть · вернуться в игру"
                    : "1–7 · 0/R/F5: обновить";
            g.drawString(font, font.plainSubstrByWidth(range, w - 16), x + 8, y + 35, 0xFF705438, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        // Keyboard and wheel navigation must have an unmistakable selection cue.
        // Draw a crisp one-pixel outline outside the focused button, without
        // scaling fonts, adding blur or covering the button's readable label.
        if (getFocused() instanceof Button focused && focused.visible && focused.active) {
            int left = focused.getX() - 2;
            int top = focused.getY() - 2;
            int right = focused.getX() + focused.getWidth() + 2;
            int bottom = focused.getY() + focused.getHeight() + 2;
            int accent = 0xFFFFD66B;
            g.fill(left, top, right, top + 1, accent);
            g.fill(left, bottom - 1, right, bottom, accent);
            g.fill(left, top + 1, left + 1, bottom - 1, accent);
            g.fill(right - 1, top + 1, right, bottom - 1, accent);
        }
    }

    // Number row and numeric keypad mirror all seven visible panel actions.
    // Disabled commands must never send packets when no owned squad is nearby.
    // One shared refresh path keeps keyboard and mouse input subject to the
    // same client cooldown and server-authoritative response handling.
    private void requestManualRefresh() {
        // A refresh packet from the old dimension could produce a misleading
        // squad snapshot after portal travel; wait for the new panel instead.
        // A respawn may reset the player clock before the next screen tick.
        // Do not request a new snapshot through an old tactical screen: close
        // it and let the player reopen G in the new life instead.
        if (!isOriginalDimension() || !isPlayerClockValid()) {
            onClose();
            return;
        }
        if (!minecraft.player.isAlive() || minecraft.player.isSpectator()) {
            minecraft.player.displayClientMessage(INACTIVE_PLAYER_HELP, true);
            return;
        }
        // Keep one authoritative manual scan in flight. Repeated R presses or
        // middle-clicks must not queue competing responses that can overwrite
        // the result of a later scan. A timed-out request remains retryable.
        if (isManualRefreshPending()) {
            minecraft.player.displayClientMessage(PENDING_SQUAD_HELP, true);
            return;
        }
        int now = minecraft.player.tickCount;
        if (lastRefreshTick != Integer.MIN_VALUE && now >= lastRefreshTick
                && now - lastRefreshTick < 10) {
            // An automatic scan may have just used the shared rate limit.
            // Tell the player why R/click did nothing instead of silently
            // ignoring an explicit request, especially after a timeout.
            minecraft.player.displayClientMessage(Component.literal(
                "Проверка отряда доступна через " + (10 - (now - lastRefreshTick)) + " тиков."), true);
            return;
        }
        lastRefreshTick = now;
        // A manual scan also satisfies the periodic polling schedule. Without
        // resetting its clock, the next screen tick can send a redundant
        // automatic request immediately after the manual reply, potentially
        // racing the next player-initiated verification on a slow connection.
        lastAutomaticRefreshTick = now;
        manualRefreshDeadlineTick = now + 40;
        manualRefreshTimedOut = false;
        pendingWheelScroll = 0;
        // Disable order buttons immediately, even before the next render frame.
        refreshOrderAvailability();
        PacketDistributor.sendToServer(new ReputationNetwork.ArmyRequest());
    }

    /** Rebuild the small navigation view in visual order, skipping unavailable controls. */
    private java.util.List<Button> selectableControls() {
        selectableControls.clear();
        for (Button button : orderButtons) {
            if (button.active && button.visible) selectableControls.add(button);
        }
        if (closeButton != null && closeButton.active && closeButton.visible) {
            selectableControls.add(closeButton);
        }
        return selectableControls;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // A portal transfer or respawn can happen before the next screen tick.
        // Reject input on the old tactical panel immediately.
        if (!isOriginalDimension() || !isPlayerClockValid()) {
            onClose();
            return true;
        }
        // The configurable army key takes precedence over numeric shortcuts.
        // Otherwise remapping G to 1-7 would accidentally issue an order instead
        // of closing the panel (and a remapped navigation key would move focus).
        // A remapped G key can overlap Ctrl/Alt/Super shortcuts used by the
        // operating system or accessibility tools. Never dismiss the tactical
        // panel because a modified shortcut happens to share its base key.
        // Shift remains usable for layouts that require it to type the binding.
        if (ReputationKeys.ARMY.matches(keyCode, scanCode)
                && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT
                    | GLFW.GLFW_MOD_SUPER)) == 0) {
            onClose();
            return true;
        }
        // Refresh with R or F5 without closing the panel. F5 is useful when R is
        // reserved by a custom key binding. The middle mouse button
        // offers the same action for players navigating the menu by mouse.
        // Lock-key state must not disable the manual squad refresh shortcut.
        // Only held Shift/Ctrl/Alt/Super should reserve R for another binding.
        int heldModifiers = modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL
            | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER);
        if ((keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_F5
                || keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0)
                && heldModifiers == 0) {
            requestManualRefresh();
            return true;
        }
        int order = -1;
        // Only unmodified number keys are squad orders. Ctrl/Alt/Shift plus a
        // digit may be reserved by accessibility tools, key remaps or overlays;
        // never turn those combinations into an unintended attack/retreat.
        // GLFW may include Caps Lock / Num Lock state in modifiers when lock-key
        // reporting is enabled. Those are not held shortcut modifiers: allow
        // numeric orders while either lock is on, including keypad orders.
        int shortcutModifiers = heldModifiers;
        if (shortcutModifiers == 0 && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_7) {
            order = keyCode - GLFW.GLFW_KEY_1;
        } else if (shortcutModifiers == 0 && keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_7) {
            order = keyCode - GLFW.GLFW_KEY_KP_1;
        }
        // Respect system/accessibility shortcuts that combine Ctrl, Alt or Super
        // with navigation keys. They must not silently change a combat selection.
        // Shift remains available for reverse Tab navigation.
        boolean reservedNavigationModifier = (heldModifiers & (GLFW.GLFW_MOD_CONTROL
            | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0;
        if (reservedNavigationModifier && (keyCode == GLFW.GLFW_KEY_TAB
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END
                || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            pendingWheelScroll = 0;
            // Screen's default focus traversal may still act on modified Tab or
            // arrow keys. Consume reserved system/accessibility combinations
            // here instead of allowing a combat order to become selected.
            return true;
        }
        // Enter/Space activate the focused vanilla Button. With Ctrl/Alt/Super
        // held, these keys are commonly used by accessibility tools and OS
        // shortcuts; do not let Screen forward them into a combat order.
        // Plain Enter/Space (and Shift+Space for keyboard users) retain vanilla
        // activation. This guard is local: it never sends a network packet.
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE)
                && (heldModifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT
                    | GLFW.GLFW_MOD_SUPER)) != 0) {
            pendingWheelScroll = 0;
            return true;
        }
        // Tab and Shift+Tab cycle through enabled commands and Close in a stable
        // visual order. Never activate a command while navigating, and skip
        // disabled orders when the player has no available squad.
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            pendingWheelScroll = 0; // A later wheel gesture must start fresh after keyboard navigation.
            java.util.List<Button> selectable = selectableControls();
            if (!selectable.isEmpty()) {
                int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                int current = selectable.indexOf(getFocused());
                int next = current < 0 ? (direction > 0 ? 0 : selectable.size() - 1)
                    : Math.floorMod(current + direction, selectable.size());
                setFocused(selectable.get(next));
            }
            return true;
        }
        // Arrow keys navigate the tactical list without activating orders.
        // Left/Right work too, supporting keyboard layouts and controller-style input.
        // This mirrors the mouse wheel and keeps the menu usable without a mouse.
        // Disabled squad actions are skipped; Close remains keyboard-accessible.
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
            || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            pendingWheelScroll = 0;
            java.util.List<Button> selectable = selectableControls();
            if (!selectable.isEmpty()) {
                int direction = (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_RIGHT) ? 1 : -1;
                int current = selectable.indexOf(getFocused());
                int next = current < 0 ? (direction > 0 ? 0 : selectable.size() - 1)
                    : Math.floorMod(current + direction, selectable.size());
                setFocused(selectable.get(next));
            }
            return true;
        }
        // Home/End jump to the first/last available control without sending an
        // order. Keep Close reachable when the player dies or has no squad.
        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            pendingWheelScroll = 0;
            java.util.List<Button> selectable = selectableControls();
            if (!selectable.isEmpty()) {
                setFocused(selectable.get(keyCode == GLFW.GLFW_KEY_HOME ? 0 : selectable.size() - 1));
            }
            return true;
        }
        // Page Up / Page Down jump three available controls at a time, wrapping
        // through Close instead of getting stuck at the list boundary. This
        // lets keyboard-only players reach the opposite end with one gesture,
        // without activating a command or sending a network packet.
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            pendingWheelScroll = 0;
            java.util.List<Button> selectable = selectableControls();
            if (!selectable.isEmpty()) {
                int current = selectable.indexOf(getFocused());
                int direction = keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
                int start = current < 0 ? (direction > 0 ? -1 : 0) : current;
                int next = Math.floorMod(start + direction * Math.min(3, selectable.size()), selectable.size());
                setFocused(selectable.get(next));
            }
            return true;
        }
        if (order >= 0) {
            // Shift+number also selects alternate hotbar actions in some key layouts.
            // Require unmodified number presses to avoid accidental attack/move orders.
            if ((modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (order == 6) {
                // Closing the tactical overlay must remain possible without a squad.
                onClose();
            } else if (canIssueOrders()) {
                issue(order);
            } else if (minecraft != null && minecraft.player != null) {
                // Keyboard shortcuts need the same guidance as disabled buttons.
                // Keep the order server-authoritative: never send an empty-squad packet.
                minecraft.player.displayClientMessage(unavailableHelp(), true);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Right-click dismisses the tactical overlay without issuing an order.
    // This is especially useful when the player spots a threat behind the
    // blur-free panel and needs to return to normal camera control quickly.
    // Only left-click may activate an order button.
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOriginalDimension() || !isPlayerClockValid()) {
            onClose();
            return true;
        }
        // Do not carry a partial trackpad gesture into a mouse-selected command.
        pendingWheelScroll = 0;
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            // Middle-click refresh is a panel action, not a global click trap.
            // Dismiss clicks on the visible world instead of sending a server
            // scan the player did not intend. This mirrors left-click outside.
            int panelWidth = Math.max(24, Math.min(238, width - 16));
            int panelX = (width - panelWidth) / 2;
            int panelY = panelTop();
            int panelBottom = Math.min(height - 2, panelY + headerHeight()
                + 7 * commandButtonHeight() + 6 * commandButtonGap() + 8);
            if (mouseX < panelX - 3 || mouseX >= panelX + panelWidth + 3
                    || mouseY < panelY - 3 || mouseY >= panelBottom) {
                onClose();
            } else {
                requestManualRefresh();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        // A click on the visible world outside the tactical panel dismisses it
        // without issuing an order. Keep clicks within the frame (including its
        // border) for the actual buttons so compact GUI scales remain usable.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int panelWidth = Math.max(24, Math.min(238, width - 16));
            int panelX = (width - panelWidth) / 2;
            int panelY = panelTop();
            int panelBottom = Math.min(height - 2, panelY + headerHeight()
                + 7 * commandButtonHeight() + 6 * commandButtonGap() + 8);
            if (mouseX < panelX - 3 || mouseX >= panelX + panelWidth + 3
                    || mouseY < panelY - 3 || mouseY >= panelBottom) {
                onClose();
                return true;
            }
            // The squad heading doubles as a refresh target. Players using a
            // mouse or touch-style controls can verify their squad without R
            // or a middle mouse button. Keep the hit area above all orders.
            if (mouseX >= panelX && mouseX < panelX + panelWidth
                    && mouseY >= panelY && mouseY < panelY + headerHeight()) {
                requestManualRefresh();
                return true;
            }
        }
        // Vanilla ignores clicks on inactive buttons without explaining why.
        // Give mouse users the same actionable server/status feedback that the
        // numbered keyboard shortcuts already provide, without sending an order.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !canIssueOrders()) {
            for (Button orderButton : orderButtons) {
                if (orderButton.visible && mouseX >= orderButton.getX()
                        && mouseX < orderButton.getX() + orderButton.getWidth()
                        && mouseY >= orderButton.getY()
                        && mouseY < orderButton.getY() + orderButton.getHeight()) {
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(unavailableHelp(), true);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Mouse wheel moves keyboard focus through the tactical commands without
    // sending any order. This allows quick one-handed selection while preserving
    // Enter/Space activation and the existing numbered shortcuts.
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isOriginalDimension() || !isPlayerClockValid()) {
            onClose();
            return true;
        }
        // Wheel navigation is local to the tactical panel. Scrolling over the
        // visible world must not silently change the selected combat order.
        // Discard a partial trackpad gesture on exit so returning to the panel
        // cannot complete an old scroll and unexpectedly select another order.
        int panelWidth = Math.max(24, Math.min(238, width - 16));
        int panelX = (width - panelWidth) / 2;
        int panelY = panelTop();
        int panelBottom = Math.min(height - 2, panelY + headerHeight()
            + 7 * commandButtonHeight() + 6 * commandButtonGap() + 8);
        if (mouseX < panelX - 3 || mouseX >= panelX + panelWidth + 3
                || mouseY < panelY - 3 || mouseY >= panelBottom) {
            pendingWheelScroll = 0;
            // The world is visible but this is still a modal tactical screen.
            // Do not delegate outside-panel wheel events to focused widgets:
            // they could change the selected combat command unexpectedly.
            return true;
        }
        // Only vertical scrolling changes the selected combat order. Horizontal
        // trackpad swipes are often used for navigation between desktops/pages;
        // treating them as squad input could leave a dangerous attack command
        // focused without the player intending to select it.
        // Diagonal touchpad gestures are common. If horizontal movement
        // dominates, treat the gesture as horizontal navigation rather than
        // selecting a potentially dangerous attack or retreat order.
        // Clear the accumulated fraction so a later vertical scroll starts
        // cleanly instead of completing a discarded diagonal gesture.
        if (scrollY == 0 || (Double.isFinite(scrollX)
                && Math.abs(scrollX) > Math.abs(scrollY))) {
            pendingWheelScroll = 0;
            // A horizontal or horizontal-dominant gesture must not fall through
            // to vanilla focus navigation, which may select a combat order.
            return true;
        }
        // Reject non-finite input before it can poison the accumulated fraction.
        double delta = scrollY;
        if (!Double.isFinite(delta)) {
            pendingWheelScroll = 0;
            return true;
        }
        // Trackpads send fractional deltas. Accumulate them so a tiny gesture
        // does not skip an entire command, while preserving fast wheel input.
        // Opposite fractional gestures cancel one another instead of discarding
        // the earlier fraction. Otherwise a 0.6 up / 0.6 down gesture could
        // unexpectedly advance focus despite zero net scrolling.
        // Bound each event BEFORE adding it to the accumulator. A finite but
        // enormous trackpad delta could otherwise overflow the sum to Infinity,
        // leaving the menu's wheel navigation permanently unresponsive.
        double boundedDelta = Math.max(-3.0, Math.min(3.0, delta));
        pendingWheelScroll += boundedDelta;
        if (Math.abs(pendingWheelScroll) < 1.0) return true;
        int direction = pendingWheelScroll > 0 ? -1 : 1;
        int wholeSteps = (int) Math.floor(Math.abs(pendingWheelScroll));
        pendingWheelScroll -= Math.copySign(wholeSteps, pendingWheelScroll);
        // Limit a single gesture to three controls. Huge scroll bursts should
        // not wrap all the way around and appear to have done nothing.
        java.util.List<Button> selectable = selectableControls();
        if (selectable.isEmpty()) return true;
        int current = selectable.indexOf(getFocused());
        // A single event may contain several wheel notches. Honour each notch
        // rather than silently dropping fast wheel/trackpad input; cap the
        // number of steps to keep unusually large deltas predictable.
        int steps = Math.min(3, wholeSteps);
        // Without keyboard focus, begin at the edge matching the wheel direction.
        // One notch selects the edge action; a multi-notch wheel event advances
        // the remaining steps rather than silently discarding them.
        int start = current < 0 ? (direction > 0 ? -1 : selectable.size()) : current;
        int stepsToMove = steps;
        // Stop at the first/last control instead of wrapping from Close to Attack.
        // A fast wheel gesture must never unexpectedly jump to a distant order.
        int next = Math.max(0, Math.min(selectable.size() - 1, start + direction * stepsToMove));
        setFocused(selectable.get(next));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
