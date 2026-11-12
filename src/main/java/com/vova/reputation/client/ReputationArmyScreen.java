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
    private final int available;
    private final java.util.List<Button> orderButtons = new java.util.ArrayList<>();
    private Button closeButton;
    private double pendingWheelScroll;
    private boolean lastOrdersEnabled;
    private String lastUnavailableReason = "";
    private static final Component NO_SQUAD_HELP = Component.literal("Нет доступных волков: приручите волка костями, затем подойдите к нему на расстояние приказов.");
    private static final Component INACTIVE_PLAYER_HELP = Component.literal("Нельзя отдавать приказы отряду в режиме наблюдателя или после гибели.");

    private boolean canIssueOrders() {
        return available > 0 && minecraft != null && minecraft.player != null
            && minecraft.player.isAlive() && !minecraft.player.isSpectator();
    }

    private Component unavailableHelp() {
        return minecraft != null && minecraft.player != null
            && (!minecraft.player.isAlive() || minecraft.player.isSpectator())
            ? INACTIVE_PLAYER_HELP : NO_SQUAD_HELP;
    }

    public ReputationArmyScreen(int available) {
        super(Component.literal("Отряд — приказы"));
        this.available = available;
    }

    @Override protected void init() {
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
        follow.setTooltip(Tooltip.create(Component.literal("За мной — волки следуют за вами и выходят из режима охраны.")));
        if (!canIssueOrders()) follow.setTooltip(Tooltip.create(unavailableHelp()));
        follow.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(follow));
        Button guard = Button.builder(commandLabel(2, "Охранять позицию", w - 16), b -> issue(1))
            .bounds(x + 8, firstButtonY + buttonHeight + buttonGap, w - 16, buttonHeight).build();
        guard.setTooltip(Tooltip.create(Component.literal("Охранять позицию — волки остаются охранять текущую позицию.")));
        if (!canIssueOrders()) guard.setTooltip(Tooltip.create(unavailableHelp()));
        guard.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(guard));
        Button rally = Button.builder(commandLabel(3, "Собрать рядом", w - 16), b -> issue(2))
            .bounds(x + 8, firstButtonY + 2 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        rally.setTooltip(Tooltip.create(Component.literal("Собрать рядом — позвать принадлежащих вам волков к себе.")));
        if (!canIssueOrders()) rally.setTooltip(Tooltip.create(unavailableHelp()));
        rally.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(rally));
        Button disengage = Button.builder(commandLabel(4, "Отступить ко мне", w - 16), b -> issue(3))
            .bounds(x + 8, firstButtonY + 3 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        disengage.setTooltip(Tooltip.create(Component.literal("Отступить ко мне — волки прекращают бой, встают и возвращаются к вам. Близкие волки уже в безопасности.")));
        if (!canIssueOrders()) disengage.setTooltip(Tooltip.create(unavailableHelp()));
        disengage.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(disengage));
        Button attack = Button.builder(commandLabel(5, "Атаковать отмеченного монстра", w - 16), b -> issue(4))
            .bounds(x + 8, firstButtonY + 4 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        attack.setTooltip(Tooltip.create(Component.literal("Атаковать отмеченного монстра — ударьте монстра не более 10 секунд назад. Вы и волки должны видеть цель в радиусе 24 блоков.")));
        if (!canIssueOrders()) attack.setTooltip(Tooltip.create(unavailableHelp()));
        attack.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(attack));
        Button waypoint = Button.builder(commandLabel(6, "К точке взгляда", w - 16), b -> issue(5))
            .bounds(x + 8, firstButtonY + 5 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        waypoint.setTooltip(Tooltip.create(Component.literal("К точке взгляда — наведите взгляд на блок не дальше 24 блоков. Волки попытаются добежать до этой точки.")));
        if (!canIssueOrders()) waypoint.setTooltip(Tooltip.create(unavailableHelp()));
        waypoint.active = canIssueOrders();
        orderButtons.add(addRenderableWidget(waypoint));
        closeButton = addRenderableWidget(Button.builder(commandLabel(7, "Закрыть", w - 16), b -> onClose())
            .bounds(x + 8, firstButtonY + 6 * (buttonHeight + buttonGap), w - 16, buttonHeight).build());
        // Keyboard/controller navigation should start on an actionable control.
        // When the squad is unavailable, focus Close instead of a disabled order.
        setInitialFocus(canIssueOrders() ? follow : closeButton);
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
            if (!enabled && closeButton != null) setFocused(closeButton);
            else if (enabled && !orderButtons.isEmpty()) setFocused(orderButtons.get(0));
        }
        if (!enabled) {
            for (Button button : orderButtons) button.setTooltip(Tooltip.create(unavailableHelp()));
        } else {
            // Restore the individual command explanations when the player becomes active.
            String[] hints = {
                "За мной — волки следуют за вами и выходят из режима охраны.",
                "Охранять позицию — волки остаются охранять текущую позицию.",
                "Собрать рядом — позвать принадлежащих вам волков к себе.",
                "Отступить ко мне — волки прекращают бой, встают и возвращаются к вам. Близкие волки уже в безопасности.",
                "Атаковать отмеченного монстра — ударьте монстра не более 10 секунд назад. Вы и волки должны видеть цель в радиусе 24 блоков.",
                "К точке взгляда — наведите взгляд на блок не дальше 24 блоков. Волки попытаются добежать до этой точки."
            };
            for (int i = 0; i < orderButtons.size(); i++) {
                orderButtons.get(i).setTooltip(Tooltip.create(Component.literal(hints[i])));
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
    private int commandButtonGap() { return height < 240 ? (height < 165 ? 1 : 2) : 6; }
    private int commandButtonHeight() {
        // Reserve room for all six orders, Close and the frame at medium GUI heights.
        // Fixed 20px buttons previously pushed Close off-screen at heights 165-235.
        int space = height - 12 - headerHeight() - 6 * commandButtonGap();
        return Math.max(4, Math.min(20, space / 7));
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
        // Windows below 165 GUI pixels cannot fit the secondary squad-count line: show the
        // actionable squad state in the heading instead of silently hiding it.
        // Show why commands are disabled directly in the panel, not only in tooltips.
        // Spectators and dead players may still have a nonzero cached squad count.
        boolean playerInactive = minecraft != null && minecraft.player != null
            && (!minecraft.player.isAlive() || minecraft.player.isSpectator());
        String headingText = playerInactive
            ? (minecraft.player.isSpectator() ? "Режим наблюдателя" : "Игрок погиб")
            : height < 165
                ? (available == 0 ? "Нет отряда" : "Отряд: " + available)
                : title.getString();
        String heading = font.plainSubstrByWidth(headingText, Math.max(1, w - 16));
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + (height < 105 ? 1 : 8), 0xFF45301B, false);
        String count = playerInactive ? "Приказы временно недоступны"
            : available == 0 ? "Приручите волка и подойдите ближе" : "Ваши волки рядом: " + available;
        if (height >= 165) {
            g.drawString(font, font.plainSubstrByWidth(count, w - 16), x + 8, y + 25, 0xFF45301B, false);
        }
        // Only taller GUI heights have room for the optional range hint.
        // Omit this optional hint rather than drawing text over a clickable command.
        // The compact 37px header (GUI heights 165-239) has no room for a
        // third text row: its 9px glyphs would overlap the first order button.
        // Reserve the range hint for the 48px header on taller windows.
        if (height >= 240) {
            String range = "1–7 / Num 1–7 · Радиус 24 блока";
            g.drawString(font, font.plainSubstrByWidth(range, w - 16), x + 8, y + 35, 0xFF705438, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // Number row and numeric keypad mirror all seven visible panel actions.
    // Disabled commands must never send packets when no owned squad is nearby.
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int order = -1;
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_7) {
            order = keyCode - GLFW.GLFW_KEY_1;
        } else if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_7) {
            order = keyCode - GLFW.GLFW_KEY_KP_1;
        }
        // Use the configured army keybind (including remapped keys) to close the panel.
        if (ReputationKeys.ARMY.matches(keyCode, scanCode)) {
            onClose();
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Mouse wheel moves keyboard focus through the tactical commands without
    // sending any order. This allows quick one-handed selection while preserving
    // Enter/Space activation and the existing numbered shortcuts.
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || !Double.isFinite(scrollY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        // Trackpads send fractional deltas. Accumulate them so a tiny gesture
        // does not skip an entire command, while preserving fast wheel input.
        if (pendingWheelScroll != 0 && Math.signum(pendingWheelScroll) != Math.signum(scrollY)) pendingWheelScroll = 0;
        pendingWheelScroll += scrollY;
        if (Math.abs(pendingWheelScroll) < 1.0) return true;
        int wholeSteps = (int) Math.min(1000, Math.floor(Math.abs(pendingWheelScroll)));
        pendingWheelScroll -= Math.copySign(wholeSteps, pendingWheelScroll);
        java.util.List<Button> selectable = new java.util.ArrayList<>();
        for (Button button : orderButtons) if (button.active) selectable.add(button);
        if (closeButton != null && closeButton.active) selectable.add(closeButton);
        if (selectable.isEmpty()) return true;
        int current = selectable.indexOf(getFocused());
        // A single event may contain several wheel notches. Honour each notch
        // rather than silently dropping fast wheel/trackpad input; cap the
        // number of steps to keep unusually large deltas predictable.
        int direction = scrollY > 0 ? -1 : 1;
        int steps = wholeSteps % selectable.size();
        int start = current < 0 ? (direction > 0 ? -1 : 0) : current;
        int next = Math.floorMod(start + direction * steps, selectable.size());
        setFocused(selectable.get(next));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
