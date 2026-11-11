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
    private static final Component NO_SQUAD_HELP = Component.literal("Нет доступных волков: приручите волка костями, затем подойдите к нему на расстояние приказов.");
    public ReputationArmyScreen(int available) {
        super(Component.literal("Отряд — приказы"));
        this.available = available;
    }

    @Override protected void init() {
        int w = Math.max(24, Math.min(238, width - 16));
        int x = (width - w) / 2;
        int y = panelTop();
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int firstButtonY = y + headerHeight();
        Button follow = Button.builder(commandLabel(1, "За мной", w - 16), b -> issue(0))
            .bounds(x + 8, firstButtonY, w - 16, buttonHeight).build();
        follow.setTooltip(Tooltip.create(Component.literal("Волки следуют за вами и выходят из режима охраны.")));
        if (available == 0) follow.setTooltip(Tooltip.create(NO_SQUAD_HELP));
        follow.active = available > 0;
        addRenderableWidget(follow);
        Button guard = Button.builder(commandLabel(2, "Охранять позицию", w - 16), b -> issue(1))
            .bounds(x + 8, firstButtonY + buttonHeight + buttonGap, w - 16, buttonHeight).build();
        guard.setTooltip(Tooltip.create(Component.literal("Волки остаются охранять текущую позицию.")));
        if (available == 0) guard.setTooltip(Tooltip.create(NO_SQUAD_HELP));
        guard.active = available > 0;
        addRenderableWidget(guard);
        Button rally = Button.builder(commandLabel(3, "Собрать рядом", w - 16), b -> issue(2))
            .bounds(x + 8, firstButtonY + 2 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        rally.setTooltip(Tooltip.create(Component.literal("Позвать принадлежащих вам волков к себе.")));
        if (available == 0) rally.setTooltip(Tooltip.create(NO_SQUAD_HELP));
        rally.active = available > 0;
        addRenderableWidget(rally);
        Button disengage = Button.builder(commandLabel(4, "Отступить ко мне", w - 16), b -> issue(3))
            .bounds(x + 8, firstButtonY + 3 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        disengage.setTooltip(Tooltip.create(Component.literal("Волки прекращают бой, встают и возвращаются к вам. Близкие волки уже в безопасности.")));
        if (available == 0) disengage.setTooltip(Tooltip.create(NO_SQUAD_HELP));
        disengage.active = available > 0;
        addRenderableWidget(disengage);
        Button attack = Button.builder(commandLabel(5, "Атаковать отмеченного монстра", w - 16), b -> issue(4))
            .bounds(x + 8, firstButtonY + 4 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        attack.setTooltip(Tooltip.create(Component.literal("Ударьте монстра не более 10 секунд назад. Вы и волки должны видеть цель в радиусе 24 блоков.")));
        if (available == 0) attack.setTooltip(Tooltip.create(NO_SQUAD_HELP));
        attack.active = available > 0;
        addRenderableWidget(attack);
        Button waypoint = Button.builder(commandLabel(6, "К точке взгляда", w - 16), b -> issue(5))
            .bounds(x + 8, firstButtonY + 5 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        waypoint.setTooltip(Tooltip.create(Component.literal("Наведите взгляд на блок не дальше 24 блоков. Волки попытаются добежать до этой точки.")));
        if (available == 0) waypoint.setTooltip(Tooltip.create(NO_SQUAD_HELP));
        waypoint.active = available > 0;
        addRenderableWidget(waypoint);
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(x + 8, firstButtonY + 6 * (buttonHeight + buttonGap), w - 16, buttonHeight).build());
    }

    // Numbered actions make the keyboard shortcuts discoverable without a separate
    // tooltip. Fit labels into narrow GUI-scaled windows at native pixel resolution.
    private Component commandLabel(int shortcut, String name, int buttonWidth) {
        String prefix = shortcut + " · ";
        int textWidth = Math.max(1, buttonWidth - 12 - font.width(prefix));
        return Component.literal(prefix + font.plainSubstrByWidth(name, textWidth));
    }

    // Fit all six commands into short GUI-scaled windows without fractional font scaling.
    private int headerHeight() { return height < 105 ? 16 : height < 120 ? 26 : height < 165 ? 37 : 48; }
    private int commandButtonGap() { return height < 240 ? (height < 165 ? 1 : 2) : 6; }
    private int commandButtonHeight() {
        // Reserve room for all six orders, Close and the frame at medium GUI heights.
        // Fixed 20px buttons previously pushed Close off-screen at heights 165-235.
        int space = height - 12 - headerHeight() - 6 * commandButtonGap();
        return Math.max(7, Math.min(20, space / 7));
    }

    private int panelTop() {
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int contentHeight = headerHeight() + 7 * buttonHeight + 6 * buttonGap + 8;
        return Math.max(4, (height - contentHeight) / 2);
    }

    private void issue(int order) {
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
        // Compact windows cannot fit the secondary squad-count line: show the
        // actionable squad state in the heading instead of silently hiding it.
        String headingText = height < 120
            ? (available == 0 ? "Нет отряда" : "Отряд: " + available)
            : title.getString();
        String heading = font.plainSubstrByWidth(headingText, Math.max(1, w - 16));
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + (height < 105 ? 3 : 8), 0xFF45301B, false);
        String count = available == 0 ? "Приручите волка и подойдите ближе" : "Ваши волки рядом: " + available;
        if (height >= 120) {
            g.drawString(font, font.plainSubstrByWidth(count, w - 16), x + 8, y + 25, 0xFF45301B, false);
        }
        // On compact GUI heights the first button starts at y + 37.
        // Omit this optional hint rather than drawing text over a clickable command.
        if (height >= 165) {
            String range = "1–6 / Num 1–6 · Радиус 24 блока";
            g.drawString(font, font.plainSubstrByWidth(range, w - 16), x + 8, y + 35, 0xFF705438, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // Number row and numeric keypad mirror the six visible squad commands.
    // Disabled commands must never send packets when no owned squad is nearby.
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int order = -1;
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_6) {
            order = keyCode - GLFW.GLFW_KEY_1;
        } else if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_6) {
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
            if (available > 0) {
                issue(order);
            } else if (minecraft != null && minecraft.player != null) {
                // Keyboard shortcuts need the same guidance as disabled buttons.
                // Keep the order server-authoritative: never send an empty-squad packet.
                minecraft.player.displayClientMessage(NO_SQUAD_HELP, true);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
